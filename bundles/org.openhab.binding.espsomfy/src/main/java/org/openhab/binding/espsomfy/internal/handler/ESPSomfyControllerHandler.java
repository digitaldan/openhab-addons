/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.espsomfy.internal.handler;

import static org.openhab.binding.espsomfy.internal.ESPSomfyBindingConstants.*;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.espsomfy.internal.ESPSomfyConfiguration;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiClient;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiException;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyWebSocket;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyWebSocketListener;
import org.openhab.binding.espsomfy.internal.discovery.ESPSomfyDiscoveryService;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyGroupDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyRoomDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyShadeDTO;
import org.openhab.core.library.types.StringType;
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

import com.google.gson.JsonObject;

/**
 * The {@link ESPSomfyControllerHandler} is the bridge handler for an ESPSomfy-RTS controller device.
 * It manages HTTP API communication, WebSocket connection for real-time events, and child thing discovery.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class ESPSomfyControllerHandler extends BaseBridgeHandler implements ESPSomfyWebSocketListener {

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyControllerHandler.class);
    private final HttpClient httpClient;
    private final WebSocketClient webSocketClient;
    private final Map<Integer, String> roomNames = new ConcurrentHashMap<>();

    private @Nullable ESPSomfyApiClient apiClient;
    private @Nullable ESPSomfyWebSocket webSocket;
    private @Nullable ScheduledFuture<?> pollingJob;
    private @Nullable ScheduledFuture<?> reconnectJob;
    private @Nullable String firmwareVersion;
    private volatile int reconnectDelaySeconds = 5;

    public ESPSomfyControllerHandler(Bridge bridge, HttpClient httpClient, WebSocketClient webSocketClient) {
        super(bridge);
        this.httpClient = httpClient;
        this.webSocketClient = webSocketClient;
    }

    @Override
    public void initialize() {
        ESPSomfyConfiguration config = getConfigAs(ESPSomfyConfiguration.class);
        if (config.hostname.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Hostname must be set");
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> initializeBridge(config));
    }

    private void initializeBridge(ESPSomfyConfiguration config) {
        try {
            ESPSomfyApiClient client = new ESPSomfyApiClient(httpClient, config.hostname, config.port);

            if (!config.password.isBlank()) {
                client.login(config.password);
            }

            JsonObject controller = client.getController();
            logger.debug("Controller response keys: {}", controller.keySet());

            // Extract firmware version - try both nested object and flat formats
            if (controller.has("version")) {
                try {
                    JsonObject versionObj = controller.getAsJsonObject("version");
                    if (versionObj.has("ver")) {
                        firmwareVersion = versionObj.get("ver").getAsString();
                    } else if (versionObj.has("name")) {
                        firmwareVersion = versionObj.get("name").getAsString();
                    }
                } catch (ClassCastException e) {
                    // version might be a plain string in some firmware versions
                    firmwareVersion = controller.get("version").getAsString();
                }
            }

            Map<String, String> properties = editProperties();
            if (controller.has("serverId")) {
                properties.put(PROPERTY_SERVER_ID, controller.get("serverId").getAsString());
            }
            if (controller.has("hostname")) {
                properties.put(Thing.PROPERTY_VENDOR, controller.get("hostname").getAsString());
            }
            String fwVersion = firmwareVersion;
            if (fwVersion != null) {
                properties.put(Thing.PROPERTY_FIRMWARE_VERSION, fwVersion);
            }
            updateProperties(properties);

            ESPSomfyRoomDTO[] rooms = client.parseRoomsFromController(controller);
            roomNames.clear();
            for (ESPSomfyRoomDTO room : rooms) {
                roomNames.put(room.roomId, room.name);
            }

            updateChannelState();

            this.apiClient = client;
            updateStatus(ThingStatus.ONLINE);

            connectWebSocket(config.hostname);
            startPolling(config);
        } catch (ESPSomfyApiException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            logger.debug("Unexpected error initializing bridge: {}", e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    @Override
    public void dispose() {
        stopPolling();
        cancelReconnect();
        disconnectWebSocket();
        apiClient = null;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            updateChannelState();
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(ESPSomfyDiscoveryService.class);
    }

    /**
     * Get the API client for child handlers to use.
     */
    public @Nullable ESPSomfyApiClient getApiClient() {
        return apiClient;
    }

    /**
     * Look up a room name by ID.
     */
    public String getRoomName(int roomId) {
        return roomNames.getOrDefault(roomId, "");
    }

    // -- WebSocket listener callbacks --

    @Override
    public void onShadeStateChanged(ESPSomfyShadeDTO shade) {
        logger.trace("Shade state update: shadeId={}, position={}", shade.shadeId, shade.position);
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof ESPSomfyShadeHandler shadeHandler && shadeHandler.getShadeId() == shade.shadeId) {
                shadeHandler.updateFromState(shade);
                break;
            }
        }
    }

    @Override
    public void onGroupStateChanged(ESPSomfyGroupDTO group) {
        logger.trace("Group state update: groupId={}", group.groupId);
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof ESPSomfyGroupHandler groupHandler && groupHandler.getGroupId() == group.groupId) {
                groupHandler.updateFromState(group);
                break;
            }
        }
    }

    @Override
    public void onWebSocketClose() {
        logger.debug("WebSocket closed, scheduling reconnect");
        scheduleReconnect();
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        logger.debug("WebSocket error: {}, scheduling reconnect", cause.getMessage());
        scheduleReconnect();
    }

    // -- Polling --

    private void startPolling(ESPSomfyConfiguration config) {
        stopPolling();
        pollingJob = scheduler.scheduleWithFixedDelay(this::poll, config.refreshInterval, config.refreshInterval,
                TimeUnit.SECONDS);
    }

    private void stopPolling() {
        ScheduledFuture<?> job = pollingJob;
        if (job != null) {
            job.cancel(true);
            pollingJob = null;
        }
    }

    private void poll() {
        ESPSomfyApiClient client = apiClient;
        if (client == null) {
            return;
        }
        try {
            JsonObject controller = client.getController();

            ESPSomfyRoomDTO[] rooms = client.parseRoomsFromController(controller);
            for (ESPSomfyRoomDTO room : rooms) {
                roomNames.put(room.roomId, room.name);
            }

            ESPSomfyShadeDTO[] shades = client.parseShadesFromController(controller);
            for (ESPSomfyShadeDTO shade : shades) {
                onShadeStateChanged(shade);
            }

            ESPSomfyGroupDTO[] groups = client.parseGroupsFromController(controller);
            for (ESPSomfyGroupDTO group : groups) {
                onGroupStateChanged(group);
            }

            updateChannelState();

            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        } catch (ESPSomfyApiException e) {
            logger.debug("Poll failed: {}", e.getMessage());
            if (getThing().getStatus() == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    private void updateChannelState() {
        String fwVersion = firmwareVersion;
        if (fwVersion != null) {
            updateState(CHANNEL_VERSION, new StringType(fwVersion));
        }
    }

    // -- WebSocket management --

    private void connectWebSocket(String hostname) {
        try {
            ESPSomfyWebSocket ws = new ESPSomfyWebSocket(webSocketClient, hostname, this);
            ws.connect();
            this.webSocket = ws;
            reconnectDelaySeconds = 5;
        } catch (ESPSomfyApiException e) {
            logger.debug("WebSocket connection failed: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void disconnectWebSocket() {
        ESPSomfyWebSocket ws = webSocket;
        if (ws != null) {
            ws.disconnect();
            webSocket = null;
        }
    }

    private void scheduleReconnect() {
        cancelReconnect();
        if (getThing().getStatus() == ThingStatus.ONLINE || getThing().getStatus() == ThingStatus.UNKNOWN) {
            logger.debug("Scheduling WebSocket reconnect in {}s", reconnectDelaySeconds);
            reconnectJob = scheduler.schedule(() -> {
                ESPSomfyConfiguration config = getConfigAs(ESPSomfyConfiguration.class);
                connectWebSocket(config.hostname);
            }, reconnectDelaySeconds, TimeUnit.SECONDS);
            reconnectDelaySeconds = Math.min(reconnectDelaySeconds * 2, 60);
        }
    }

    private void cancelReconnect() {
        ScheduledFuture<?> job = reconnectJob;
        if (job != null) {
            job.cancel(false);
            reconnectJob = null;
        }
    }
}
