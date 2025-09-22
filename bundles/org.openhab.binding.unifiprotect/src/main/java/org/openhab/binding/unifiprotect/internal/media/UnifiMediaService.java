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
package org.openhab.binding.unifiprotect.internal.media;

import java.net.URI;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.handler.UnifiProtectCameraHandler;
import org.openhab.core.thing.ThingUID;

/**
 * Interface for the UnifiMediaService.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public interface UnifiMediaService {

    /** Register/replace a camera stream (Thing UID as stable ID). */
    void registerStream(String streamId, List<URI> sources);

    /** Remove a camera stream when Thing goes away. */
    void unregisterStream(String streamId);

    void registerHandler(UnifiProtectCameraHandler handler);

    void unregisterHandler(UnifiProtectCameraHandler handler);

    @Nullable
    UnifiProtectCameraHandler getHandler(ThingUID thingUID);

    /** Health/Liveness. */
    boolean isHealthy();

    String getPlayBasePath();

    String getImageBasePath();
}
