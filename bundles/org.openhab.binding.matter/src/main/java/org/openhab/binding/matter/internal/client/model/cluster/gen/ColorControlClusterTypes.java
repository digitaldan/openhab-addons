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

// 

package org.openhab.binding.matter.internal.client.model.cluster.gen;

import java.util.Map;
import java.util.List;

import org.openhab.binding.matter.internal.client.model.cluster.types.*;
import static java.util.Map.entry;  

/**
 * AUTO-GENERATED by zap. DO NOT EDIT!
 *
 * ColorControl
 *
 * @author Dan Cunningham - Initial contribution
 */
public class ColorControlClusterTypes {

    public static final String CLUSTER_NAME = "COLOR_CONTROL_CLUSTER";
    public static final int CLUSTER_ID = 0x0300;

    //ZCL Enums
    public enum ColorLoopAction {
        DEACTIVATE(0, "Deactivate"),
        ACTIVATEFROMCOLORLOOPSTARTENHANCEDHUE(1, "ActivateFromColorLoopStartEnhancedHue"),
        ACTIVATEFROMENHANCEDCURRENTHUE(2, "ActivateFromEnhancedCurrentHue"),
        UNKNOWN_VALUE(3,"UnknownValue");

        public final int value;
        public final String label;
        private ColorLoopAction(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum ColorLoopDirection {
        DECREMENTHUE(0, "DecrementHue"),
        INCREMENTHUE(1, "IncrementHue"),
        UNKNOWN_VALUE(2,"UnknownValue");

        public final int value;
        public final String label;
        private ColorLoopDirection(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum ColorMode {
        CURRENTHUEANDCURRENTSATURATION(0, "CurrentHueAndCurrentSaturation"),
        CURRENTXANDCURRENTY(1, "CurrentXAndCurrentY"),
        COLORTEMPERATURE(2, "ColorTemperature"),
        UNKNOWN_VALUE(3,"UnknownValue");

        public final int value;
        public final String label;
        private ColorMode(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum HueDirection {
        SHORTESTDISTANCE(0, "ShortestDistance"),
        LONGESTDISTANCE(1, "LongestDistance"),
        UP(2, "Up"),
        DOWN(3, "Down"),
        UNKNOWN_VALUE(4,"UnknownValue");

        public final int value;
        public final String label;
        private HueDirection(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum HueMoveMode {
        STOP(0, "Stop"),
        UP(1, "Up"),
        DOWN(3, "Down"),
        UNKNOWN_VALUE(2,"UnknownValue");

        public final int value;
        public final String label;
        private HueMoveMode(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum HueStepMode {
        UP(1, "Up"),
        DOWN(3, "Down"),
        UNKNOWN_VALUE(0,"UnknownValue");

        public final int value;
        public final String label;
        private HueStepMode(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum SaturationMoveMode {
        STOP(0, "Stop"),
        UP(1, "Up"),
        DOWN(3, "Down"),
        UNKNOWN_VALUE(2,"UnknownValue");

        public final int value;
        public final String label;
        private SaturationMoveMode(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    public enum SaturationStepMode {
        UP(1, "Up"),
        DOWN(3, "Down"),
        UNKNOWN_VALUE(0,"UnknownValue");

        public final int value;
        public final String label;
        private SaturationStepMode(int value, String label){
            this.value = value;
            this.label = label;
        }
    };

    //ZCL Bitmaps
    public static class ColorCapabilities {
        public boolean hueSaturationSupported;
        public boolean enhancedHueSupported;
        public boolean colorLoopSupported;
        public boolean XYAttributesSupported;
        public boolean colorTemperatureSupported;
        public ColorCapabilities(boolean hueSaturationSupported, boolean enhancedHueSupported, boolean colorLoopSupported, boolean XYAttributesSupported, boolean colorTemperatureSupported){
            this.hueSaturationSupported = hueSaturationSupported;
            this.enhancedHueSupported = enhancedHueSupported;
            this.colorLoopSupported = colorLoopSupported;
            this.XYAttributesSupported = XYAttributesSupported;
            this.colorTemperatureSupported = colorTemperatureSupported;
        }
    }
    public static class ColorLoopUpdateFlags {
        public boolean updateAction;
        public boolean updateDirection;
        public boolean updateTime;
        public boolean updateStartHue;
        public ColorLoopUpdateFlags(boolean updateAction, boolean updateDirection, boolean updateTime, boolean updateStartHue){
            this.updateAction = updateAction;
            this.updateDirection = updateDirection;
            this.updateTime = updateTime;
            this.updateStartHue = updateStartHue;
        }
    }
    public static class Feature {
        public boolean hueAndSaturation;
        public boolean enhancedHue;
        public boolean colorLoop;
        public boolean xy;
        public boolean colorTemperature;
        public Feature(boolean hueAndSaturation, boolean enhancedHue, boolean colorLoop, boolean xy, boolean colorTemperature){
            this.hueAndSaturation = hueAndSaturation;
            this.enhancedHue = enhancedHue;
            this.colorLoop = colorLoop;
            this.xy = xy;
            this.colorTemperature = colorTemperature;
        }
    }

    public static class MoveToHueCommandOptions {
        public Integer hue;
        public HueDirection direction;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveToHueCommandOptions(Integer hue, HueDirection direction, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.hue = hue;
            this.direction = direction;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveHueCommandOptions {
        public HueMoveMode moveMode;
        public Integer rate;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveHueCommandOptions(HueMoveMode moveMode, Integer rate, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.moveMode = moveMode;
            this.rate = rate;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class StepHueCommandOptions {
        public HueStepMode stepMode;
        public Integer stepSize;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  StepHueCommandOptions(HueStepMode stepMode, Integer stepSize, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.stepMode = stepMode;
            this.stepSize = stepSize;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveToSaturationCommandOptions {
        public Integer saturation;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveToSaturationCommandOptions(Integer saturation, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.saturation = saturation;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveSaturationCommandOptions {
        public SaturationMoveMode moveMode;
        public Integer rate;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveSaturationCommandOptions(SaturationMoveMode moveMode, Integer rate, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.moveMode = moveMode;
            this.rate = rate;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class StepSaturationCommandOptions {
        public SaturationStepMode stepMode;
        public Integer stepSize;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  StepSaturationCommandOptions(SaturationStepMode stepMode, Integer stepSize, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.stepMode = stepMode;
            this.stepSize = stepSize;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveToHueAndSaturationCommandOptions {
        public Integer hue;
        public Integer saturation;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveToHueAndSaturationCommandOptions(Integer hue, Integer saturation, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.hue = hue;
            this.saturation = saturation;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveToColorCommandOptions {
        public Integer colorX;
        public Integer colorY;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveToColorCommandOptions(Integer colorX, Integer colorY, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.colorX = colorX;
            this.colorY = colorY;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveColorCommandOptions {
        public Integer rateX;
        public Integer rateY;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveColorCommandOptions(Integer rateX, Integer rateY, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.rateX = rateX;
            this.rateY = rateY;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class StepColorCommandOptions {
        public Integer stepX;
        public Integer stepY;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  StepColorCommandOptions(Integer stepX, Integer stepY, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.stepX = stepX;
            this.stepY = stepY;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveToColorTemperatureCommandOptions {
        public Integer colorTemperatureMireds;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveToColorTemperatureCommandOptions(Integer colorTemperatureMireds, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.colorTemperatureMireds = colorTemperatureMireds;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class EnhancedMoveToHueCommandOptions {
        public Integer enhancedHue;
        public HueDirection direction;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  EnhancedMoveToHueCommandOptions(Integer enhancedHue, HueDirection direction, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.enhancedHue = enhancedHue;
            this.direction = direction;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class EnhancedMoveHueCommandOptions {
        public HueMoveMode moveMode;
        public Integer rate;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  EnhancedMoveHueCommandOptions(HueMoveMode moveMode, Integer rate, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.moveMode = moveMode;
            this.rate = rate;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class EnhancedStepHueCommandOptions {
        public HueStepMode stepMode;
        public Integer stepSize;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  EnhancedStepHueCommandOptions(HueStepMode stepMode, Integer stepSize, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.stepMode = stepMode;
            this.stepSize = stepSize;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class EnhancedMoveToHueAndSaturationCommandOptions {
        public Integer enhancedHue;
        public Integer saturation;
        public Integer transitionTime;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  EnhancedMoveToHueAndSaturationCommandOptions(Integer enhancedHue, Integer saturation, Integer transitionTime, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.enhancedHue = enhancedHue;
            this.saturation = saturation;
            this.transitionTime = transitionTime;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class ColorLoopSetCommandOptions {
        public ColorLoopUpdateFlags updateFlags;
        public ColorLoopAction action;
        public ColorLoopDirection direction;
        public Integer time;
        public Integer startHue;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  ColorLoopSetCommandOptions(ColorLoopUpdateFlags updateFlags, ColorLoopAction action, ColorLoopDirection direction, Integer time, Integer startHue, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.updateFlags = updateFlags;
            this.action = action;
            this.direction = direction;
            this.time = time;
            this.startHue = startHue;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class StopMoveStepCommandOptions {
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  StopMoveStepCommandOptions(Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class MoveColorTemperatureCommandOptions {
        public HueMoveMode moveMode;
        public Integer rate;
        public Integer colorTemperatureMinimumMireds;
        public Integer colorTemperatureMaximumMireds;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  MoveColorTemperatureCommandOptions(HueMoveMode moveMode, Integer rate, Integer colorTemperatureMinimumMireds, Integer colorTemperatureMaximumMireds, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.moveMode = moveMode;
            this.rate = rate;
            this.colorTemperatureMinimumMireds = colorTemperatureMinimumMireds;
            this.colorTemperatureMaximumMireds = colorTemperatureMaximumMireds;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
    public static class StepColorTemperatureCommandOptions {
        public HueStepMode stepMode;
        public Integer stepSize;
        public Integer transitionTime;
        public Integer colorTemperatureMinimumMireds;
        public Integer colorTemperatureMaximumMireds;
        public Map<String, Boolean> optionsMask;
        public Map<String, Boolean> optionsOverride;
        public  StepColorTemperatureCommandOptions(HueStepMode stepMode, Integer stepSize, Integer transitionTime, Integer colorTemperatureMinimumMireds, Integer colorTemperatureMaximumMireds, Map<String, Boolean> optionsMask, Map<String, Boolean> optionsOverride){
            this.stepMode = stepMode;
            this.stepSize = stepSize;
            this.transitionTime = transitionTime;
            this.colorTemperatureMinimumMireds = colorTemperatureMinimumMireds;
            this.colorTemperatureMaximumMireds = colorTemperatureMaximumMireds;
            this.optionsMask = optionsMask;
            this.optionsOverride = optionsOverride;
        }
    }
}