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

import org.openhab.binding.matter.internal.client.model.cluster.ClusterCommand;
import org.openhab.binding.matter.internal.client.model.cluster.gen.ThermostatClusterTypes.*;
/**
 * Thermostat
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ThermostatClusterCommands {

    public static ClusterCommand setpointRaiseLower(SetpointAdjustMode mode, Integer amount) {
        return new ClusterCommand("setpointRaiseLower", new SetpointRaiseLowerCommandOptions(mode, amount));
    }

    public static ClusterCommand getWeeklyScheduleResponse(Integer numberOfTransitionsForSequence, DayOfWeek dayOfWeekForSequence, ModeForSequence modeForSequence, ThermostatScheduleTransition transitions) {
        return new ClusterCommand("getWeeklyScheduleResponse", new GetWeeklyScheduleResponseCommandOptions(numberOfTransitionsForSequence, dayOfWeekForSequence, modeForSequence, transitions));
    }

    public static ClusterCommand setWeeklySchedule(Integer numberOfTransitionsForSequence, DayOfWeek dayOfWeekForSequence, ModeForSequence modeForSequence, ThermostatScheduleTransition transitions) {
        return new ClusterCommand("setWeeklySchedule", new SetWeeklyScheduleCommandOptions(numberOfTransitionsForSequence, dayOfWeekForSequence, modeForSequence, transitions));
    }

    public static ClusterCommand getWeeklySchedule(DayOfWeek daysToReturn, ModeForSequence modeToReturn) {
        return new ClusterCommand("getWeeklySchedule", new GetWeeklyScheduleCommandOptions(daysToReturn, modeToReturn));
    }

    public static ClusterCommand clearWeeklySchedule() {
        return new ClusterCommand("clearWeeklySchedule", new ClearWeeklyScheduleCommandOptions());
    }
}