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
public class Button {

    @SerializedName("href")
    @Expose
    public String href;
    @SerializedName("buttonNumber")
    @Expose
    public Integer buttonNumber;
    @SerializedName("programmingModel")
    @Expose
    public Reference programmingModel;
    @SerializedName("parent")
    @Expose
    public Reference parent;
}
