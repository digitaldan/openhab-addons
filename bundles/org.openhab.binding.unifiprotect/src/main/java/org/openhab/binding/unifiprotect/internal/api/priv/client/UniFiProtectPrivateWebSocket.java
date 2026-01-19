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
package org.openhab.binding.unifiprotect.internal.api.priv.client;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Bridge;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Chime;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Doorlock;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Light;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Sensor;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Viewer;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.system.Bootstrap;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.system.Event;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.system.Nvr;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.system.User;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.types.ModelType;
import org.openhab.binding.unifiprotect.internal.api.priv.exception.UniFiProtectException;
import org.openhab.binding.unifiprotect.internal.api.priv.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * WebSocket client for UniFi Protect real-time updates
 * Connects to /proxy/protect/ws/updates for device and event updates
 *
 * @author Dan Cunningham - Initial contribution
 */
public class UniFiProtectPrivateWebSocket {

    private static final Logger logger = LoggerFactory.getLogger(UniFiProtectPrivateWebSocket.class);

    private final String wsUrl;
    private final String authCookie;
    private final Consumer<WebSocketUpdate> updateHandler;
    private final UniFiProtectPrivateClient client;
    private final WebSocketClient wsClient;

    private Session session;
    private CompletableFuture<Void> connectFuture;
    private volatile boolean shouldReconnect = true;
    private volatile int reconnectAttempts = 0;
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int INITIAL_RECONNECT_DELAY_MS = 1000;
    private static final int MAX_RECONNECT_DELAY_MS = 60000;

    /**
     * Create UniFi Protect WebSocket client
     *
     * @param wsUrl WebSocket URL
     * @param authCookie Authentication cookie
     * @param updateHandler Handler for WebSocket updates
     * @param client Reference to the main client
     * @param httpClient HTTP client to use (shared from main client)
     */
    public UniFiProtectPrivateWebSocket(String wsUrl, String authCookie, Consumer<WebSocketUpdate> updateHandler,
            UniFiProtectPrivateClient client, HttpClient httpClient) {
        this.wsUrl = wsUrl;
        this.authCookie = authCookie;
        this.updateHandler = updateHandler;
        this.client = client;

        // Create WebSocket client using the shared HTTP client (already configured for SSL)
        this.wsClient = new WebSocketClient(httpClient);
        // Prevent wsClient.stop() from stopping the shared HttpClient instance
        this.wsClient.unmanage(httpClient);

        try {
            wsClient.start();
        } catch (Exception e) {
            throw new UniFiProtectException("Failed to start WebSocket client", e);
        }
    }

    /**
     * Connect to WebSocket
     */
    public CompletableFuture<Void> connect() {
        if (connectFuture != null && !connectFuture.isDone()) {
            return connectFuture;
        }

        connectFuture = new CompletableFuture<>();

        try {
            URI uri = new URI(wsUrl);
            UniFiWebSocketAdapter adapter = new UniFiWebSocketAdapter();

            // Create request with cookie
            ClientUpgradeRequest request = new ClientUpgradeRequest();
            if (authCookie != null) {
                request.setHeader("Cookie", authCookie);
            }

            // Connect (Jetty 9 API returns Future, not CompletableFuture)
            Future<Session> future = wsClient.connect(adapter, uri, request);

            // Handle connection result asynchronously
            CompletableFuture.runAsync(() -> {
                try {
                    session = future.get(10, TimeUnit.SECONDS);
                    reconnectAttempts = 0; // Reset on successful connection
                    logger.info("WebSocket connected");
                    connectFuture.complete(null);
                } catch (Exception ex) {
                    logger.error("WebSocket connection failed", ex);
                    connectFuture.completeExceptionally(ex);
                    scheduleReconnect();
                }
            });

        } catch (Exception e) {
            logger.error("Failed to connect WebSocket", e);
            connectFuture.completeExceptionally(e);
        }

        return connectFuture;
    }

    /**
     * Disconnect WebSocket
     */
    public void disconnect() {
        shouldReconnect = false; // Prevent reconnection

        if (session != null && session.isOpen()) {
            session.close();
        }

        try {
            wsClient.stop();
        } catch (Exception e) {
            logger.warn("Error stopping WebSocket client", e);
        }

        logger.info("WebSocket disconnected");
    }

    /**
     * Schedule reconnection with exponential backoff
     */
    private void scheduleReconnect() {
        if (!shouldReconnect) {
            logger.debug("Reconnection disabled, not attempting to reconnect");
            return;
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            logger.error("Max reconnection attempts ({}) reached, giving up", MAX_RECONNECT_ATTEMPTS);
            return;
        }

        reconnectAttempts++;
        int delayMs = Math.min(INITIAL_RECONNECT_DELAY_MS * (1 << (reconnectAttempts - 1)), MAX_RECONNECT_DELAY_MS);

        logger.info("Scheduling WebSocket reconnection attempt {} in {}ms", reconnectAttempts, delayMs);

        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS).execute(() -> {
            if (shouldReconnect) {
                logger.info("Attempting WebSocket reconnection (attempt {})", reconnectAttempts);
                connect().exceptionally(ex -> {
                    logger.error("Reconnection attempt {} failed", reconnectAttempts, ex);
                    return null;
                });
            }
        });
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    /**
     * WebSocket update message
     */
    public static class WebSocketUpdate {
        public String action;
        public ModelType modelType;
        public String id;
        public String newUpdateId;
        public JsonObject data;

        public WebSocketUpdate(String action, ModelType modelType, String id, String newUpdateId, JsonObject data) {
            this.action = action;
            this.modelType = modelType;
            this.id = id;
            this.newUpdateId = newUpdateId;
            this.data = data;
        }

        @Override
        public String toString() {
            return String.format("WebSocketUpdate{action='%s', modelType=%s, id='%s'}", action, modelType, id);
        }
    }

    /**
     * WebSocket adapter to handle messages
     */
    private class UniFiWebSocketAdapter extends WebSocketAdapter {

        @Override
        public void onWebSocketConnect(Session session) {
            super.onWebSocketConnect(session);
            logger.debug("WebSocket session connected");
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            try {
                // UniFi Protect sends binary WebSocket packets with a specific format:
                // Packet contains frames with headers followed by JSON data
                // For simplicity, we'll try to parse the JSON from the binary data

                ByteBuffer buffer = ByteBuffer.wrap(payload, offset, len);
                parseWebSocketPacket(buffer);

            } catch (Exception e) {
                logger.warn("Error parsing WebSocket binary message", e);
            }
        }

        @Override
        public void onWebSocketText(String message) {
            try {
                // Sometimes we might get text messages too
                logger.debug("WebSocket text message received");

                // TRACE log text message
                if (logger.isTraceEnabled()) {
                    logger.trace("WebSocket Text Message: {}", message);
                }

                JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                processUpdate(json);
            } catch (Exception e) {
                logger.warn("Error parsing WebSocket text message", e);
            }
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            logger.error("WebSocket error", cause);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason) {
            logger.info("WebSocket closed: {} - {}", statusCode, reason);

            // Attempt to reconnect if enabled
            if (shouldReconnect) {
                scheduleReconnect();
            }
        }

        /**
         * Parse binary WebSocket packet from UniFi Protect
         * The format is: [action frame][data frame]
         * Each frame has a header (8 bytes) + JSON payload
         */
        private void parseWebSocketPacket(ByteBuffer buffer) {
            try {
                // Parse action frame (first frame)
                if (buffer.remaining() < 8) {
                    logger.warn("Packet too small for header");
                    return;
                }

                // Read header (8 bytes)
                int packetType = buffer.get() & 0xFF;
                int payloadFormat = buffer.get() & 0xFF;
                int deflated = buffer.get() & 0xFF;
                buffer.get(); // Skip unknown byte
                int payloadSize = buffer.getInt();

                logger.trace("Action frame header: type={}, format={}, deflated={}, size={}", packetType, payloadFormat,
                        deflated, payloadSize);

                // Read action JSON
                if (buffer.remaining() < payloadSize) {
                    logger.warn("Not enough data for action payload");
                    return;
                }

                byte[] actionBytes = new byte[payloadSize];
                buffer.get(actionBytes);
                String actionJson = new String(actionBytes);
                JsonObject actionData = JsonParser.parseString(actionJson).getAsJsonObject();

                // TRACE log action frame
                if (logger.isTraceEnabled()) {
                    logger.trace("WebSocket Action Frame JSON: {}", actionJson);
                }

                // Parse data frame (second frame) if present
                JsonObject dataObject = null;
                if (buffer.remaining() >= 8) {
                    // Read data frame header
                    buffer.get(); // packet type
                    buffer.get(); // payload format
                    buffer.get(); // deflated
                    buffer.get(); // unknown
                    int dataPayloadSize = buffer.getInt();

                    if (buffer.remaining() >= dataPayloadSize && dataPayloadSize > 0) {
                        byte[] dataBytes = new byte[dataPayloadSize];
                        buffer.get(dataBytes);
                        String dataJson = new String(dataBytes);
                        dataObject = JsonParser.parseString(dataJson).getAsJsonObject();

                        // TRACE log data frame
                        if (logger.isTraceEnabled()) {
                            logger.trace("WebSocket Data Frame JSON: {}", dataJson);
                        }
                    }
                }

                // Process the update
                processUpdate(actionData, dataObject);

            } catch (Exception e) {
                logger.warn("Error parsing WebSocket packet", e);
            }
        }

        /**
         * Process update from action and data JSON
         */
        private void processUpdate(JsonObject actionData, JsonObject dataObject) {
            try {
                String action = actionData.has("action") ? actionData.get("action").getAsString() : null;
                String modelKey = actionData.has("modelKey") ? actionData.get("modelKey").getAsString() : null;
                String id = actionData.has("id") ? actionData.get("id").getAsString() : null;
                String newUpdateId = actionData.has("newUpdateId") && !actionData.get("newUpdateId").isJsonNull()
                        ? actionData.get("newUpdateId").getAsString()
                        : null;

                ModelType modelType = ModelType.fromString(modelKey);

                logger.debug("WebSocket update: action={}, model={}, id={}", action, modelType, id);

                // TRACE log decoded message details
                if (logger.isTraceEnabled()) {
                    logger.trace("Decoded WebSocket Message:");
                    logger.trace("  Action: {}", action);
                    logger.trace("  ModelKey: {}", modelKey);
                    logger.trace("  ModelType: {}", modelType);
                    logger.trace("  ID: {}", id);
                    logger.trace("  NewUpdateId: {}", newUpdateId);
                    if (dataObject != null) {
                        logger.trace("  Data fields: {}", dataObject.keySet());
                    }
                }

                // Update bootstrap's lastUpdateId if present
                if (newUpdateId != null && client.getCachedBootstrap() != null) {
                    client.getCachedBootstrap().lastUpdateId = newUpdateId;
                }

                // Create update object
                WebSocketUpdate update = new WebSocketUpdate(action, modelType, id, newUpdateId, dataObject);

                // Apply incremental updates to cached bootstrap
                applyIncrementalUpdate(action, modelType, id, dataObject);

                // Call handler after applying update
                if (updateHandler != null) {
                    updateHandler.accept(update);
                }

            } catch (Exception e) {
                logger.warn("Error processing WebSocket update", e);
            }
        }

        /**
         * Process simple JSON update (text message)
         */
        private void processUpdate(JsonObject json) {
            processUpdate(json, json.has("data") ? json.getAsJsonObject("data") : null);
        }

        /**
         * Apply incremental update to cached bootstrap
         */
        private void applyIncrementalUpdate(String action, ModelType modelType, String id, JsonObject data) {
            Bootstrap bootstrap = client.getCachedBootstrap();
            if (bootstrap == null) {
                logger.debug("No cached bootstrap to update");
                return;
            }

            if (data == null || id == null || modelType == null) {
                logger.debug("Incomplete update data, skipping incremental update");
                return;
            }

            try {
                switch (action) {
                    case "add":
                    case "update":
                        updateBootstrapObject(bootstrap, modelType, id, data);
                        break;
                    case "remove":
                        removeBootstrapObject(bootstrap, modelType, id);
                        break;
                    default:
                        logger.debug("Unknown action: {}", action);
                }
            } catch (Exception e) {
                logger.warn("Error applying incremental update: action={}, model={}, id={}", action, modelType, id, e);
            }
        }

        /**
         * Update or add an object in bootstrap
         */
        private void updateBootstrapObject(Bootstrap bootstrap, ModelType modelType, String id, JsonObject data) {
            switch (modelType) {
                case CAMERA:
                    if (bootstrap.cameras != null) {
                        var camera = JsonUtil.getGson().fromJson(data, Camera.class);
                        bootstrap.cameras.put(id, camera);
                        logger.debug("Updated camera: {}", id);
                    }
                    break;
                case LIGHT:
                    if (bootstrap.lights != null) {
                        var light = JsonUtil.getGson().fromJson(data, Light.class);
                        bootstrap.lights.put(id, light);
                        logger.debug("Updated light: {}", id);
                    }
                    break;
                case SENSOR:
                    if (bootstrap.sensors != null) {
                        var sensor = JsonUtil.getGson().fromJson(data, Sensor.class);
                        bootstrap.sensors.put(id, sensor);
                        logger.debug("Updated sensor: {}", id);
                    }
                    break;
                case DOORLOCK:
                    if (bootstrap.doorlocks != null) {
                        var doorlock = JsonUtil.getGson().fromJson(data, Doorlock.class);
                        bootstrap.doorlocks.put(id, doorlock);
                        logger.debug("Updated doorlock: {}", id);
                    }
                    break;
                case CHIME:
                    if (bootstrap.chimes != null) {
                        var chime = JsonUtil.getGson().fromJson(data, Chime.class);
                        bootstrap.chimes.put(id, chime);
                        logger.debug("Updated chime: {}", id);
                    }
                    break;
                case VIEWER:
                    if (bootstrap.viewers != null) {
                        var viewer = JsonUtil.getGson().fromJson(data, Viewer.class);
                        bootstrap.viewers.put(id, viewer);
                        logger.debug("Updated viewer: {}", id);
                    }
                    break;
                case BRIDGE:
                    if (bootstrap.bridges != null) {
                        var bridge = JsonUtil.getGson().fromJson(data, Bridge.class);
                        bootstrap.bridges.put(id, bridge);
                        logger.debug("Updated bridge: {}", id);
                    }
                    break;
                case NVR:
                    bootstrap.nvr = JsonUtil.getGson().fromJson(data, Nvr.class);
                    logger.debug("Updated NVR");
                    break;
                case USER:
                    if (bootstrap.users != null) {
                        var user = JsonUtil.getGson().fromJson(data, User.class);
                        bootstrap.users.put(id, user);
                        logger.debug("Updated user: {}", id);
                    }
                    break;
                case EVENT:
                    if (bootstrap.events != null) {
                        var event = JsonUtil.getGson().fromJson(data, Event.class);
                        bootstrap.events.put(id, event);
                        logger.debug("Updated event: {}", id);
                    }
                    break;
                default:
                    logger.debug("Unhandled model type for update: {}", modelType);
            }
        }

        /**
         * Remove an object from bootstrap
         */
        private void removeBootstrapObject(Bootstrap bootstrap, ModelType modelType, String id) {
            switch (modelType) {
                case CAMERA:
                    if (bootstrap.cameras != null) {
                        bootstrap.cameras.remove(id);
                        logger.debug("Removed camera: {}", id);
                    }
                    break;
                case LIGHT:
                    if (bootstrap.lights != null) {
                        bootstrap.lights.remove(id);
                        logger.debug("Removed light: {}", id);
                    }
                    break;
                case SENSOR:
                    if (bootstrap.sensors != null) {
                        bootstrap.sensors.remove(id);
                        logger.debug("Removed sensor: {}", id);
                    }
                    break;
                case DOORLOCK:
                    if (bootstrap.doorlocks != null) {
                        bootstrap.doorlocks.remove(id);
                        logger.debug("Removed doorlock: {}", id);
                    }
                    break;
                case CHIME:
                    if (bootstrap.chimes != null) {
                        bootstrap.chimes.remove(id);
                        logger.debug("Removed chime: {}", id);
                    }
                    break;
                case VIEWER:
                    if (bootstrap.viewers != null) {
                        bootstrap.viewers.remove(id);
                        logger.debug("Removed viewer: {}", id);
                    }
                    break;
                case BRIDGE:
                    if (bootstrap.bridges != null) {
                        bootstrap.bridges.remove(id);
                        logger.debug("Removed bridge: {}", id);
                    }
                    break;
                case USER:
                    if (bootstrap.users != null) {
                        bootstrap.users.remove(id);
                        logger.debug("Removed user: {}", id);
                    }
                    break;
                case EVENT:
                    if (bootstrap.events != null) {
                        bootstrap.events.remove(id);
                        logger.debug("Removed event: {}", id);
                    }
                    break;
                default:
                    logger.debug("Unhandled model type for removal: {}", modelType);
            }
        }
    }
}
