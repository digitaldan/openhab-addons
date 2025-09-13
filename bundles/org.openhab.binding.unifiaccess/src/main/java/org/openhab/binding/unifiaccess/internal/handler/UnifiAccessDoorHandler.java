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

import static org.openhab.binding.unifiaccess.internal.UnifiAccessBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiaccess.internal.UnifiAccessBindingConstants;
import org.openhab.binding.unifiaccess.internal.api.UniFiAccessApiClient;
import org.openhab.binding.unifiaccess.internal.config.UnifiAccessDoorConfiguration;
import org.openhab.binding.unifiaccess.internal.dto.Door;
import org.openhab.binding.unifiaccess.internal.dto.DoorState;
import org.openhab.binding.unifiaccess.internal.dto.UniFiAccessHttpException;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thing handler for UniFi Access Door things.
 * 
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiAccessDoorHandler extends BaseThingHandler {

    public static final String CONFIG_DOOR_ID = UnifiAccessBindingConstants.CONFIG_DOOR_ID;

    private final Logger logger = LoggerFactory.getLogger(UnifiAccessDoorHandler.class);

    private String doorId = "";

    public UnifiAccessDoorHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        doorId = getConfigAs(UnifiAccessDoorConfiguration.class).doorId;
        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            scheduler.execute(this::refreshState);
            return;
        }
        String channelId = channelUID.getId();
        UnifiAccessBridgeHandler bridge = getBridgeHandler();
        UniFiAccessApiClient api = bridge != null ? bridge.getApiClient() : null;
        if (api == null) {
            return;
        }
        try {
            switch (channelId) {
                case UnifiAccessBindingConstants.CHANNEL_LOCK:
                    if (command instanceof OnOffType onOff) {
                        if (onOff == OnOffType.ON) {
                            api.lockEarly(doorId);
                        } else {
                            api.unlockDoor(doorId, null, null, null);
                        }
                    }
                    break;
                case UnifiAccessBindingConstants.CHANNEL_UNLOCK_NOW:
                    api.unlockDoor(doorId, null, null, null);
                    break;
                case UnifiAccessBindingConstants.CHANNEL_KEEP_UNLOCKED:
                    api.keepDoorUnlocked(doorId);
                    break;
                case UnifiAccessBindingConstants.CHANNEL_KEEP_LOCKED:
                    api.keepDoorLocked(doorId);
                    break;
                case UnifiAccessBindingConstants.CHANNEL_UNLOCK_MINUTES:
                    int minutes = Integer.parseInt(command.toString());
                    api.unlockForMinutes(doorId, minutes);
                    break;
                case UnifiAccessBindingConstants.CHANNEL_LOCK_EARLY:
                    api.lockEarly(doorId);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            logger.debug("Command failed for door {}: {}", doorId, e.getMessage());
        }
    }

    public void updateFromDoor(Door door) {
        logger.debug("Updating door state from door: {}", door);
        if (door.doorLockRelayStatus != null) {
            updateLock(door.doorLockRelayStatus);
        }
        if (door.doorPositionStatus != null) {
            updatePosition(door.doorPositionStatus);
        }
    }

    public void updateLock(DoorState.LockState lock) {
        updateState(UnifiAccessBindingConstants.CHANNEL_LOCK,
                lock == DoorState.LockState.LOCKED ? OnOffType.ON : OnOffType.OFF);
        updateStatus(ThingStatus.ONLINE);
    }

    public void updatePosition(DoorState.DoorPosition position) {
        updateState(UnifiAccessBindingConstants.CHANNEL_POSITION,
                position == DoorState.DoorPosition.OPEN ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        updateStatus(ThingStatus.ONLINE);
    }

    public void setLastUnlock(@Nullable String actorName, long whenEpochMs) {
        if (actorName != null) {
            updateState(UnifiAccessBindingConstants.CHANNEL_LAST_ACTOR, StringType.valueOf(actorName));
        }
        if (whenEpochMs > 0) {
            updateState(UnifiAccessBindingConstants.CHANNEL_LAST_UNLOCK,
                    new DateTimeType(java.time.Instant.ofEpochMilli(whenEpochMs)));
        }
    }

    protected void refreshState() {
        logger.debug("Refreshing door state for {}", doorId);
        UnifiAccessBridgeHandler bridge = getBridgeHandler();
        UniFiAccessApiClient api = bridge != null ? bridge.getApiClient() : null;
        if (api == null) {
            return;
        }
        try {
            Door door = api.getDoor(doorId);
            if (door != null) {
                updateFromDoor(door);
            }
        } catch (UniFiAccessHttpException e) {
            logger.debug("Refresh failed for door {}: {}", doorId, e.getMessage(), e);
        }
    }

    private @Nullable UnifiAccessBridgeHandler getBridgeHandler() {
        var b = getBridge();
        if (b == null) {
            return null;
        }
        var h = b.getHandler();
        return (h instanceof UnifiAccessBridgeHandler) ? (UnifiAccessBridgeHandler) h : null;
    }
}
