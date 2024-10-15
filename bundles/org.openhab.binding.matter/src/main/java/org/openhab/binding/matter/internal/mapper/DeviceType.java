/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.matter.internal.mapper;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.matter.internal.client.AttributeListener;
import org.openhab.binding.matter.internal.client.MatterWebsocketClient;
import org.openhab.binding.matter.internal.client.model.cluster.BaseCluster;
import org.openhab.binding.matter.internal.handler.EndpointHandler;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.binding.builder.ThingBuilder;
import org.openhab.core.thing.type.ChannelTypeUID;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DeviceType} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public abstract class DeviceType implements AttributeListener {
    private final Logger logger = LoggerFactory.getLogger(DeviceType.class);

    private final static BigDecimal TEMPERATURE_MULTIPLIER = new BigDecimal(100);

    private static final Map<Integer, Class<? extends DeviceType>> deviceMappers = new HashMap();
    protected BigInteger nodeId;
    protected int endpointId;
    protected EndpointHandler handler;
    protected Integer deviceType;

    static {
        LightingType.supportedTypes().forEach(id -> deviceMappers.put(id, LightingType.class));
        ThermostatType.supportedTypes().forEach(id -> deviceMappers.put(id, ThermostatType.class));
        WindowCoveringType.supportedTypes().forEach(id -> deviceMappers.put(id, WindowCoveringType.class));
    }

    public static @Nullable Class<? extends DeviceType> getDeviceMapper(int deviceType) {
        return deviceMappers.get(deviceType);
    }

    public DeviceType(EndpointHandler handler, Integer deviceType) {
        this.handler = handler;
        this.nodeId = handler.getNodeId();
        this.endpointId = handler.getEndpointId();
        this.deviceType = deviceType;
    }

    public abstract List<Integer> supportedClusters();

    public abstract void handleCommand(ChannelUID channelUID, Command command);

    public abstract void updateCluster(BaseCluster cluster);

    public abstract List<ChannelUID> createChannels(Map<String, BaseCluster> clusters);

    protected Map<ChannelUID, StateDescription> stateDescriptions = new HashMap<>();

    public @Nullable StateDescription getStateDescription(ChannelUID channelUID) {
        return stateDescriptions.get(channelUID);
    }

    protected ChannelUID createChannel(BaseCluster cluster, String channelName, ChannelTypeUID channelType,
            String channelLabel, String itemType) {

        Channel channel = ChannelBuilder
                .create(new ChannelUID(handler.getThing().getUID(), channelType.getId()), itemType)
                .withType(channelType).withLabel(channelLabel).build();
        List<Channel> channels = new ArrayList<>(handler.getThing().getChannels());

        boolean hasMatchingUID = channels.stream().anyMatch(c -> channel.getUID().equals(c.getUID()));
        if (!hasMatchingUID) {
            logger.debug("createChannel: adding channel {}", channel.getUID());
            channels.add(channel);
            ThingBuilder thingBuilder = handler.editThing();
            thingBuilder.withChannels(channels);
            handler.updateThing(thingBuilder.build());
        }
        return channel.getUID();
    }

    protected void updateState(ChannelTypeUID channelType, State state) {
        handler.updateState(channelType.getId(), state);
    }

    protected void writeAttribute(String clusterName, String attributeName, String value) {
        MatterWebsocketClient ws = handler.getClient();
        if (ws != null) {
            ws.clusterWriteAttribute(nodeId, handler.getEndpointId(), clusterName, attributeName, value);
        }
    }

    /**
     * Converts a ZigBee 8 bit level as used in Level Control cluster and others to a percentage
     *
     * @param level an integer between 0 and 254
     * @return the scaled {@link PercentType}
     */
    protected PercentType levelToPercent(int level) {
        return new PercentType((int) (level * 100.0 / 254.0 + 0.5));
    }

    /**
     * Converts a {@link PercentType} to an 8 bit level scaled between 0 and 254
     *
     * @param percent the {@link PercentType} to convert
     * @return a scaled value between 0 and 254
     */
    protected int percentToLevel(PercentType percent) {
        return (int) (percent.floatValue() * 254.0f / 100.0f + 0.5f);
    }

    /**
     * Converts a {@link Command} to a ZigBee / Matter temperature integer
     *
     * @param command the {@link Command} to convert
     * @return the {@link Command} or null if the conversion was not possible
     */
    protected @Nullable Integer temperatureToValue(Command command) {
        BigDecimal value = null;
        if (command instanceof QuantityType<?> quantity) {
            if (quantity.getUnit() == SIUnits.CELSIUS) {
                value = quantity.toBigDecimal();
            } else if (quantity.getUnit() == ImperialUnits.FAHRENHEIT) {
                QuantityType<?> celsius = quantity.toUnit(SIUnits.CELSIUS);
                if (celsius == null) {
                    return null;
                }
                value = celsius.toBigDecimal();
            } else {
                return null;
            }
        } else if (command instanceof Number number) {
            // No scale, so assumed to be Celsius
            value = BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return null;
        }
        return value.setScale(2, RoundingMode.CEILING).multiply(TEMPERATURE_MULTIPLIER).intValue();
    }

    /**
     * Converts an integer value into a {@link QuantityType}. The temperature as an integer is assumed to be multiplied
     * by 100 as per the ZigBee / Matter standard format.
     *
     * @param value the integer value to convert
     * @return the {@link QuantityType}
     */
    protected QuantityType valueToTemperature(int value) {
        return new QuantityType<>(BigDecimal.valueOf(value, 2), SIUnits.CELSIUS);
    }
}
