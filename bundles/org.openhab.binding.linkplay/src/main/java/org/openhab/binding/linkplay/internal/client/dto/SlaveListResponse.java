/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
package org.openhab.binding.linkplay.internal.client.dto;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Response from /multiroom:getSlaveList
 * 
 * @author Dan Cunningham - Initial contribution
 */
public class SlaveListResponse {

    public int slaves;
    @SerializedName("wmrm_version")
    public String wmrmVersion;
    public int surround;
    @SerializedName("slave_list")
    public List<Slave> slaveList;

    /**
     * Master units will report greater than 0 slaves.
     * Slave units, or units that are not part of a multiroom group, will report 0 slaves.
     */
    public boolean isMaster() {
        return slaves > 0;
    }
}
