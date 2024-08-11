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

// AUTO-GENERATED, DO NOT EDIT!

package org.openhab.binding.matter.internal.client.model.cluster.gen;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.openhab.binding.matter.internal.client.model.cluster.BaseCluster;
import org.openhab.binding.matter.internal.client.model.cluster.ClusterCommand;
import org.openhab.binding.matter.internal.client.model.cluster.gen.DataTypes.*;

/**
 * BooleanState
 *
 * @author Dan Cunningham - Initial contribution
 */
public class BooleanStateCluster extends BaseCluster {

    public static final String CLUSTER_NAME = "BooleanState";
    public static final int CLUSTER_ID = 0x0045;

    public Integer clusterRevision; // 65533 ClusterRevision 
    /**
    * This represents a boolean state.
The semantics of this boolean state are defined by the device type using this cluster.
For example, in a Contact Sensor device type, FALSE&#x3D;open or no contact, TRUE&#x3D;closed or contact.
    */
    public Boolean stateValue; // 0 bool R V




    public BooleanStateCluster(String nodeId, int endpointId) {
        super(nodeId, endpointId, 69, "BooleanState");
    }

    

    public String toString() {
        String str = "";
        str += "clusterRevision : " + clusterRevision + "\n";
        str += "stateValue : " + stateValue + "\n";
        return str;
    }
}
