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
package org.openhab.binding.smartmeter;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link SmartMeterConfiguration} is the class used to match the
 * thing configuration.
 *
 * @author Matthias Steigenberger - Initial contribution
 */
@NonNullByDefault
public class SmartMeterConfiguration {

    @Nullable
    public String port;
    public Integer refresh = 10;
    public Integer baudrateChangeDelay = 0;
    @Nullable
    public String initMessage;
    public String baudrate = "AUTO";
    public String mode = "SML";
    public String conformity = "NONE";
}
