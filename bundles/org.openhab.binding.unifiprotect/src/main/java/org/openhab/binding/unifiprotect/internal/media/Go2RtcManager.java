/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.unifiprotect.internal.media;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Go2RtcManager.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class Go2RtcManager {
    private final Logger logger = LoggerFactory.getLogger(Go2RtcManager.class);
    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "go2rtc-supervisor");
        t.setDaemon(true);
        return t;
    });
    private final Path workDir; // ${userdata}/unifiprotect
    private final Path configFile; // ${userdata}/cache/unifiprotect/go2rtc.yaml
    private final Supplier<Path> go2rtcPathSupplier; // NativeHelper::ensureGo2Rtc
    private final Supplier<Path> ffmpegPathSupplier; // NativeHelper::ensureFfmpeg (optional)
    private final String listenHost = "127.0.0.1";
    private final int listenPort = 1984;
    private final Duration healthTimeout = Duration.ofSeconds(2);
    private final Duration restartDelay = Duration.ofSeconds(5);
    private boolean stopping = false;
    @Nullable
    private ScheduledFuture<?> tickFuture;
    @Nullable
    private Process process;
    @Nullable
    private ScheduledFuture<?> pendingRestart;

    public Go2RtcManager(Path workDir, Supplier<Path> go2rtcPathSupplier, Supplier<Path> ffmpegPathSupplier) {
        this.workDir = workDir;
        this.go2rtcPathSupplier = go2rtcPathSupplier;
        this.ffmpegPathSupplier = ffmpegPathSupplier;
        this.configFile = workDir.resolve("go2rtc.yaml");
    }

    /** Write yaml (atomically) and schedule a debounced (re)start if changed. */
    public synchronized void applyConfig(String yamlContent) throws IOException {
        logger.debug("Applying config: {}", configFile);
        Files.createDirectories(workDir);
        Files.writeString(configFile, yamlContent, StandardCharsets.UTF_8);
        scheduleRestart();
    }

    public synchronized void startIfNeeded() throws IOException {
        Process p1 = this.process;
        if (p1 != null && p1.isAlive()) {
            return;
        }
        logger.debug("Starting go2rtc with config: {}", configFile);
        Path bin = go2rtcPathSupplier.get(); // ensures download if needed
        List<String> cmd = new ArrayList<>();
        cmd.add(bin.toString());
        cmd.add("-config");
        cmd.add(configFile.toString());
        cmd.add(listenHost + ":" + listenPort);
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile()).redirectErrorStream(true);
        // Ensure ffmpeg is on PATH so go2rtc can invoke it directly
        try {
            if (ffmpegPathSupplier != null) {
                Path ffmpeg = ffmpegPathSupplier.get();
                if (ffmpeg != null && ffmpeg.getParent() != null) {
                    String ffmpegDir = ffmpeg.getParent().toString();
                    Map<String, String> env = pb.environment();
                    String pathKey = env.containsKey("Path") ? "Path" : (env.containsKey("PATH") ? "PATH" : "PATH");
                    String currentPath = env.getOrDefault(pathKey, "");
                    if (!currentPath.contains(ffmpegDir)) {
                        String updatedPath = ffmpegDir + java.io.File.pathSeparator + currentPath;
                        env.put(pathKey, updatedPath);
                        logger.debug("Prepended FFmpeg dir to {}: {}", pathKey, ffmpegDir);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Failed to prepend FFmpeg path for go2rtc process", e);
        }
        logger.debug("Starting go2rtc with in dir {} cmd: {} and args: {}", workDir.toFile(), bin,
                cmd.stream().collect(Collectors.joining(" ")));
        Process process = pb.start();
        this.process = process;
        // Ensure we don't lose track of exit and clear reference deterministically
        try {
            process.onExit().thenAccept(p -> {
                synchronized (Go2RtcManager.this) {
                    Process p2 = this.process;
                    if (p.equals(p2)) {
                        logger.debug("go2rtc process exited with code {}", p.exitValue());
                        this.process = null;
                    }
                }
            });
        } catch (Throwable t) {
            logger.debug("onExit hook not installed: {}", t.toString());
        }
        attachLogger(process);
        ScheduledFuture<?> tickFuture = this.tickFuture;
        if (tickFuture != null) {
            tickFuture.cancel(true);
        }
        this.tickFuture = exec.scheduleWithFixedDelay(this::tick, 1, 5, TimeUnit.SECONDS);
        logger.debug("Scheduled tick for go2rtc with config: {}", configFile);
    }

    private void attachLogger(Process process) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (logger.isTraceEnabled()) {
                        logger.trace("[{}] {}", "go2rtc", line);
                    }
                }
            } catch (IOException ignored) {
            }
        }, "go2rtc-logger");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void destroy() {
        stopping = true;
        ScheduledFuture<?> pr = this.pendingRestart;
        if (pr != null) {
            pr.cancel(true);
            this.pendingRestart = null;
        }
        Process process = this.process;
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(3, TimeUnit.SECONDS))
                    process.destroyForcibly();
            } catch (InterruptedException ignored) {
            }
        }
        exec.shutdownNow();
    }

    public void restart() throws IOException {
        stop();
        startIfNeeded();
    }

    public synchronized void stop() {
        logger.debug("Stopping go2rtc");
        ScheduledFuture<?> tickFuture = this.tickFuture;
        if (tickFuture != null) {
            tickFuture.cancel(true);
        }
        Process process = this.process;
        if (process != null) {
            try {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (Exception ignored) {
            }
            this.process = null;
        }
    }

    private void tick() {
        synchronized (this) {
            if (stopping) {
                return;
            }
            Process process = this.process;
            if (process == null || !process.isAlive()) {
                try {
                    startIfNeeded();
                } catch (IOException e) {
                    logger.debug("Failed to start go2rtc with config: {}", configFile, e);
                }
                return;
            }
            if (!isHealthy()) {
                try {
                    process.destroy();
                    if (!process.waitFor(2, TimeUnit.SECONDS))
                        process.destroyForcibly();
                    startIfNeeded();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public boolean isHealthy() {
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(healthTimeout).build();
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create("http://" + listenHost + ":" + listenPort + "/"))
                    .timeout(healthTimeout).GET().build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return res.statusCode() >= 200 && res.statusCode() < 500; // UI or 404 ok
        } catch (Exception e) {
            return false;
        }
    }

    public String getBaseUrl() {
        return "http://" + listenHost + ":" + listenPort;
    }

    private synchronized void scheduleRestart() {
        if (stopping) {
            return;
        }
        ScheduledFuture<?> pr = this.pendingRestart;
        if (pr != null) {
            pr.cancel(false);
        }
        long delayMs = restartDelay.toMillis();
        this.pendingRestart = exec.schedule(() -> {
            try {
                restart();
            } catch (IOException e) {
                logger.debug("Failed to restart go2rtc after debounce delay with config: {}", configFile, e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
        logger.debug("Scheduled go2rtc restart in {} ms", delayMs);
    }
}
