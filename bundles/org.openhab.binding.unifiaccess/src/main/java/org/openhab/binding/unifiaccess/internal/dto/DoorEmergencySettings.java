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
 * Door emergency settings payload for get/set endpoints.
 *
 * <p>
 * Booleans are represented as strings ("true"/"false") in some responses,
 * so helpers expose null-safe boolean views.
 * </p>
 *
 * @author Dan Cunningham - Initial contribution
 */
public class DoorEmergencySettings {
    /** Building-wide evacuation mode flag ("true"/"false"). */
    public String evacuation;

    /** Lockdown mode flag ("true"/"false"). */
    public String lockdown;

    public boolean isEvacuationEnabled() {
        return "true".equalsIgnoreCase(evacuation);
    }

    public boolean isLockdownEnabled() {
        return "true".equalsIgnoreCase(lockdown);
    }
}
