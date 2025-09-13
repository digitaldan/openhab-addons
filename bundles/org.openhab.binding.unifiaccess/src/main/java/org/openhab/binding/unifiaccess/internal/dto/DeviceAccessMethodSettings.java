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
 * Device access-method settings (Section 8.2).
 *
 * <p>
 * Fetched from <code>/api/v1/developer/devices/:device_id/settings</code>.
 * The API returns booleans as strings ("true"/"false"), so helpers expose
 * null-safe boolean views.
 * </p>
 *
 * @author Dan Cunningham - Initial contribution
 */
public class DeviceAccessMethodSettings {
    public Nfc nfc;
    public Bt btTap;
    public Bt btButton;
    public Bt btShake;
    public MobileWave mobileWave;
    public PinCode pinCode;
    public Face face;

    /** Common base for methods with a single enabled field. */
    public static abstract class EnabledFlag {
        public String enabled;

        public boolean isEnabled() {
            return toBool(enabled);
        }
    }

    /** NFC access method. */
    public static class Nfc extends EnabledFlag {
    }

    /** Generic Bluetooth-based methods (Tap / Button / Shake). */
    public static class Bt extends EnabledFlag {
    }

    /** Mobile Wave method. */
    public static class MobileWave extends EnabledFlag {
    }

    /** PIN settings (enable + shuffle). */
    public static class PinCode extends EnabledFlag {
        public String pinCodeShuffle;

        public boolean isShuffleEnabled() {
            return toBool(pinCodeShuffle);
        }
    }

    /** Face Unlock. */
    public static class Face extends EnabledFlag {
    }

    /* ---------- helpers ---------- */
    private static boolean toBool(String s) {
        return "true".equalsIgnoreCase(s);
    }
}
