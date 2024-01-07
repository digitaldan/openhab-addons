/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
// AUTO-GENERATED by zap. DO NOT EDIT!

package org.openhab.binding.matter.internal.client.model.cluster;

import java.util.List;
import java.util.Map;

/**
 * UnitLocalization
 * 
 * @author Dan Cunningham - Initial contribution
 */
public class UnitLocalizationCluster extends BaseCluster {

    public static final String CLUSTER_NAME = "UNIT_LOCALIZATION_CLUSTER";
    public static final int CLUSTER_ID = 0x002D;

    // ZCL Enums
    public enum TempUnitEnum {
        FAHRENHEIT(0, "Fahrenheit"),
        CELSIUS(1, "Celsius"),
        KELVIN(2, "Kelvin"),
        UNKNOWN_VALUE(3, "UnknownValue");

        public final int value;
        public final String label;

        private TempUnitEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    // ZCL Bitmaps
    public static class Feature {
        public boolean temperatureUnit;

        public Feature(boolean temperatureUnit) {
            this.temperatureUnit = temperatureUnit;
        }

        @SuppressWarnings({ "unchecked", "null" })
        public static Feature fromJson(String json) {
            Map<String, Boolean> m = GSON.fromJson(json, Map.class);
            Boolean[] keys = m.values().toArray(new Boolean[0]);
            return new Feature(keys[0]);
        }
    }

    public TempUnitEnum temperatureUnit; // 0 TempUnitEnum reportable writable
    public List<Integer> generatedCommandList; // 65528 command_id reportable
    public List<Integer> acceptedCommandList; // 65529 command_id reportable
    public List<Integer> eventList; // 65530 event_id reportable
    public List<Integer> attributeList; // 65531 attrib_id reportable
    public Map<String, Boolean> featureMap; // 65532 bitmap32 reportable
    public Integer clusterRevision; // 65533 int16u reportable

    public UnitLocalizationCluster(long nodeId, int endpointId) {
        super(nodeId, endpointId, 79, "UnitLocalization");
    }

    public String toString() {
        String str = "";
        str += "temperatureUnit : " + temperatureUnit + "\n";
        str += "generatedCommandList : " + generatedCommandList + "\n";
        str += "acceptedCommandList : " + acceptedCommandList + "\n";
        str += "eventList : " + eventList + "\n";
        str += "attributeList : " + attributeList + "\n";
        str += "featureMap : " + featureMap + "\n";
        str += "clusterRevision : " + clusterRevision + "\n";
        return str;
    }
}
