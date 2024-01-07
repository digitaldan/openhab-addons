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
 * Thermostat
 * 
 * @author Dan Cunningham - Initial contribution
 */
public class ThermostatCluster extends BaseCluster {

    public static final String CLUSTER_NAME = "THERMOSTAT_CLUSTER";
    public static final int CLUSTER_ID = 0x0201;

    class ThermostatScheduleTransition {
        public Integer transitionTime; // int16u
        public Integer heatSetpoint; // int16s
        public Integer coolSetpoint; // int16s

        public ThermostatScheduleTransition(Integer transitionTime, Integer heatSetpoint, Integer coolSetpoint) {
            this.transitionTime = transitionTime;
            this.heatSetpoint = heatSetpoint;
            this.coolSetpoint = coolSetpoint;
        }
    }

    // ZCL Enums
    public enum SetpointAdjustMode {
        HEAT(0, "Heat"),
        COOL(1, "Cool"),
        BOTH(2, "Both"),
        UNKNOWN_VALUE(3, "UnknownValue");

        public final int value;
        public final String label;

        private SetpointAdjustMode(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum ThermostatControlSequence {
        COOLINGONLY(0, "CoolingOnly"),
        COOLINGWITHREHEAT(1, "CoolingWithReheat"),
        HEATINGONLY(2, "HeatingOnly"),
        HEATINGWITHREHEAT(3, "HeatingWithReheat"),
        COOLINGANDHEATING(4, "CoolingAndHeating"),
        COOLINGANDHEATINGWITHREHEAT(5, "CoolingAndHeatingWithReheat"),
        UNKNOWN_VALUE(6, "UnknownValue");

        public final int value;
        public final String label;

        private ThermostatControlSequence(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum ThermostatRunningMode {
        OFF(0, "Off"),
        COOL(3, "Cool"),
        HEAT(4, "Heat"),
        UNKNOWN_VALUE(1, "UnknownValue");

        public final int value;
        public final String label;

        private ThermostatRunningMode(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    public enum ThermostatSystemMode {
        OFF(0, "Off"),
        AUTO(1, "Auto"),
        COOL(3, "Cool"),
        HEAT(4, "Heat"),
        EMERGENCYHEAT(5, "EmergencyHeat"),
        PRECOOLING(6, "Precooling"),
        FANONLY(7, "FanOnly"),
        DRY(8, "Dry"),
        SLEEP(9, "Sleep"),
        UNKNOWN_VALUE(2, "UnknownValue");

        public final int value;
        public final String label;

        private ThermostatSystemMode(int value, String label) {
            this.value = value;
            this.label = label;
        }
    };

    // ZCL Bitmaps
    public static class DayOfWeek {
        public boolean sunday;
        public boolean monday;
        public boolean tuesday;
        public boolean wednesday;
        public boolean thursday;
        public boolean friday;
        public boolean saturday;
        public boolean away;

        public DayOfWeek(boolean sunday, boolean monday, boolean tuesday, boolean wednesday, boolean thursday,
                boolean friday, boolean saturday, boolean away) {
            this.sunday = sunday;
            this.monday = monday;
            this.tuesday = tuesday;
            this.wednesday = wednesday;
            this.thursday = thursday;
            this.friday = friday;
            this.saturday = saturday;
            this.away = away;
        }

        @SuppressWarnings({ "unchecked", "null" })
        public static DayOfWeek fromJson(String json) {
            Map<String, Boolean> m = GSON.fromJson(json, Map.class);
            Boolean[] keys = m.values().toArray(new Boolean[0]);
            return new DayOfWeek(keys[0], keys[1], keys[2], keys[3], keys[4], keys[5], keys[6], keys[7]);
        }
    }

    public static class Feature {
        public boolean heating;
        public boolean cooling;
        public boolean occupancy;
        public boolean scheduleConfiguration;
        public boolean setback;
        public boolean autoMode;
        public boolean localTemperatureNotExposed;

        public Feature(boolean heating, boolean cooling, boolean occupancy, boolean scheduleConfiguration,
                boolean setback, boolean autoMode, boolean localTemperatureNotExposed) {
            this.heating = heating;
            this.cooling = cooling;
            this.occupancy = occupancy;
            this.scheduleConfiguration = scheduleConfiguration;
            this.setback = setback;
            this.autoMode = autoMode;
            this.localTemperatureNotExposed = localTemperatureNotExposed;
        }

        @SuppressWarnings({ "unchecked", "null" })
        public static Feature fromJson(String json) {
            Map<String, Boolean> m = GSON.fromJson(json, Map.class);
            Boolean[] keys = m.values().toArray(new Boolean[0]);
            return new Feature(keys[0], keys[1], keys[2], keys[3], keys[4], keys[5], keys[6]);
        }
    }

    public static class ModeForSequence {
        public boolean heatSetpointPresent;
        public boolean coolSetpointPresent;

        public ModeForSequence(boolean heatSetpointPresent, boolean coolSetpointPresent) {
            this.heatSetpointPresent = heatSetpointPresent;
            this.coolSetpointPresent = coolSetpointPresent;
        }

        @SuppressWarnings({ "unchecked", "null" })
        public static ModeForSequence fromJson(String json) {
            Map<String, Boolean> m = GSON.fromJson(json, Map.class);
            Boolean[] keys = m.values().toArray(new Boolean[0]);
            return new ModeForSequence(keys[0], keys[1]);
        }
    }

    public Integer localTemperature; // 0 int16s reportable
    public Integer outdoorTemperature; // 1 int16s reportable
    public Map<String, Boolean> occupancy; // 2 bitmap8 reportable
    public Integer absMinHeatSetpointLimit; // 3 int16s reportable
    public Integer absMaxHeatSetpointLimit; // 4 int16s reportable
    public Integer absMinCoolSetpointLimit; // 5 int16s reportable
    public Integer absMaxCoolSetpointLimit; // 6 int16s reportable
    public Integer PICoolingDemand; // 7 int8u reportable
    public Integer PIHeatingDemand; // 8 int8u reportable
    public Map<String, Boolean> HVACSystemTypeConfiguration; // 9 bitmap8 reportable writable
    public Integer localTemperatureCalibration; // 16 int8s reportable writable
    public Integer occupiedCoolingSetpoint; // 17 int16s reportable writable
    public Integer occupiedHeatingSetpoint; // 18 int16s reportable writable
    public Integer unoccupiedCoolingSetpoint; // 19 int16s reportable writable
    public Integer unoccupiedHeatingSetpoint; // 20 int16s reportable writable
    public Integer minHeatSetpointLimit; // 21 int16s reportable writable
    public Integer maxHeatSetpointLimit; // 22 int16s reportable writable
    public Integer minCoolSetpointLimit; // 23 int16s reportable writable
    public Integer maxCoolSetpointLimit; // 24 int16s reportable writable
    public Integer minSetpointDeadBand; // 25 int8s reportable writable
    public Map<String, Boolean> remoteSensing; // 26 bitmap8 reportable writable
    public ThermostatControlSequence controlSequenceOfOperation; // 27 ThermostatControlSequence reportable writable
    public Integer systemMode; // 28 enum8 reportable writable
    public Integer thermostatRunningMode; // 30 enum8 reportable
    public Integer startOfWeek; // 32 enum8 reportable
    public Integer numberOfWeeklyTransitions; // 33 int8u reportable
    public Integer numberOfDailyTransitions; // 34 int8u reportable
    public Integer temperatureSetpointHold; // 35 enum8 reportable writable
    public Integer temperatureSetpointHoldDuration; // 36 int16u reportable writable
    public Map<String, Boolean> thermostatProgrammingOperationMode; // 37 bitmap8 reportable writable
    public Map<String, Boolean> thermostatRunningState; // 41 bitmap16 reportable
    public Integer setpointChangeSource; // 48 enum8 reportable
    public Integer setpointChangeAmount; // 49 int16s reportable
    public Integer setpointChangeSourceTimestamp; // 50 epoch_s reportable
    public Integer occupiedSetback; // 52 int8u reportable writable
    public Integer occupiedSetbackMin; // 53 int8u reportable
    public Integer occupiedSetbackMax; // 54 int8u reportable
    public Integer unoccupiedSetback; // 55 int8u reportable writable
    public Integer unoccupiedSetbackMin; // 56 int8u reportable
    public Integer unoccupiedSetbackMax; // 57 int8u reportable
    public Integer emergencyHeatDelta; // 58 int8u reportable writable
    public Integer ACType; // 64 enum8 reportable writable
    public Integer ACCapacity; // 65 int16u reportable writable
    public Integer ACRefrigerantType; // 66 enum8 reportable writable
    public Integer ACCompressorType; // 67 enum8 reportable writable
    public Map<String, Boolean> ACErrorCode; // 68 bitmap32 reportable writable
    public Integer ACLouverPosition; // 69 enum8 reportable writable
    public Integer ACCoilTemperature; // 70 int16s reportable
    public Integer ACCapacityformat; // 71 enum8 reportable writable
    public List<Integer> generatedCommandList; // 65528 command_id reportable
    public List<Integer> acceptedCommandList; // 65529 command_id reportable
    public List<Integer> eventList; // 65530 event_id reportable
    public List<Integer> attributeList; // 65531 attrib_id reportable
    public Map<String, Boolean> featureMap; // 65532 bitmap32 reportable
    public Integer clusterRevision; // 65533 int16u reportable

    public ThermostatCluster(long nodeId, int endpointId) {
        super(nodeId, endpointId, 72, "Thermostat");
    }

    public void setpointRaiseLower(MatterClient client, SetpointAdjustMode mode, Integer amount) throws Exception {
        final SetpointAdjustMode _mode = mode;
        final Integer _amount = amount;
        Object o = new Object() {
            public SetpointAdjustMode mode = _mode;
            public Integer amount = _amount;
        };
        sendCommand(client, "setpointRaiseLower", o);
    }

    public void getWeeklyScheduleResponse(MatterClient client, Integer numberOfTransitionsForSequence,
            DayOfWeek dayOfWeekForSequence, ModeForSequence modeForSequence, ThermostatScheduleTransition transitions)
            throws Exception {
        final Integer _numberOfTransitionsForSequence = numberOfTransitionsForSequence;
        final DayOfWeek _dayOfWeekForSequence = dayOfWeekForSequence;
        final ModeForSequence _modeForSequence = modeForSequence;
        final ThermostatScheduleTransition _transitions = transitions;
        Object o = new Object() {
            public Integer numberOfTransitionsForSequence = _numberOfTransitionsForSequence;
            public DayOfWeek dayOfWeekForSequence = _dayOfWeekForSequence;
            public ModeForSequence modeForSequence = _modeForSequence;
            public ThermostatScheduleTransition transitions = _transitions;
        };
        sendCommand(client, "getWeeklyScheduleResponse", o);
    }

    public void setWeeklySchedule(MatterClient client, Integer numberOfTransitionsForSequence,
            DayOfWeek dayOfWeekForSequence, ModeForSequence modeForSequence, ThermostatScheduleTransition transitions)
            throws Exception {
        final Integer _numberOfTransitionsForSequence = numberOfTransitionsForSequence;
        final DayOfWeek _dayOfWeekForSequence = dayOfWeekForSequence;
        final ModeForSequence _modeForSequence = modeForSequence;
        final ThermostatScheduleTransition _transitions = transitions;
        Object o = new Object() {
            public Integer numberOfTransitionsForSequence = _numberOfTransitionsForSequence;
            public DayOfWeek dayOfWeekForSequence = _dayOfWeekForSequence;
            public ModeForSequence modeForSequence = _modeForSequence;
            public ThermostatScheduleTransition transitions = _transitions;
        };
        sendCommand(client, "setWeeklySchedule", o);
    }

    public void getWeeklySchedule(MatterClient client, DayOfWeek daysToReturn, ModeForSequence modeToReturn)
            throws Exception {
        final DayOfWeek _daysToReturn = daysToReturn;
        final ModeForSequence _modeToReturn = modeToReturn;
        Object o = new Object() {
            public DayOfWeek daysToReturn = _daysToReturn;
            public ModeForSequence modeToReturn = _modeToReturn;
        };
        sendCommand(client, "getWeeklySchedule", o);
    }

    public void clearWeeklySchedule(MatterClient client) throws Exception {
        Object o = new Object() {
        };
        sendCommand(client, "clearWeeklySchedule", o);
    }

    public String toString() {
        String str = "";
        str += "localTemperature : " + localTemperature + "\n";
        str += "outdoorTemperature : " + outdoorTemperature + "\n";
        str += "occupancy : " + occupancy + "\n";
        str += "absMinHeatSetpointLimit : " + absMinHeatSetpointLimit + "\n";
        str += "absMaxHeatSetpointLimit : " + absMaxHeatSetpointLimit + "\n";
        str += "absMinCoolSetpointLimit : " + absMinCoolSetpointLimit + "\n";
        str += "absMaxCoolSetpointLimit : " + absMaxCoolSetpointLimit + "\n";
        str += "PICoolingDemand : " + PICoolingDemand + "\n";
        str += "PIHeatingDemand : " + PIHeatingDemand + "\n";
        str += "HVACSystemTypeConfiguration : " + HVACSystemTypeConfiguration + "\n";
        str += "localTemperatureCalibration : " + localTemperatureCalibration + "\n";
        str += "occupiedCoolingSetpoint : " + occupiedCoolingSetpoint + "\n";
        str += "occupiedHeatingSetpoint : " + occupiedHeatingSetpoint + "\n";
        str += "unoccupiedCoolingSetpoint : " + unoccupiedCoolingSetpoint + "\n";
        str += "unoccupiedHeatingSetpoint : " + unoccupiedHeatingSetpoint + "\n";
        str += "minHeatSetpointLimit : " + minHeatSetpointLimit + "\n";
        str += "maxHeatSetpointLimit : " + maxHeatSetpointLimit + "\n";
        str += "minCoolSetpointLimit : " + minCoolSetpointLimit + "\n";
        str += "maxCoolSetpointLimit : " + maxCoolSetpointLimit + "\n";
        str += "minSetpointDeadBand : " + minSetpointDeadBand + "\n";
        str += "remoteSensing : " + remoteSensing + "\n";
        str += "controlSequenceOfOperation : " + controlSequenceOfOperation + "\n";
        str += "systemMode : " + systemMode + "\n";
        str += "thermostatRunningMode : " + thermostatRunningMode + "\n";
        str += "startOfWeek : " + startOfWeek + "\n";
        str += "numberOfWeeklyTransitions : " + numberOfWeeklyTransitions + "\n";
        str += "numberOfDailyTransitions : " + numberOfDailyTransitions + "\n";
        str += "temperatureSetpointHold : " + temperatureSetpointHold + "\n";
        str += "temperatureSetpointHoldDuration : " + temperatureSetpointHoldDuration + "\n";
        str += "thermostatProgrammingOperationMode : " + thermostatProgrammingOperationMode + "\n";
        str += "thermostatRunningState : " + thermostatRunningState + "\n";
        str += "setpointChangeSource : " + setpointChangeSource + "\n";
        str += "setpointChangeAmount : " + setpointChangeAmount + "\n";
        str += "setpointChangeSourceTimestamp : " + setpointChangeSourceTimestamp + "\n";
        str += "occupiedSetback : " + occupiedSetback + "\n";
        str += "occupiedSetbackMin : " + occupiedSetbackMin + "\n";
        str += "occupiedSetbackMax : " + occupiedSetbackMax + "\n";
        str += "unoccupiedSetback : " + unoccupiedSetback + "\n";
        str += "unoccupiedSetbackMin : " + unoccupiedSetbackMin + "\n";
        str += "unoccupiedSetbackMax : " + unoccupiedSetbackMax + "\n";
        str += "emergencyHeatDelta : " + emergencyHeatDelta + "\n";
        str += "ACType : " + ACType + "\n";
        str += "ACCapacity : " + ACCapacity + "\n";
        str += "ACRefrigerantType : " + ACRefrigerantType + "\n";
        str += "ACCompressorType : " + ACCompressorType + "\n";
        str += "ACErrorCode : " + ACErrorCode + "\n";
        str += "ACLouverPosition : " + ACLouverPosition + "\n";
        str += "ACCoilTemperature : " + ACCoilTemperature + "\n";
        str += "ACCapacityformat : " + ACCapacityformat + "\n";
        str += "generatedCommandList : " + generatedCommandList + "\n";
        str += "acceptedCommandList : " + acceptedCommandList + "\n";
        str += "eventList : " + eventList + "\n";
        str += "attributeList : " + attributeList + "\n";
        str += "featureMap : " + featureMap + "\n";
        str += "clusterRevision : " + clusterRevision + "\n";
        return str;
    }
}
