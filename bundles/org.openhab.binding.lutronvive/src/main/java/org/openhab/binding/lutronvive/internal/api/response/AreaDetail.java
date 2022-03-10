/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.lutronvive.internal.api.response;

import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 *
 * @author Dan Cunningham - Initial contribution
 */
public class AreaDetail {

    @SerializedName("area")
    @Expose
    public Area area;
    @SerializedName("areaRelation")
    @Expose
    public AreaRelation areaRelation;
    @SerializedName("devices")
    @Expose
    public List<Device> devices = null;
    @SerializedName("buttonGroups")
    @Expose
    public List<Reference> buttonGroups = null;
    @SerializedName("occupancyGroups")
    @Expose
    public List<Reference> occupancyGroups = null;
    @SerializedName("daylightingGainGroups")
    @Expose
    public List<Reference> daylightingGainGroups = null;
    @SerializedName("daylightingGainSettings")
    @Expose
    public Reference daylightingGainSettings;
}
