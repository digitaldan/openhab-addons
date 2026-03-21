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
package org.openhab.binding.espsomfy.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyGroupDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyShadeDTO;

/**
 * Listener interface for ESPSomfy WebSocket events.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public interface ESPSomfyWebSocketListener {

    /**
     * Called when a shade state update is received.
     */
    void onShadeStateChanged(ESPSomfyShadeDTO shade);

    /**
     * Called when a group state update is received.
     */
    void onGroupStateChanged(ESPSomfyGroupDTO group);

    /**
     * Called when the WebSocket connection is closed.
     */
    void onWebSocketClose();

    /**
     * Called when a WebSocket error occurs.
     */
    void onWebSocketError(Throwable cause);
}
