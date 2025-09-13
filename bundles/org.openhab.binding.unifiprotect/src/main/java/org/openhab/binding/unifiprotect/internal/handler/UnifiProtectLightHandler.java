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
package org.openhab.binding.unifiprotect.internal.handler;

import java.io.IOException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.UnifiProtectBindingConstants;
import org.openhab.binding.unifiprotect.internal.api.UniFiProtectApiClient;
import org.openhab.binding.unifiprotect.internal.config.UnifiProtectDeviceConfiguration;
import org.openhab.binding.unifiprotect.internal.dto.Device;
import org.openhab.binding.unifiprotect.internal.dto.Light;
import org.openhab.binding.unifiprotect.internal.dto.LightDeviceSettings;
import org.openhab.binding.unifiprotect.internal.dto.LightModeSettings;
import org.openhab.binding.unifiprotect.internal.dto.events.BaseEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.EventType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Child handler for a UniFi Protect Floodlight.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiProtectLightHandler extends UnifiProtectAbstractDeviceHandler {

    private final Logger logger = LoggerFactory.getLogger(UnifiProtectLightHandler.class);
    private String deviceId = "";
    private volatile @Nullable Light light;

    public UnifiProtectLightHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        deviceId = getConfigAs(UnifiProtectDeviceConfiguration.class).deviceId;
    }

    @Override
    public void handleEvent(BaseEvent event, WSEventType eventType) {
        if (event.type == null) {
            return;
        }

        if (event.type == EventType.LIGHT_MOTION) {
            // Trigger PIR motion event and update last motion timestamp
            if (hasChannel(UnifiProtectBindingConstants.CHANNEL_PIR_MOTION)) {
                triggerChannel(new ChannelUID(thing.getUID(), UnifiProtectBindingConstants.CHANNEL_PIR_MOTION));
            }
            if (event.start != null) {
                updateDateTimeChannel(UnifiProtectBindingConstants.CHANNEL_LAST_MOTION, event.start);
            }
        }
    }

    public void updateFromDevice(Device device) {
        if (device instanceof Light light) {
            this.light = light;
            this.device = light;

            // Update simple booleans
            updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_IS_DARK, light.isDark);
            updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_LIGHT, light.isLightOn);

            // Update last motion if available
            if (light.lastMotion != null) {
                updateDateTimeChannel(UnifiProtectBindingConstants.CHANNEL_LAST_MOTION, light.lastMotion);
            }

            // Update mode settings
            LightModeSettings lms = light.lightModeSettings;
            if (lms != null) {
                if (lms.mode != null) {
                    updateStringChannel(UnifiProtectBindingConstants.CHANNEL_LIGHT_MODE, lms.mode.getApiValue());
                }
                if (lms.enableAt != null) {
                    updateStringChannel(UnifiProtectBindingConstants.CHANNEL_ENABLE_AT, lms.enableAt.getApiValue());
                }
            }

            // Update device settings
            LightDeviceSettings lds = light.lightDeviceSettings;
            if (lds != null) {
                updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_INDICATOR_ENABLED, lds.isIndicatorEnabled);
                updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_PIR_DURATION, lds.pirDuration);
                updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_PIR_SENSITIVITY, lds.pirSensitivity);
                updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_LED_LEVEL, lds.ledLevel);
            }
        }
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String id = channelUID.getId();

        // RefreshType handling - emit current state
        if (command instanceof RefreshType) {
            Light l = light;
            if (l == null) {
                return;
            }
            switch (id) {
                case UnifiProtectBindingConstants.CHANNEL_LIGHT:
                    updateBooleanChannel(id, l.isLightOn);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_IS_DARK:
                    updateBooleanChannel(id, l.isDark);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_LAST_MOTION:
                    if (l.lastMotion != null) {
                        updateDateTimeChannel(id, l.lastMotion);
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_LIGHT_MODE:
                    if (l.lightModeSettings != null && l.lightModeSettings.mode != null) {
                        updateStringChannel(id, l.lightModeSettings.mode.getApiValue());
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_ENABLE_AT:
                    if (l.lightModeSettings != null && l.lightModeSettings.enableAt != null) {
                        updateStringChannel(id, l.lightModeSettings.enableAt.getApiValue());
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_INDICATOR_ENABLED:
                    if (l.lightDeviceSettings != null) {
                        updateBooleanChannel(id, l.lightDeviceSettings.isIndicatorEnabled);
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_PIR_DURATION:
                    if (l.lightDeviceSettings != null) {
                        updateIntegerChannel(id, l.lightDeviceSettings.pirDuration);
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_PIR_SENSITIVITY:
                    if (l.lightDeviceSettings != null) {
                        updateIntegerChannel(id, l.lightDeviceSettings.pirSensitivity);
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_LED_LEVEL:
                    if (l.lightDeviceSettings != null) {
                        updateIntegerChannel(id, l.lightDeviceSettings.ledLevel);
                    }
                    return;
                default:
                    return;
            }
        }

        UniFiProtectApiClient api = getApiClient();
        if (api == null) {
            return;
        }

        try {
            switch (id) {
                case UnifiProtectBindingConstants.CHANNEL_LIGHT: {
                    boolean value = OnOffType.ON.equals(command);
                    // Force light on/off
                    var patch = UniFiProtectApiClient.buildPatch("isLightForceEnabled", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_LIGHT_MODE: {
                    String value = command.toString();
                    var patch = UniFiProtectApiClient.buildPatch("lightModeSettings.mode", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_ENABLE_AT: {
                    String value = command.toString();
                    var patch = UniFiProtectApiClient.buildPatch("lightModeSettings.enableAt", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_INDICATOR_ENABLED: {
                    boolean value = OnOffType.ON.equals(command);
                    var patch = UniFiProtectApiClient.buildPatch("lightDeviceSettings.isIndicatorEnabled", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_PIR_DURATION: {
                    int value;
                    try {
                        value = ((DecimalType) command).intValue();
                    } catch (Exception e) {
                        break;
                    }
                    var patch = UniFiProtectApiClient.buildPatch("lightDeviceSettings.pirDuration", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_PIR_SENSITIVITY: {
                    int value;
                    try {
                        value = ((DecimalType) command).intValue();
                    } catch (Exception e) {
                        break;
                    }
                    value = Math.max(0, Math.min(100, value));
                    var patch = UniFiProtectApiClient.buildPatch("lightDeviceSettings.pirSensitivity", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_LED_LEVEL: {
                    int value;
                    try {
                        value = ((DecimalType) command).intValue();
                    } catch (Exception e) {
                        break;
                    }
                    value = Math.max(1, Math.min(6, value));
                    var patch = UniFiProtectApiClient.buildPatch("lightDeviceSettings.ledLevel", value);
                    Light updated = api.patchLight(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                // Read-only channels - ignore commands
                case UnifiProtectBindingConstants.CHANNEL_IS_DARK:
                case UnifiProtectBindingConstants.CHANNEL_PIR_MOTION:
                case UnifiProtectBindingConstants.CHANNEL_LAST_MOTION:
                default:
                    break;
            }
        } catch (IOException e) {
            logger.debug("Error handling light command", e);
        }
    }
}
