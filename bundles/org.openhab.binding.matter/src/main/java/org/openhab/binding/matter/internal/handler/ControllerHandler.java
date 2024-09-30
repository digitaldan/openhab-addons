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
package org.openhab.binding.matter.internal.handler;

import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_COMMAND;
import static org.openhab.binding.matter.internal.MatterBindingConstants.CHANNEL_PAIR_CODE;
import static org.openhab.binding.matter.internal.MatterBindingConstants.THING_TYPE_ENDPOINT;

import java.io.File;
import java.math.BigInteger;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.matter.internal.client.MatterClientListener;
import org.openhab.binding.matter.internal.client.MatterWebsocketClient;
import org.openhab.binding.matter.internal.client.model.Endpoint;
import org.openhab.binding.matter.internal.client.model.Node;
import org.openhab.binding.matter.internal.client.model.cluster.BaseCluster;
import org.openhab.binding.matter.internal.client.model.cluster.gen.BasicInformationCluster;
import org.openhab.binding.matter.internal.client.model.ws.AttributeChangedMessage;
import org.openhab.binding.matter.internal.client.model.ws.NodeStateMessage;
import org.openhab.binding.matter.internal.config.ControllerConfiguration;
import org.openhab.binding.matter.internal.discovery.MatterDiscoveryHandler;
import org.openhab.binding.matter.internal.discovery.MatterDiscoveryService;
import org.openhab.core.OpenHAB;
import org.openhab.core.config.core.Configuration;
import org.openhab.core.config.core.validation.ConfigValidationException;
import org.openhab.core.thing.*;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ControllerHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class ControllerHandler extends BaseBridgeHandler implements MatterClientListener, MatterDiscoveryHandler {

    private final Logger logger = LoggerFactory.getLogger(ControllerHandler.class);
    // The endpoints / devices associated with a node. Typically a node has 1 device endpoint, but may have more (Hue
    // bridge, complicated devices, etc..)
    private Map<BigInteger, Map<Integer, Endpoint>> nodeEndpoints = Collections.synchronizedMap(new HashMap<>());
    // Last time the node sent an update to us
    // private Map<BigInteger, LocalDateTime> nodesLastUpdate = new ConcurrentHashMap<>();
    // Set of nodes we are waiting to connect to
    private Set<BigInteger> outstandingNodeRequests = Collections.synchronizedSet(new HashSet<>());
    // Set of nodes we need to try reconnecting to
    private Set<BigInteger> disconnectedNodes = Collections.synchronizedSet(new HashSet<>());
    // Nodes which are linked to endpoints.
    private Set<BigInteger> linkedNodes = Collections.synchronizedSet(new HashSet<>());

    private @Nullable MatterDiscoveryService discoveryService;
    private MatterWebsocketClient client;
    private @Nullable ScheduledFuture<?> reconnectFuture;
    private boolean running = true;
    private boolean ready = false;
    // private boolean updating = false;
    private @Nullable ScheduledFuture<?> checkFuture;

    public ControllerHandler(Bridge bridge) {
        super(bridge);
        client = new MatterWebsocketClient();
        client.addListener(this);
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Set.of(MatterDiscoveryService.class);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand {} {}", channelUID, command);
        if (!client.isConnected()) {
            logger.debug("not connected");
            return;
        }
        if (CHANNEL_PAIR_CODE.equals(channelUID.getId())) {
            try {
                client.pairNode(command.toString());
            } catch (Exception e) {
                logger.debug("Could not pair", e);
            }
        }
        if (CHANNEL_COMMAND.equals(channelUID.getId())) {
            String[] args = command.toString().split(" ");
            if (args.length < 2) {
                logger.debug("Commands require at least 2 segments");
                return;
            }
            Object[] params = args.length > 2 ? (Object[]) Arrays.copyOfRange(args, 2, args.length) : new String[0];
            client.genericCommand(args[0], args[1], params).thenAccept(result -> {
                logger.debug("Command {} ", command);
                logger.debug("Result: {}", result);
            }).exceptionally(e -> {
                logger.debug("Could not send command", e);
                return null;
            });
        }
    }

    @Override
    public void handleConfigurationUpdate(Map<String, Object> configurationParameters)
            throws ConfigValidationException {
        logger.debug("handleConfigurationUpdate");
        validateConfigurationParameters(configurationParameters);
        Configuration configuration = editConfiguration();
        boolean reinitialize = false;
        String pairCode = null;
        for (Entry<String, Object> configurationParameter : configurationParameters.entrySet()) {
            Object value = configurationParameter.getValue();
            logger.debug("{}: old: {} new: {}", configurationParameter.getKey(),
                    configuration.get(configurationParameter.getKey()), configurationParameter.getValue());
            // Ignore any configuration parameters that have not changed
            if (Objects.equals(configurationParameter.getValue(), configuration.get(configurationParameter.getKey()))) {
                logger.debug("Controller Configuration update ignored {} to {} ({})", configurationParameter.getKey(),
                        value, value == null ? "null" : value.getClass().getSimpleName());
                continue;
            }
            logger.debug("Controller Configuration update {} to {}", configurationParameter.getKey(), value);
            switch (configurationParameter.getKey()) {
                case "host":
                case "port":
                case "nodeId":
                    reinitialize = true;
                    break;
                case "pairCode":
                    if (!value.toString().isBlank()) {
                        pairCode = value.toString();
                        value = "";
                    }
            }
            configuration.put(configurationParameter.getKey(), value);
        }
        updateConfiguration(configuration);
        if (reinitialize) {
            dispose();
            initialize();
        } else if (pairCode != null) {
            try {
                client.pairNode(pairCode);
            } catch (Exception e) {
                logger.debug("Could not pair", e);
            }
        }
    }

    @Override
    public void initialize() {
        logger.debug("initialize");
        String folderName = OpenHAB.getUserDataFolder() + File.separator + "matter";
        File folder = new File(folderName);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        String storagePath = folder.getAbsolutePath() + File.separator + "controller-" + getThing().getUID().getId()
                + ".json";
        logger.debug("matter config: {}", storagePath);
        final ControllerConfiguration config = getConfigAs(ControllerConfiguration.class);
        checkFuture = scheduler.scheduleAtFixedRate(this::checkNodes, 5, 5, TimeUnit.MINUTES);
        scheduler.execute(() -> {
            try {
                if (!config.host.isBlank() && config.port > 0) {
                    logger.debug("Connecting to custom host {} and port {}", config.host, config.port);
                    client.connect(config.host, config.port, storagePath);
                } else {
                    logger.debug("Connecting to embedded service");
                    client.connect(storagePath);
                }
                running = true;
            } catch (Exception e) {
                logger.debug("Could not init", e);
                setOffline(e.getLocalizedMessage());
            }
        });
    }

    @Override
    public void dispose() {
        logger.debug("dispose");
        ready = false;
        running = false;
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        ScheduledFuture<?> checkFuture = this.checkFuture;
        if (checkFuture != null) {
            checkFuture.cancel(true);
        }
        nodeEndpoints.clear();
        outstandingNodeRequests.clear();
        disconnectedNodes.clear();
        linkedNodes.clear();
        // nodesLastUpdate.clear();
        client.disconnect();
    }

    @Override
    public void childHandlerInitialized(ThingHandler childHandler, Thing childThing) {
        super.childHandlerInitialized(childHandler, childThing);
        logger.debug("childHandlerInitialized ready {} {}", ready, childHandler);
        // if (!ready || updating) {
        // return;
        // }
        if (childHandler instanceof EndpointHandler handler) {
            BigInteger nodeId = handler.getNodeId();
            linkedNodes.add(nodeId);
            updateNode(nodeId);
        }
    }

    @Override
    public void childHandlerDisposed(ThingHandler childHandler, Thing childThing) {
        super.childHandlerDisposed(childHandler, childThing);
        logger.debug("childHandlerDisposed {}", childHandler);
        if (!ready) {
            return;
        }
        if (childHandler instanceof EndpointHandler handler) {
            endpointRemoved(handler.getNodeId(), handler.getEndpointId(), false);
        }
    }

    @Override
    public void setDiscoveryService(@Nullable MatterDiscoveryService service) {
        logger.debug("setDiscoveryService");
        this.discoveryService = service;
    }

    @Override
    public void startScan() {
        refresh();
    }

    @Override
    public void onEvent(NodeStateMessage message) {
        logger.debug("Node onEvent: node {} is {}", message.nodeId, message.state);
        switch (message.state) {
            case CONNECTED:
                // if (!updating) {
                updateNode(message.nodeId);
                // }
                break;
            case DECOMMISSIONED:
                updateEndpointStatuses(message.nodeId, ThingStatus.OFFLINE, ThingStatusDetail.GONE,
                        "Node " + message.state);
                removeNode(message.nodeId, false);
                break;
            case DISCONNECTED:
                updateEndpointStatuses(message.nodeId, ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Node " + message.state);
                // only add an endpoint to our disconnect list if we care about it
                if (nodeEndpoints.containsKey(message.nodeId)) {
                    disconnectedNodes.add(message.nodeId);
                }
                break;
            case RECONNECTING:
            case WAITINGFORDEVICEDISCOVERY:
                updateEndpointStatuses(message.nodeId, ThingStatus.UNKNOWN, ThingStatusDetail.NOT_YET_READY,
                        "Node " + message.state);
                break;
            case STRUCTURECHANGED:
                break;
            default:
        }
    }

    @Override
    public void onEvent(AttributeChangedMessage message) {
        EndpointHandler handler = endpointHandler(message.path.nodeId, message.path.endpointId);
        // modifyNodesLastUpdate(message.path.nodeId);
        if (handler == null) {
            logger.debug("No handler found for node {}", message.path.nodeId);
            return;
        }
        handler.onEvent(message);
    }

    @Override
    public void onConnect() {
        logger.debug("Websocket connected");
    }

    @Override
    public void onDisconnect(String reason) {
        if (!running) {
            return;
        }
        client.disconnect();
        setOffline(reason);
    }

    @Override
    public void onReady() {
        ready = true;
        updateStatus(ThingStatus.ONLINE);
        logger.debug("onReady obtaining lock");
        // updating = true;
        // client.getCommissionedNodeIds(false).thenAccept(nodeIds -> {
        // for (BigInteger id : nodeIds) {
        // updateNode(id);
        // }
        // }).exceptionally(e -> {
        // logger.debug("Error communicating with controller", e);
        // setOffline(e.getLocalizedMessage());
        // return null;
        // }).whenComplete((nodeIds, e) -> {
        // logger.debug("onReady releasing lock");
        // updating = false;
        // });
        linkedNodes.forEach(nodeId -> updateNode(nodeId));
    }

    protected MatterWebsocketClient getClient() {
        return client;
    }

    private void refresh() {
        logger.debug("refresh");
        // if (!ready || updating) {
        // return;
        // }
        if (!ready) {
            return;
        }
        logger.debug("refresh obtaining lock");
        // updating = true;
        client.getConnectedNodeIds().thenAccept(nodeIds -> {
            for (BigInteger id : nodeIds) {
                updateNode(id);
            }
        }).exceptionally(e -> {
            logger.debug("Error communicating with controller", e);
            setOffline(e.getLocalizedMessage());
            return null;
        }).whenComplete((nodeIds, e) -> {
            logger.debug("refresh releasing lock");
            // updating = false;
        });
    }

    protected void endpointRemoved(BigInteger nodeId, int endpointId, boolean isDeleted) {
        logger.debug("endpointRemoved endpoint {}:{}", nodeId, endpointId);

        // only remove the node from the network if all endpoints things on the node are deleted
        synchronized (nodeEndpoints) {
            boolean lastEndpoint = true;
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof EndpointHandler endpointHandler) {
                    if (endpointHandler.getNodeId().equals(nodeId)) {
                        // if this handler has another endpoint on the node, then its not last
                        if (endpointHandler.endpointId != endpointId) {
                            lastEndpoint = false;
                            break;
                        }
                    }
                }
            }
            if (lastEndpoint) {
                removeNode(nodeId, isDeleted);
            }
        }
    }

    private void removeNode(BigInteger nodeId, boolean decommission) {
        try {
            logger.debug("Decommissioning node {}", nodeId);
            nodeEndpoints.remove(nodeId);
            disconnectedNodes.remove(nodeId);
            outstandingNodeRequests.remove(nodeId);
            // nodesLastUpdate.remove(nodeId);
            // check if we remove deleted endpoint things from the actual matter network
            if (decommission && getConfigAs(ControllerConfiguration.class).decommissionNodesOnDelete) {
                logger.debug("Decommissioning node {}", nodeId);
                client.removeNode(nodeId);
            } else {
                client.disconnectNode(nodeId);
            }
        } catch (Exception e) {
            logger.debug("Could not decommission node {}", nodeId, e);
        }
    }

    private synchronized void reconnect() {
        ScheduledFuture<?> reconnectFuture = this.reconnectFuture;
        if (reconnectFuture != null) {
            reconnectFuture.cancel(true);
        }
        this.reconnectFuture = scheduler.schedule(this::initialize, 30, TimeUnit.SECONDS);
    }

    protected synchronized void updateNode(BigInteger id) {
        logger.debug("updateNode BEGIN {}", id);
        // If we are already waiting to get this node, return;
        if (!ready || outstandingNodeRequests.contains(id)) {
            return;
        }
        outstandingNodeRequests.add(id);
        client.getNode(id).thenAccept(node -> {
            updateNode(node);
            // modifyNodesLastUpdate(node.id);
            disconnectedNodes.remove(id);
            logger.debug("updateNode END {}", id);
        }).exceptionally(e -> {
            logger.debug("Could not update node {}", id, e);
            disconnectedNodes.add(id);
            String message = e.getMessage();
            updateEndpointStatuses(id, ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    message != null ? message : "");
            return null;
        }).whenComplete((node, e) -> outstandingNodeRequests.remove(id));
    }

    private void updateNode(Node node) {
        synchronized (nodeEndpoints) {
            Map<Integer, Endpoint> endpoints = new HashMap<>();
            for (Endpoint e : node.endpoints.values()) {
                endpoints.put(e.number, e);
                discoverChildEndpoint(node, e);
                EndpointHandler handler = endpointHandler(node.id, e.number);
                if (handler != null) {
                    Thing thing = handler.getThing();
                    updateEndpointThingProperties(node, thing, e.number);
                    handler.updateEndpoint(e);
                }
            }
            nodeEndpoints.put(node.id, endpoints);
        }
    }

    private void updateEndpointStatuses(BigInteger nodeId, ThingStatus status, ThingStatusDetail detail,
            String details) {
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof EndpointHandler endpointHandler) {
                if (nodeId.equals(endpointHandler.getNodeId())) {
                    endpointHandler.setEndpointStatus(status, detail, details);
                }
            }
        }
    }

    private void setOffline(@Nullable String message) {
        client.disconnect();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
        reconnect();
    }

    private void discoverChildEndpoint(Node node, Endpoint endpoint) {
        logger.debug("discoverChildEndpoint {}", node.id);
        // endpoint 0 is the root info cluster, not an actual device
        if (endpoint.number == 0) {
            return;
        }

        MatterDiscoveryService discoveryService = this.discoveryService;
        if (discoveryService != null) {
            ThingUID bridgeUID = getThing().getUID();
            ThingUID thingUID = new ThingUID(THING_TYPE_ENDPOINT, bridgeUID, node.id + "_" + endpoint.number);
            discoveryService.discoverChildEndpointThing(thingUID, bridgeUID, node, endpoint.number);
        }
    }

    private @Nullable EndpointHandler endpointHandler(BigInteger nodeId, int endpointId) {
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler instanceof EndpointHandler endpointHandler) {
                if (nodeId.equals(endpointHandler.getNodeId()) && endpointHandler.getEndpointId() == endpointId) {
                    return endpointHandler;
                }
            }
        }
        return null;
    }

    private void updateEndpointThingProperties(Node node, Thing thing, int endpointNum) {
        Endpoint root = node.endpoints.get(Integer.valueOf(0));
        if (root != null) {
            BaseCluster cluster = root.clusters.get(BasicInformationCluster.CLUSTER_NAME);
            if (cluster != null && cluster instanceof BasicInformationCluster basicCluster) {
                thing.setProperty(Thing.PROPERTY_SERIAL_NUMBER, basicCluster.serialNumber);
                thing.setProperty(Thing.PROPERTY_FIRMWARE_VERSION, basicCluster.softwareVersionString);
                thing.setProperty(Thing.PROPERTY_VENDOR, basicCluster.vendorName);
                thing.setProperty(Thing.PROPERTY_MODEL_ID, basicCluster.productName);
                thing.setProperty(Thing.PROPERTY_HARDWARE_VERSION, basicCluster.hardwareVersionString);
                thing.setProperty("path", node.id + ":" + endpointNum);
            }
        }
    }

    // private void modifyNodesLastUpdate(BigInteger nodeId) {
    // nodesLastUpdate.put(nodeId, LocalDateTime.now());
    // }

    /**
     * I'm not sure if this is necessary anymore, need to check if matter.js constantly scans for nodes for us
     */
    private void checkNodes() {
        // LocalDateTime checkTime = LocalDateTime.now().minusMinutes(5);
        // nodesLastUpdate.forEach((nodeId, lastUpdate) -> {
        // if (lastUpdate.isBefore(checkTime)) {
        // updateNode(nodeId);
        // }
        // });
        if (disconnectedNodes.size() > 0) {
            client.getCommissionedNodeIds(false).thenAccept(nodeIds -> {
                // check to make sure a disconnected node is actually known by the controller.
                // if its not, then we can never connect to it again.
                Set<BigInteger> disconnectedNodesCopy = Set.copyOf(disconnectedNodes);
                disconnectedNodesCopy.forEach(nodeId -> {
                    if (nodeIds.contains(nodeId)) {
                        updateNode(nodeId);
                    } else {
                        disconnectedNodes.remove(nodeId);
                    }
                });
            }).exceptionally(e -> {
                logger.debug("Error communicating with controller", e);
                return null;
            });

        }
    }
}
