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

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ZoneTypeGroup extends Reference {

    @SerializedName("type")
    @Expose
    public String type;
    @SerializedName("area")
    @Expose
    public Area area;
    @SerializedName("occupancySettings")
    @Expose
    public Reference occupancySettings;
    @SerializedName("emergencySettings")
    @Expose
    public Reference emergencySettings;
}
