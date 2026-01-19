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
package org.openhab.binding.unifiprotect.internal.api.priv.dto.system;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * API response wrapper for API key operations
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ApiKeyResponse {
    public int code;

    @SerializedName("codeS")
    public String codeString;

    public String msg;
    public Object data; // Can be single ApiKey or List<ApiKey>

    public ApiKey getApiKey() {
        if (data instanceof ApiKey) {
            return (ApiKey) data;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<ApiKey> getApiKeys() {
        if (data instanceof List) {
            return (List<ApiKey>) data;
        }
        return List.of();
    }
}
