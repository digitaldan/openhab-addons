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
package org.openhab.binding.espsomfy.internal.api;

import static org.openhab.binding.espsomfy.internal.ESPSomfyBindingConstants.WEBSOCKET_PORT;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketError;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyGroupDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyShadeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * WebSocket client for receiving real-time events from ESPSomfy-RTS devices.
 * The firmware uses a Socket.IO-like protocol where messages are formatted as:
 * {@code 42[eventName,{json data}]}
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
@WebSocket(maxIdleTime = Integer.MAX_VALUE)
public class ESPSomfyWebSocket {

    private static final String SOCKET_IO_PREFIX = "42[";

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyWebSocket.class);
    private final Gson gson = new Gson();
    private final WebSocketClient webSocketClient;
    private final String hostname;
    private final ESPSomfyWebSocketListener listener;
    private volatile @Nullable Session session;

    public ESPSomfyWebSocket(WebSocketClient webSocketClient, String hostname, ESPSomfyWebSocketListener listener) {
        this.webSocketClient = webSocketClient;
        this.hostname = hostname;
        this.listener = listener;
    }

    /**
     * Connect to the ESPSomfy WebSocket server.
     */
    public void connect() throws ESPSomfyApiException {
        try {
            URI uri = new URI("ws://" + hostname + ":" + WEBSOCKET_PORT);
            logger.debug("Connecting WebSocket to {}", uri);
            disconnect();
            webSocketClient.connect(this, uri);
        } catch (URISyntaxException e) {
            throw new ESPSomfyApiException("Invalid WebSocket URI: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ESPSomfyApiException("Failed to connect WebSocket: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect from the WebSocket server.
     */
    public void disconnect() {
        Session localSession = session;
        if (localSession != null && localSession.isOpen()) {
            localSession.close(StatusCode.NORMAL, "Binding disconnecting");
        }
        session = null;
    }

    /**
     * Check if the WebSocket is currently connected.
     */
    public boolean isConnected() {
        Session localSession = session;
        return localSession != null && localSession.isOpen();
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        logger.debug("WebSocket connected to {}", hostname);
        this.session = session;
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        logger.trace("WebSocket message: {}", message);
        if (!message.startsWith(SOCKET_IO_PREFIX)) {
            logger.trace("Ignoring non-event message: {}", message);
            return;
        }
        // Parse Socket.IO-like format: 42[eventName,{json}]
        int commaIndex = message.indexOf(',', SOCKET_IO_PREFIX.length());
        if (commaIndex < 0 || !message.endsWith("]")) {
            logger.debug("Malformed WebSocket event: {}", message);
            return;
        }
        String rawEventName = message.substring(SOCKET_IO_PREFIX.length(), commaIndex);
        // Socket.IO sends event names as quoted strings: 42["shadeState",{...}]
        String eventName = rawEventName.startsWith("\"") && rawEventName.endsWith("\"")
                ? rawEventName.substring(1, rawEventName.length() - 1)
                : rawEventName;
        String jsonData = message.substring(commaIndex + 1, message.length() - 1);

        try {
            switch (eventName) {
                case "shadeState":
                    ESPSomfyShadeDTO shade = gson.fromJson(jsonData, ESPSomfyShadeDTO.class);
                    if (shade != null) {
                        listener.onShadeStateChanged(shade);
                    }
                    break;
                case "groupState":
                    ESPSomfyGroupDTO group = gson.fromJson(jsonData, ESPSomfyGroupDTO.class);
                    if (group != null) {
                        listener.onGroupStateChanged(group);
                    }
                    break;
                default:
                    logger.trace("Unhandled WebSocket event: {}", eventName);
            }
        } catch (RuntimeException e) {
            logger.debug("Error parsing WebSocket event '{}': {}", eventName, e.getMessage());
        }
    }

    @OnWebSocketClose
    public void onClose(int statusCode, @Nullable String reason) {
        logger.debug("WebSocket closed: {} - {}", statusCode, reason);
        session = null;
        if (statusCode != StatusCode.NORMAL) {
            listener.onWebSocketClose();
        }
    }

    @OnWebSocketError
    public void onError(@Nullable Throwable cause) {
        if (cause != null) {
            logger.debug("WebSocket error: {}", cause.getMessage());
            listener.onWebSocketError(cause);
        }
        session = null;
    }
}
