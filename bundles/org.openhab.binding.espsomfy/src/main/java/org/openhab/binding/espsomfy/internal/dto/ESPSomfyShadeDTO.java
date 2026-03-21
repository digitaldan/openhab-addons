/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.espsomfy.internal.dto;

import com.google.gson.annotations.SerializedName;

/**
 * DTO for ESPSomfy shade data from both HTTP API and WebSocket events.
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ESPSomfyShadeDTO {

    public int shadeId;
    public String name = "";
    public int roomId = -1;

    @SerializedName("type")
    public int shadeType;

    public int position;
    public int tiltPosition;
    public int tiltType;
    public int direction;
    public int tiltDirection;
    public int myPos = -1;
    public int myTiltPos = -1;
    public int target;
    public int tiltTarget;
    public long remoteAddress;
    public boolean paired;
    public int bitLength;
    public int proto;
    public int flags;
    public boolean sunSensor;
    public boolean light;
    public boolean flipCommands;
    public boolean flipPosition;
    public int sortOrder;
    public int lastRollingCode;
    public boolean inGroup;
}
