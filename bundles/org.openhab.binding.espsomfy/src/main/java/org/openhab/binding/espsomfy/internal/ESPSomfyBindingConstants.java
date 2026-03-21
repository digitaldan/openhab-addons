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
package org.openhab.binding.espsomfy.internal;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link ESPSomfyBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class ESPSomfyBindingConstants {

    public static final String BINDING_ID = "espsomfy";

    // Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_CONTROLLER = new ThingTypeUID(BINDING_ID, "controller");
    public static final ThingTypeUID THING_TYPE_SHADE = new ThingTypeUID(BINDING_ID, "shade");
    public static final ThingTypeUID THING_TYPE_GROUP = new ThingTypeUID(BINDING_ID, "group");

    public static final Set<ThingTypeUID> SUPPORTED_BRIDGE_TYPES = Set.of(THING_TYPE_CONTROLLER);
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES = Set.of(THING_TYPE_SHADE, THING_TYPE_GROUP);
    public static final Set<ThingTypeUID> ALL_SUPPORTED_TYPES = Set.of(THING_TYPE_CONTROLLER, THING_TYPE_SHADE,
            THING_TYPE_GROUP);

    // Bridge channel IDs
    public static final String CHANNEL_VERSION = "version";

    // Shade/Group channel IDs
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_TILT = "tilt";
    public static final String CHANNEL_DIRECTION = "direction";
    public static final String CHANNEL_MY_POSITION = "myPosition";
    public static final String CHANNEL_COMMAND = "command";
    public static final String CHANNEL_SUNNY = "sunny";
    public static final String CHANNEL_WINDY = "windy";

    // Config property names
    public static final String CONFIG_HOSTNAME = "hostname";
    public static final String CONFIG_SHADE_ID = "shadeId";
    public static final String CONFIG_GROUP_ID = "groupId";

    // Thing properties
    public static final String PROPERTY_SHADE_TYPE = "shadeType";
    public static final String PROPERTY_REMOTE_ADDRESS = "remoteAddress";
    public static final String PROPERTY_ROOM_NAME = "roomName";
    public static final String PROPERTY_SERVER_ID = "serverId";

    // mDNS
    public static final String MDNS_SERVICE_TYPE = "_espsomfy_rts._tcp.local.";

    // Ports
    public static final int DEFAULT_HTTP_PORT = 80;
    public static final int WEBSOCKET_PORT = 8080;

    // Timeouts
    public static final int API_TIMEOUT_MS = 5000;
}
