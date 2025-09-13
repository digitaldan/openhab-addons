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

import org.eclipse.jdt.annotation.Nullable;

/**
 * Generic API response wrapper: { "code": "SUCCESS" | <error_code>, "data": ..., "msg": "ok" }.
 *
 * <p>
 * Use as ApiResponse&lt;YourType&gt; for all endpoints that follow this pattern.
 * </p>
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ApiResponse<T> {
    public ApiResponseCode code;
    public @Nullable T data;
    public String msg;

    public boolean isSuccess() {
        return code == ApiResponseCode.SUCCESS;
    }

    /** Non-empty, human-friendly message (falls back to an empty string). */
    public String messageOrEmpty() {
        return msg == null ? "" : msg;
    }
}
