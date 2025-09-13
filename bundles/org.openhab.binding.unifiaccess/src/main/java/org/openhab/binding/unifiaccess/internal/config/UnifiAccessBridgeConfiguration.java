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
package org.openhab.binding.unifiaccess.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link UnifiAccessBridgeConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiAccessBridgeConfiguration {
    // Bridge configuration as defined in thing-types.xml
    public String baseUrl = ""; // e.g., https://ua.local/api/v1/developer
    public String authToken = ""; // X-Auth-Token or Bearer token
    public int port = 12445; // controller/API port to use with baseUrl
    public int requestTimeout = 15; // seconds
    public int pollInterval = 30; // seconds
}
