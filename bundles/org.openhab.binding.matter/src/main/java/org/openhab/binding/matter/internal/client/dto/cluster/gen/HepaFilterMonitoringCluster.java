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

// AUTO-GENERATED, DO NOT EDIT!

package org.openhab.binding.matter.internal.client.dto.cluster.gen;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.eclipse.jdt.annotation.NonNull;

import org.openhab.binding.matter.internal.client.dto.cluster.ClusterCommand;

/**
 * HepaFilterMonitoring
 *
 * @author Dan Cunningham - Initial contribution
 */
public class HepaFilterMonitoringCluster extends BaseCluster {

public static final int CLUSTER_ID = 0x0071;
    public static final String CLUSTER_NAME = "HepaFilterMonitoring";
    public static final String CLUSTER_PREFIX = "hepaFilterMonitoring";

    //Structs
    /**
    * Indicates the product identifier that can be used as a replacement for the resource.
    */
     public class ReplacementProductStruct {
        public ProductIdentifierTypeEnum productIdentifierType; // ProductIdentifierTypeEnum
        public String productIdentifierValue; // string
        public ReplacementProductStruct(ProductIdentifierTypeEnum productIdentifierType, String productIdentifierValue) {
            this.productIdentifierType = productIdentifierType;
            this.productIdentifierValue = productIdentifierValue;
        }
     }


    //Enums
    /**
    * Indicates the direction in which the condition of the resource changes over time.
    */
    public enum DegradationDirectionEnum implements MatterEnum {
        UP(0, "Up"),
        DOWN(1, "Down");
        public final Integer value;
        public final String label;
        private DegradationDirectionEnum(Integer value, String label){
            this.value = value;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }
    public enum ChangeIndicationEnum implements MatterEnum {
        OK(0, "Ok"),
        WARNING(1, "Warning"),
        CRITICAL(2, "Critical");
        public final Integer value;
        public final String label;
        private ChangeIndicationEnum(Integer value, String label){
            this.value = value;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }
    /**
    * Indicate the type of identifier used to describe the product. Devices SHOULD use globally-recognized IDs over OEM specific ones.
    */
    public enum ProductIdentifierTypeEnum implements MatterEnum {
        UPC(0, "Upc"),
        GTIN8(1, "Gtin8"),
        EAN(2, "Ean"),
        GTIN14(3, "Gtin14"),
        OEM(4, "Oem");
        public final Integer value;
        public final String label;
        private ProductIdentifierTypeEnum(Integer value, String label){
            this.value = value;
            this.label = label;
        }

        @Override
        public Integer getValue() {
            return value;
        }

        @Override
        public String getLabel() {
            return label;
        }
    }


    public HepaFilterMonitoringCluster(BigInteger nodeId, int endpointId) {
        super(nodeId, endpointId, 113, "HepaFilterMonitoring");
    }

    
    @Override
    public @NonNull String toString() {
        String str = "";
        return str;
    }
}
