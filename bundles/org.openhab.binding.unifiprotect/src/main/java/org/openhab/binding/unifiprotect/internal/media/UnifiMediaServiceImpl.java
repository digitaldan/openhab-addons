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
import java.util.*;
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
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.ThingUID;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.*;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the WebRtcMediaService interface.
 *
 * @author Dan Cunningham - Initial contribution
 */
@Component(service = UnifiMediaService.class, immediate = true, configurationPid = "org.binding.unifiprotect", property = Constants.SERVICE_PID
        + "=" + "org.binding.unifiprotect")
@ConfigurableService(category = "binding", label = "UnifiProtect", description_uri = "binding:unifiprotect")
@NonNullByDefault
public class UnifiMediaServiceImpl implements UnifiMediaService {
    private final Logger logger = LoggerFactory.getLogger(UnifiMediaServiceImpl.class);
    private UnifiProtectConfiguration config = new UnifiProtectConfiguration();
    private final Map<String, List<URI>> streams = new ConcurrentHashMap<>();
    private final Map<ThingUID, UnifiProtectCameraHandler> handlers = new ConcurrentHashMap<>();
    private final String servletBasePath = "/" + UnifiProtectBindingConstants.BINDING_ID + "/media";
    private final String playBasePath = servletBasePath + "/play";
    private final String imageBasePath = servletBasePath + "/image";
    @Nullable
    private Go2RtcManager go2rtc;
    private HttpService httpService;
    private NetworkAddressService networkAddressService;
    private HttpClient httpClient;
    private String proxiedBasePath = "http://127.0.0.1:1984"; // go2rtc API base
    private static final Path CACHE_DIR = Paths.get(OpenHAB.getUserDataFolder(), "cache",
            "org.openhab.binding.unifiprotect", "binaries");

    @Activate
    public UnifiMediaServiceImpl(@Reference HttpService httpService, @Reference HttpClientFactory httpClientFactory,
            @Reference NetworkAddressService networkAddressService) {
        this.httpService = httpService;
        this.networkAddressService = networkAddressService;
        this.httpClient = httpClientFactory.createHttpClient(UnifiProtectBindingConstants.BINDING_ID,
                new SslContextFactory.Client(true));
        try {
            this.httpClient.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Activate
    protected void activate(Map<String, Object> properties) {
        logger.debug("Activating WebRtcMediaServiceImpl with properties: {}", properties.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", ")));
        config = (new Configuration(properties)).as(UnifiProtectConfiguration.class);
        NativeHelper nativeHelper = new NativeHelper(CACHE_DIR, config.downloadBinaries, httpClient);

        try {
            // Ensure binaries upfront
            Path ffmpegBin = nativeHelper.ensureFfmpeg();
            Path go2rtcBin = nativeHelper.ensureGo2Rtc();

            // Pass ffmpeg path supplier so go2rtc gets PATH updated
            go2rtc = new Go2RtcManager(CACHE_DIR, () -> {
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
            });

            applyYamlAndEnsureRunning();

            httpService.registerServlet(playBasePath, new PlayStreamServlet(proxiedBasePath, httpClient), null, null);
            httpService.registerServlet(imageBasePath, new ImageServlet(this), null, null);
        } catch (IOException | ServletException | NamespaceException e) {
            logger.warn("Failed to activate WebRtcMediaServiceImpl", e);
        }
    }

    @Deactivate
    protected void deactivate() {
        try {
            if (go2rtc != null)
                go2rtc.destroy();
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
    public void registerStream(String streamId, List<URI> sources) {
        streams.put(streamId, sources);
        rebuildAndApplyYaml();
    }

    @Override
    public void unregisterStream(String streamId) {
        streams.remove(streamId);
        rebuildAndApplyYaml();
    }

    @Override
    public void registerHandler(UnifiProtectCameraHandler handler) {
        handlers.put(handler.getThing().getUID(), handler);
    }

    @Override
    public void unregisterHandler(UnifiProtectCameraHandler handler) {
        handlers.remove(handler.getThing().getUID());
    }

    @Override
    @Nullable
    public UnifiProtectCameraHandler getHandler(ThingUID thingUID) {
        return handlers.get(thingUID);
    }

    @Override
    public boolean isHealthy() {
        return go2rtc != null && go2rtc.isHealthy();
    }

    @Override
    public String getPlayBasePath() {
        return playBasePath;
    }

    @Override
    public String getImageBasePath() {
        return imageBasePath;
    }

    private void rebuildAndApplyYaml() {
        logger.debug("Rebuilding and applying YAML");
        Go2RtcManager go2rtc = this.go2rtc;
        if (go2rtc == null) {
            logger.debug("Go2rtc not initialized, skipping rebuild");
            return;
        }
        if (streams.isEmpty()) {
            logger.debug("No streams to apply, stopping go2rtc");
            go2rtc.stop();
            return;
        }
        Go2RtcConfigBuilder b = new Go2RtcConfigBuilder("127.0.0.1", 1984).ffmpegOptions(config.ffmpegOptions)
                .stun("stun:stun.l.google.com:19302");
        String primaryIpv4HostAddress = networkAddressService.getPrimaryIpv4HostAddress();
        if (primaryIpv4HostAddress != null) {
            b.candidates(primaryIpv4HostAddress + ":8555");
        }
        streams.forEach((id, uris) -> b.addStreams(id, uris));
        String yaml = b.build();
        try {
            logger.trace("Applying YAML: {}", yaml);
            go2rtc.applyConfig(yaml);
        } catch (IOException e) {
            logger.warn("Failed to apply YAML and ensure running", e);
        }
    }

    private void applyYamlAndEnsureRunning() throws IOException {
        Go2RtcManager go2rtc = this.go2rtc;
        if (go2rtc == null) {
            logger.debug("Go2rtc not initialized, skipping applyYamlAndEnsureRunning");
            return;
        }
        go2rtc.startIfNeeded();
    }
}
