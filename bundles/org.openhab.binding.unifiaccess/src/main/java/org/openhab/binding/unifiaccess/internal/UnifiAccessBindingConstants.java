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
package org.openhab.binding.unifiaccess.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link UnifiAccessBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiAccessBindingConstants {

    public static final String BINDING_ID = "unifiaccess";

    // Thing Type UIDs (as defined in thing-types.xml)
    public static final ThingTypeUID BRIDGE_THING_TYPE = new ThingTypeUID(BINDING_ID, "bridge");
    public static final ThingTypeUID DOOR_THING_TYPE = new ThingTypeUID(BINDING_ID, "door");

    // Thing configuration keys
    public static final String CONFIG_DOOR_ID = "doorId";

    // Door channel ids (match thing-types.xml)
    public static final String CHANNEL_LOCK = "lock";
    public static final String CHANNEL_POSITION = "position";
    public static final String CHANNEL_LAST_UNLOCK = "lastunlock";
    public static final String CHANNEL_LAST_ACTOR = "lastactor";
    public static final String CHANNEL_LOCK_RULE = "lockrule";

    // Door control channels
    public static final String CHANNEL_UNLOCK_NOW = "unlocknow";
    public static final String CHANNEL_KEEP_UNLOCKED = "keepunlocked";
    public static final String CHANNEL_KEEP_LOCKED = "keeplocked";
    public static final String CHANNEL_UNLOCK_MINUTES = "unlockminutes";
    public static final String CHANNEL_LOCK_EARLY = "lockearly";
}
