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
import org.openhab.binding.espsomfy.internal.ESPSomfyShadeConfiguration;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiClient;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiException;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyShadeDTO;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
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
 * The {@link ESPSomfyShadeHandler} handles commands and state updates for a single ESPSomfy shade.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class ESPSomfyShadeHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyShadeHandler.class);
    private int shadeId = -1;
    private int currentPosition;

    public ESPSomfyShadeHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        ESPSomfyShadeConfiguration config = getConfigAs(ESPSomfyShadeConfiguration.class);
        shadeId = config.shadeId;
        if (shadeId < 0) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "shadeId must be set");
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
        scheduler.execute(this::initializeShade);
    }

    private void initializeShade() {
        logger.debug("Initializing shade {}", shadeId);
        ESPSomfyApiClient client = getApiClient();
        if (client == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }
        try {
            ESPSomfyShadeDTO shade = client.getShade(shadeId);
            logger.debug("Shade {} loaded: position={}, type={}", shadeId, shade.position, shade.shadeType);
            updateProperties(shade);
            updateFromState(shade);
            updateStatus(ThingStatus.ONLINE);
        } catch (ESPSomfyApiException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (RuntimeException e) {
            logger.debug("Unexpected error initializing shade {}: {}", shadeId, e.getMessage(), e);
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
                case CHANNEL_TILT:
                    handleTiltCommand(client, command);
                    break;
                case CHANNEL_COMMAND:
                    if (command instanceof StringType) {
                        client.sendShadeCommand(shadeId, command.toString());
                    }
                    break;
                default:
                    if (command instanceof RefreshType) {
                        refreshState(client);
                    }
            }
        } catch (ESPSomfyApiException e) {
            logger.debug("Command failed for shade {}: {}", shadeId, e.getMessage());
        }
    }

    private void handlePositionCommand(ESPSomfyApiClient client, Command command) throws ESPSomfyApiException {
        if (command instanceof UpDownType) {
            client.sendShadeCommand(shadeId, command == UpDownType.UP ? "Up" : "Down");
        } else if (command instanceof StopMoveType) {
            client.sendShadeCommand(shadeId, "My");
        } else if (command instanceof PercentType percentCommand) {
            client.sendShadePosition(shadeId, percentCommand.intValue());
        } else if (command instanceof RefreshType) {
            refreshState(client);
        }
    }

    private void handleTiltCommand(ESPSomfyApiClient client, Command command) throws ESPSomfyApiException {
        if (command instanceof UpDownType) {
            client.sendTiltCommand(shadeId, command == UpDownType.UP ? "Up" : "Down");
        } else if (command instanceof StopMoveType) {
            client.sendTiltCommand(shadeId, "My");
        } else if (command instanceof PercentType percentCommand) {
            client.setPositions(shadeId, currentPosition, percentCommand.intValue());
        } else if (command instanceof RefreshType) {
            refreshState(client);
        }
    }

    private void refreshState(ESPSomfyApiClient client) {
        try {
            ESPSomfyShadeDTO shade = client.getShade(shadeId);
            updateFromState(shade);
        } catch (ESPSomfyApiException e) {
            logger.debug("Refresh failed for shade {}: {}", shadeId, e.getMessage());
        }
    }

    /**
     * Called by the bridge handler when a state update is received from WebSocket or polling.
     */
    public void updateFromState(ESPSomfyShadeDTO shade) {
        currentPosition = shade.position;
        updateState(CHANNEL_POSITION, new PercentType(shade.position));
        updateState(CHANNEL_DIRECTION, new DecimalType(shade.direction));
        updateState(CHANNEL_MY_POSITION, new DecimalType(shade.myPos));

        if (shade.tiltType > 0) {
            updateState(CHANNEL_TILT, new PercentType(shade.tiltPosition));
        }
        if (shade.sunSensor) {
            updateState(CHANNEL_SUNNY, OnOffType.from((shade.flags & 0x20) != 0));
            updateState(CHANNEL_WINDY, OnOffType.from((shade.flags & 0x10) != 0));
        }
    }

    /**
     * Get the shade ID this handler manages.
     */
    public int getShadeId() {
        return shadeId;
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        if (bridgeStatusInfo.getStatus() == ThingStatus.ONLINE) {
            scheduler.execute(this::initializeShade);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
        }
    }

    private void updateProperties(ESPSomfyShadeDTO shade) {
        Map<String, String> properties = editProperties();
        properties.put(PROPERTY_SHADE_TYPE, String.valueOf(shade.shadeType));
        properties.put(PROPERTY_REMOTE_ADDRESS, String.valueOf(shade.remoteAddress));

        Bridge bridge = getBridge();
        if (bridge != null && bridge.getHandler() instanceof ESPSomfyControllerHandler controllerHandler) {
            String roomName = controllerHandler.getRoomName(shade.roomId);
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
