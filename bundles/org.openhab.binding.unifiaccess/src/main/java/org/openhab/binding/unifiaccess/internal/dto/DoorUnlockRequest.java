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

import java.util.Map;

/**
 * Request body for remote door unlock (optional actor + passthrough extra).
 *
 * @author Dan Cunningham - Initial contribution
 */
public class DoorUnlockRequest {
    public String actorId;
    public String actorName;
    public Map<String, Object> extra;

    public DoorUnlockRequest(String actorId, String actorName, Map<String, Object> extra) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.extra = extra;
    }
}
