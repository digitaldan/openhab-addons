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
package org.openhab.binding.unifiprotect.internal.handler;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.binding.unifiprotect.internal.UnifiProtectBindingConstants;
import org.openhab.binding.unifiprotect.internal.UnifiProtectDiscoveryService;
import org.openhab.binding.unifiprotect.internal.api.UniFiProtectApiClient;
import org.openhab.binding.unifiprotect.internal.config.UnifiProtectNVRConfiguration;
import org.openhab.binding.unifiprotect.internal.dto.Camera;
import org.openhab.binding.unifiprotect.internal.dto.Light;
import org.openhab.binding.unifiprotect.internal.dto.Nvr;
import org.openhab.binding.unifiprotect.internal.dto.ProtectVersionInfo;
import org.openhab.binding.unifiprotect.internal.dto.Sensor;
import org.openhab.binding.unifiprotect.internal.dto.events.BaseEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.EventType;
import org.openhab.binding.unifiprotect.internal.dto.gson.DeviceTypeAdapterFactory;
import org.openhab.binding.unifiprotect.internal.dto.gson.EventTypeAdapterFactory;
import org.openhab.binding.unifiprotect.internal.handler.UnifiProtectAbstractDeviceHandler.WSEventType;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Bridge handler for the UniFi Protect NVR.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiProtectNVRHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(UnifiProtectNVRHandler.class);

    private @Nullable UnifiProtectNVRConfiguration config;

    private @Nullable UniFiProtectApiClient apiClient;
    private @Nullable ScheduledFuture<?> pollTask;
    private @Nullable ScheduledFuture<?> reconnectTask;
    private @Nullable UnifiProtectDiscoveryService discoveryService;
    private final HttpClient httpClient;
    private Gson gson;
    private boolean shuttingDown = false;

    // Deduplication of WS events (same event id and WS type within a short window)
    private static final long WS_EVENT_DEDUP_WINDOW_MS = 2000; // 2 seconds
    private final Map<String, Long> recentEventKeys = new ConcurrentHashMap<>();

    public UnifiProtectNVRHandler(Thing thing, HttpClientFactory httpClientFactory) {
        super((Bridge) thing);
        gson = new GsonBuilder().registerTypeAdapterFactory(new DeviceTypeAdapterFactory())
                .registerTypeAdapterFactory(new EventTypeAdapterFactory()).create();
        httpClient = httpClientFactory.createHttpClient(UnifiProtectBindingConstants.BINDING_ID,
                new SslContextFactory.Client(true));
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return List.of(UnifiProtectDiscoveryService.class);
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        logger.debug("Child handler initialized: {}", childHandler);
        if (getThing().getStatus() == ThingStatus.ONLINE) {
            if (childHandler instanceof UnifiProtectAbstractDeviceHandler<?> handler) {
                scheduler.execute(() -> {
                    Object devIdObj = childThing.getConfiguration().get(UnifiProtectBindingConstants.DEVICE_ID);
                    String deviceId = devIdObj != null ? String.valueOf(devIdObj) : null;
                    if (deviceId == null) {
                        return;
                    }
                    refreshChildFromApi(deviceId, handler);
                });
            }
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
    }

    @Override
    public void initialize() {
        logger.debug("Initializing NVR");
        config = getConfigAs(UnifiProtectNVRConfiguration.class);
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            try {
                Map<String, String> headers = new HashMap<>();
                UnifiProtectNVRConfiguration cfg = this.config;
                if (cfg == null) {
                    updateStatus(ThingStatus.OFFLINE);
                    return;
                }
                headers.put("Authorization", "Bearer " + cfg.token);

                URI base = URI.create("https://" + cfg.hostname + "/proxy/protect/integration/");
                UniFiProtectApiClient apiClient = new UniFiProtectApiClient(httpClient, base, gson, cfg.token);
                this.apiClient = apiClient;

                apiClient.subscribeEvents(add -> {
                    if (!isDuplicate(add.item, WSEventType.ADD)) {
                        routeEvent(add.item, WSEventType.ADD);
                    }
                }, update -> {
                    if (!isDuplicate(update.item, WSEventType.UPDATE)) {
                        routeEvent(update.item, WSEventType.UPDATE);
                    }
                }, () -> {
                    updateStatus(ThingStatus.ONLINE);
                    scheduler.execute(() -> syncDevices());
                }, (code, reason) -> {
                    logger.warn("Event WS closed: {} {}", code, reason);
                    setOfflineAndReconnect();
                }, err -> logger.warn("Event WS error", err)).get();

                apiClient.subscribeDevices(add -> {
                    UnifiProtectDiscoveryService discoveryService = this.discoveryService;
                    if (discoveryService == null) {
                        logger.warn("Discovery service not set");
                        return;
                    }
                    switch (add.item.modelKey) {
                        case CAMERA:
                            discoveryService.discoverCameras(apiClient);
                            break;
                        case LIGHT:
                            discoveryService.discoverLights(apiClient);
                            break;
                        case SENSOR:
                            discoveryService.discoverSensors(apiClient);
                            break;
                        default:
                            // ignore
                    }
                }, update -> {
                    scheduler.execute(() -> {
                        if (update.item == null || update.item.id == null) {
                            return;
                        }
                        String id = update.item.id;
                        switch (update.item.modelKey) {
                            case CAMERA:
                                refreshChildFromApi(UnifiProtectBindingConstants.THING_TYPE_CAMERA, id);
                                break;
                            case LIGHT:
                                refreshChildFromApi(UnifiProtectBindingConstants.THING_TYPE_LIGHT, id);
                                break;
                            case SENSOR:
                                refreshChildFromApi(UnifiProtectBindingConstants.THING_TYPE_SENSOR, id);
                                break;
                            default:
                                break;
                        }
                    });
                }, remove -> {
                    scheduler.execute(() -> {
                        if (remove.item == null || remove.item.id == null) {
                            return;
                        }
                        String id = remove.item.id;
                        switch (remove.item.modelKey) {
                            case CAMERA:
                                markChildGone(UnifiProtectBindingConstants.THING_TYPE_CAMERA, id);
                                break;
                            case LIGHT:
                                markChildGone(UnifiProtectBindingConstants.THING_TYPE_LIGHT, id);
                                break;
                            case SENSOR:
                                markChildGone(UnifiProtectBindingConstants.THING_TYPE_SENSOR, id);
                                break;
                            default:
                                break;
                        }
                    });
                }, () -> {
                    // ignore on-open
                }, (code, reason) -> {
                    logger.warn("Device WS closed: {} {}", code, reason);
                    setOfflineAndReconnect();
                }, err -> logger.warn("Device WS error", err)).get();
            } catch (Exception e) {
                logger.warn("Initialization failed", e);
                updateStatus(ThingStatus.OFFLINE);
            }
        });
    }

    @Override
    public void dispose() {
        shuttingDown = true;
        stopTasks();
        stopApiClient();
        try {
            httpClient.stop();
        } catch (Exception ignored) {
        }
        super.dispose();
    }

    @Nullable
    public UniFiProtectApiClient getApiClient() {
        return apiClient;
    }

    public void setDiscoveryService(UnifiProtectDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    private @Nullable <T extends ThingHandler> T findChildHandler(ThingTypeUID thingType, String deviceId,
            Class<T> handlerType) {
        for (Thing t : getThing().getThings()) {
            if (thingType.equals(t.getThingTypeUID())) {
                String devId = getDeviceId(t);
                if (devId != null && devId.equals(deviceId)) {
                    ThingHandler handler = t.getHandler();
                    if (handlerType.isInstance(handler)) {
                        return handlerType.cast(handler);
                    }
                }
            }
        }
        return null;
    }

    private @Nullable String getDeviceId(Thing thing) {
        Object devIdObj = thing.getConfiguration().get(UnifiProtectBindingConstants.DEVICE_ID);
        return devIdObj != null ? String.valueOf(devIdObj) : null;
    }

    private void refreshChildFromApi(ThingTypeUID type, String deviceId) {
        if (UnifiProtectBindingConstants.THING_TYPE_CAMERA.equals(type)) {
            UnifiProtectCameraHandler handler = findChildHandler(type, deviceId, UnifiProtectCameraHandler.class);
            if (handler != null) {
                refreshChildFromApi(deviceId, handler);
            }
        } else if (UnifiProtectBindingConstants.THING_TYPE_LIGHT.equals(type)) {
            UnifiProtectLightHandler handler = findChildHandler(type, deviceId, UnifiProtectLightHandler.class);
            if (handler != null) {
                refreshChildFromApi(deviceId, handler);
            }
        } else if (UnifiProtectBindingConstants.THING_TYPE_SENSOR.equals(type)) {
            UnifiProtectSensorHandler handler = findChildHandler(type, deviceId, UnifiProtectSensorHandler.class);
            if (handler != null) {
                refreshChildFromApi(deviceId, handler);
            }
        }
    }

    private void refreshChildFromApi(String deviceId, UnifiProtectAbstractDeviceHandler<?> handler) {
        UniFiProtectApiClient apiClient = this.apiClient;
        if (apiClient == null) {
            return;
        }
        try {
            if (handler instanceof UnifiProtectCameraHandler cameraHandler) {
                Camera cam = apiClient.getCamera(deviceId);
                cameraHandler.updateFromDevice(cam);
            } else if (handler instanceof UnifiProtectLightHandler lightHandler) {
                Light light = apiClient.getLight(deviceId);
                lightHandler.updateFromDevice(light);
            } else if (handler instanceof UnifiProtectSensorHandler sensorHandler) {
                Sensor sensor = apiClient.getSensor(deviceId);
                sensorHandler.updateFromDevice(sensor);
            }
        } catch (IOException e) {
            logger.debug("Failed to refresh child {} from API", deviceId, e);
        }
    }

    private boolean isDuplicate(@Nullable BaseEvent event, WSEventType wsType) {
        if (event == null || event.id == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        String key = wsType.name() + ":" + event.id;
        Long last = recentEventKeys.get(key);
        if (last != null && (now - last) < WS_EVENT_DEDUP_WINDOW_MS) {
            return true;
        }
        recentEventKeys.put(key, now);
        if (recentEventKeys.size() > 2048) {
            recentEventKeys.entrySet().removeIf(e -> (now - e.getValue()) >= WS_EVENT_DEDUP_WINDOW_MS);
        }
        return false;
    }

    private void markChildGone(ThingTypeUID type, String deviceId) {
        if (UnifiProtectBindingConstants.THING_TYPE_CAMERA.equals(type)) {
            UnifiProtectCameraHandler ch = findChildHandler(type, deviceId, UnifiProtectCameraHandler.class);
            if (ch != null) {
                ch.markGone();
            }
        } else if (UnifiProtectBindingConstants.THING_TYPE_LIGHT.equals(type)) {
            UnifiProtectLightHandler lh = findChildHandler(type, deviceId, UnifiProtectLightHandler.class);
            if (lh != null) {
                lh.markGone();
            }
        } else if (UnifiProtectBindingConstants.THING_TYPE_SENSOR.equals(type)) {
            UnifiProtectSensorHandler sh = findChildHandler(type, deviceId, UnifiProtectSensorHandler.class);
            if (sh != null) {
                sh.markGone();
            }
        }
    }

    private void syncDevices() {
        UniFiProtectApiClient apiClient = this.apiClient;
        if (apiClient == null) {
            return;
        }
        try {
            try {
                ProtectVersionInfo meta = apiClient.getMetaInfo();
                if (meta.applicationVersion != null) {
                    updateProperty("applicationVersion", meta.applicationVersion);
                }
            } catch (IOException e) {
                logger.debug("Failed to read meta info", e);
                setOfflineAndReconnect();
                return;
            }

            // Basic NVR fetch (validate connectivity and log)
            Nvr nvr = apiClient.getNvr();
            logger.debug("NVR name: {}", nvr.name);

            UnifiProtectDiscoveryService discoveryService = this.discoveryService;
            if (discoveryService != null) {
                apiClient.listCameras().forEach(camera -> {
                    UnifiProtectCameraHandler ch = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_CAMERA,
                            camera.id, UnifiProtectCameraHandler.class);
                    if (ch != null) {
                        ch.updateFromDevice(camera);
                    } else {
                        discoveryService.discoverCamera(camera);
                    }
                });
                apiClient.listLights().forEach(light -> {
                    UnifiProtectLightHandler lh = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_LIGHT,
                            light.id, UnifiProtectLightHandler.class);
                    if (lh != null) {
                        lh.updateFromDevice(light);
                    } else {
                        discoveryService.discoverLight(light);
                    }
                });
                apiClient.listSensors().forEach(sensor -> {
                    UnifiProtectSensorHandler sh = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_SENSOR,
                            sensor.id, UnifiProtectSensorHandler.class);
                    if (sh != null) {
                        sh.updateFromDevice(sensor);
                    } else {
                        discoveryService.discoverSensor(sensor);
                    }
                });
            } else {
                logger.warn("Discovery service not set");
            }
        } catch (IOException e) {
            logger.warn("Initial sync failed", e);
        }
    }

    private synchronized void setOfflineAndReconnect() {
        ScheduledFuture<?> reconnectTask = this.reconnectTask;
        if (shuttingDown || reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }
        updateStatus(ThingStatus.OFFLINE);
        stopApiClient();
        stopTasks();
        stopReconnectTask();
        this.reconnectTask = scheduler.schedule(this::initialize, 5, TimeUnit.SECONDS);
    }

    private void routeEvent(BaseEvent event, WSEventType eventType) {
        if (event.device == null) {
            return;
        }
        String deviceId = event.device;
        EventType et = event.type;
        switch (et) {
            case CAMERA_MOTION:
            case SMART_AUDIO_DETECT:
            case SMART_DETECT_ZONE:
            case SMART_DETECT_LINE:
            case SMART_DETECT_LOITER_ZONE: {
                UnifiProtectCameraHandler ch = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_CAMERA,
                        deviceId, UnifiProtectCameraHandler.class);
                if (ch != null) {
                    ch.handleEvent(event, eventType);
                }
                break;
            }
            case LIGHT_MOTION: {
                UnifiProtectLightHandler lh = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_LIGHT, deviceId,
                        UnifiProtectLightHandler.class);
                if (lh != null) {
                    lh.handleEvent(event, eventType);
                }
                break;
            }
            case SENSOR_MOTION:
            case SENSOR_OPENED:
            case SENSOR_CLOSED:
            case SENSOR_ALARM:
            case SENSOR_BATTERY_LOW:
            case SENSOR_TAMPER:
            case SENSOR_WATER_LEAK:
            case SENSOR_EXTREME_VALUES: {
                UnifiProtectSensorHandler sh = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_SENSOR,
                        deviceId, UnifiProtectSensorHandler.class);
                if (sh != null) {
                    sh.handleEvent(event, eventType);
                }
                break;
            }
            case RING: {
                UnifiProtectCameraHandler ch = findChildHandler(UnifiProtectBindingConstants.THING_TYPE_CAMERA,
                        deviceId, UnifiProtectCameraHandler.class);
                if (ch != null) {
                    ch.handleEvent(event, eventType);
                }
                break;
            }
            default:
                break;
        }
    }

    private void stopApiClient() {
        UniFiProtectApiClient apiClient = this.apiClient;
        if (apiClient != null) {
            try {
                apiClient.close();
            } catch (IOException ignored) {
            }
            this.apiClient = null;
        }
    }

    private void stopTasks() {
        stopPollTask();
        stopReconnectTask();
    }

    private void stopPollTask() {
        ScheduledFuture<?> pollTask = this.pollTask;
        if (pollTask != null) {
            pollTask.cancel(true);
            this.pollTask = null;
        }
    }

    private void stopReconnectTask() {
        ScheduledFuture<?> reconnectTask = this.reconnectTask;
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
            this.reconnectTask = null;
        }
    }
}
