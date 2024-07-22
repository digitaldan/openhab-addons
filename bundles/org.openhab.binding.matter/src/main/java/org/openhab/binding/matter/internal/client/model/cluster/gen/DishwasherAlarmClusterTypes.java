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

// 

package org.openhab.binding.matter.internal.client.model.cluster.gen;

import java.util.Map;
import java.util.List;

import org.openhab.binding.matter.internal.client.model.cluster.types.*;
import static java.util.Map.entry;  

/**
 * AUTO-GENERATED by zap. DO NOT EDIT!
 *
 * DishwasherAlarm
 *
 * @author Dan Cunningham - Initial contribution
 */
public class DishwasherAlarmClusterTypes {

    public static final String CLUSTER_NAME = "DISHWASHER_ALARM_CLUSTER";
    public static final int CLUSTER_ID = 0x005D;

    //ZCL Bitmaps
    public static class AlarmMap {
        public boolean inflowError;
        public boolean drainError;
        public boolean doorError;
        public boolean tempTooLow;
        public boolean tempTooHigh;
        public boolean waterLevelError;
        public AlarmMap(boolean inflowError, boolean drainError, boolean doorError, boolean tempTooLow, boolean tempTooHigh, boolean waterLevelError){
            this.inflowError = inflowError;
            this.drainError = drainError;
            this.doorError = doorError;
            this.tempTooLow = tempTooLow;
            this.tempTooHigh = tempTooHigh;
            this.waterLevelError = waterLevelError;
        }
    }
    public static class Feature {
        public boolean reset;
        public Feature(boolean reset){
            this.reset = reset;
        }
    }

    public static class ResetCommandOptions {
        public AlarmMap alarms;
        public  ResetCommandOptions(AlarmMap alarms){
            this.alarms = alarms;
        }
    }
    public static class ModifyEnabledAlarmsCommandOptions {
        public AlarmMap mask;
        public  ModifyEnabledAlarmsCommandOptions(AlarmMap mask){
            this.mask = mask;
        }
    }
}