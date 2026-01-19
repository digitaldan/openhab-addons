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
package org.openhab.binding.unifiprotect.internal.handler;

import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.unifiprotect.internal.UnifiProtectBindingConstants;
import org.openhab.binding.unifiprotect.internal.api.UniFiProtectHybridClient;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.Chime;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.events.BaseEvent;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.types.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link UnifiProtectChimeHandler} is responsible for handling commands for UniFi Protect chimes.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiProtectChimeHandler extends UnifiProtectAbstractDeviceHandler<Chime> {

    private final Logger logger = LoggerFactory.getLogger(UnifiProtectChimeHandler.class);

    public UnifiProtectChimeHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            try {
                UniFiProtectHybridClient client = getApiClient();
                if (client == null) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "No API client available");
                    return;
                }

                if (!client.isPrivateApiEnabled()) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                            "Chime requires Private API to be enabled");
                    return;
                }

                updateStatus(ThingStatus.ONLINE);
                updateFromPrivateApi();
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                logger.debug("Error initializing chime", e);
            }
        });
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String channelId = channelUID.getId();
        UniFiProtectHybridClient api = getApiClient();

        if (api == null || !api.isPrivateApiEnabled()) {
            logger.debug("Private API not available for chime command");
            return;
        }

        try {
            switch (channelId) {
                case UnifiProtectBindingConstants.CHANNEL_PLAY_CHIME:
                    if (command == OnOffType.ON) {
                        // Get current volume and repeat times from the chime
                        api.getPrivateChime(deviceId).thenAccept(chime -> {
                            Integer volume = chime.volume != null ? chime.volume : 100;
                            Integer repeatTimes = chime.repeatTimes != null ? chime.repeatTimes : 1;

                            api.playChime(deviceId, volume, repeatTimes).thenRun(() -> {
                                logger.debug("Playing chime");
                                // Reset the switch after playing starts
                                scheduler.schedule(() -> {
                                    updateState(channelUID, OnOffType.OFF);
                                }, 1, TimeUnit.SECONDS);
                            }).exceptionally(ex -> {
                                logger.warn("Failed to play chime", ex);
                                return null;
                            });
                        }).exceptionally(ex -> {
                            logger.warn("Failed to get chime data", ex);
                            return null;
                        });
                    }
                    break;

                case UnifiProtectBindingConstants.CHANNEL_PLAY_BUZZER:
                    if (command == OnOffType.ON) {
                        api.playChimeBuzzer(deviceId).thenRun(() -> {
                            logger.debug("Playing buzzer");
                            // Reset the switch after playing starts
                            scheduler.schedule(() -> {
                                updateState(channelUID, OnOffType.OFF);
                            }, 1, TimeUnit.SECONDS);
                        }).exceptionally(ex -> {
                            logger.warn("Failed to play buzzer", ex);
                            return null;
                        });
                    }
                    break;

                case UnifiProtectBindingConstants.CHANNEL_CHIME_VOLUME:
                    if (command instanceof PercentType percentCmd) {
                        int volume = percentCmd.intValue();
                        api.setChimeVolume(deviceId, volume).thenAccept(updatedChime -> {
                            logger.debug("Set chime volume to {}", volume);
                            updateState(channelUID, new PercentType(volume));
                        }).exceptionally(ex -> {
                            logger.warn("Failed to set chime volume", ex);
                            return null;
                        });
                    } else if (command instanceof DecimalType decimalCmd) {
                        int volume = decimalCmd.intValue();
                        api.setChimeVolume(deviceId, volume).thenAccept(updatedChime -> {
                            logger.debug("Set chime volume to {}", volume);
                            updateState(channelUID, new PercentType(volume));
                        }).exceptionally(ex -> {
                            logger.warn("Failed to set chime volume", ex);
                            return null;
                        });
                    }
                    break;

                case UnifiProtectBindingConstants.CHANNEL_CHIME_REPEAT_TIMES:
                    if (command instanceof DecimalType decimalCmd) {
                        int repeatTimes = decimalCmd.intValue();
                        api.setChimeRepeatTimes(deviceId, repeatTimes).thenAccept(updatedChime -> {
                            logger.debug("Set chime repeat times to {}", repeatTimes);
                            updateState(channelUID, new DecimalType(repeatTimes));
                        }).exceptionally(ex -> {
                            logger.warn("Failed to set chime repeat times", ex);
                            return null;
                        });
                    }
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            logger.debug("Error handling command", e);
        }
    }

    @Override
    public void updateFromDevice(org.openhab.binding.unifiprotect.internal.api.pub.dto.Chime device) {
        // The public API doesn't support chimes
        // All updates come from Private API
        updateFromPrivateApi();
    }

    /**
     * Fetch and update all chime channels from Private API
     */
    private void updateFromPrivateApi() {
        UniFiProtectHybridClient api = getApiClient();
        if (api == null || !api.isPrivateApiEnabled()) {
            return;
        }

        try {
            // Fetch chime data from Private API
            api.getPrivateChime(deviceId).thenAccept(chime -> {
                scheduler.execute(() -> {
                    updateChimeChannels(chime);
                });
            }).exceptionally(ex -> {
                logger.debug("Failed to fetch Private API chime status", ex);
                return null;
            });
        } catch (Exception e) {
            logger.debug("Error updating Private API chime status", e);
        }
    }

    /**
     * Update chime channels from Private API data
     */
    public void updateChimeChannels(org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Chime chime) {
        if (chime == null) {
            return;
        }

        scheduler.execute(() -> {
            // Device properties
            if (chime.name != null) {
                updateProperty(UnifiProtectBindingConstants.PROPERTY_NAME, chime.name);
            }
            if (chime.marketName != null) {
                updateProperty(UnifiProtectBindingConstants.PROPERTY_MODEL, chime.marketName);
            } else if (chime.type != null) {
                updateProperty(UnifiProtectBindingConstants.PROPERTY_MODEL, chime.type);
            }
            if (chime.firmwareVersion != null) {
                updateProperty(UnifiProtectBindingConstants.PROPERTY_FIRMWARE_VERSION, chime.firmwareVersion);
            }
            if (chime.mac != null) {
                updateProperty(UnifiProtectBindingConstants.PROPERTY_MAC_ADDRESS, chime.mac);
            }
            if (chime.host != null) {
                updateProperty(UnifiProtectBindingConstants.PROPERTY_IP_ADDRESS, chime.host);
            }

            // Volume
            if (chime.volume != null) {
                updateState(UnifiProtectBindingConstants.CHANNEL_CHIME_VOLUME, new PercentType(chime.volume));
            }

            // Repeat times
            if (chime.repeatTimes != null) {
                updateState(UnifiProtectBindingConstants.CHANNEL_CHIME_REPEAT_TIMES,
                        new DecimalType(chime.repeatTimes));
            }

            // Update thing status
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }
        });
    }

    @Override
    public void handleEvent(BaseEvent event, WSEventType type) {
        // Chimes don't have events in the public API
        // All updates come through Private API WebSocket
    }

    public void refresh() {
        updateFromPrivateApi();
    }
}
