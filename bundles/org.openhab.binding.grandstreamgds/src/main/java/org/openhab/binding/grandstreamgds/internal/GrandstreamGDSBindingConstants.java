/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.grandstreamgds.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link GrandstreamGDSBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class GrandstreamGDSBindingConstants {

    public static final String BINDING_ID = "grandstreamgds";

    public static final String BASE_SERVLET_PATH = "/grandstreamgds";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_GDS = new ThingTypeUID(BINDING_ID, "gds");

    // List of all Channel ids
    public static final String CHANNEL_DOOR_OPEN = "dooropen";
    public static final String CHANNEL_KEEP_DOOR_OPEN = "keepdooropen";
    public static final String CHANNEL_DI_1 = "digital_input1";
    public static final String CHANNEL_DI_2 = "digital_input2";
    public static final String CHANNEL_CALL_AUTO = "call_auto";
    public static final String CHANNEL_CALL_ACCOUNT1 = "call_account1";
    public static final String CHANNEL_CALL_ACCOUNT2 = "call_account2";
    public static final String CHANNEL_CALL_ACCOUNT3 = "call_account3";
    public static final String CHANNEL_CALL_ACCOUNT4 = "call_account4";
    public static final String CHANNEL_CALL_HANGUP = "call_hangup";
}
