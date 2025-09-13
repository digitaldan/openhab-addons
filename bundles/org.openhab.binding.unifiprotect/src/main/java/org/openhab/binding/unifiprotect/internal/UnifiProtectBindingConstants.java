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
package org.openhab.binding.unifiprotect.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link UnifiProtectBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiProtectBindingConstants {

    public static final String BINDING_ID = "unifiprotect";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_NVR = new ThingTypeUID(BINDING_ID, "nvr");
    public static final ThingTypeUID THING_TYPE_CAMERA = new ThingTypeUID(BINDING_ID, "camera");
    public static final ThingTypeUID THING_TYPE_LIGHT = new ThingTypeUID(BINDING_ID, "light");
    public static final ThingTypeUID THING_TYPE_SENSOR = new ThingTypeUID(BINDING_ID, "sensor");

    // List of all Configuration ids
    public static final String DEVICE_ID = "deviceId";

    // List of all Channel ids
    // Shared / generic
    public static final String CHANNEL_STATUS = "status";

    // NVR
    public static final String CHANNEL_DOORBELL_DEFAULT_MESSAGE = "doorbelldefaultmessage";
    public static final String CHANNEL_DOORBELL_DEFAULT_MESSAGE_RESET_MS = "doorbelldefaultmessageresetms";
    public static final String CHANNEL_DEVICE_ADDED = "deviceadded";
    public static final String CHANNEL_DEVICE_UPDATED = "deviceupdated";
    public static final String CHANNEL_DEVICE_REMOVED = "deviceremoved";

    // Camera
    public static final String CHANNEL_SNAPSHOT = "snapshot";
    public static final String CHANNEL_MIC_VOLUME = "micvolume";
    public static final String CHANNEL_VIDEO_MODE = "videomode";
    public static final String CHANNEL_HDR_TYPE = "hdrtype";
    public static final String CHANNEL_OSD_NAME = "osdname";
    public static final String CHANNEL_OSD_DATE = "osddate";
    public static final String CHANNEL_OSD_LOGO = "osdlogo";
    public static final String CHANNEL_LED_ENABLED = "ledenabled";
    public static final String CHANNEL_ACTIVE_PATROL_SLOT = "activepatrolslot";
    // Triggers and Contacts
    public static final String CHANNEL_MOTION = "motion";
    public static final String CHANNEL_MOTION_START = "motionstart";
    public static final String CHANNEL_MOTION_END = "motionend";
    public static final String CHANNEL_MOTION_CONTACT = "motioncontact";
    public static final String CHANNEL_MOTION_SNAPSHOT = "motionsnapshot";
    public static final String CHANNEL_MOTION_SNAPSHOT_LABEL = "Motion Snapshot";

    public static final String CHANNEL_SMART_AUDIO_DETECT = "smartaudiodetect";
    public static final String CHANNEL_SMART_AUDIO_DETECT_START = "smartaudiodetectstart";
    public static final String CHANNEL_SMART_AUDIO_DETECT_END = "smartaudiodetectend";
    public static final String CHANNEL_SMART_AUDIO_DETECT_CONTACT = "smartaudiodetectcontact";
    public static final String CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT = "smartaudiodetectsnapshot";
    public static final String CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT_LABEL = "Smart Audio Detect Snapshot";

    public static final String CHANNEL_SMART_DETECT_ZONE = "smartdetectzone";
    public static final String CHANNEL_SMART_DETECT_ZONE_START = "smartdetectzonestart";
    public static final String CHANNEL_SMART_DETECT_ZONE_END = "smartdetectzoneend";
    public static final String CHANNEL_SMART_DETECT_ZONE_CONTACT = "smartdetectzonecontact";
    public static final String CHANNEL_SMART_DETECT_ZONE_SNAPSHOT = "smartdetectzonesnapshot";
    public static final String CHANNEL_SMART_DETECT_ZONE_SNAPSHOT_LABEL = "Smart Detect Zone Snapshot";

    public static final String CHANNEL_SMART_DETECT_LINE = "smartdetectline";
    public static final String CHANNEL_SMART_DETECT_LINE_START = "smartdetectlinestart";
    public static final String CHANNEL_SMART_DETECT_LINE_END = "smartdetectlineend";
    public static final String CHANNEL_SMART_DETECT_LINE_CONTACT = "smartdetectlinecontact";
    public static final String CHANNEL_SMART_DETECT_LINE_SNAPSHOT = "smartdetectlinesnapshot";
    public static final String CHANNEL_SMART_DETECT_LINE_SNAPSHOT_LABEL = "Smart Detect Line Snapshot";

    public static final String CHANNEL_SMART_DETECT_LOITER = "smartdetectloiter";
    public static final String CHANNEL_SMART_DETECT_LOITER_START = "smartdetectloiterstart";
    public static final String CHANNEL_SMART_DETECT_LOITER_END = "smartdetectloiterend";
    public static final String CHANNEL_SMART_DETECT_LOITER_CONTACT = "smartdetectloitercontact";
    public static final String CHANNEL_SMART_DETECT_LOITER_SNAPSHOT = "smartdetectloitersnapshot";
    public static final String CHANNEL_SMART_DETECT_LOITER_SNAPSHOT_LABEL = "Smart Detect Loiter Snapshot";

    public static final String CHANNEL_RING = "ring";
    public static final String CHANNEL_RING_START = "ringstart";
    public static final String CHANNEL_RING_END = "ringend";
    public static final String CHANNEL_RING_CONTACT = "ringcontact";
    public static final String CHANNEL_RING_SNAPSHOT = "ringsnapshot";

    // Light (floodlight)
    public static final String CHANNEL_LIGHT = "light";
    public static final String CHANNEL_IS_DARK = "isdark";
    public static final String CHANNEL_PIR_MOTION = "pirmotion";
    public static final String CHANNEL_LAST_MOTION = "lastmotion";
    public static final String CHANNEL_LIGHT_MODE = "lightmode";
    public static final String CHANNEL_ENABLE_AT = "enableat";
    public static final String CHANNEL_INDICATOR_ENABLED = "indicatorenabled";
    public static final String CHANNEL_PIR_DURATION = "pirduration";
    public static final String CHANNEL_PIR_SENSITIVITY = "pirsensitivity";
    public static final String CHANNEL_LED_LEVEL = "ledlevel";

    // Sensor
    public static final String CHANNEL_BATTERY = "battery";
    public static final String CHANNEL_CONTACT = "contact";
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_HUMIDITY = "humidity";
    public static final String CHANNEL_ILLUMINANCE = "illuminance";
    public static final String CHANNEL_SENSOR_MOTION = "motion";
    public static final String CHANNEL_ALARM_CONTACT = "alarmcontact";
    public static final String CHANNEL_ALARM = "alarm";
    public static final String CHANNEL_WATER_LEAK_CONTACT = "waterleakcontact";
    public static final String CHANNEL_WATER_LEAK = "waterleak";
    public static final String CHANNEL_TAMPER_CONTACT = "tampercontact";
    public static final String CHANNEL_TAMPER = "tamper";
    public static final String CHANNEL_OPENED = "opened";
    public static final String CHANNEL_CLOSED = "closed";
}
