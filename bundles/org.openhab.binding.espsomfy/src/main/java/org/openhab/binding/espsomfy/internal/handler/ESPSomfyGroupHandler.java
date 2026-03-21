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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.espsomfy.internal.ESPSomfyGroupConfiguration;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiClient;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiException;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyGroupDTO;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StopMoveType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.types.UpDownType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ESPSomfyGroupHandler} handles commands and state updates for an ESPSomfy shade group.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class ESPSomfyGroupHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyGroupHandler.class);
    private int groupId = -1;

    public ESPSomfyGroupHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        ESPSomfyGroupConfiguration config = getConfigAs(ESPSomfyGroupConfiguration.class);
        groupId = config.groupId;
        if (groupId < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "groupId must be set");
            return;
        }
        Bridge bridge = getBridge();
        if (bridge == null || bridge.getHandler() == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED);
            return;
        }
        if (bridge.getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(this::initializeGroup);
    }

    private void initializeGroup() {
        logger.debug("Initializing group {}", groupId);
        ESPSomfyApiClient client = getApiClient();
        if (client == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }
        try {
            ESPSomfyGroupDTO group = client.getGroup(groupId);
            logger.debug("Group {} loaded: name={}", groupId, group.name);
            updateProperties(group);
            updateFromState(group);
            updateStatus(ThingStatus.ONLINE);
        } catch (ESPSomfyApiException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            logger.debug("Unexpected error initializing group {}: {}", groupId, e.getMessage(), e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        ESPSomfyApiClient client = getApiClient();
        if (client == null) {
            logger.debug("Cannot handle command, bridge not available");
            return;
        }

        try {
            switch (channelUID.getId()) {
                case CHANNEL_POSITION:
                    handlePositionCommand(client, command);
                    break;
                case CHANNEL_COMMAND:
                    if (command instanceof StringType) {
                        client.sendGroupCommand(groupId, command.toString());
                    }
                    break;
                default:
                    if (command instanceof RefreshType) {
                        refreshState(client);
                    }
            }
        } catch (ESPSomfyApiException e) {
            logger.debug("Command failed for group {}: {}", groupId, e.getMessage());
        }
    }

    private void handlePositionCommand(ESPSomfyApiClient client, Command command) throws ESPSomfyApiException {
        if (command instanceof UpDownType) {
            client.sendGroupCommand(groupId, command == UpDownType.UP ? "Up" : "Down");
        } else if (command instanceof StopMoveType) {
            client.sendGroupCommand(groupId, "My");
        } else if (command instanceof PercentType percentCommand) {
            client.sendGroupPosition(groupId, percentCommand.intValue());
        } else if (command instanceof RefreshType) {
            refreshState(client);
        }
    }

    private void refreshState(ESPSomfyApiClient client) {
        try {
            ESPSomfyGroupDTO group = client.getGroup(groupId);
            updateFromState(group);
        } catch (ESPSomfyApiException e) {
            logger.debug("Refresh failed for group {}: {}", groupId, e.getMessage());
        }
    }

    /**
     * Called by the bridge handler when a state update is received from WebSocket or polling.
     */
    public void updateFromState(ESPSomfyGroupDTO group) {
        updateState(CHANNEL_POSITION, new PercentType(group.position));
        updateState(CHANNEL_DIRECTION, new DecimalType(group.direction));
        updateState(CHANNEL_MY_POSITION, new DecimalType(group.myPos));
    }

    /**
     * Get the group ID this handler manages.
     */
    public int getGroupId() {
        return groupId;
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            scheduler.execute(this::initializeGroup);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    private void updateProperties(ESPSomfyGroupDTO group) {
        Map<String, String> properties = editProperties();
        properties.put(PROPERTY_REMOTE_ADDRESS, String.valueOf(group.remoteAddress));

        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof ESPSomfyControllerHandler controllerHandler) {
            String roomName = controllerHandler.getRoomName(group.roomId);
            if (!roomName.isEmpty()) {
                properties.put(PROPERTY_ROOM_NAME, roomName);
            }
        }
        updateProperties(properties);
    }

    private @Nullable ESPSomfyApiClient getApiClient() {
        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof ESPSomfyControllerHandler controllerHandler) {
            return controllerHandler.getApiClient();
        }
        return null;
    }
}
