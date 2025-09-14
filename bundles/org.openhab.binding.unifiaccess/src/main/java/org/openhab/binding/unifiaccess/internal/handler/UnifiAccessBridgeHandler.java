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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

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
import org.openhab.binding.unifiaccess.internal.dto.Notification.DeviceUpdateV2Data;
import org.openhab.binding.unifiaccess.internal.dto.Notification.LocationUpdateV2Data;
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
 * Bridge handler that manages the UniFi Access API client
 * 
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiAccessBridgeHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(UnifiAccessBridgeHandler.class);
    private static final int DEFAULT_PORT = 12445;
    private static final String DEFAULT_PATH = "/api/v1/developer";
    private final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();
    private final HttpClient httpClient;
    private @Nullable UniFiAccessApiClient apiClient;
    private UnifiAccessBridgeConfiguration config = new UnifiAccessBridgeConfiguration();
    private @Nullable ScheduledFuture<?> reconnectFuture;
    private @Nullable UnifiAccessDiscoveryService discoveryService;
    private final Map<String, String> remoteViewRequestToDeviceId = new ConcurrentHashMap<>();

    public UnifiAccessBridgeHandler(Bridge bridge, HttpClientFactory httpClientFactory) {
        super(bridge);
        httpClient = httpClientFactory.createHttpClient(UnifiAccessBindingConstants.BINDING_ID,
                new SslContextFactory.Client(true));
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
                URI configuredBase = URI.create("https://" + config.host + ":" + DEFAULT_PORT + DEFAULT_PATH);
                if (!httpClient.isStarted()) {
                    httpClient.start();
                }
                logger.debug("Creating UniFiAccessApiClient with base: {} and token: {}", configuredBase,
                        config.authToken);
                apiClient = new UniFiAccessApiClient(httpClient, configuredBase, gson, config.authToken);
                connect();
            } catch (Exception e) {
                logger.warn("Bridge initialization failed: {}", e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, e.getMessage());
            }
        });
    }

    @Override
    public void dispose() {
        try {
            UniFiAccessApiClient client = this.apiClient;
            if (client != null) {
                client.close();
            }
        } catch (Exception e) {
            logger.debug("Failed to close notifications WebSocket: {}", e.getMessage());
        }
        cancelReconnect();
        try {
            httpClient.stop();
        } catch (Exception ignored) {
        }
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::syncDevices);
        }
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        logger.debug("Child handler initialized: {}", childHandler);
        if (childHandler instanceof UnifiAccessDoorHandler) {
            if (getThing().getStatus() == ThingStatus.ONLINE) {
                syncDevices();
            }
        } else if (childHandler instanceof UnifiAccessDeviceHandler) {
            if (getThing().getStatus() == ThingStatus.ONLINE) {
                syncDevices();
            }
        }
    }

    private void connect() {
        UniFiAccessApiClient client = this.apiClient;
        if (client != null) {
            try {
                client.openNotifications(() -> {
                    logger.info("Notifications WebSocket opened");
                    updateStatus(ThingStatus.ONLINE);
                    scheduler.execute(UnifiAccessBridgeHandler.this::syncDevices);
                }, notification -> {

                    logger.debug("Notification event: {} data: {}", notification.event, notification.data);
                    try {
                        switch (notification.event) {
                            // When a doorbell rings
                            case "access.remote_view":
                                RemoteViewData rv = notification.dataAsRemoteView(gson);
                                try {
                                    if (rv != null) {
                                        if (rv.requestId != null && rv.deviceId != null) {
                                            remoteViewRequestToDeviceId.put(rv.requestId, rv.deviceId);
                                        }
                                        if (rv.clearRequestId != null && rv.deviceId != null) {
                                            remoteViewRequestToDeviceId.put(rv.clearRequestId, rv.deviceId);
                                        }
                                        handleRemoteView(rv);
                                    }
                                } catch (Exception ex) {
                                    logger.debug("Failed to handle remote_view: {}", ex.getMessage());
                                }
                                break;
                            // Doorbell status change
                            case "access.remote_view.change":
                                RemoteViewChangeData rvc = notification.dataAsRemoteViewChange(gson);
                                try {
                                    handleRemoteViewChange(rvc);
                                } catch (Exception ex) {
                                    logger.debug("Failed to handle remote_view.change: {}", ex.getMessage());
                                }
                                break;
                            // Remote door unlock by admin
                            case "access.data.device.remote_unlock":
                                RemoteUnlockData ru = notification.dataAsRemoteUnlock(gson);
                                logger.debug("Device remote unlock: {}", ru.name);
                                handleRemoteUnlock(ru);
                                break;
                            case "access.data.device.update":
                                // TODO: handle device update which carries Online status
                                notification.dataAsDeviceUpdate(gson);
                                break;
                            case "access.data.v2.device.update":
                                DeviceUpdateV2Data du2 = notification.dataAsDeviceUpdateV2(gson);
                                try {
                                    handleDeviceUpdateV2(du2);
                                } catch (Exception ex) {
                                    logger.debug("Failed to handle device update: {}", ex.getMessage());
                                }

                                break;
                            case "access.data.v2.location.update":
                                LocationUpdateV2Data lu2 = notification.dataAsLocationUpdateV2(gson);
                                try {
                                    handleLocationUpdateV2(lu2);
                                } catch (Exception ex) {
                                    logger.debug("Failed to handle location update: {}", ex.getMessage());
                                }
                                break;
                            case "access.logs.insights.add":
                                notification.dataAsLogsInsightsAdd(gson);
                                break;
                            case "access.logs.add":
                                notification.dataAsLogsAdd(gson);
                                break;
                            case "access.base.info":
                                notification.dataAsBaseInfo(gson);
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
                    setOfflineAndReconnect(error.getMessage());
                }, (statusCode, reason) -> {
                    logger.info("Notifications closed: {} - {}", statusCode, reason);
                    setOfflineAndReconnect(reason);
                });
            } catch (Exception e) {
                logger.warn("Failed to open notifications WebSocket: {}", e.getMessage());
                setOfflineAndReconnect(e.getMessage());
            }
        }
    }

    public void setDiscoveryService(UnifiAccessDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    private void setOfflineAndReconnect(@Nullable String message) {
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null && !reconnectFuture.isDone()) {
            return;
        }
        String msg = message != null ? message : "Unknown error";
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR, msg);
        this.reconnectFuture = scheduler.schedule(() -> {
            try {
                connect();
            } catch (Exception ex) {
                logger.debug("Reconnect attempt failed to schedule connect: {}", ex.getMessage());
            }
        }, 5, java.util.concurrent.TimeUnit.SECONDS);
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

    private synchronized void syncDevices() {
        UniFiAccessApiClient client = this.apiClient;
        if (client == null) {
            return;
        }
        try {
            List<Door> doors = client.getDoors();
            List<Device> devices = client.getDevices();

            // TODO we need to use the isOnline status of the "device" to update both a
            // device, and a doors thing status

            // exclude any whose locationId matches a door id
            List<Device> filteredDevices = devices.stream()
                    .filter(device -> device.locationId == null
                            || !doors.stream().anyMatch(door -> door.id.equals(device.locationId)))
                    .collect(Collectors.toList());

            UnifiAccessDiscoveryService discoveryService = this.discoveryService;
            if (discoveryService != null) {
                discoveryService.discoverDoors(doors);
                discoveryService.discoverDevices(filteredDevices);
            }
            logger.trace("Polled UniFi Access: {} doors", doors.size());
            if (!doors.isEmpty()) {
                for (Thing t : getThing().getThings()) {
                    logger.trace("Checking thing: {} against {}", t.getThingTypeUID(),
                            UnifiAccessBindingConstants.DOOR_THING_TYPE);
                    if (UnifiAccessBindingConstants.DOOR_THING_TYPE.equals(t.getThingTypeUID())) {
                        if (t.getHandler() instanceof UnifiAccessDoorHandler dh) {
                            logger.trace("Updating door: {}", dh.deviceId);
                            String did = String
                                    .valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DEVICE_ID));
                            logger.trace("Device ID: {}", did);
                            Door match = doors.stream().filter(x -> did.equals(x.id)).findFirst().orElse(null);
                            if (match != null) {
                                dh.updateFromDoor(match);
                            }
                        }
                    }
                }
            }
            if (!filteredDevices.isEmpty()) {
                for (Thing t : getThing().getThings()) {
                    if (UnifiAccessBindingConstants.DEVICE_THING_TYPE.equals(t.getThingTypeUID())) {
                        if (t.getHandler() instanceof UnifiAccessDeviceHandler dh) {
                            try {
                                var settings = client.getDeviceAccessMethodSettings(dh.deviceId);
                                dh.updateFromSettings(settings);
                            } catch (UniFiAccessHttpException ex) {
                                logger.debug("Failed to update device {}: {}", dh.deviceId, ex.getMessage());
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
        for (Thing t : getThing().getThings()) {
            if (UnifiAccessBindingConstants.DOOR_THING_TYPE.equals(t.getThingTypeUID())) {
                String did = String.valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DEVICE_ID));
                if (data.uniqueId.equals(did) && t.getHandler() instanceof UnifiAccessDoorHandler dh) {
                    dh.handleRemoteUnlock(data);
                }
            }
        }
    }

    private void handleRemoteView(RemoteViewData rv) {
        UnifiAccessDeviceHandler dh = getDeviceHandlerByDeviceId(rv.deviceId);
        if (dh != null) {
            dh.handleRemoteView(rv);
        }
    }

    private void handleRemoteViewChange(RemoteViewChangeData rvc) {
        // First try to route via remote call request id mapping (if available)
        String deviceId = null;
        if (rvc != null && rvc.remoteCallRequestId != null) {
            deviceId = remoteViewRequestToDeviceId.get(rvc.remoteCallRequestId);
        }
        if (deviceId != null) {
            UnifiAccessDeviceHandler dh = getDeviceHandlerByDeviceId(deviceId);
            if (dh != null) {
                dh.handleRemoteViewChange(rvc);
                return;
            }
        }
    }

    private void handleDeviceUpdateV2(Notification.DeviceUpdateV2Data updateData) {
        if (updateData.locationStates != null) {
            updateData.locationStates.forEach(ls -> {
                UnifiAccessDoorHandler dh = getDoorHandler(ls.locationId);
                if (dh != null) {
                    dh.handleLocationState(ls);
                }
            });
        } else {
            // update for a device ?
            // TODO this update carries the isOnline status of the device, so we need to update the device thing status
        }
    }

    private void handleLocationUpdateV2(LocationUpdateV2Data lu2) {
        // Forward to matching device handlers by device ids under this location
        if (lu2.state != null && lu2.deviceIds != null) {
            for (String deviceId : lu2.deviceIds) {
                UnifiAccessDoorHandler dh = getDoorHandler(deviceId);
                if (dh != null) {
                    dh.handleLocationUpdateV2(lu2);
                }
                UnifiAccessDeviceHandler uh = getDeviceHandlerByDeviceId(deviceId);
                if (uh != null) {
                    uh.handleLocationState(lu2.state);
                }
            }
        }
    }

    private @Nullable UnifiAccessDoorHandler getDoorHandler(String doorId) {
        for (Thing t : getThing().getThings()) {
            if (UnifiAccessBindingConstants.DOOR_THING_TYPE.equals(t.getThingTypeUID())) {
                String did = String.valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DEVICE_ID));
                if (doorId.equals(did) && t.getHandler() instanceof UnifiAccessDoorHandler dh) {
                    return dh;
                }
            }
        }
        return null;
    }

    private @Nullable UnifiAccessDeviceHandler getDeviceHandlerByDeviceId(String deviceId) {
        for (Thing t : getThing().getThings()) {
            if (UnifiAccessBindingConstants.DEVICE_THING_TYPE.equals(t.getThingTypeUID())) {
                String did = String.valueOf(t.getConfiguration().get(UnifiAccessBindingConstants.CONFIG_DEVICE_ID));
                if (deviceId.equals(did) && t.getHandler() instanceof UnifiAccessDeviceHandler dh) {
                    return dh;
                }
            }
        }
        return null;
    }
}
