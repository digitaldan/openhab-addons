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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.UnifiProtectBindingConstants;
import org.openhab.binding.unifiprotect.internal.api.UniFiProtectApiClient;
import org.openhab.binding.unifiprotect.internal.config.UnifiProtectDeviceConfiguration;
import org.openhab.binding.unifiprotect.internal.dto.ApiValueEnum;
import org.openhab.binding.unifiprotect.internal.dto.Camera;
import org.openhab.binding.unifiprotect.internal.dto.CameraFeatureFlags;
import org.openhab.binding.unifiprotect.internal.dto.Device;
import org.openhab.binding.unifiprotect.internal.dto.HdrType;
import org.openhab.binding.unifiprotect.internal.dto.LedSettings;
import org.openhab.binding.unifiprotect.internal.dto.OsdSettings;
import org.openhab.binding.unifiprotect.internal.dto.VideoMode;
import org.openhab.binding.unifiprotect.internal.dto.events.BaseEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.CameraSmartDetectAudioEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.CameraSmartDetectLineEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.CameraSmartDetectLoiterEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.CameraSmartDetectZoneEvent;
import org.openhab.binding.unifiprotect.internal.dto.events.RingEvent;
import org.openhab.core.library.CoreItemFactory;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.type.ChannelKind;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

/**
 * Child handler for a UniFi Protect Camera.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UnifiProtectCameraHandler extends UnifiProtectAbstractDeviceHandler {
    private final Logger logger = LoggerFactory.getLogger(UnifiProtectCameraHandler.class);
    private String deviceId = "";
    private @Nullable Camera camera;

    public UnifiProtectCameraHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        deviceId = getConfigAs(UnifiProtectDeviceConfiguration.class).deviceId;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        String id = channelUID.getId();
        if (command instanceof RefreshType) {
            // State refresh: re-emit current state where possible
            Camera cam = camera;
            if (cam == null) {
                return;
            }
            switch (id) {
                case UnifiProtectBindingConstants.CHANNEL_MIC_VOLUME:
                    updateIntegerChannel(id, cam.micVolume);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_HDR_TYPE:
                    if (cam.hdrType != null) {
                        updateApiValueChannel(id, cam.hdrType);
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_VIDEO_MODE:
                    if (cam.videoMode != null) {
                        updateApiValueChannel(id, cam.videoMode);
                    }
                    return;
                case UnifiProtectBindingConstants.CHANNEL_OSD_NAME:
                    updateBooleanChannel(id, cam.osdSettings != null ? cam.osdSettings.isNameEnabled : null);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_OSD_DATE:
                    updateBooleanChannel(id, cam.osdSettings != null ? cam.osdSettings.isDateEnabled : null);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_OSD_LOGO:
                    updateBooleanChannel(id, cam.osdSettings != null ? cam.osdSettings.isLogoEnabled : null);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_LED_ENABLED:
                    updateBooleanChannel(id, cam.ledSettings != null ? cam.ledSettings.isEnabled : null);
                    return;
                case UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT:
                    updateIntegerChannel(id, cam.activePatrolSlot);
                    return;
                default:
                    return;
            }
        }

        UniFiProtectApiClient api = getApiClient();
        // Camera cam = camera;
        if (api == null) {
            return;
        }

        try {
            switch (id) {
                case UnifiProtectBindingConstants.CHANNEL_MIC_VOLUME: {
                    int volume;
                    try {
                        volume = ((DecimalType) command).intValue();
                    } catch (Exception e) {
                        break;
                    }
                    volume = Math.max(0, Math.min(100, volume));
                    JsonObject patch = UniFiProtectApiClient.buildPatch("micVolume", volume);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_HDR_TYPE: {
                    String value = command.toString();
                    JsonObject patch = UniFiProtectApiClient.buildPatch("hdrType", value);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_VIDEO_MODE: {
                    String value = command.toString();
                    JsonObject patch = UniFiProtectApiClient.buildPatch("videoMode", value);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_OSD_NAME: {
                    boolean value = OnOffType.ON.equals(command);
                    JsonObject patch = UniFiProtectApiClient.buildPatch("osdSettings.isNameEnabled", value);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_OSD_DATE: {
                    boolean value = OnOffType.ON.equals(command);
                    JsonObject patch = UniFiProtectApiClient.buildPatch("osdSettings.isDateEnabled", value);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_OSD_LOGO: {
                    boolean value = OnOffType.ON.equals(command);
                    JsonObject patch = UniFiProtectApiClient.buildPatch("osdSettings.isLogoEnabled", value);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_LED_ENABLED: {
                    boolean value = OnOffType.ON.equals(command);
                    JsonObject patch = UniFiProtectApiClient.buildPatch("ledSettings.isEnabled", value);
                    Camera updated = api.patchCamera(deviceId, patch);
                    updateFromDevice(updated);
                    break;
                }
                case UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT: {
                    int slot;
                    try {
                        slot = ((DecimalType) command).intValue();
                    } catch (Exception e) {
                        break;
                    }
                    if (slot <= 0) {
                        api.ptzPatrolStop(deviceId);
                        updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT, 0);
                    } else {
                        api.ptzPatrolStart(deviceId, String.valueOf(slot));
                        updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT, slot);
                    }
                    break;
                }
                default:
                    break;
            }
        } catch (Exception e) {
            logger.debug("Error handling command", e);
        }
    }

    @Override
    public void updateFromDevice(Device device) {
        if (device instanceof Camera camera) {
            this.camera = camera;
            // Ensure dynamic channels reflect camera capabilities
            addRemoveChannels(camera);
            if (getThing().getStatus() != ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            }

            // Update initial states for available channels
            updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_MIC_VOLUME, camera.micVolume);

            OsdSettings osd = camera.osdSettings;
            if (osd != null) {
                updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_OSD_NAME, osd.isNameEnabled);
                updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_OSD_DATE, osd.isDateEnabled);
                updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_OSD_LOGO, osd.isLogoEnabled);
            }

            LedSettings led = camera.ledSettings;
            if (led != null) {
                updateBooleanChannel(UnifiProtectBindingConstants.CHANNEL_LED_ENABLED, led.isEnabled);
            }

            HdrType hdr = camera.hdrType;
            if (hdr != null) {
                updateApiValueChannel(UnifiProtectBindingConstants.CHANNEL_HDR_TYPE, hdr);
            }

            VideoMode videoMode = camera.videoMode;
            if (videoMode != null) {
                updateApiValueChannel(UnifiProtectBindingConstants.CHANNEL_VIDEO_MODE, videoMode);
            }

            updateIntegerChannel(UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT, camera.activePatrolSlot);
        }
    }

    @Override
    public void handleEvent(BaseEvent event, WSEventType eventType) {
        if (event.type == null) {
            return;
        }

        switch (event.type) {
            case CAMERA_MOTION:
                updateSnapshot(UnifiProtectBindingConstants.CHANNEL_MOTION_SNAPSHOT);
                // Trigger motion on start and end if channel exists
                if (hasChannel(UnifiProtectBindingConstants.CHANNEL_MOTION)) {
                    String channelId = eventType == WSEventType.ADD ? UnifiProtectBindingConstants.CHANNEL_MOTION_START
                            : UnifiProtectBindingConstants.CHANNEL_MOTION_END;
                    triggerChannel(new ChannelUID(thing.getUID(), channelId));
                    updateState(UnifiProtectBindingConstants.CHANNEL_MOTION_CONTACT,
                            eventType == WSEventType.ADD ? OnOffType.OFF : OnOffType.ON);
                }
                break;

            case SMART_AUDIO_DETECT:
                if (event instanceof CameraSmartDetectAudioEvent e) {
                    updateSnapshot(UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT);
                    String channelId = eventType == WSEventType.ADD
                            ? UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_START
                            : UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_END;
                    if (hasChannel(channelId)) {
                        if (e.smartDetectTypes == null || e.smartDetectTypes.isEmpty()) {
                            triggerChannel(new ChannelUID(thing.getUID(), channelId), "none");
                        } else {
                            for (ApiValueEnum type : e.smartDetectTypes) {
                                triggerChannel(new ChannelUID(thing.getUID(), channelId), type.getApiValue());
                            }
                        }
                        updateState(UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_CONTACT,
                                eventType == WSEventType.ADD ? OnOffType.OFF : OnOffType.ON);
                    }
                }
                break;

            case SMART_DETECT_ZONE:
                if (event instanceof CameraSmartDetectZoneEvent e) {
                    updateSnapshot(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_SNAPSHOT);
                    String channelId = eventType == WSEventType.ADD
                            ? UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_START
                            : UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_END;
                    if (hasChannel(channelId)) {
                        if (e.smartDetectTypes == null || e.smartDetectTypes.isEmpty()) {
                            triggerChannel(new ChannelUID(thing.getUID(), channelId), "none");
                        } else {
                            for (ApiValueEnum type : e.smartDetectTypes) {
                                triggerChannel(new ChannelUID(thing.getUID(), channelId), type.getApiValue());
                            }
                        }
                        updateState(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_CONTACT,
                                eventType == WSEventType.ADD ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                    }
                }
                break;

            case SMART_DETECT_LINE:
                if (event instanceof CameraSmartDetectLineEvent e) {
                    updateSnapshot(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_SNAPSHOT);
                    String channelId = eventType == WSEventType.ADD
                            ? UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_START
                            : UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_END;
                    if (hasChannel(channelId)) {
                        if (e.smartDetectTypes == null || e.smartDetectTypes.isEmpty()) {
                            triggerChannel(new ChannelUID(thing.getUID(), channelId), "none");
                        } else {
                            for (ApiValueEnum type : e.smartDetectTypes) {
                                triggerChannel(new ChannelUID(thing.getUID(), channelId), type.getApiValue());
                            }
                        }
                        updateState(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_CONTACT,
                                eventType == WSEventType.ADD ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                    }
                }
                break;

            case SMART_DETECT_LOITER_ZONE:
                if (event instanceof CameraSmartDetectLoiterEvent e) {
                    updateSnapshot(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_SNAPSHOT);
                    String channelId = eventType == WSEventType.ADD
                            ? UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_START
                            : UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_END;
                    if (hasChannel(channelId)) {
                        if (e.smartDetectTypes == null || e.smartDetectTypes.isEmpty()) {
                            triggerChannel(new ChannelUID(thing.getUID(), channelId), "none");
                        } else {
                            for (ApiValueEnum type : e.smartDetectTypes) {
                                triggerChannel(new ChannelUID(thing.getUID(), channelId), type.getApiValue());
                            }
                        }
                        updateState(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_CONTACT,
                                eventType == WSEventType.ADD ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                    }
                }
                break;
            case RING:
                if (event instanceof RingEvent && hasChannel(UnifiProtectBindingConstants.CHANNEL_RING)) {
                    updateSnapshot(UnifiProtectBindingConstants.CHANNEL_RING_SNAPSHOT);
                    triggerChannel(new ChannelUID(thing.getUID(), UnifiProtectBindingConstants.CHANNEL_RING),
                            event.end == null ? "PRESSED" : "RELEASED");
                    updateState(UnifiProtectBindingConstants.CHANNEL_RING_CONTACT,
                            eventType == WSEventType.ADD ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                }
                break;

            default:
                // ignore other event types in camera handler
                break;
        }
    }

    private void addRemoveChannels(Camera camera) {
        // List<Channel> channelAdd = new ArrayList<>(thing.getChannels());

        List<Channel> channelRemove = new ArrayList<>();
        for (Channel existing : thing.getChannels()) {
            channelRemove.add(existing);
        }
        updateThing(editThing().withoutChannels(channelRemove).build());
        List<Channel> channelAdd = new ArrayList<>();

        // Desired set accumulates all channels that should exist after this call
        Set<String> desiredIds = new HashSet<>();

        addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_MOTION_START,
                UnifiProtectBindingConstants.CHANNEL_MOTION, channelAdd, desiredIds);
        addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_MOTION_END, UnifiProtectBindingConstants.CHANNEL_MOTION,
                channelAdd, desiredIds);
        addChannel(UnifiProtectBindingConstants.CHANNEL_MOTION_CONTACT, CoreItemFactory.CONTACT,
                UnifiProtectBindingConstants.CHANNEL_MOTION_CONTACT, channelAdd, desiredIds);
        addChannel(UnifiProtectBindingConstants.CHANNEL_MOTION_SNAPSHOT, CoreItemFactory.IMAGE,
                UnifiProtectBindingConstants.CHANNEL_SNAPSHOT, channelAdd, desiredIds);

        CameraFeatureFlags flags = camera.featureFlags;
        if (flags != null) {
            // Mic
            if (flags.hasMic) {
                addChannel(UnifiProtectBindingConstants.CHANNEL_MIC_VOLUME, CoreItemFactory.NUMBER,
                        UnifiProtectBindingConstants.CHANNEL_MIC_VOLUME, channelAdd, desiredIds);
            }
            // LED
            if (flags.hasLedStatus) {
                addChannel(UnifiProtectBindingConstants.CHANNEL_LED_ENABLED, CoreItemFactory.SWITCH,
                        UnifiProtectBindingConstants.CHANNEL_LED_ENABLED, channelAdd, desiredIds);
            }
            // HDR
            if (flags.hasHdr) {
                addChannel(UnifiProtectBindingConstants.CHANNEL_HDR_TYPE, CoreItemFactory.STRING,
                        UnifiProtectBindingConstants.CHANNEL_HDR_TYPE, channelAdd, desiredIds);
            }
            // Video modes
            if (flags.videoModes != null && !flags.videoModes.isEmpty()) {
                addChannel(UnifiProtectBindingConstants.CHANNEL_VIDEO_MODE, CoreItemFactory.STRING,
                        UnifiProtectBindingConstants.CHANNEL_VIDEO_MODE, channelAdd, desiredIds);
            }
            // Smart object detection
            if (flags.smartDetectTypes != null && !flags.smartDetectTypes.isEmpty()) {
                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_START,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE, channelAdd, desiredIds);
                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_END,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_CONTACT, CoreItemFactory.CONTACT,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_CONTACT, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_SNAPSHOT, CoreItemFactory.IMAGE,
                        UnifiProtectBindingConstants.CHANNEL_SNAPSHOT, channelAdd, desiredIds,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE_SNAPSHOT_LABEL);

                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_START,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE, channelAdd, desiredIds);
                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_END,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_CONTACT, CoreItemFactory.CONTACT,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_CONTACT, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_SNAPSHOT, CoreItemFactory.IMAGE,
                        UnifiProtectBindingConstants.CHANNEL_SNAPSHOT, channelAdd, desiredIds,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE_SNAPSHOT_LABEL);

                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_START,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER, channelAdd, desiredIds);
                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_END,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_CONTACT, CoreItemFactory.CONTACT,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_CONTACT, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_SNAPSHOT, CoreItemFactory.IMAGE,
                        UnifiProtectBindingConstants.CHANNEL_SNAPSHOT, channelAdd, desiredIds,
                        UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER_SNAPSHOT_LABEL);
            }
            // Smart audio detection
            if (flags.smartDetectAudioTypes != null && !flags.smartDetectAudioTypes.isEmpty()) {
                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_START,
                        UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT, channelAdd, desiredIds);
                addTriggerChannel(UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_END,
                        UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_CONTACT, CoreItemFactory.CONTACT,
                        UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_CONTACT, channelAdd, desiredIds);
                addChannel(UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT, CoreItemFactory.IMAGE,
                        UnifiProtectBindingConstants.CHANNEL_SNAPSHOT, channelAdd, desiredIds,
                        UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT_SNAPSHOT_LABEL);
            }
        }

        // OSD related if settings present
        if (camera.osdSettings != null) {
            addChannel(UnifiProtectBindingConstants.CHANNEL_OSD_NAME, CoreItemFactory.SWITCH,
                    UnifiProtectBindingConstants.CHANNEL_OSD_NAME, channelAdd, desiredIds);
            addChannel(UnifiProtectBindingConstants.CHANNEL_OSD_DATE, CoreItemFactory.SWITCH,
                    UnifiProtectBindingConstants.CHANNEL_OSD_DATE, channelAdd, desiredIds);
            addChannel(UnifiProtectBindingConstants.CHANNEL_OSD_LOGO, CoreItemFactory.SWITCH,
                    UnifiProtectBindingConstants.CHANNEL_OSD_LOGO, channelAdd, desiredIds);
        }

        // PTZ patrol slot if present
        if (camera.activePatrolSlot != null) {
            addChannel(UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT, CoreItemFactory.NUMBER,
                    UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT, channelAdd, desiredIds);
        }

        // Compute removals by diffing existing managed channels against desired
        // for (Channel existing : thing.getChannels()) {
        // String id = existing.getUID().getId();
        // if (MANAGED_CHANNEL_IDS.contains(id) && !desiredIds.contains(id)) {
        // channelRemove.add(existing);
        // }
        // }

        // updateThing(editThing().withChannels(channelAdd).withoutChannels(channelRemove).build());
        updateThing(editThing().withChannels(channelAdd).build());
    }

    private void addChannel(String channelId, String itemType, String channelTypeId, List<Channel> channelAdd,
            Set<String> desiredIds) {
        addChannel(channelId, itemType, channelTypeId, channelAdd, desiredIds, null);
    }

    private void addChannel(String channelId, String itemType, String channelTypeId, List<Channel> channelAdd,
            Set<String> desiredIds, @Nullable String label) {
        ChannelUID uid = new ChannelUID(thing.getUID(), channelId);
        desiredIds.add(channelId);
        if (thing.getChannel(uid) == null) {
            ChannelBuilder builder = ChannelBuilder.create(uid, itemType)
                    .withType(new ChannelTypeUID(UnifiProtectBindingConstants.BINDING_ID, channelTypeId));
            if (label != null) {
                builder.withLabel(label);
            }
            Channel ch = builder.build();
            channelAdd.add(ch);
        }
    }

    private void addTriggerChannel(String channelId, String channelTypeId, List<Channel> channelAdd,
            Set<String> desiredIds) {
        ChannelUID uid = new ChannelUID(thing.getUID(), channelId);
        desiredIds.add(channelId);
        if (thing.getChannel(uid) == null) {
            Channel ch = ChannelBuilder.create(uid, null)
                    .withType(new ChannelTypeUID(UnifiProtectBindingConstants.BINDING_ID, channelTypeId))
                    .withKind(ChannelKind.TRIGGER).build();
            channelAdd.add(ch);
        }
    }

    private static final Set<String> MANAGED_CHANNEL_IDS = Set.of(UnifiProtectBindingConstants.CHANNEL_SNAPSHOT,
            UnifiProtectBindingConstants.CHANNEL_MOTION, UnifiProtectBindingConstants.CHANNEL_MIC_VOLUME,
            UnifiProtectBindingConstants.CHANNEL_LED_ENABLED, UnifiProtectBindingConstants.CHANNEL_HDR_TYPE,
            UnifiProtectBindingConstants.CHANNEL_VIDEO_MODE, UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_ZONE,
            UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LINE,
            UnifiProtectBindingConstants.CHANNEL_SMART_DETECT_LOITER,
            UnifiProtectBindingConstants.CHANNEL_SMART_AUDIO_DETECT, UnifiProtectBindingConstants.CHANNEL_OSD_NAME,
            UnifiProtectBindingConstants.CHANNEL_OSD_DATE, UnifiProtectBindingConstants.CHANNEL_OSD_LOGO,
            UnifiProtectBindingConstants.CHANNEL_ACTIVE_PATROL_SLOT);

    private void updateSnapshot(String channelId) {
        if (hasChannel(channelId)) {
            UniFiProtectApiClient client = getApiClient();
            if (client != null) {
                try {
                    updateState(channelId, new RawType(client.getSnapshot(deviceId, true), "image/jpeg"));
                } catch (IOException e) {
                    logger.debug("Error getting snapshot", e);
                }
            }

        }
    }
}
