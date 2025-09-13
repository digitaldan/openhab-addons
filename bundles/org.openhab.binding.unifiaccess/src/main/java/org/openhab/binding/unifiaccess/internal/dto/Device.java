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
package org.openhab.binding.unifiaccess.internal.dto;

/**
 * Device (Section 8 "Device"): basic identity for Access devices.
 *
 * <p>
 * Returned by <code>/api/v1/developer/devices</code> with
 * <code>alias</code>, <code>id</code>, <code>name</code>, and <code>type</code>.
 * </p>
 *
 * @author Dan Cunningham - Initial contribution
 */
public class Device {
    public String alias;
    public String id;
    public String name;
    public String type; // e.g., "UAH", "UDA-LITE", "UA-G2-PRO"
}
