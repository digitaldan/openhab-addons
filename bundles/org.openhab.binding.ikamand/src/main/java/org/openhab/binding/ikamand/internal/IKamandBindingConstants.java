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
package org.openhab.binding.ikamand.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link IKamandBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class IKamandBindingConstants {

    private static final String BINDING_ID = "ikamand";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_IKAMAND = new ThingTypeUID(BINDING_ID, "ikamand");

    // List of all Channel ids
    public static final String CHANNEL_FAN_SPEED = "fan-speed";
    public static final String CHANNEL_TEMPERATURE_PIT = "pit-temp";
    public static final String CHANNEL_TARGET_TEMPERATURE = "target-temp";
    public static final String CHANNEL_TEMPERATURE_PROBE1 = "probe1-temp";
    public static final String CHANNEL_TEMPERATURE_PROBE2 = "probe2-temp";
    public static final String CHANNEL_TEMPERATURE_PROBE3 = "probe3-temp";
    public static final String CHANNEL_RM = "rm";
    public static final String CHANNEL_CM = "cm";
    public static final String CHANNEL_AG = "ag";
    public static final String CHANNEL_AS = "as";
    public static final String CHANNEL_FOOD_PROBE = "food-probe";
    public static final String CHANNEL_CURRENT_TIME = "current-time";
    public static final String CHANNEL_COOK_END_TIME = "cook-end-time";
    public static final String CHANNEL_GRILL_END_TIME = "grill-end-time";
    public static final String CHANNEL_COOK_ID = "cook-id";
    public static final String CHANNEL_COOK_START = "cook-start";
    public static final String CHANNEL_TARGET_FOOD_TEMP = "target-food-temp";
    // Alias for AG – grill start flag
    public static final String CHANNEL_GRILL_START = "grill-start";

    // iKamand API key constants
    public static final String KEY_COOK_END_TIME = "sce";
    public static final String KEY_COOK_ID = "csid";
    public static final String KEY_COOK_START = "acs";
    public static final String KEY_CURRENT_TIME = "ct";
    public static final String KEY_FAN_SPEED = "dc";
    public static final String KEY_FOOD_PROBE = "p";
    public static final String KEY_PIT_TEMP = "pt";
    public static final String KEY_PROBE_1 = "t1";
    public static final String KEY_PROBE_2 = "t2";
    public static final String KEY_PROBE_3 = "t3";
    public static final String KEY_TARGET_FOOD_TEMP = "tft";
    public static final String KEY_TARGET_PIT_TEMP = "tpt";
    public static final String KEY_UNKNOWN_RECEIVE_VAR1 = "rm";
    public static final String KEY_UNKNOWN_RECEIVE_VAR2 = "cm";
    public static final String KEY_UNKNOWN_RECEIVE_VAR3 = "ag";
    public static final String KEY_UNKNOWN_SEND_VAR1 = "as";
    public static final String KEY_UPTIME = "time";
    public static final String KEY_GRILL_END_TIME = "sge";

    // /cgi-bin/info keys
    public static final String KEY_ENV = "env";
    public static final String KEY_FW_VERSION = "fw_version";
    public static final String KEY_MAC_ADDRESS = "MAC";
}
