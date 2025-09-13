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
package org.openhab.binding.unifiaccess.internal.handler;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.binding.unifiaccess.internal.UnifiAccessBindingConstants;
import org.openhab.binding.unifiaccess.internal.UnifiAccessDiscoveryService;
import org.openhab.binding.unifiaccess.internal.api.UniFiAccessApiClient;
import org.openhab.binding.unifiaccess.internal.config.UnifiAccessBridgeConfiguration;
import org.openhab.binding.unifiaccess.internal.dto.Device;
import org.openhab.binding.unifiaccess.internal.dto.Door;
import org.openhab.binding.unifiaccess.internal.dto.Notification;
import org.openhab.binding.unifiaccess.internal.dto.Notification.BaseInfoData;
import org.openhab.binding.unifiaccess.internal.dto.Notification.DeviceUpdateData;
import org.openhab.binding.unifiaccess.internal.dto.Notification.DeviceUpdateV2Data;
import org.openhab.binding.unifiaccess.internal.dto.Notification.DeviceUpdateV2Data.LocationState;
import org.openhab.binding.unifiaccess.internal.dto.Notification.LocationUpdateV2Data;
import org.openhab.binding.unifiaccess.internal.dto.Notification.LogsAddData;
import org.openhab.binding.unifiaccess.internal.dto.Notification.LogsInsightsAddData;
import org.openhab.binding.unifiaccess.internal.dto.Notification.RemoteUnlockData;
import org.openhab.binding.unifiaccess.internal.dto.Notification.RemoteViewChangeData;
import org.openhab.binding.unifiaccess.internal.dto.Notification.RemoteViewData;
import org.openhab.binding.unifiaccess.internal.dto.UniFiAccessHttpException;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Bridge handler that manages the UniFi Access API client, background polling,
 * and webhook servlet registration.
 * 
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiAccessBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(UnifiAccessBridgeHandler.class);

    private final HttpClient httpClient;
    private final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    private @Nullable UniFiAccessApiClient apiClient;

    private @Nullable UnifiAccessBridgeConfiguration config;
    private @Nullable ScheduledFuture<?> reconnectFuture;
    private int reconnectAttempts;

    public UnifiAccessBridgeHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        httpClient = httpClientFactory.createHttpClient(UnifiAccessBindingConstants.BINDING_ID, new SslContextFactory.Client(true));
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(UnifiAccessDiscoveryService.class);
    }

    @Override
    public void initialize() {
        config = getConfigAs(UnifiAccessBridgeConfiguration.class);

        updateStatus(ThingStatus.UNKNOWN);

        scheduler.execute(() -> {
            try {
                UnifiAccessBridgeConfiguration cfg = this.config;
                if (cfg == null || cfg.baseUrl == null || cfg.baseUrl.isBlank()) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Missing baseUrl");
                    return;
                }
                String token = Objects.requireNonNullElse(cfg.authToken, "");
                Duration timeout = Duration.ofSeconds(Math.max(1, cfg.requestTimeout));

                // Build base URI, applying configured port if baseUrl lacks an explicit one
                URI configuredBase = URI.create(cfg.baseUrl);
                if (cfg.port > 0 && configuredBase.getHost() != null && configuredBase.getPort() == -1) {
                    configuredBase = new URI(configuredBase.getScheme(), configuredBase.getUserInfo(),
                            configuredBase.getHost(), cfg.port, configuredBase.getPath(), configuredBase.getQuery(),
                            configuredBase.getFragment());
                }
                if(!httpClient.isStarted()) {
                    httpClient.start();
                }
                UniFiAccessApiClient client = new UniFiAccessApiClient(httpClient, configuredBase, gson, timeout,
                        token);
                apiClient = client;

                // Connectivity check
                List<Device> devices = client.getDevices();
                logger.debug("UniFi Access connectivity OK - {} devices", devices != null ? devices.size() : 0);

                // Start polling
                connect();
            } catch (Exception e) {
                logger.warn("Bridge initialization failed: {}", e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, e.getMessage());
            }
        });
    }

    @Override
    public void dispose() {
        // Close notifications WebSocket
        try {
            UniFiAccessApiClient client = this.apiClient;
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            logger.debug("Failed to close notifications WebSocket: {}", e.getMessage());
        }

        // Cancel any pending reconnect task
        cancelReconnect();
        if (httpClient != null) {
            try {
                httpClient.stop();
            } catch (Exception ignored) {
            }
        }
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::pollOnce);
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        logger.debug("Child handler initialized: {}", childHandler);
        if (childHandler instanceof UnifiAccessDoorHandler handler) {
            if (getThing().getStatus() == ThingStatus.ONLINE) {
                handler.refreshState();
            }
        }
    }

    private void connect() {
        // Start WebSocket notifications (best-effort)
        UniFiAccessApiClient client = this.apiClient;
        if (client != null) {
            try {
                client.openNotifications(() -> {
                    logger.info("Notifications WebSocket opened");
                    updateStatus(ThingStatus.ONLINE);
                    // Reset backoff on successful connection
                    reconnectAttempts = 0;
                    scheduler.execute(UnifiAccessBridgeHandler.this::pollOnce);
                }, notification -> {

                    logger.debug("Notification event: {} data: {}", notification.event, notification.data);
                    // Basic typed parsing for known events
                    try {
                        switch (notification.event) {
                            // When a doorbell rings
                            case "access.remote_view":
                                RemoteViewData rv = notification.dataAsRemoteView(gson);
                                logger.trace("Parsed remote_view: {}", rv);
                                break;
                            // Doorbell status change
                            case "access.remote_view.change":
                                RemoteViewChangeData rvc = notification.dataAsRemoteViewChange(gson);
                                // logger.trace("Parsed remote_view.change: {}", rvc);
                                break;
                            // Remote door unlock by admin
                            case "access.data.device.remote_unlock":
                                RemoteUnlockData ru = notification.dataAsRemoteUnlock(gson);
                                logger.debug("Device remote unlock: {}", ru.name);
                                handleRemoteUnlock(ru);
                                break;
                            case "access.data.device.update":
                                DeviceUpdateData du = notification.dataAsDeviceUpdate(gson);
                                // logger.trace("Parsed device.update: {}", du);
                                break;
                            case "access.data.v2.device.update":
                                DeviceUpdateV2Data du2 = notification.dataAsDeviceUpdateV2(gson);
                                LocationState ls = du2.locationStates.getFirst();
                                if (ls != null) {
                                    logger.debug("Device update v2: {} dps: {} lock: {}", du2.name, ls.dps, ls.lock);
                                    try {
                                        handleDeviceUpdateV2(du2, ls);
                                    } catch (Exception ex) {
                                        logger.debug("Failed to handle device update: {}", ex.getMessage());
                                    }
                                }
                                break;
                            case "access.data.v2.location.update":
                                LocationUpdateV2Data lu2 = notification.dataAsLocationUpdateV2(gson);
                                // logger.trace("Parsed v2.location.update: {}", lu2);
                                break;
                            case "access.logs.insights.add":
                                LogsInsightsAddData lia = notification.dataAsLogsInsightsAdd(gson);
                                // logger.trace("Parsed logs.insights.add: {}", lia);
                                break;
                            case "access.logs.add":
                                LogsAddData la = notification.dataAsLogsAdd(gson);
                                // logger.trace("Parsed logs.add: {}", la);
                                break;
                            case "access.base.info":
                                BaseInfoData bi = notification.dataAsBaseInfo(gson);
                                // logger.trace("Parsed base.info: {}", bi);
                                break;
                            default:
                                // leave as raw
                                break;
                        }
                    } catch (Exception ex) {
                        logger.debug("Failed to parse typed notification for {}: {}", notification.event,
                                ex.getMessage());
                    }
                }, error -> {
                    logger.warn("Notifications error: {}", error.getMessage());
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                            error.getMessage());
                    scheduleReconnect();
                }, (statusCode, reason) -> {

                    logger.info("Notifications closed: {} - {}", statusCode, reason);
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, reason);
                    scheduleReconnect();
                });
            } catch (Exception e) {
                logger.warn("Failed to open notifications WebSocket: {}", e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, e.getMessage());
                scheduleReconnect();
            }
        }
    }

    private void scheduleReconnect() {
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            return;
        }
        int attempt = Math.min(reconnectAttempts, 5);
        long delaySeconds = Math.min(60, 2L << attempt); // 2,4,8,16,32,60
        reconnectAttempts++;
        reconnectFuture = scheduler.schedule(() -> {
            try {
                connect();
            } catch (Exception ex) {
                logger.debug("Reconnect attempt failed to schedule connect: {}", ex.getMessage());
            }
        }, delaySeconds, java.util.concurrent.TimeUnit.SECONDS);
        logger.info("Scheduled reconnect in {}s (attempt #{})", delaySeconds, reconnectAttempts);
    }

    private void cancelReconnect() {
        try {
            ScheduledFuture<?> f = reconnectFuture;
            if (f != null) {
                f.cancel(true);
            }
        } catch (Exception ignored) {
        } finally {
            reconnectFuture = null;
        }
    }

    private void pollOnce() {
        UniFiAccessApiClient client = this.apiClient;
        if (client == null) {
            return;
        }
        try {
            List<Door> doors = client.getDoors();
            logger.trace("Polled UniFi Access: {} doors", doors != null ? doors.size() : 0);
            if (doors != null && !doors.isEmpty()) {
                for (Thing t : getThing().getThings()) {
                    if (UnifiAccessBindingConstants.DOOR_THING_TYPE.equals(t.getThingTypeUID())) {
                        if (t.getHandler() instanceof UnifiAccessDoorHandler dh) {
                            String did = String
                                    .valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DOOR_ID));
                            Door match = doors.stream().filter(x -> did.equals(x.id)).findFirst().orElse(null);
                            if (match != null) {
                                dh.updateFromDoor(match);
                            }
                        }
                    }
                }
            }
        } catch (UniFiAccessHttpException e) {
            logger.debug("Polling error: {}", e.getMessage());
        }
    }


    public @Nullable UniFiAccessApiClient getApiClient() {
        return apiClient;
    }

    public @Nullable UnifiAccessBridgeConfiguration getUaConfig() {
        return config;
    }

    private void handleRemoteUnlock(Notification.RemoteUnlockData data) {
        String doorId = data.uniqueId;
        for (Thing t : getThing().getThings()) {
            if (UnifiAccessBindingConstants.DOOR_THING_TYPE.equals(t.getThingTypeUID())) {
                String did = String.valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DOOR_ID));
                if (doorId.equals(did) && t.getHandler() instanceof UnifiAccessDoorHandler dh) {
                    dh.setLastUnlock(data.fullName, System.currentTimeMillis());
                }
            }
        }
    }

    private void handleDeviceUpdateV2(Notification.DeviceUpdateV2Data updateData, LocationState ls) {
        if (updateData == null || ls == null) {
            return;
        }
        // du2.name is device name; ls.locationId corresponds to door id
        String locationId = ls.locationId;
        for (Thing t : getThing().getThings()) {
            if (UnifiAccessBindingConstants.DOOR_THING_TYPE.equals(t.getThingTypeUID())) {
                String did = String.valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DOOR_ID));
                if (locationId != null && locationId.equals(did)
                        && t.getHandler() instanceof UnifiAccessDoorHandler dh) {
                    if (ls.lock != null) {
                        dh.updateLock(ls.lock);
                    }
                    if (ls.dps != null) {
                        dh.updatePosition(ls.dps);
                    }
                }
            }
        }
    }
}
