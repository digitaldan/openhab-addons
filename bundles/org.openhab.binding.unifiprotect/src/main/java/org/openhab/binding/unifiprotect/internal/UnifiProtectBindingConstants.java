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

    // Shared
    public static final String CHANNEL_STATUS = "status";

    // NVR
    public static final String CHANNEL_DOORBELL_DEFAULT_MESSAGE = "doorbell-default-message";
    public static final String CHANNEL_DOORBELL_DEFAULT_MESSAGE_RESET_MS = "doorbell-default-message-reset-ms";
    public static final String CHANNEL_DEVICE_ADDED = "device-added";
    public static final String CHANNEL_DEVICE_UPDATED = "device-updated";
    public static final String CHANNEL_DEVICE_REMOVED = "device-removed";

    // Camera
    public static final String CHANNEL_SNAPSHOT = "snapshot";
    public static final String CHANNEL_MIC_VOLUME = "mic-volume";
    public static final String CHANNEL_VIDEO_MODE = "video-mode";
    public static final String CHANNEL_HDR_TYPE = "hdr-type";
    public static final String CHANNEL_OSD_NAME = "osd-name";
    public static final String CHANNEL_OSD_DATE = "osd-date";
    public static final String CHANNEL_OSD_LOGO = "osd-logo";
    public static final String CHANNEL_LED_ENABLED = "led-enabled";
    public static final String CHANNEL_ACTIVE_PATROL_SLOT = "active-patrol-slot";
    public static final String CHANNEL_RTSP_STREAM_HIGH = "rtsp-stream-high";
    public static final String CHANNEL_RTSP_STREAM_HIGH_LABEL = "RTSP Stream High";
    public static final String CHANNEL_RTSP_STREAM_MEDIUM = "rtsp-stream-medium";
    public static final String CHANNEL_RTSP_STREAM_MEDIUM_LABEL = "RTSP Stream Medium";
    public static final String CHANNEL_RTSP_STREAM_LOW = "rtsp-stream-low";
    public static final String CHANNEL_RTSP_STREAM_LOW_LABEL = "RTSP Stream Low";
    public static final String CHANNEL_RTSP_STREAM_PACKAGE = "rtsp-stream-package";
    public static final String CHANNEL_RTSP_STREAM_PACKAGE_LABEL = "RTSP Stream Package";
    public static final String CHANNEL_RTSP_STREAM = "rtsp-stream";
    // Triggers and Contacts
    public static final String CHANNEL_MOTION = "motion";
    public static final String CHANNEL_MOTION_START = "motion-start";
    public static final String CHANNEL_MOTION_UPDATE = "motion-update";
    public static final String CHANNEL_MOTION_CONTACT = "motion-contact";
    public static final String CHANNEL_MOTION_SNAPSHOT = "motion-snapshot";
    public static final String CHANNEL_MOTION_SNAPSHOT_LABEL = "Motion Snapshot";

    public static final String CHANNEL_SMART_AUDIO_DETECT = "smart-audio-detect";
    public static final String CHANNEL_SMART_AUDIO_DETECT_START = "smart-audio-detect-start";
    public static final String CHANNEL_SMART_AUDIO_DETECT_UPDATE = "smart-audio-detect-update";
    public static final String CHANNEL_SMART_AUDIO_DETECT_CONTACT = "smart-audio-detect-contact";
    public static final String CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT = "smart-audio-detect-snapshot";
    public static final String CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT_LABEL = "Smart Audio Detect Snapshot";

    public static final String CHANNEL_SMART_DETECT_ZONE = "smart-detect-zone";
    public static final String CHANNEL_SMART_DETECT_ZONE_START = "smart-detect-zone-start";
    public static final String CHANNEL_SMART_DETECT_ZONE_UPDATE = "smart-detect-zone-update";
    public static final String CHANNEL_SMART_DETECT_ZONE_CONTACT = "smart-detect-zone-contact";
    public static final String CHANNEL_SMART_DETECT_ZONE_SNAPSHOT = "smart-detect-zone-snapshot";
    public static final String CHANNEL_SMART_DETECT_ZONE_SNAPSHOT_LABEL = "Smart Detect Zone Snapshot";

    public static final String CHANNEL_SMART_DETECT_LINE = "smart-detect-line";
    public static final String CHANNEL_SMART_DETECT_LINE_START = "smart-detect-line-start";
    public static final String CHANNEL_SMART_DETECT_LINE_UPDATE = "smart-detect-line-update";
    public static final String CHANNEL_SMART_DETECT_LINE_CONTACT = "smart-detect-line-contact";
    public static final String CHANNEL_SMART_DETECT_LINE_SNAPSHOT = "smart-detect-line-snapshot";
    public static final String CHANNEL_SMART_DETECT_LINE_SNAPSHOT_LABEL = "Smart Detect Line Snapshot";

    public static final String CHANNEL_SMART_DETECT_LOITER = "smart-detect-loiter";
    public static final String CHANNEL_SMART_DETECT_LOITER_START = "smart-detect-loiter-start";
    public static final String CHANNEL_SMART_DETECT_LOITER_UPDATE = "smart-detect-loiter-update";
    public static final String CHANNEL_SMART_DETECT_LOITER_CONTACT = "smart-detect-loiter-contact";
    public static final String CHANNEL_SMART_DETECT_LOITER_SNAPSHOT = "smart-detect-loiter-snapshot";
    public static final String CHANNEL_SMART_DETECT_LOITER_SNAPSHOT_LABEL = "Smart Detect Loiter Snapshot";

    public static final String CHANNEL_RING = "ring";
    public static final String CHANNEL_RING_START = "ring-start";
    public static final String CHANNEL_RING_END = "ring-end";
    public static final String CHANNEL_RING_CONTACT = "ring-contact";
    public static final String CHANNEL_RING_SNAPSHOT = "ring-snapshot";

    // Light (floodlight)
    public static final String CHANNEL_LIGHT = "light";
    public static final String CHANNEL_IS_DARK = "is-dark";
    public static final String CHANNEL_PIR_MOTION = "pir-motion";
    public static final String CHANNEL_LAST_MOTION = "last-motion";
    public static final String CHANNEL_LIGHT_MODE = "light-mode";
    public static final String CHANNEL_ENABLE_AT = "enable-at";
    public static final String CHANNEL_INDICATOR_ENABLED = "indicator-enabled";
    public static final String CHANNEL_PIR_DURATION = "pir-duration";
    public static final String CHANNEL_PIR_SENSITIVITY = "pir-sensitivity";
    public static final String CHANNEL_LED_LEVEL = "led-level";

    // Sensor
    public static final String CHANNEL_BATTERY = "battery";
    public static final String CHANNEL_CONTACT = "contact";
    public static final String CHANNEL_TEMPERATURE = "temperature";
    public static final String CHANNEL_HUMIDITY = "humidity";
    public static final String CHANNEL_ILLUMINANCE = "illuminance";
    public static final String CHANNEL_SENSOR_MOTION = "motion";
    public static final String CHANNEL_ALARM_CONTACT = "alarm-contact";
    public static final String CHANNEL_ALARM = "alarm";
    public static final String CHANNEL_WATER_LEAK_CONTACT = "water-leak-contact";
    public static final String CHANNEL_WATER_LEAK = "water-leak";
    public static final String CHANNEL_TAMPER_CONTACT = "tamper-contact";
    public static final String CHANNEL_TAMPER = "tamper";
    public static final String CHANNEL_OPENED = "opened";
    public static final String CHANNEL_CLOSED = "closed";
}
