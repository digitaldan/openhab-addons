/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.grandstreamgds.internal;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Dan Cunningham
 *
 */
public enum GDSResponseCode {

    SUCCESS(0, "Success"),
    USER_DOES_NOT_EXIST(1, "User does not exist"),
    PASSWORD_ERROR(2, "Password error"),
    MAX_LOGIN_OR_MAX_WHITE_LIST_NUMBER(3, "Max login or max white list number"),
    PHONE_NUMBER_EXIST(4, "Phone number exist"),
    PARAMETER_SHOULD_NOT_BE_EMPTY(5, "Parameter should not be empty"),
    PARAMETER_CONTAIN_ILLEGAL_CHARACTERS(6, "Parameter contain illegal characters"),
    MESSAGE_SENDING_ERROR(7, "Message sending error"),
    NEW_PASSWORD_NOT_MATCH(8, "New password not match"),
    FTP_TEST_ERROR(9, "FTP test error"),
    HOSTAGE_PASSWD(10, "Hostage passwd"),
    REMOTE_LOCAL_PIN_NOT_SAME_WITH_HOSTAGE_PASSWORD(11, "Remote /Local PIN not same with hostage password"),
    SMTP_TIMEOUT(12, "SMTP timeout"),
    RETRIEVE_PASSWORD_NO_ACCOUNT(13, "Retrieve Password, Account does not exist"),
    SMTP_AUTHENTICATION_FAILED(14, "SMTP authentication failed"),
    SMTP_TEST_FAILED(15, "SMTP test failed"),
    INVALID_SMTP_SERVER(16, "Invalid SMTP server"),
    EMAIL_ADDRESS_IS_EMPTY(17, "Email address is empty"),
    EMAIL_TEST_TIMEOUT(18, "Email test timeout"),
    SIP_IS_CALLING(19, "SIP is calling"),
    AUTHENTICATION_FAILED(401, "Authentication failed"),
    SESSION_TIME_OUT(408, "Session time out"),
    UNKNOWN(-1, "Unknown");

    private final Integer code;
    private final String description;

    private static final Map<Integer, GDSResponseCode> typeCache = new HashMap<>(values().length, 1);

    static {
        for (GDSResponseCode e : values()) {
            typeCache.put(e.code, e);
        }
    }

    GDSResponseCode(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return this.code;
    }

    public String getDescription() {
        return this.description;
    }

    public static GDSResponseCode fromCode(String code) {
        try {
            GDSResponseCode rc = typeCache.get(Integer.parseInt(code));
            if (rc != null) {
                return rc;
            }
        } catch (NumberFormatException ignored) {
        }
        return UNKNOWN;
    }
}
