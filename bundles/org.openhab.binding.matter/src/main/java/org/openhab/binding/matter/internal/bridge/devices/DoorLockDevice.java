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
package org.openhab.binding.matter.internal.bridge.devices;

import java.util.Map;
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.matter.internal.bridge.MatterBridgeClient;
import org.openhab.binding.matter.internal.client.model.cluster.gen.DoorLockCluster;
import org.openhab.core.items.GenericItem;
import org.openhab.core.items.GroupItem;
import org.openhab.core.items.Item;
import org.openhab.core.items.MetadataRegistry;
import org.openhab.core.library.items.SwitchItem;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.State;

/**
 * The {@link DoorLockDevice}
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class DoorLockDevice extends GenericDevice {

    public DoorLockDevice(MetadataRegistry metadataRegistry, MatterBridgeClient client, GenericItem item) {
        super(metadataRegistry, client, item);
    }

    @Override
    public String deviceType() {
        return "DoorLockDevice";
    }

    @Override
    public void handleMatterEvent(String clusterName, String attributeName, Object data) {
        switch (attributeName) {
            case "lockState": {
                int lockInt = Integer.parseInt(data.toString());
                boolean locked = DoorLockCluster.LockStateEnum.LOCKED.getValue() == lockInt;
                if (primaryItem instanceof GroupItem groupItem) {
                    groupItem.send(OnOffType.from(locked));
                } else {
                    ((SwitchItem) primaryItem).send(OnOffType.from(locked));
                }
            }
            default:
                break;
        }
    }

    @Override
    public Map<String, Object> activate() {
        dispose();
        primaryItem.addStateChangeListener(this);
        return Map.of("lockState",
                Optional.ofNullable(primaryItem.getStateAs(OnOffType.class))
                        .orElseGet(() -> OnOffType.OFF) == OnOffType.ON ? DoorLockCluster.LockStateEnum.LOCKED.value
                                : DoorLockCluster.LockStateEnum.UNLOCKED.value);
    }

    @Override
    public void dispose() {
        primaryItem.removeStateChangeListener(this);
    }

    public void updateState(Item item, State state) {
        if (state instanceof OnOffType onOffType) {
            setEndpointState("doorLock", "lockState",
                    onOffType == OnOffType.ON ? DoorLockCluster.LockStateEnum.LOCKED.value
                            : DoorLockCluster.LockStateEnum.UNLOCKED.value);
        }
    }
}
