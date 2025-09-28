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

import java.time.Instant;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.unifiprotect.internal.api.UniFiProtectApiClient;
import org.openhab.binding.unifiprotect.internal.config.UnifiProtectDeviceConfiguration;
import org.openhab.binding.unifiprotect.internal.dto.ApiValueEnum;
import org.openhab.binding.unifiprotect.internal.dto.Device;
import org.openhab.binding.unifiprotect.internal.dto.events.BaseEvent;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;

/**
 * Abstract handler for all device types.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public abstract class UnifiProtectAbstractDeviceHandler<T extends Device> extends BaseThingHandler {
    protected @Nullable T device;
    protected String deviceId = "";

    public enum WSEventType {
        ADD,
        UPDATE
    }

    public UnifiProtectAbstractDeviceHandler(Thing thing) {
        super(thing);
    }

    public void updateFromDevice(T device) {
        this.device = device;
    }

    public abstract void handleEvent(BaseEvent event, WSEventType eventType);

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        deviceId = getConfigAs(UnifiProtectDeviceConfiguration.class).deviceId;
    }

    // making public
    @Override
    public void updateStatus(ThingStatus status) {
        super.updateStatus(status);
    }

    @Override
    public void updateStatus(ThingStatus status, ThingStatusDetail statusDetail, @Nullable String description) {
        super.updateStatus(status, statusDetail, description);
    }

    public void markGone() {
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.GONE, "GONE");
    }

    protected @Nullable UniFiProtectApiClient getApiClient() {
        Thing bridge = getBridge();
        if (bridge != null) {
            BaseThingHandler h = (BaseThingHandler) bridge.getHandler();
            if (h instanceof UnifiProtectNVRHandler) {
                return ((UnifiProtectNVRHandler) h).getApiClient();
            }
        }
        return null;
    }

    protected boolean hasChannel(String channelId) {
        return thing.getChannel(new ChannelUID(thing.getUID(), channelId)) != null;
    }

    protected void updateBooleanChannel(String channelId, @Nullable Boolean value) {
        if (value != null && hasChannel(channelId)) {
            updateState(channelId, value ? OnOffType.ON : OnOffType.OFF);
        }
    }

    protected void updateIntegerChannel(String channelId, @Nullable Integer value) {
        if (value != null && hasChannel(channelId)) {
            updateState(channelId, new DecimalType(value));
        }
    }

    protected void updateStringChannel(String channelId, @Nullable String value) {
        if (hasChannel(channelId)) {
            updateState(channelId, new StringType(value));
        }
    }

    protected void updateApiValueChannel(String channelId, ApiValueEnum value) {
        updateStringChannel(channelId, value.getApiValue());
    }

    protected void updateDateTimeChannel(String channelId, long epochMillis) {
        if (hasChannel(channelId)) {
            updateState(channelId, new DateTimeType(Instant.ofEpochMilli(epochMillis)));
        }
    }

    protected void updateDecimalChannel(String channelId, @Nullable Number value) {
        if (value != null && hasChannel(channelId)) {
            updateState(channelId, new DecimalType(java.math.BigDecimal.valueOf(value.doubleValue())));
        }
    }

    protected void updateContactChannel(String channelId, @Nullable Boolean isOpen) {
        if (isOpen != null && hasChannel(channelId)) {
            updateState(channelId, isOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
        }
    }
}
