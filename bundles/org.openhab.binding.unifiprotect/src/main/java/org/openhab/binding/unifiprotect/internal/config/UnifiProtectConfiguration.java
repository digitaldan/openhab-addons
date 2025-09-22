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
package org.openhab.binding.unifiprotect.internal.config;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link UnifiProtectConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiProtectConfiguration {
    public boolean downloadBinaries = true;
    public String ffmpegOptions = "-use_wallclock_as_timestamps 1 -re -fflags nobuffer -f alaw -ar 8000 -ac 1 -i - -vn -b:a 32k -application voip -frame_duration 20";
}
