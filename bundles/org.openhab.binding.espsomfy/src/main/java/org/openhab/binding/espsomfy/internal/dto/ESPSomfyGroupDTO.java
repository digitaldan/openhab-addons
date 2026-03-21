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

/**
 * DTO for ESPSomfy group data from both HTTP API and WebSocket events.
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ESPSomfyGroupDTO {

    public int groupId;
    public String name = "";
    public int roomId = -1;
    public long remoteAddress;
    public int position;
    public int direction;
    public int myPos = -1;
    public boolean sunSensor;
    public int flags;
    public int[] shades = new int[0];
    public int lastRollingCode;
    public int bitLength;
    public int proto;
}
