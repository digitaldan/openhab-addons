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
package org.openhab.binding.linkplay.internal;

import java.util.Collections;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link LinkPlayBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class LinkPlayBindingConstants {

    private static final String BINDING_ID = "linkplay";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_PLAYER = new ThingTypeUID(BINDING_ID, "player");
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Collections.singleton(THING_TYPE_PLAYER);

    public static final String PROPERTY_UDN = "udn";
    public static final String PROPERTY_FIRMWARE = "firmwareVersion";
    public static final String PROPERTY_MODEL = "model";
    public static final String PROPERTY_IP = "ipAddress";
    public static final String PROPERTY_MAC = "macAddress";
    public static final String PROPERTY_MANUFACTURER = "manufacturer";
    public static final String PROPERTY_DEVICE_NAME = "deviceName";
    public static final String PROPERTY_GROUP_NAME = "groupName";
    public static final String CONFIG_IP_ADDRESS = "ipAddress";
    public static final String CONFIG_UDN = "udn";
    public static final String CONFIG_REFRESH_INTERVAL = "refreshInterval";

    // ----------------- Channel Group IDs -----------------
    public static final String GROUP_PLAYBACK = "playback";
    public static final String GROUP_METADATA = "metadata";
    public static final String GROUP_INPUT = "input";
    public static final String GROUP_EQUALISER = "equaliser";
    public static final String GROUP_MULTIROOM = "multiroom";
    public static final String GROUP_DEVICE = "device";
    public static final String GROUP_PRESETS = "presets";

    // ----------------- Channel IDs -----------------
    public static final String CHANNEL_PLAYER_CONTROL = "playerControl";
    public static final String CHANNEL_PLAYBACK_STATE = "playbackState";
    public static final String CHANNEL_TRACK_POSITION = "trackPosition";
    public static final String CHANNEL_TRACK_DURATION = "trackDuration";
    public static final String CHANNEL_REPEAT_SHUFFLE_MODE = "repeatShuffleMode";
    public static final String CHANNEL_EQ_PRESET = "eqPreset";
    public static final String CHANNEL_VOLUME = "volume";
    public static final String CHANNEL_MUTE = "mute";

    // Metadata
    public static final String CHANNEL_TRACK_TITLE = "trackTitle";
    public static final String CHANNEL_TRACK_ARTIST = "trackArtist";
    public static final String CHANNEL_TRACK_ALBUM = "trackAlbum";
    public static final String CHANNEL_ALBUM_ART_URL = "albumArtUrl";
    public static final String CHANNEL_ALBUM_ART = "albumArt";
    public static final String CHANNEL_SAMPLE_RATE = "sampleRate";
    public static final String CHANNEL_BIT_DEPTH = "bitDepth";
    public static final String CHANNEL_BIT_RATE = "bitRate";

    // Input / Source
    public static final String CHANNEL_SOURCE_INPUT = "sourceInput";
    public static final String CHANNEL_BT_CONNECTED = "bluetoothConnected";
    public static final String CHANNEL_BT_PAIRED_DEVICE = "bluetoothPairedDevice";
    public static final String CHANNEL_LINE_IN_ACTIVE = "lineInActive";

    // Equaliser & Output
    public static final String CHANNEL_EQ_ENABLED = "eqEnabled";
    public static final String CHANNEL_EQ_BAND = "eqBand";
    public static final String CHANNEL_OUTPUT_HW_MODE = "outputHardwareMode";
    public static final String CHANNEL_CHANNEL_BALANCE = "channelBalance";
    public static final String CHANNEL_SPDIF_DELAY = "spdifSwitchDelayMs";

    // Multi-room
    public static final String CHANNEL_SLAVE_VOLUME = "slaveVolume";
    public static final String CHANNEL_SLAVE_MUTE = "slaveMute";
    public static final String CHANNEL_SLAVE_CHANNEL = "slaveChannel";
    public static final String CHANNEL_MULTIROOM_ACTIVE = "multiroomActive";
    public static final String CHANNEL_MULTIROOM_LEADER = "multiroomLeader";
    public static final String CHANNEL_JOIN_GROUP = "joinGroup";
    public static final String CHANNEL_LEAVE_GROUP = "leaveGroup";
    public static final String CHANNEL_UNGROUP = "ungroup";

    // Device & System
    public static final String CHANNEL_LED_ENABLED = "ledEnabled";
    public static final String CHANNEL_TOUCH_KEYS_ENABLED = "touchKeysEnabled";
    public static final String CHANNEL_SHUTDOWN_TIMER = "shutdownTimer";
    public static final String CHANNEL_REBOOT = "reboot";
    public static final String CHANNEL_FACTORY_RESET = "factoryReset";

    // Presets
    public static final String CHANNEL_PRESET_COUNT = "presetCount";
    public static final String CHANNEL_PLAY_PRESET = "playPreset";
    public static final String CHANNEL_PRESET_NAME = "presetName";
    // New preset detail channels
    public static final String CHANNEL_PRESET_URL = "presetUrl";
    public static final String CHANNEL_PRESET_SOURCE = "presetSource";
    public static final String CHANNEL_PRESET_PIC_URL = "presetPicUrl";
    public static final String CHANNEL_PRESET_PIC = "presetPic";
    public static final String CHANNEL_PRESET_PLAY = "presetPlay";

    // Group proxy channels that are set by the leader when in a group
    public static final Set<String> GROUP_PROXY_CHANNELS = Set.of(CHANNEL_PLAYER_CONTROL, CHANNEL_PLAYBACK_STATE,
            CHANNEL_TRACK_POSITION, CHANNEL_TRACK_DURATION, CHANNEL_REPEAT_SHUFFLE_MODE, CHANNEL_TRACK_TITLE,
            CHANNEL_TRACK_ARTIST, CHANNEL_TRACK_ALBUM, CHANNEL_ALBUM_ART_URL, CHANNEL_ALBUM_ART, CHANNEL_SAMPLE_RATE,
            CHANNEL_BIT_DEPTH, CHANNEL_BIT_RATE);
}
