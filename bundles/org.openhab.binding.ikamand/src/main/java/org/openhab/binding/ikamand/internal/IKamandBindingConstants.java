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
    public static final String CHANNEL_TEMPERATURE_PIT_CURRENT = "temperature-pit-current";
    public static final String CHANNEL_TEMPERATURE_PIT_TARGET = "temperature-pit-target";
    public static final String CHANNEL_TEMPERATURE_PROBE1_CURRENT = "temperature-probe1-current";
    public static final String CHANNEL_TEMPERATURE_PROBE2_CURRENT = "temperature-probe2-current";
    public static final String CHANNEL_TEMPERATURE_PROBE3_CURRENT = "temperature-probe3-current";
    public static final String CHANNEL_TEMPERATURE_PROBE1_TARGET = "temperature-probe1-target";
    public static final String CHANNEL_TEMPERATURE_PROBE2_TARGET = "temperature-probe2-target";
    public static final String CHANNEL_TEMPERATURE_PROBE3_TARGET = "temperature-probe3-target";
    public static final String CHANNEL_RM = "rm";
    public static final String CHANNEL_CM = "cm";
    public static final String CHANNEL_AG = "ag";
    public static final String CHANNEL_AS = "as";
    public static final String CHANNEL_CURRENT_TIME = "current-time";
    public static final String CHANNEL_COOK_END_TIME = "cook-end-time";
    public static final String CHANNEL_GRILL_END_TIME = "grill-end-time";
    public static final String CHANNEL_COOK_ID = "cook-id";
    public static final String CHANNEL_COOK_START_PROBE_1 = "cook-start-probe-1";
    public static final String CHANNEL_COOK_START_PROBE_2 = "cook-start-probe-2";
    public static final String CHANNEL_COOK_START_PROBE_3 = "cook-start-probe-3";
    public static final String CHANNEL_COOK_STOP = "cook-stop";
    public static final String CHANNEL_GRILL_START = "grill-start";
    public static final String CHANNEL_GRILL_RUNNING = "grill-running";

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
