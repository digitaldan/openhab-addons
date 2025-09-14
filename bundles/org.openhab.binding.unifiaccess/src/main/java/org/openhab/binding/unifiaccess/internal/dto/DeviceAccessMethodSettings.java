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

    public static abstract class EnabledFlag {
        public Boolean enabled;
    }

    public static class Nfc extends EnabledFlag {
    }

    public static class Bt extends EnabledFlag {
    }

    public static class MobileWave extends EnabledFlag {
    }

    public static class PinCode extends EnabledFlag {
        public Boolean pinCodeShuffle;
    }

    public static class Face extends EnabledFlag {
    }
}
