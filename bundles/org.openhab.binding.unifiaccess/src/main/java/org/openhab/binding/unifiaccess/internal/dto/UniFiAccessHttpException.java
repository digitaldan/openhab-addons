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
 * HTTP error wrapper for UniFi Access client.
 *
 * @author Dan Cunningham - Initial contribution
 */
public class UniFiAccessHttpException extends RuntimeException {
    private final int status;

    public UniFiAccessHttpException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    public UniFiAccessHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
