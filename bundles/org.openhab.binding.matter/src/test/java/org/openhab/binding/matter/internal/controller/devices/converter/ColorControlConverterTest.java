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
package org.openhab.binding.matter.internal.controller.devices.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.openhab.binding.matter.internal.MatterChannelTypeProvider;
import org.openhab.binding.matter.internal.MatterStateDescriptionOptionProvider;
import org.openhab.binding.matter.internal.bridge.MatterBridgeClient;
import org.openhab.binding.matter.internal.client.dto.cluster.gen.ColorControlCluster;
import org.openhab.binding.matter.internal.client.dto.cluster.gen.ColorControlCluster.ColorMode;
import org.openhab.binding.matter.internal.client.dto.ws.AttributeChangedMessage;
import org.openhab.binding.matter.internal.client.dto.ws.Path;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.HSBType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.unit.Units;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelGroupUID;
import org.openhab.core.types.StateDescription;

/**
 * Test class for ColorControlConverter
 * 
 * @author Dan Cunningham - Initial contribution
 */
class ColorControlConverterTest {

    @Mock
    private ColorControlCluster mockCluster;
    @Mock
    private MatterBridgeClient mockBridgeClient;
    @Mock
    private MatterStateDescriptionOptionProvider mockStateDescriptionProvider;
    @Mock
    private MatterChannelTypeProvider mockChannelTypeProvider;

    private TestMatterBaseThingHandler mockHandler;
    private ColorControlConverter converter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockHandler = Mockito.spy(new TestMatterBaseThingHandler(mockBridgeClient, mockStateDescriptionProvider,
                mockChannelTypeProvider));
        mockCluster.featureMap = new ColorControlCluster.FeatureMap(true, false, false, false, true);
        mockCluster.colorTempPhysicalMinMireds = 153;
        mockCluster.colorTempPhysicalMaxMireds = 500;
        converter = new ColorControlConverter(mockCluster, mockHandler, 1, "TestLabel");
    }

    @Test
    void testCreateChannels() {

        ChannelGroupUID thingUID = new ChannelGroupUID("matter:node:test:12345:1");
        Map<Channel, StateDescription> channels = converter.createChannels(thingUID);

        assertEquals(3, channels.size());
        for (Channel channel : channels.keySet()) {
            String channelId = channel.getUID().getIdWithoutGroup();
            switch (channelId) {
                case "colorcontrol-color":
                    assertEquals("Color", channel.getAcceptedItemType());
                    break;
                case "colorcontrol-colortemperature":
                    assertEquals("Dimmer", channel.getAcceptedItemType());
                    break;
                case "colorcontrol-colortemperature-abs":
                    assertEquals("Number:Temperature", channel.getAcceptedItemType());
                    break;
            }
        }
    }

    @Test
    void testOnEventWithHueSaturation() throws InterruptedException {
        converter.supportsHue = true;

        AttributeChangedMessage levelMsg = new AttributeChangedMessage();
        levelMsg.path = new Path();
        levelMsg.path.attributeName = "currentLevel";
        levelMsg.value = 254; // 100%
        converter.onEvent(levelMsg);

        AttributeChangedMessage modeMsg = new AttributeChangedMessage();
        modeMsg.path = new Path();
        modeMsg.path.attributeName = "colorMode";
        modeMsg.value = ColorMode.CURRENT_HUE_AND_CURRENT_SATURATION;
        converter.onEvent(modeMsg);

        AttributeChangedMessage hueMsg = new AttributeChangedMessage();
        hueMsg.path = new Path();
        hueMsg.path.attributeName = "currentHue";
        hueMsg.value = 127; // ~180 degrees
        converter.onEvent(hueMsg);

        AttributeChangedMessage satMsg = new AttributeChangedMessage();
        satMsg.path = new Path();
        satMsg.path.attributeName = "currentSaturation";
        satMsg.value = 254; // 100%
        converter.onEvent(satMsg);

        // Wait for color update timer
        Thread.sleep(600); // Wait slightly longer than the 500ms timer

        verify(mockHandler, times(1)).updateState(eq(1), eq("colorcontrol-color"),
                eq(new HSBType(new DecimalType(180), new PercentType(100), new PercentType(100))));
    }

    @Test
    void testOnEventWithColorTemperature() throws InterruptedException {
        converter.supportsColorTemperature = true;

        AttributeChangedMessage modeMsg = new AttributeChangedMessage();
        modeMsg.path = new Path();
        modeMsg.path.attributeName = "colorMode";
        modeMsg.value = ColorMode.COLOR_TEMPERATURE_MIREDS;
        converter.onEvent(modeMsg);

        AttributeChangedMessage tempMsg = new AttributeChangedMessage();
        tempMsg.path = new Path();
        tempMsg.path.attributeName = "colorTemperatureMireds";
        tempMsg.value = 250;
        converter.onEvent(tempMsg);

        // Wait for color update timer
        Thread.sleep(600);

        verify(mockHandler, times(1)).updateState(eq(1), eq("colorcontrol-temperature"), eq(new PercentType(27)));
        verify(mockHandler, times(1)).updateState(eq(1), eq("colorcontrol-temperature-abs"),
                eq(new QuantityType<>(250, Units.MIRED)));
    }

    @Test
    void testInitState() throws InterruptedException {
        mockCluster.colorMode = ColorMode.CURRENT_HUE_AND_CURRENT_SATURATION;
        mockCluster.currentHue = 127; // ~180 degrees
        mockCluster.currentSaturation = 254; // 100%
        mockCluster.colorTemperatureMireds = 250;
        mockCluster.featureMap.hueSaturation = true;
        mockCluster.featureMap.colorTemperature = true;

        converter.initState();

        // Wait for color update timer
        Thread.sleep(600);

        verify(mockHandler, times(1)).updateState(eq(1), eq("colorcontrol-color"),
                eq(new HSBType(new DecimalType(180), new PercentType(100), new PercentType(100))));
    }

    @Test
    void testOnEventWithBrightness() {
        AttributeChangedMessage msg = new AttributeChangedMessage();
        msg.path = new Path();
        msg.path.attributeName = "currentLevel";
        msg.value = 254; // 100%
        converter.onEvent(msg);

        verify(mockHandler, times(1)).updateState(eq(1), eq("colorcontrol-color"), eq(new HSBType("0,0,100")));
    }

    @Test
    void testOnEventWithOnOff() {
        mockCluster.colorMode = ColorMode.CURRENT_HUE_AND_CURRENT_SATURATION;
        mockCluster.currentHue = 127; // ~180 degrees
        mockCluster.currentSaturation = 254; // 100%
        converter.initState();

        AttributeChangedMessage msg = new AttributeChangedMessage();
        msg.path = new Path();
        msg.path.attributeName = "onOff";
        msg.value = false;
        converter.onEvent(msg);

        verify(mockHandler, times(1)).updateState(eq(1), eq("colorcontrol-color"), eq(new HSBType("180,100,0"))); // Sam
    }

    @Test
    void testOnEventWithOnOffFollowedByLevel() {
        mockCluster.colorMode = ColorMode.CURRENT_HUE_AND_CURRENT_SATURATION;
        mockCluster.currentHue = 127; // ~180 degrees
        mockCluster.currentSaturation = 254; // 100%
        converter.initState();

        AttributeChangedMessage offMsg = new AttributeChangedMessage();
        offMsg.path = new Path();
        offMsg.path.attributeName = "onOff";
        offMsg.value = false;
        converter.onEvent(offMsg);

        // Then change level while off
        AttributeChangedMessage levelMsg = new AttributeChangedMessage();
        levelMsg.path = new Path();
        levelMsg.path.attributeName = "currentLevel";
        levelMsg.value = 254; // 100%
        converter.onEvent(levelMsg);

        // Verify brightness remains 0 since device is off (2 updates, one for onOff, one for level)
        verify(mockHandler, times(2)).updateState(eq(1), eq("colorcontrol-color"), eq(new HSBType("180,100,0")));
    }
}
