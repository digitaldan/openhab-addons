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
 * IlluminanceMeasurement
 * 
 * @author Dan Cunningham - Initial contribution
 */
public class IlluminanceMeasurementCluster extends BaseCluster {

    public static final String CLUSTER_NAME = "ILLUMINANCE_MEASUREMENT_CLUSTER";
    public static final int CLUSTER_ID = 0x0400;

    // ZCL Enums
    public enum LightSensorTypeEnum {
        PHOTODIODE(0, "Photodiode"),
        CMOS(1, "CMOS"),
        UNKNOWN_VALUE(2, "UnknownValue");

        public final int value;
        public final String label;

        private LightSensorTypeEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public Integer measuredValue; // 0 int16u reportable
    public Integer minMeasuredValue; // 1 int16u reportable
    public Integer maxMeasuredValue; // 2 int16u reportable
    public Integer tolerance; // 3 int16u reportable
    public LightSensorTypeEnum lightSensorType; // 4 LightSensorTypeEnum reportable
    public List<Integer> generatedCommandList; // 65528 command_id reportable
    public List<Integer> acceptedCommandList; // 65529 command_id reportable
    public List<Integer> eventList; // 65530 event_id reportable
    public List<Integer> attributeList; // 65531 attrib_id reportable
    public Map<String, Boolean> featureMap; // 65532 bitmap32 reportable
    public Integer clusterRevision; // 65533 int16u reportable

    public IlluminanceMeasurementCluster(long nodeId, int endpointId) {
        super(nodeId, endpointId, 45, "IlluminanceMeasurement");
    }

    public String toString() {
        String str = "";
        str += "measuredValue : " + measuredValue + "\n";
        str += "minMeasuredValue : " + minMeasuredValue + "\n";
        str += "maxMeasuredValue : " + maxMeasuredValue + "\n";
        str += "tolerance : " + tolerance + "\n";
        str += "lightSensorType : " + lightSensorType + "\n";
        str += "generatedCommandList : " + generatedCommandList + "\n";
        str += "acceptedCommandList : " + acceptedCommandList + "\n";
        str += "eventList : " + eventList + "\n";
        str += "attributeList : " + attributeList + "\n";
        str += "featureMap : " + featureMap + "\n";
        str += "clusterRevision : " + clusterRevision + "\n";
        return str;
    }
}
