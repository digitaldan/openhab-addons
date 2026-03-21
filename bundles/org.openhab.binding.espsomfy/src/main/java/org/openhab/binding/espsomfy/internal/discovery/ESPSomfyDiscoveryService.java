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
package org.openhab.binding.espsomfy.internal.discovery;

import static org.openhab.binding.espsomfy.internal.ESPSomfyBindingConstants.*;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiClient;
import org.openhab.binding.espsomfy.internal.api.ESPSomfyApiException;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyGroupDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyRoomDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyShadeDTO;
import org.openhab.binding.espsomfy.internal.handler.ESPSomfyControllerHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * The {@link ESPSomfyDiscoveryService} discovers shades and groups connected to an ESPSomfy controller.
 *
 * @author Dan Cunningham - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = ESPSomfyDiscoveryService.class)
@NonNullByDefault
public class ESPSomfyDiscoveryService extends AbstractThingHandlerDiscoveryService<ESPSomfyControllerHandler> {

    private static final int SEARCH_TIME_SECONDS = 10;

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyDiscoveryService.class);

    public ESPSomfyDiscoveryService() {
        super(ESPSomfyControllerHandler.class, SUPPORTED_THING_TYPES, SEARCH_TIME_SECONDS);
    }

    @Override
    protected void startScan() {
        ESPSomfyApiClient client = thingHandler.getApiClient();
        if (client == null) {
            logger.debug("Cannot scan, bridge API client not available");
            return;
        }

        ThingUID bridgeUID = thingHandler.getThing().getUID();

        try {
            JsonObject controller = client.getController();

            // Build room name lookup
            Map<Integer, String> roomNames = new HashMap<>();
            ESPSomfyRoomDTO[] rooms = client.parseRoomsFromController(controller);
            for (ESPSomfyRoomDTO room : rooms) {
                roomNames.put(room.roomId, room.name);
            }

            // Discover shades
            ESPSomfyShadeDTO[] shades = client.parseShadesFromController(controller);
            for (ESPSomfyShadeDTO shade : shades) {
                String name = shade.name.isEmpty() ? "Shade " + shade.shadeId : shade.name;
                String roomName = roomNames.getOrDefault(shade.roomId, "");
                String label = "Somfy Shade: " + name + (roomName.isEmpty() ? "" : " (" + roomName + ")");
                ThingUID shadeUID = new ThingUID(THING_TYPE_SHADE, bridgeUID, String.valueOf(shade.shadeId));
                DiscoveryResult result = DiscoveryResultBuilder.create(shadeUID).withBridge(bridgeUID).withLabel(label)
                        .withProperty(CONFIG_SHADE_ID, shade.shadeId).withRepresentationProperty(CONFIG_SHADE_ID)
                        .build();
                thingDiscovered(result);
            }

            // Discover groups
            ESPSomfyGroupDTO[] groups = client.parseGroupsFromController(controller);
            for (ESPSomfyGroupDTO group : groups) {
                String name = group.name.isEmpty() ? "Group " + group.groupId : group.name;
                String roomName = roomNames.getOrDefault(group.roomId, "");
                String label = "Somfy Group: " + name + (roomName.isEmpty() ? "" : " (" + roomName + ")");
                ThingUID groupUID = new ThingUID(THING_TYPE_GROUP, bridgeUID, String.valueOf(group.groupId));
                DiscoveryResult result = DiscoveryResultBuilder.create(groupUID).withBridge(bridgeUID).withLabel(label)
                        .withProperty(CONFIG_GROUP_ID, group.groupId).withRepresentationProperty(CONFIG_GROUP_ID)
                        .build();
                thingDiscovered(result);
            }
        } catch (ESPSomfyApiException e) {
            logger.debug("Discovery scan failed: {}", e.getMessage());
        }
    }
}
