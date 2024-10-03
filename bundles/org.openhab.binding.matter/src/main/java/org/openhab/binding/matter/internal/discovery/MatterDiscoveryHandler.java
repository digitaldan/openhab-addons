/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.matter.internal.discovery;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Dan Cunningham
 *
 */
public interface MatterDiscoveryHandler {
    /**
     * Sets a {@link MatterDiscoveryService} to call when device information is received
     *
     * @param service
     */
    void setDiscoveryService(MatterDiscoveryService service);

    void startScan(@Nullable String code);
}
