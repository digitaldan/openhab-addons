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

package org.openhab.binding.matter.internal.client.model.cluster.gen;

import java.util.Map;
import java.util.List;

import org.openhab.binding.matter.internal.client.model.cluster.BaseCluster;
import org.openhab.binding.matter.internal.client.model.cluster.gen.ElectricalMeasurementClusterTypes.*;

/**
 * ElectricalMeasurement
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ElectricalMeasurementCluster extends BaseCluster {

    public static final String CLUSTER_NAME = "ElectricalMeasurement";
    public static final int CLUSTER_ID = 0x0B04;

    public Map<String, Boolean> measurementType; // 0 bitmap32 reportable 
    public Integer dcVoltage; // 256 int16s reportable 
    public Integer dcVoltageMin; // 257 int16s reportable 
    public Integer dcVoltageMax; // 258 int16s reportable 
    public Integer dcCurrent; // 259 int16s reportable 
    public Integer dcCurrentMin; // 260 int16s reportable 
    public Integer dcCurrentMax; // 261 int16s reportable 
    public Integer dcPower; // 262 int16s reportable 
    public Integer dcPowerMin; // 263 int16s reportable 
    public Integer dcPowerMax; // 264 int16s reportable 
    public Integer dcVoltageMultiplier; // 512 int16u reportable 
    public Integer dcVoltageDivisor; // 513 int16u reportable 
    public Integer dcCurrentMultiplier; // 514 int16u reportable 
    public Integer dcCurrentDivisor; // 515 int16u reportable 
    public Integer dcPowerMultiplier; // 516 int16u reportable 
    public Integer dcPowerDivisor; // 517 int16u reportable 
    public Integer acFrequency; // 768 int16u reportable 
    public Integer acFrequencyMin; // 769 int16u reportable 
    public Integer acFrequencyMax; // 770 int16u reportable 
    public Integer neutralCurrent; // 771 int16u reportable 
    public Integer totalActivePower; // 772 int32s reportable 
    public Integer totalReactivePower; // 773 int32s reportable 
    public Integer totalApparentPower; // 774 int32u reportable 
    public Integer measured1stHarmonicCurrent; // 775 int16s reportable 
    public Integer measured3rdHarmonicCurrent; // 776 int16s reportable 
    public Integer measured5thHarmonicCurrent; // 777 int16s reportable 
    public Integer measured7thHarmonicCurrent; // 778 int16s reportable 
    public Integer measured9thHarmonicCurrent; // 779 int16s reportable 
    public Integer measured11thHarmonicCurrent; // 780 int16s reportable 
    public Integer measuredPhase1stHarmonicCurrent; // 781 int16s reportable 
    public Integer measuredPhase3rdHarmonicCurrent; // 782 int16s reportable 
    public Integer measuredPhase5thHarmonicCurrent; // 783 int16s reportable 
    public Integer measuredPhase7thHarmonicCurrent; // 784 int16s reportable 
    public Integer measuredPhase9thHarmonicCurrent; // 785 int16s reportable 
    public Integer measuredPhase11thHarmonicCurrent; // 786 int16s reportable 
    public Integer acFrequencyMultiplier; // 1024 int16u reportable 
    public Integer acFrequencyDivisor; // 1025 int16u reportable 
    public Integer powerMultiplier; // 1026 int32u reportable 
    public Integer powerDivisor; // 1027 int32u reportable 
    public Integer harmonicCurrentMultiplier; // 1028 int8s reportable 
    public Integer phaseHarmonicCurrentMultiplier; // 1029 int8s reportable 
    public Integer instantaneousVoltage; // 1280 int16s reportable 
    public Integer instantaneousLineCurrent; // 1281 int16u reportable 
    public Integer instantaneousActiveCurrent; // 1282 int16s reportable 
    public Integer instantaneousReactiveCurrent; // 1283 int16s reportable 
    public Integer instantaneousPower; // 1284 int16s reportable 
    public Integer rmsVoltage; // 1285 int16u reportable 
    public Integer rmsVoltageMin; // 1286 int16u reportable 
    public Integer rmsVoltageMax; // 1287 int16u reportable 
    public Integer rmsCurrent; // 1288 int16u reportable 
    public Integer rmsCurrentMin; // 1289 int16u reportable 
    public Integer rmsCurrentMax; // 1290 int16u reportable 
    public Integer activePower; // 1291 int16s reportable 
    public Integer activePowerMin; // 1292 int16s reportable 
    public Integer activePowerMax; // 1293 int16s reportable 
    public Integer reactivePower; // 1294 int16s reportable 
    public Integer apparentPower; // 1295 int16u reportable 
    public Integer powerFactor; // 1296 int8s reportable 
    public Integer averageRmsVoltageMeasurementPeriod; // 1297 int16u reportable writable
    public Integer averageRmsUnderVoltageCounter; // 1299 int16u reportable writable
    public Integer rmsExtremeOverVoltagePeriod; // 1300 int16u reportable writable
    public Integer rmsExtremeUnderVoltagePeriod; // 1301 int16u reportable writable
    public Integer rmsVoltageSagPeriod; // 1302 int16u reportable writable
    public Integer rmsVoltageSwellPeriod; // 1303 int16u reportable writable
    public Integer acVoltageMultiplier; // 1536 int16u reportable 
    public Integer acVoltageDivisor; // 1537 int16u reportable 
    public Integer acCurrentMultiplier; // 1538 int16u reportable 
    public Integer acCurrentDivisor; // 1539 int16u reportable 
    public Integer acPowerMultiplier; // 1540 int16u reportable 
    public Integer acPowerDivisor; // 1541 int16u reportable 
    public Map<String, Boolean> overloadAlarmsMask; // 1792 bitmap8 reportable writable
    public Integer voltageOverload; // 1793 int16s reportable 
    public Integer currentOverload; // 1794 int16s reportable 
    public Map<String, Boolean> acOverloadAlarmsMask; // 2048 bitmap16 reportable writable
    public Integer acVoltageOverload; // 2049 int16s reportable 
    public Integer acCurrentOverload; // 2050 int16s reportable 
    public Integer acActivePowerOverload; // 2051 int16s reportable 
    public Integer acReactivePowerOverload; // 2052 int16s reportable 
    public Integer averageRmsOverVoltage; // 2053 int16s reportable 
    public Integer averageRmsUnderVoltage; // 2054 int16s reportable 
    public Integer rmsExtremeOverVoltage; // 2055 int16s reportable 
    public Integer rmsExtremeUnderVoltage; // 2056 int16s reportable 
    public Integer rmsVoltageSag; // 2057 int16s reportable 
    public Integer rmsVoltageSwell; // 2058 int16s reportable 
    public Integer lineCurrentPhaseB; // 2305 int16u reportable 
    public Integer activeCurrentPhaseB; // 2306 int16s reportable 
    public Integer reactiveCurrentPhaseB; // 2307 int16s reportable 
    public Integer rmsVoltagePhaseB; // 2309 int16u reportable 
    public Integer rmsVoltageMinPhaseB; // 2310 int16u reportable 
    public Integer rmsVoltageMaxPhaseB; // 2311 int16u reportable 
    public Integer rmsCurrentPhaseB; // 2312 int16u reportable 
    public Integer rmsCurrentMinPhaseB; // 2313 int16u reportable 
    public Integer rmsCurrentMaxPhaseB; // 2314 int16u reportable 
    public Integer activePowerPhaseB; // 2315 int16s reportable 
    public Integer activePowerMinPhaseB; // 2316 int16s reportable 
    public Integer activePowerMaxPhaseB; // 2317 int16s reportable 
    public Integer reactivePowerPhaseB; // 2318 int16s reportable 
    public Integer apparentPowerPhaseB; // 2319 int16u reportable 
    public Integer powerFactorPhaseB; // 2320 int8s reportable 
    public Integer averageRmsVoltageMeasurementPeriodPhaseB; // 2321 int16u reportable 
    public Integer averageRmsOverVoltageCounterPhaseB; // 2322 int16u reportable 
    public Integer averageRmsUnderVoltageCounterPhaseB; // 2323 int16u reportable 
    public Integer rmsExtremeOverVoltagePeriodPhaseB; // 2324 int16u reportable 
    public Integer rmsExtremeUnderVoltagePeriodPhaseB; // 2325 int16u reportable 
    public Integer rmsVoltageSagPeriodPhaseB; // 2326 int16u reportable 
    public Integer rmsVoltageSwellPeriodPhaseB; // 2327 int16u reportable 
    public Integer lineCurrentPhaseC; // 2561 int16u reportable 
    public Integer activeCurrentPhaseC; // 2562 int16s reportable 
    public Integer reactiveCurrentPhaseC; // 2563 int16s reportable 
    public Integer rmsVoltagePhaseC; // 2565 int16u reportable 
    public Integer rmsVoltageMinPhaseC; // 2566 int16u reportable 
    public Integer rmsVoltageMaxPhaseC; // 2567 int16u reportable 
    public Integer rmsCurrentPhaseC; // 2568 int16u reportable 
    public Integer rmsCurrentMinPhaseC; // 2569 int16u reportable 
    public Integer rmsCurrentMaxPhaseC; // 2570 int16u reportable 
    public Integer activePowerPhaseC; // 2571 int16s reportable 
    public Integer activePowerMinPhaseC; // 2572 int16s reportable 
    public Integer activePowerMaxPhaseC; // 2573 int16s reportable 
    public Integer reactivePowerPhaseC; // 2574 int16s reportable 
    public Integer apparentPowerPhaseC; // 2575 int16u reportable 
    public Integer powerFactorPhaseC; // 2576 int8s reportable 
    public Integer averageRmsVoltageMeasurementPeriodPhaseC; // 2577 int16u reportable 
    public Integer averageRmsOverVoltageCounterPhaseC; // 2578 int16u reportable 
    public Integer averageRmsUnderVoltageCounterPhaseC; // 2579 int16u reportable 
    public Integer rmsExtremeOverVoltagePeriodPhaseC; // 2580 int16u reportable 
    public Integer rmsExtremeUnderVoltagePeriodPhaseC; // 2581 int16u reportable 
    public Integer rmsVoltageSagPeriodPhaseC; // 2582 int16u reportable 
    public Integer rmsVoltageSwellPeriodPhaseC; // 2583 int16u reportable 
    public List<Integer> generatedCommandList; // 65528 command_id reportable 
    public List<Integer> acceptedCommandList; // 65529 command_id reportable 
    public List<Integer> eventList; // 65530 event_id reportable 
    public List<Integer> attributeList; // 65531 attrib_id reportable 
    public Map<String, Boolean> featureMap; // 65532 bitmap32 reportable 
    public Integer clusterRevision; // 65533 int16u reportable 

    public ElectricalMeasurementCluster(String nodeId, int endpointId) {
        super(nodeId, endpointId, 18, "ElectricalMeasurement");
    }

    public String toString() {
        String str = "";
        str += "measurementType : " + measurementType + "\n";
        str += "dcVoltage : " + dcVoltage + "\n";
        str += "dcVoltageMin : " + dcVoltageMin + "\n";
        str += "dcVoltageMax : " + dcVoltageMax + "\n";
        str += "dcCurrent : " + dcCurrent + "\n";
        str += "dcCurrentMin : " + dcCurrentMin + "\n";
        str += "dcCurrentMax : " + dcCurrentMax + "\n";
        str += "dcPower : " + dcPower + "\n";
        str += "dcPowerMin : " + dcPowerMin + "\n";
        str += "dcPowerMax : " + dcPowerMax + "\n";
        str += "dcVoltageMultiplier : " + dcVoltageMultiplier + "\n";
        str += "dcVoltageDivisor : " + dcVoltageDivisor + "\n";
        str += "dcCurrentMultiplier : " + dcCurrentMultiplier + "\n";
        str += "dcCurrentDivisor : " + dcCurrentDivisor + "\n";
        str += "dcPowerMultiplier : " + dcPowerMultiplier + "\n";
        str += "dcPowerDivisor : " + dcPowerDivisor + "\n";
        str += "acFrequency : " + acFrequency + "\n";
        str += "acFrequencyMin : " + acFrequencyMin + "\n";
        str += "acFrequencyMax : " + acFrequencyMax + "\n";
        str += "neutralCurrent : " + neutralCurrent + "\n";
        str += "totalActivePower : " + totalActivePower + "\n";
        str += "totalReactivePower : " + totalReactivePower + "\n";
        str += "totalApparentPower : " + totalApparentPower + "\n";
        str += "measured1stHarmonicCurrent : " + measured1stHarmonicCurrent + "\n";
        str += "measured3rdHarmonicCurrent : " + measured3rdHarmonicCurrent + "\n";
        str += "measured5thHarmonicCurrent : " + measured5thHarmonicCurrent + "\n";
        str += "measured7thHarmonicCurrent : " + measured7thHarmonicCurrent + "\n";
        str += "measured9thHarmonicCurrent : " + measured9thHarmonicCurrent + "\n";
        str += "measured11thHarmonicCurrent : " + measured11thHarmonicCurrent + "\n";
        str += "measuredPhase1stHarmonicCurrent : " + measuredPhase1stHarmonicCurrent + "\n";
        str += "measuredPhase3rdHarmonicCurrent : " + measuredPhase3rdHarmonicCurrent + "\n";
        str += "measuredPhase5thHarmonicCurrent : " + measuredPhase5thHarmonicCurrent + "\n";
        str += "measuredPhase7thHarmonicCurrent : " + measuredPhase7thHarmonicCurrent + "\n";
        str += "measuredPhase9thHarmonicCurrent : " + measuredPhase9thHarmonicCurrent + "\n";
        str += "measuredPhase11thHarmonicCurrent : " + measuredPhase11thHarmonicCurrent + "\n";
        str += "acFrequencyMultiplier : " + acFrequencyMultiplier + "\n";
        str += "acFrequencyDivisor : " + acFrequencyDivisor + "\n";
        str += "powerMultiplier : " + powerMultiplier + "\n";
        str += "powerDivisor : " + powerDivisor + "\n";
        str += "harmonicCurrentMultiplier : " + harmonicCurrentMultiplier + "\n";
        str += "phaseHarmonicCurrentMultiplier : " + phaseHarmonicCurrentMultiplier + "\n";
        str += "instantaneousVoltage : " + instantaneousVoltage + "\n";
        str += "instantaneousLineCurrent : " + instantaneousLineCurrent + "\n";
        str += "instantaneousActiveCurrent : " + instantaneousActiveCurrent + "\n";
        str += "instantaneousReactiveCurrent : " + instantaneousReactiveCurrent + "\n";
        str += "instantaneousPower : " + instantaneousPower + "\n";
        str += "rmsVoltage : " + rmsVoltage + "\n";
        str += "rmsVoltageMin : " + rmsVoltageMin + "\n";
        str += "rmsVoltageMax : " + rmsVoltageMax + "\n";
        str += "rmsCurrent : " + rmsCurrent + "\n";
        str += "rmsCurrentMin : " + rmsCurrentMin + "\n";
        str += "rmsCurrentMax : " + rmsCurrentMax + "\n";
        str += "activePower : " + activePower + "\n";
        str += "activePowerMin : " + activePowerMin + "\n";
        str += "activePowerMax : " + activePowerMax + "\n";
        str += "reactivePower : " + reactivePower + "\n";
        str += "apparentPower : " + apparentPower + "\n";
        str += "powerFactor : " + powerFactor + "\n";
        str += "averageRmsVoltageMeasurementPeriod : " + averageRmsVoltageMeasurementPeriod + "\n";
        str += "averageRmsUnderVoltageCounter : " + averageRmsUnderVoltageCounter + "\n";
        str += "rmsExtremeOverVoltagePeriod : " + rmsExtremeOverVoltagePeriod + "\n";
        str += "rmsExtremeUnderVoltagePeriod : " + rmsExtremeUnderVoltagePeriod + "\n";
        str += "rmsVoltageSagPeriod : " + rmsVoltageSagPeriod + "\n";
        str += "rmsVoltageSwellPeriod : " + rmsVoltageSwellPeriod + "\n";
        str += "acVoltageMultiplier : " + acVoltageMultiplier + "\n";
        str += "acVoltageDivisor : " + acVoltageDivisor + "\n";
        str += "acCurrentMultiplier : " + acCurrentMultiplier + "\n";
        str += "acCurrentDivisor : " + acCurrentDivisor + "\n";
        str += "acPowerMultiplier : " + acPowerMultiplier + "\n";
        str += "acPowerDivisor : " + acPowerDivisor + "\n";
        str += "overloadAlarmsMask : " + overloadAlarmsMask + "\n";
        str += "voltageOverload : " + voltageOverload + "\n";
        str += "currentOverload : " + currentOverload + "\n";
        str += "acOverloadAlarmsMask : " + acOverloadAlarmsMask + "\n";
        str += "acVoltageOverload : " + acVoltageOverload + "\n";
        str += "acCurrentOverload : " + acCurrentOverload + "\n";
        str += "acActivePowerOverload : " + acActivePowerOverload + "\n";
        str += "acReactivePowerOverload : " + acReactivePowerOverload + "\n";
        str += "averageRmsOverVoltage : " + averageRmsOverVoltage + "\n";
        str += "averageRmsUnderVoltage : " + averageRmsUnderVoltage + "\n";
        str += "rmsExtremeOverVoltage : " + rmsExtremeOverVoltage + "\n";
        str += "rmsExtremeUnderVoltage : " + rmsExtremeUnderVoltage + "\n";
        str += "rmsVoltageSag : " + rmsVoltageSag + "\n";
        str += "rmsVoltageSwell : " + rmsVoltageSwell + "\n";
        str += "lineCurrentPhaseB : " + lineCurrentPhaseB + "\n";
        str += "activeCurrentPhaseB : " + activeCurrentPhaseB + "\n";
        str += "reactiveCurrentPhaseB : " + reactiveCurrentPhaseB + "\n";
        str += "rmsVoltagePhaseB : " + rmsVoltagePhaseB + "\n";
        str += "rmsVoltageMinPhaseB : " + rmsVoltageMinPhaseB + "\n";
        str += "rmsVoltageMaxPhaseB : " + rmsVoltageMaxPhaseB + "\n";
        str += "rmsCurrentPhaseB : " + rmsCurrentPhaseB + "\n";
        str += "rmsCurrentMinPhaseB : " + rmsCurrentMinPhaseB + "\n";
        str += "rmsCurrentMaxPhaseB : " + rmsCurrentMaxPhaseB + "\n";
        str += "activePowerPhaseB : " + activePowerPhaseB + "\n";
        str += "activePowerMinPhaseB : " + activePowerMinPhaseB + "\n";
        str += "activePowerMaxPhaseB : " + activePowerMaxPhaseB + "\n";
        str += "reactivePowerPhaseB : " + reactivePowerPhaseB + "\n";
        str += "apparentPowerPhaseB : " + apparentPowerPhaseB + "\n";
        str += "powerFactorPhaseB : " + powerFactorPhaseB + "\n";
        str += "averageRmsVoltageMeasurementPeriodPhaseB : " + averageRmsVoltageMeasurementPeriodPhaseB + "\n";
        str += "averageRmsOverVoltageCounterPhaseB : " + averageRmsOverVoltageCounterPhaseB + "\n";
        str += "averageRmsUnderVoltageCounterPhaseB : " + averageRmsUnderVoltageCounterPhaseB + "\n";
        str += "rmsExtremeOverVoltagePeriodPhaseB : " + rmsExtremeOverVoltagePeriodPhaseB + "\n";
        str += "rmsExtremeUnderVoltagePeriodPhaseB : " + rmsExtremeUnderVoltagePeriodPhaseB + "\n";
        str += "rmsVoltageSagPeriodPhaseB : " + rmsVoltageSagPeriodPhaseB + "\n";
        str += "rmsVoltageSwellPeriodPhaseB : " + rmsVoltageSwellPeriodPhaseB + "\n";
        str += "lineCurrentPhaseC : " + lineCurrentPhaseC + "\n";
        str += "activeCurrentPhaseC : " + activeCurrentPhaseC + "\n";
        str += "reactiveCurrentPhaseC : " + reactiveCurrentPhaseC + "\n";
        str += "rmsVoltagePhaseC : " + rmsVoltagePhaseC + "\n";
        str += "rmsVoltageMinPhaseC : " + rmsVoltageMinPhaseC + "\n";
        str += "rmsVoltageMaxPhaseC : " + rmsVoltageMaxPhaseC + "\n";
        str += "rmsCurrentPhaseC : " + rmsCurrentPhaseC + "\n";
        str += "rmsCurrentMinPhaseC : " + rmsCurrentMinPhaseC + "\n";
        str += "rmsCurrentMaxPhaseC : " + rmsCurrentMaxPhaseC + "\n";
        str += "activePowerPhaseC : " + activePowerPhaseC + "\n";
        str += "activePowerMinPhaseC : " + activePowerMinPhaseC + "\n";
        str += "activePowerMaxPhaseC : " + activePowerMaxPhaseC + "\n";
        str += "reactivePowerPhaseC : " + reactivePowerPhaseC + "\n";
        str += "apparentPowerPhaseC : " + apparentPowerPhaseC + "\n";
        str += "powerFactorPhaseC : " + powerFactorPhaseC + "\n";
        str += "averageRmsVoltageMeasurementPeriodPhaseC : " + averageRmsVoltageMeasurementPeriodPhaseC + "\n";
        str += "averageRmsOverVoltageCounterPhaseC : " + averageRmsOverVoltageCounterPhaseC + "\n";
        str += "averageRmsUnderVoltageCounterPhaseC : " + averageRmsUnderVoltageCounterPhaseC + "\n";
        str += "rmsExtremeOverVoltagePeriodPhaseC : " + rmsExtremeOverVoltagePeriodPhaseC + "\n";
        str += "rmsExtremeUnderVoltagePeriodPhaseC : " + rmsExtremeUnderVoltagePeriodPhaseC + "\n";
        str += "rmsVoltageSagPeriodPhaseC : " + rmsVoltageSagPeriodPhaseC + "\n";
        str += "rmsVoltageSwellPeriodPhaseC : " + rmsVoltageSwellPeriodPhaseC + "\n";
        str += "generatedCommandList : " + generatedCommandList + "\n";
        str += "acceptedCommandList : " + acceptedCommandList + "\n";
        str += "eventList : " + eventList + "\n";
        str += "attributeList : " + attributeList + "\n";
        str += "featureMap : " + featureMap + "\n";
        str += "clusterRevision : " + clusterRevision + "\n";
        return str;
    }
}