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
import org.openhab.binding.matter.internal.client.model.cluster.gen.ElectricalMeasurementClusterTypes.*;
/**
 * ElectricalMeasurement
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ElectricalMeasurementClusterCommands {

    public static ClusterCommand getProfileInfoResponseCommand(Integer profileCount, Integer profileIntervalPeriod, Integer maxNumberOfIntervals, Integer listOfAttributes) {
        return new ClusterCommand("getProfileInfoResponseCommand", new GetProfileInfoResponseCommandCommandOptions(profileCount, profileIntervalPeriod, maxNumberOfIntervals, listOfAttributes));
    }

    public static ClusterCommand getProfileInfoCommand() {
        return new ClusterCommand("getProfileInfoCommand", new GetProfileInfoCommandCommandOptions());
    }

    public static ClusterCommand getMeasurementProfileResponseCommand(Integer startTime, Integer status, Integer profileIntervalPeriod, Integer numberOfIntervalsDelivered, Integer attributeId, Integer intervals) {
        return new ClusterCommand("getMeasurementProfileResponseCommand", new GetMeasurementProfileResponseCommandCommandOptions(startTime, status, profileIntervalPeriod, numberOfIntervalsDelivered, attributeId, intervals));
    }

    public static ClusterCommand getMeasurementProfileCommand(Integer attributeId, Integer startTime, Integer numberOfIntervals) {
        return new ClusterCommand("getMeasurementProfileCommand", new GetMeasurementProfileCommandCommandOptions(attributeId, startTime, numberOfIntervals));
    }
}