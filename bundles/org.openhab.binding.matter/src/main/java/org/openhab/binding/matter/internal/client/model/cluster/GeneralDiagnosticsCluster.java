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

import org.openhab.binding.matter.internal.client.MatterClient;

/**
 * GeneralDiagnostics
 * 
 * @author Dan Cunningham - Initial contribution
 */
public class GeneralDiagnosticsCluster extends BaseCluster {

    public static final String CLUSTER_NAME = "GENERAL_DIAGNOSTICS_CLUSTER";
    public static final int CLUSTER_ID = 0x0033;

    class NetworkInterface {
        public String name; // char_string
        public Boolean isOperational; // boolean
        public Boolean offPremiseServicesReachableIPv4; // boolean
        public Boolean offPremiseServicesReachableIPv6; // boolean
        public String hardwareAddress; // octet_string
        public String IPv4Addresses; // octet_string
        public String IPv6Addresses; // octet_string
        public InterfaceTypeEnum type; // InterfaceTypeEnum

        public NetworkInterface(String name, Boolean isOperational, Boolean offPremiseServicesReachableIPv4,
                Boolean offPremiseServicesReachableIPv6, String hardwareAddress, String IPv4Addresses,
                String IPv6Addresses, InterfaceTypeEnum type) {
            this.name = name;
            this.isOperational = isOperational;
            this.offPremiseServicesReachableIPv4 = offPremiseServicesReachableIPv4;
            this.offPremiseServicesReachableIPv6 = offPremiseServicesReachableIPv6;
            this.hardwareAddress = hardwareAddress;
            this.IPv4Addresses = IPv4Addresses;
            this.IPv6Addresses = IPv6Addresses;
            this.type = type;
        }
    }

    // ZCL Enums
    public enum BootReasonEnum {
        UNSPECIFIED(0, "Unspecified"),
        POWERONREBOOT(1, "PowerOnReboot"),
        BROWNOUTRESET(2, "BrownOutReset"),
        SOFTWAREWATCHDOGRESET(3, "SoftwareWatchdogReset"),
        HARDWAREWATCHDOGRESET(4, "HardwareWatchdogReset"),
        SOFTWAREUPDATECOMPLETED(5, "SoftwareUpdateCompleted"),
        SOFTWARERESET(6, "SoftwareReset"),
        UNKNOWN_VALUE(7, "UnknownValue");

        public final int value;
        public final String label;

        private BootReasonEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum HardwareFaultEnum {
        UNSPECIFIED(0, "Unspecified"),
        RADIO(1, "Radio"),
        SENSOR(2, "Sensor"),
        RESETTABLEOVERTEMP(3, "ResettableOverTemp"),
        NONRESETTABLEOVERTEMP(4, "NonResettableOverTemp"),
        POWERSOURCE(5, "PowerSource"),
        VISUALDISPLAYFAULT(6, "VisualDisplayFault"),
        AUDIOOUTPUTFAULT(7, "AudioOutputFault"),
        USERINTERFACEFAULT(8, "UserInterfaceFault"),
        NONVOLATILEMEMORYERROR(9, "NonVolatileMemoryError"),
        TAMPERDETECTED(10, "TamperDetected"),
        UNKNOWN_VALUE(11, "UnknownValue");

        public final int value;
        public final String label;

        private HardwareFaultEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum InterfaceTypeEnum {
        UNSPECIFIED(0, "Unspecified"),
        WIFI(1, "WiFi"),
        ETHERNET(2, "Ethernet"),
        CELLULAR(3, "Cellular"),
        THREAD(4, "Thread"),
        UNKNOWN_VALUE(5, "UnknownValue");

        public final int value;
        public final String label;

        private InterfaceTypeEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum NetworkFaultEnum {
        UNSPECIFIED(0, "Unspecified"),
        HARDWAREFAILURE(1, "HardwareFailure"),
        NETWORKJAMMED(2, "NetworkJammed"),
        CONNECTIONFAILED(3, "ConnectionFailed"),
        UNKNOWN_VALUE(4, "UnknownValue");

        public final int value;
        public final String label;

        private NetworkFaultEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum RadioFaultEnum {
        UNSPECIFIED(0, "Unspecified"),
        WIFIFAULT(1, "WiFiFault"),
        CELLULARFAULT(2, "CellularFault"),
        THREADFAULT(3, "ThreadFault"),
        NFCFAULT(4, "NFCFault"),
        BLEFAULT(5, "BLEFault"),
        ETHERNETFAULT(6, "EthernetFault"),
        UNKNOWN_VALUE(7, "UnknownValue");

        public final int value;
        public final String label;

        private RadioFaultEnum(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public NetworkInterface networkInterfaces; // 0 NetworkInterface reportable
    public Integer rebootCount; // 1 int16u reportable
    public Long upTime; // 2 int64u reportable
    public Integer totalOperationalHours; // 3 int32u reportable
    public BootReasonEnum bootReason; // 4 BootReasonEnum reportable
    public HardwareFaultEnum activeHardwareFaults; // 5 HardwareFaultEnum reportable
    public RadioFaultEnum activeRadioFaults; // 6 RadioFaultEnum reportable
    public NetworkFaultEnum activeNetworkFaults; // 7 NetworkFaultEnum reportable
    public Boolean testEventTriggersEnabled; // 8 boolean reportable
    public List<Integer> generatedCommandList; // 65528 command_id reportable
    public List<Integer> acceptedCommandList; // 65529 command_id reportable
    public List<Integer> eventList; // 65530 event_id reportable
    public List<Integer> attributeList; // 65531 attrib_id reportable
    public Map<String, Boolean> featureMap; // 65532 bitmap32 reportable
    public Integer clusterRevision; // 65533 int16u reportable

    public GeneralDiagnosticsCluster(long nodeId, int endpointId) {
        super(nodeId, endpointId, 31, "GeneralDiagnostics");
    }

    public void testEventTrigger(MatterClient client, String enableKey, Long eventTrigger) throws Exception {
        final String _enableKey = enableKey;
        final Long _eventTrigger = eventTrigger;
        Object o = new Object() {
            public String enableKey = _enableKey;
            public Long eventTrigger = _eventTrigger;
        };
        sendCommand(client, "testEventTrigger", o);
    }

    public void timeSnapshot(MatterClient client) throws Exception {
        Object o = new Object() {
        };
        sendCommand(client, "timeSnapshot", o);
    }

    public void timeSnapshotResponse(MatterClient client, Long systemTimeUs, Long UTCTimeUs) throws Exception {
        final Long _systemTimeUs = systemTimeUs;
        final Long _UTCTimeUs = UTCTimeUs;
        Object o = new Object() {
            public Long systemTimeUs = _systemTimeUs;
            public Long UTCTimeUs = _UTCTimeUs;
        };
        sendCommand(client, "timeSnapshotResponse", o);
    }

    public String toString() {
        String str = "";
        str += "networkInterfaces : " + networkInterfaces + "\n";
        str += "rebootCount : " + rebootCount + "\n";
        str += "upTime : " + upTime + "\n";
        str += "totalOperationalHours : " + totalOperationalHours + "\n";
        str += "bootReason : " + bootReason + "\n";
        str += "activeHardwareFaults : " + activeHardwareFaults + "\n";
        str += "activeRadioFaults : " + activeRadioFaults + "\n";
        str += "activeNetworkFaults : " + activeNetworkFaults + "\n";
        str += "testEventTriggersEnabled : " + testEventTriggersEnabled + "\n";
        str += "generatedCommandList : " + generatedCommandList + "\n";
        str += "acceptedCommandList : " + acceptedCommandList + "\n";
        str += "eventList : " + eventList + "\n";
        str += "attributeList : " + attributeList + "\n";
        str += "featureMap : " + featureMap + "\n";
        str += "clusterRevision : " + clusterRevision + "\n";
        return str;
    }
}
