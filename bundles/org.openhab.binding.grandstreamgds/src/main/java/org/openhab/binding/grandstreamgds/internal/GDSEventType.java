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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 *
 * @author daniel
 *
 */
@NonNullByDefault
public enum GDSEventType {

    OPEN_DOOR_VIA_CARD("Indicates that someone opens the door via card or key fob.", "Open Door via Card", "100"),
    OPEN_DOOR_VIA_CARD_OVER_WIEGAND(
            "Indicates that someone opens the door via card or key fob using Wiegand interface connected to GDS.",
            "Open Door via Card (over Wiegand)", "101"),
    VISITING_LOG("Indicates that door has been opened for visitor which pressed door bell button.", "Visiting Log",
            "200"),
    OPEN_DOOR_VIA_UNIVERSAL_PIN("Indicates that door has been opened successfully using local PIN code via GDS keypad.",
            "Open Door via Universal PIN", "300"),
    OPEN_DOOR_VIA_PRIVATE_PIN(
            "Indicates that someone opened the door successfully using their private PIN code via GDS keypad.",
            "Open Door via Private PIN", "301"),
    OPEN_DOOR_VIA_GUEST_PIN("Indicates that a guest used “Guest PIN” code to open the door using GDS keypad.",
            "Open Door via Guest PIN", "302"),
    OPEN_DOOR_VIA_DI(
            "Indicates that door has been opened using DI (Digital Input) Signal, such as using a push button.",
            "Open Door via DI", "400"),
    CALL_OUT_LOG(
            "Indicates the GDS unit initiated a call out, for example when someone uses the keypad to dial a number or press door bell button which preconfigured destination number.",
            "Call Out Log", "500"),
    CALL_IN_LOG("Indicates that call has been received by the GDS unit.", "Call In Log", "501"),
    CALL_LOG_DOOR_BELL_CALL("Indicates that someone has initiated a call using door bell button.",
            "Call Log (Door Bell Call)", "504"),
    OPEN_DOOR_VIA_CARD_AND_PIN(
            "Indicates that someone used his RFID card or key fob, plus his own private password to authenticate and open the door.",
            "Open Door via Card and PIN", "600"),
    KEEP_DOOR_OPEN_IMMEDIATELY("Key door Open (immediately) action has been performed from the web Interface.",
            "Keep Door Open (Immediately)", "601"),
    KEEP_DOOR_OPEN_SCHEDULED(
            "Key door Open (immediately) action has been set from the web Interface and the event is triggered.",
            "Keep Door Open (Scheduled)", "602"),
    OPEN_DOOR_VIA_REMOTE_PIN(
            "Indicates that someone did send remote PIN code to open the door using GDS manager tool for example.",
            "Open Door via Remote PIN", "700"),
    HTTP_API_OPEN_DOOR("Indicates that someone did send remote PIN code to open the door HTTP API command.",
            "HTTP API Open Door", "800"),
    MOTION_DETECTION("Indicates that motion detection is triggered.", "Motion Detection", "900"),
    DI_ALARM("Indicates that alarm IN is triggered.", "DI Alarm", "1000"),
    DISMANTLE_BY_FORCE("Indicates that the unit has been dismantled by force.", "Dismantle by Force", "1100"),
    SYSTEM_UP("Indicates that the system is UP", "System up", "1101"),
    REBOOT("Indicates that the GDS unit has been rebooted.", "Reboot", "1102"),
    RESET_CLEAR_ALL_DATA("Factory reset (clear all data) has been performed.", "Reset (Clear All Data)", "1103"),
    RESET_RETAIN_NETWORK_DATA_ONLY("Factory reset (Retain Network Data Only) has been performed.",
            "Reset (Retain Network Data Only)", "1104"),
    RESET_RETAIN_ONLY_CARD_INFORMATION("Factory reset (Retain Only Card Information) has been performed.",
            "Reset (Retain Only Card Information)", "1105"),
    RESET_RETAIN_NETWORK_DATA_AND_CARD_INFORMATION(
            "Factory reset (Retain Network Data and Card Information) has been performed.",
            "Reset (Retain Network Data and Card Information)", "1106"),
    RESET_WIEGAND("Factory reset using Wiegand module has been performed on the unit.", "Reset (Wiegand)", "1107"),
    CONFIG_UPDATE("Indicates that the system’s configuration has been updated.", "Config Update", "1108"),
    FIRMWARE_UPDATE("Indicates that the system’s firmware has been upgraded.", "Firmware Update (1.0.0.0)", "1109"),
    HOSTAGE_ALARM("Indicates that someone has entered the hostage alarm PIN code to open the door.", "Hostage Alarm",
            "1200"),
    INVALID_PASSWORD(
            "Indicates that someone has entered wrong password PIN code to open the door for 5 attempts and corresponding alarm action has been triggered.",
            "Invalid Password", "1300"),
    MAINBOARD_TEMPERATURE_NORMAL("Indicates that device’s mainboard temperature is normal, (around 32°C).",
            "Mainboard Temperature(32°C) Normal", "1400"),
    MAINBOARD_TEMPERATURE_TOO_LOW("Indicates that device’s mainboard temperature is to low.",
            "Mainboard Temperature(32°C) Too Low", "1401"),
    MAINBOARD_TEMPERATURE_TOO_HIGH("Indicates that device’s mainboard temperature is to high.",
            "Mainboard Temperature(32°C) Too High", "1402"),
    SENSOR_TEMPERATURE_NORMAL("Indicates that device’s sensor temperature is normal, (around 32°C).",
            "Sensor Temperature(32°C) Normal", "1403"),
    SENSOR_TEMPERATURE_TOO_LOW("Indicates that device’s sensor temperature is to low.",
            "Sensor Temperature(32°C) Too Low", "1404"),
    SENSOR_TEMPERATURE_TOO_HIGH("Indicates that device’s sensor temperature is to high.",
            "Sensor Temperature(32°C) Too High", "1405");

    private final String description;
    private final String label;
    private final String type;

    private static final Map<String, GDSEventType> typeCache = new HashMap<>(values().length, 1);

    static {
        for (GDSEventType e : values()) {
            typeCache.put(e.type, e);
        }
    }

    GDSEventType(String description, String label, String type) {
        this.description = description;
        this.label = label;
        this.type = type;
    }

    public String getDescription() {
        return this.description;
    }

    public String getLabel() {
        return this.label;
    }

    public String getType() {
        return this.type;
    }

    public static @Nullable GDSEventType fromType(String type) {
        return typeCache.get(type);
    }

    // public static void main(String arg[]) {
    // /**
    // * <channel id="open_door_via_card" typeId="gdsevent">
    // * <label></label>
    // * <description></description>
    // * </channel>
    // */
    // for (GDSEventType e : GDSEventType.values()) {
    // System.out.println("<channel id=\"event_" + e.toString().toLowerCase() + "\" typeId=\"gdsevent\">");
    // System.out.println("<label>" + e.getLabel() + " Event Trigger</label>");
    // System.out.println("<description>" + e.getDescription() + "</description>");
    // System.out.println("</channel>");
    //
    // }
    // }

    public static void main(String arg[]) {
        /**
         * <channel id="open_door_via_card" typeId="gdsevent">
         * <label></label>
         * <description></description>
         * </channel>
         */
        System.out.println("| Trigger Event  | type   | description                  |");
        System.out.println("|----------|--------|------------------------------|");
        for (GDSEventType e : GDSEventType.values()) {
            System.out.println("| event_" + e.toString().toLowerCase() + " | " + e.getLabel() + " | "
                    + e.getDescription() + " | ");

        }
    }
}
