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

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.binding.unifiprotect.internal.UnifiProtectBindingConstants;
import org.openhab.binding.unifiprotect.internal.config.UnifiProtectConfiguration;
import org.openhab.binding.unifiprotect.internal.handler.UnifiProtectCameraHandler;
import org.openhab.core.OpenHAB;
import org.openhab.core.config.core.ConfigurableService;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.ThingUID;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the WebRtcMediaService interface.
 *
 * @author Dan Cunningham - Initial contribution
 */
@Component(service = UnifiMediaService.class, immediate = true, configurationPid = "org.openhab.unifiprotect", property = Constants.SERVICE_PID
        + "=" + "org.openhab.unifiprotect")
@ConfigurableService(category = "system", label = "UnifiProtect", description_uri = "binding:unifiprotect")
@NonNullByDefault
public class UnifiMediaServiceImpl implements UnifiMediaService {
    private static final Path CACHE_DIR = Paths.get(OpenHAB.getUserDataFolder(), "cache",
            "org.openhab.binding.unifiprotect");
    private static final Path BIN_DIR = CACHE_DIR.resolve("bin");
    private static final Path CONFIG_DIR = CACHE_DIR.resolve("config");

    private final Logger logger = LoggerFactory.getLogger(UnifiMediaServiceImpl.class);
    private final Map<ThingUID, Map<String, List<URI>>> cameraStreams = new ConcurrentHashMap<>();
    private final Map<ThingUID, UnifiProtectCameraHandler> handlers = new ConcurrentHashMap<>();
    private final String servletBasePath = "/" + UnifiProtectBindingConstants.BINDING_ID + "/media";
    private final String playBasePath = servletBasePath + "/play";
    private final String imageBasePath = servletBasePath + "/image";

    // Per-camera go2rtc instances and state
    private final Map<ThingUID, Go2RtcManager> managers = new ConcurrentHashMap<>();
    private final Map<ThingUID, Integer> apiPorts = new ConcurrentHashMap<>();
    private final Map<ThingUID, Integer> webrtcPorts = new ConcurrentHashMap<>();
    private final Map<ThingUID, Integer> rtspPorts = new ConcurrentHashMap<>();
    private int nextApiPort = 1984;
    private int nextRtspPort = 8554;
    private int nextWebrtcPort = 8555;

    private UnifiProtectConfiguration config = new UnifiProtectConfiguration();
    private HttpService httpService;
    private HttpClient httpClient;

    @Activate
    public UnifiMediaServiceImpl(@Nullable Map<String, Object> properties, @Reference HttpService httpService,
            @Reference HttpClientFactory httpClientFactory) {
        if (properties != null) {
            logger.debug("Initializing with properties: {}", properties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
            config = (new Configuration(properties)).as(UnifiProtectConfiguration.class);
        }

        this.httpService = httpService;
        this.httpClient = httpClientFactory.createHttpClient(UnifiProtectBindingConstants.BINDING_ID,
                new SslContextFactory.Client(true));
        try {
            this.httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        NativeHelper nativeHelper = new NativeHelper(BIN_DIR, config.downloadBinaries, httpClient);
        try {
            // Ensure binaries exist
            nativeHelper.ensureFfmpeg();
            nativeHelper.ensureGo2Rtc();

            httpService.registerServlet(playBasePath, new PlayStreamServlet(this, httpClient), null, null);
            httpService.registerServlet(imageBasePath, new ImageServlet(this), null, null);
        } catch (IOException | ServletException | NamespaceException e) {
            logger.warn("Failed to activate WebRtcMediaServiceImpl", e);
            throw new RuntimeException(e);
        }
    }

    @Modified
    protected void modified(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            config = (new Configuration(properties)).as(UnifiProtectConfiguration.class);
        }
    }

    @Deactivate
    protected void deactivate() {
        try {
            managers.values().forEach(m -> {
                try {
                    m.destroy();
                } catch (Exception ignored) {
                }
            });
            managers.clear();
        } catch (Exception ignored) {
        }
        try {
            httpClient.stop();
        } catch (Exception ignored) {
        }
        try {
            httpService.unregister(playBasePath);
            httpService.unregister(imageBasePath);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void registerHandler(UnifiProtectCameraHandler handler, Map<String, List<URI>> streams) {
        ThingUID uid = handler.getThing().getUID();
        handlers.put(uid, handler);
        cameraStreams.put(uid, streams);
        ensureManager(uid);
        rebuildAndApplyYaml(uid);
    }

    @Override
    public void unregisterHandler(UnifiProtectCameraHandler handler) {
        ThingUID uid = handler.getThing().getUID();
        handlers.remove(uid);
        cameraStreams.remove(uid);
        Go2RtcManager m = managers.remove(uid);
        apiPorts.remove(uid);
        webrtcPorts.remove(uid);
        rtspPorts.remove(uid);
        if (m != null) {
            try {
                m.destroy();
                m.deleteConfigFile();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    @Nullable
    public UnifiProtectCameraHandler getHandler(ThingUID thingUID) {
        return handlers.get(thingUID);
    }

    @Override
    public boolean isHealthy() {
        if (managers.isEmpty()) {
            return false;
        }
        for (Go2RtcManager m : managers.values()) {
            if (!m.isHealthy()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getPlayBasePath() {
        return playBasePath;
    }

    @Override
    public String getImageBasePath() {
        return imageBasePath;
    }

    private void rebuildAndApplyYaml(ThingUID uid) {
        logger.debug("Rebuilding and applying YAML for {}", uid);
        Go2RtcManager manager = managers.get(uid);
        if (manager != null) {
            int apiPort = apiPorts.getOrDefault(uid, 1984);
            int webrtcPort = webrtcPorts.getOrDefault(uid, 8555);
            int rtspPort = rtspPorts.getOrDefault(uid, 8554);

            Go2RtcConfigBuilder b = new Go2RtcConfigBuilder("127.0.0.1", apiPort).stun("stun:stun.l.google.com:19302")
                    .webrtcListen(webrtcPort).rtspListen(rtspPort);

            Map<String, List<URI>> streamsForUid = cameraStreams.get(uid);
            boolean hasAny = false;
            if (streamsForUid != null) {
                for (Map.Entry<String, List<URI>> e : streamsForUid.entrySet()) {
                    b.addStreams(e.getKey(), e.getValue());
                    hasAny = true;
                }
            }
            if (!hasAny) {
                logger.debug("No streams for {}, stopping go2rtc instance", uid);
                try {
                    manager.stop();
                } catch (Exception ignored) {
                }
                return;
            }
            String yaml = b.build();
            try {
                logger.trace("Applying YAML for {}: {}", uid, yaml);
                manager.applyConfig(yaml);
            } catch (IOException e) {
                logger.warn("Failed to apply YAML for {}", uid, e);
            }
        }
    }

    private synchronized void ensureManager(ThingUID uid) {
        if (managers.containsKey(uid)) {
            return;
        }
        // Allocate ports
        int apiPort = nextApiPort;
        int rtspPort = nextRtspPort;
        int webrtcPort = nextWebrtcPort;
        // increment in pairs to keep rtsp even, webrtc odd, aligned
        nextApiPort += 2;
        nextRtspPort += 2;
        nextWebrtcPort += 2;
        apiPorts.put(uid, apiPort);
        webrtcPorts.put(uid, webrtcPort);
        rtspPorts.put(uid, rtspPort);

        // Create a dedicated manager with config filename based on UID
        String configName = uid.getAsString() + ".yaml";
        NativeHelper nativeHelper = new NativeHelper(BIN_DIR, config.downloadBinaries, httpClient);
        try {
            Path go2rtcBin = nativeHelper.ensureGo2Rtc();
            Path ffmpegBin = nativeHelper.ensureFfmpeg();
            Go2RtcManager manager = new Go2RtcManager(BIN_DIR, CONFIG_DIR, () -> {
                try {
                    return go2rtcBin;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, () -> {
                try {
                    return ffmpegBin;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, "127.0.0.1", apiPort, configName);
            managers.put(uid, manager);
            manager.startIfNeeded();
        } catch (IOException e) {
            logger.warn("Failed to create go2rtc manager for {}", uid, e);
        }
    }

    @Nullable
    private ThingUID extractThingUID(String streamId) {
        try {
            String[] parts = streamId.split(":");
            // Expect 4 segments for a handler UID; if 5, the last is quality
            if (parts.length == 5) {
                String candidate = String.join(":", parts[0], parts[1], parts[2], parts[3]);
                return new ThingUID(candidate);
            } else {
                return new ThingUID(streamId);
            }
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @Nullable
    public String getGo2RtcBaseForStream(String streamId) {
        ThingUID uid = extractThingUID(streamId);
        if (uid == null) {
            return null;
        }
        Go2RtcManager m = managers.get(uid);
        return m != null ? m.getBaseUrl() : null;
    }
}
