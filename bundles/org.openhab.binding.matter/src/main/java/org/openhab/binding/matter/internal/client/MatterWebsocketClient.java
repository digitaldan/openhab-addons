/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.matter.internal.client;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.matter.internal.client.model.Endpoint;
import org.openhab.binding.matter.internal.client.model.Node;
import org.openhab.binding.matter.internal.client.model.cluster.BaseCluster;
import org.openhab.binding.matter.internal.client.model.cluster.ClusterCommand;
import org.openhab.binding.matter.internal.client.model.ws.AttributeChangedMessage;
import org.openhab.binding.matter.internal.client.model.ws.Event;
import org.openhab.binding.matter.internal.client.model.ws.Message;
import org.openhab.binding.matter.internal.client.model.ws.NodeStateMessage;
import org.openhab.binding.matter.internal.client.model.ws.Request;
import org.openhab.binding.matter.internal.client.model.ws.Response;
import org.openhab.binding.matter.internal.util.NodeManager;
import org.openhab.binding.matter.internal.util.NodeRunner;
import org.openhab.binding.matter.internal.util.NodeRunner.NodeExitListener;
import org.openhab.core.common.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

/**
 *
 * @author Dan Cunningham
 *
 */
public class MatterWebsocketClient implements WebSocketListener, NodeExitListener {

    private final Logger logger = LoggerFactory.getLogger(MatterWebsocketClient.class);

    Gson gson = new GsonBuilder().registerTypeAdapter(Node.class, new NodeDeserializer()).create();
    private final ScheduledExecutorService scheduler = ThreadPoolManager
            .getScheduledPool("matter-js.MatterWebsocketClient");
    private Session session;
    WebSocketClient client = new WebSocketClient();
    NodeRunner nodeRunner;
    private final ConcurrentHashMap<String, CompletableFuture<JsonElement>> pendingRequests = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AttributeListener> attributeListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<NodeStateListener> nodeStateListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<ControllerStateListener> controllerStateListeners = new CopyOnWriteArrayList<>();

    public void connect(String host, int port, String storagePath) throws Exception {
        connectWebsocket(host, port, storagePath);
    }

    public void connect(String storagePath) throws Exception {
        disconnect();
        final int port = startNodeJs();
        scheduler.schedule(() -> {
            try {
                connectWebsocket("localhost", port, storagePath);
            } catch (Exception e) {
                disconnect();
                logger.error("Could not connect", e);
                for (ControllerStateListener listener : controllerStateListeners) {
                    String msg = e.getLocalizedMessage();
                    listener.onDisconnect(msg != null ? msg : "Exception connecting");
                }
            }
        }, 5, TimeUnit.SECONDS);
    }

    private void connectWebsocket(String host, int port, String storagePath) throws Exception {
        logger.debug("Connecting {}:{} ", host, port);
        String dest = "ws://" + host + ":" + port + "?nodeId=0&storagePath=" + storagePath;
        WebSocketClient client = new WebSocketClient();
        client.setMaxIdleTimeout(Long.MAX_VALUE);
        client.start();
        URI uri = new URI(dest);
        client.connect(this, uri, new ClientUpgradeRequest()).get();
    }

    public void disconnect() {
        Session session = this.session;
        try {
            if (session != null && session.isOpen()) {
                session.disconnect();
                session.close();
                session = null;
            }
        } catch (IOException e) {
            logger.debug("Error trying to disconnect", e);
        } finally {
            try {
                client.stop();
            } catch (Exception e) {
                logger.debug("Error closing Web Socket", e);
            }
        }
        NodeRunner nodeRunner = this.nodeRunner;
        if (nodeRunner != null) {
            nodeRunner.stopNode();
        }
    }

    public void addAttributeListener(AttributeListener listener) {
        attributeListeners.add(listener);
    }

    public void removeAttributeListener(AttributeListener listener) {
        attributeListeners.remove(listener);
    }

    public void addNodeStateListener(NodeStateListener listener) {
        nodeStateListeners.add(listener);
    }

    public void removeNodeStateListener(NodeStateListener listener) {
        nodeStateListeners.remove(listener);
    }

    public void addControllerStateListener(ControllerStateListener listener) {
        controllerStateListeners.add(listener);
    }

    public void removeControllerStateListener(ControllerStateListener listener) {
        controllerStateListeners.remove(listener);
    }

    protected CompletableFuture<JsonElement> sendMessage(String namespace, String functionName, Object args[]) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<JsonElement> responseFuture = new CompletableFuture<>();
        pendingRequests.put(requestId, responseFuture);
        Request message = new Request(requestId, namespace, functionName, args);
        String jsonMessage = gson.toJson(message);
        logger.debug("sendMessage: {}", jsonMessage);
        Future<Void> future = session.getRemote().sendStringByFuture(jsonMessage);
        try {
            future.get(5, TimeUnit.SECONDS); // Wait for up to 5 seconds for the message to be sent
            logger.debug("Message sent successfully {}", requestId);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Failed to send message: {}", requestId, e);
        }
        return responseFuture;
    }

    @Override
    public void onWebSocketConnect(Session session) {
        this.session = session;
        for (ControllerStateListener listener : controllerStateListeners) {
            listener.onConnect();
        }
    }

    @Override
    public void onWebSocketText(String msg) {
        logger.debug("onWebSocketText {}", msg);
        scheduler.submit(() -> {
            Message message = gson.fromJson(msg, Message.class);
            if (message == null) {
                logger.debug("invalid Message");
                return;
            }
            if ("response".equals(message.type)) {
                Response response = gson.fromJson(message.message, Response.class);
                if (response == null) {
                    logger.debug("invalid response Message");
                    return;
                }
                CompletableFuture<JsonElement> future = pendingRequests.remove(response.id);
                if (future != null) {
                    logger.debug("result type: {} ", response.type);
                    if (!"resultSuccess".equals(response.type)) {
                        future.completeExceptionally(new Exception(response.error));
                    } else {
                        future.complete(response.result);
                    }
                }
            } else if ("event".equals(message.type)) {
                Event event = gson.fromJson(message.message, Event.class);
                if (event == null) {
                    logger.debug("invalid Event");
                    return;
                }
                switch (event.type) {
                    case "attributeChanged":
                        logger.debug("attributeChanged message {}", event.data);
                        AttributeChangedMessage changedMessage = gson.fromJson(event.data,
                                AttributeChangedMessage.class);
                        if (changedMessage == null) {
                            logger.debug("invalid AttributeChangedMessage");
                            return;
                        }
                        for (AttributeListener listener : attributeListeners) {
                            try {
                                listener.onEvent(changedMessage);
                            } catch (Exception e) {
                                logger.debug("Error notifing listener", e);
                            }
                        }
                        break;
                    case "nodeStateInformation":
                        logger.debug("nodeStateInformation message {}", event.data);
                        NodeStateMessage nodeStateMessage = gson.fromJson(event.data, NodeStateMessage.class);
                        if (nodeStateMessage == null) {
                            logger.debug("invalid NodeStateMessage");
                            return;
                        }
                        for (NodeStateListener listener : nodeStateListeners) {
                            try {
                                listener.onEvent(nodeStateMessage);
                            } catch (Exception e) {
                                logger.debug("Error notifying listener", e);
                            }
                        }
                        break;
                    case "ready":
                        for (ControllerStateListener listener : controllerStateListeners) {
                            listener.onReady();
                        }
                        break;
                    default:
                        break;
                }
            }
        });
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        logger.debug("onWebSocketClose {} {}", statusCode, reason);
        for (ControllerStateListener listener : controllerStateListeners) {
            listener.onDisconnect(reason);
        }
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        logger.debug("onWebSocketError", cause);
    }

    @Override
    public void onWebSocketBinary(byte[] payload, int offset, int len) {
        logger.debug("onWebSocketBinary data, not supported");
    }

    public boolean isConnected() {
        return session != null && session.isOpen();
    }

    public List<String> getCommissionedNodeIds() throws Exception {
        CompletableFuture<JsonElement> future = sendMessage("nodes", "listNodes", null);
        JsonElement obj = future.get();
        List<String> nodes = gson.fromJson(obj, new TypeToken<List<String>>() {
        }.getType());
        return nodes;
    }

    public Node getNode(String id) throws Exception {
        CompletableFuture<JsonElement> future = sendMessage("nodes", "getNode", new Object[] { id });
        JsonElement obj = future.get();
        Node node = gson.fromJson(obj, Node.class);
        return node;
    }

    public void pairNode(String code) throws Exception {
        String formattedCode = code.replaceAll("-", "");
        String[] parts = formattedCode.split(" ");
        CompletableFuture<JsonElement> future = null;
        if (parts.length == 2) {
            future = sendMessage("nodes", "pair", new Object[] { "", parts[0], parts[1] });
        } else {
            future = sendMessage("nodes", "pair", new Object[] { formattedCode });
        }
        future.get();
    }

    public void clusterCommand(String nodeId, Integer endpointId, String clusterName, ClusterCommand command)
            throws Exception {
        Object[] clusterArgs = { String.valueOf(nodeId), endpointId, clusterName, command.commandName, command.args };
        CompletableFuture<JsonElement> future = sendMessage("clusters", "command", clusterArgs);
        future.get();
    }

    public class NodeDeserializer implements JsonDeserializer<Node> {
        @Override
        public Node deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jsonObjectNode = json.getAsJsonObject();
            Node node = new Node();
            node.id = jsonObjectNode.get("id").getAsString();
            node.endpoints = new HashMap<>();
            JsonObject endpointsJson = jsonObjectNode.get("endpoints").getAsJsonObject();
            Set<Map.Entry<String, JsonElement>> endpointEntries = endpointsJson.entrySet();
            for (Map.Entry<String, JsonElement> endpointEntry : endpointEntries) {
                JsonObject jsonObjectElement = endpointEntry.getValue().getAsJsonObject();
                Endpoint endpoint = new Endpoint();
                endpoint.number = jsonObjectElement.get("number").getAsInt();
                endpoint.clusters = new HashMap<>();
                JsonObject clustersJson = jsonObjectElement.get("clusters").getAsJsonObject();
                Set<Map.Entry<String, JsonElement>> clusterEntries = clustersJson.entrySet();
                for (Map.Entry<String, JsonElement> clusterEntry : clusterEntries) {
                    String clusterName = clusterEntry.getKey();
                    JsonElement clusterElement = clusterEntry.getValue();

                    try {
                        Class<?> clazz = Class
                                .forName(BaseCluster.class.getPackageName() + ".gen." + clusterName + "Cluster");
                        if (BaseCluster.class.isAssignableFrom(clazz)) {
                            BaseCluster cluster = context.deserialize(clusterElement, clazz);
                            for (Map.Entry<String, JsonElement> entry : clusterElement.getAsJsonObject().entrySet()) {
                                Field field;
                                try {
                                    field = getField(clazz, entry.getKey());
                                } catch (NoSuchFieldException e) {
                                    logger.debug("Skipping field {}", entry.getKey());
                                    continue;
                                }
                                field.setAccessible(true);
                                field.set(cluster, gson.fromJson(entry.getValue(), field.getType()));
                            }
                            endpoint.clusters.put(clusterName, cluster);
                        }
                    } catch (ClassNotFoundException e) {
                        logger.debug("Cluster not found: {} ", clusterName);
                    } catch (IllegalArgumentException | SecurityException | IllegalAccessException e) {
                        logger.debug("Exception for cluster {}", clusterName, e);
                    }
                }
                node.endpoints.put(endpoint.number, endpoint);
            }
            return node;
        }

        private Field getField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                Class<?> superClass = clazz.getSuperclass();
                if (superClass == null) {
                    throw e;
                } else {
                    return getField(superClass, fieldName);
                }
            }
        }
    }

    private int startNodeJs() throws IOException {
        NodeManager nodeManager = new NodeManager();
        String nodePath = nodeManager.getNodePath();
        NodeRunner oldRunner = this.nodeRunner;
        if (oldRunner != null) {
            oldRunner.removeExitListener(this);
            oldRunner.stopNode();
        }
        nodeRunner = new NodeRunner(nodePath);
        nodeRunner.addExitListener(this);
        return nodeRunner.runNodeWithResource("/matter.js");
    }

    @Override
    public void onNodeExit(int exitCode) {
        disconnect();
        for (ControllerStateListener listener : controllerStateListeners) {
            listener.onDisconnect("Exit code " + exitCode);
        }
    }
}