/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.grandstreamgds.internal;

import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.jmdns.ServiceInfo;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author daniel
 *
 */
@NonNullByDefault
@Component(configurationPid = "discovery.grandstreamgds")
public class GrandstreamGDSDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(GrandstreamGDSDiscoveryParticipant.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return Collections.singleton(GrandstreamGDSBindingConstants.THING_TYPE_GDS);
    }

    @Override
    public String getServiceType() {
        return "_Grandstream-video._tcp.local.";
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        DiscoveryResult result = null;
        ThingUID uid = getThingUID(service);
        if (uid != null) {
            Map<String, Object> properties = new HashMap<>(2);
            // remove the domain from the name
            InetAddress ip = getIpAddress(service);
            if (ip == null) {
                return null;
            }
            String inetAddress = ip.toString().substring(1); // trim leading slash
            String label = service.getName();
            String macaddress = service.getPropertyString("macaddress");
            int port = service.getPort();
            // do our best to guess the protocol, not perfect, but probably right most of the time
            String proto = String.valueOf(port).contains("443") ? "https://" : "http://";
            properties.put("macaddress", macaddress);
            properties.put("url", proto + inetAddress + ":" + port);

            result = DiscoveryResultBuilder.create(uid).withProperties(properties).withLabel(label)
                    .withRepresentationProperty("macaddress").build();
            logger.debug("Created {} for GDS host '{}' name '{}'", result, inetAddress, label);
        }
        return result;
    }

    /**
     * @see org.openhab.core.config.discovery.mdns.MDNSDiscoveryParticipant#getThingUID(javax.jmdns.ServiceInfo)
     */
    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        if (service.getType() != null) {
            if (service.getType().equals(getServiceType())) {
                String mac = service.getPropertyString("macaddress");
                if (mac != null) {
                    return new ThingUID(GrandstreamGDSBindingConstants.THING_TYPE_GDS, mac.toLowerCase());
                }
            }
        }
        return null;
    }

    /**
     * {@link InetAddress} gets the IP address of the device in v4 or v6 format.
     *
     * @param ServiceInfo service
     * @return InetAddress the IP address
     *
     */
    private @Nullable InetAddress getIpAddress(ServiceInfo service) {
        InetAddress address = null;
        for (InetAddress addr : service.getInet4Addresses()) {
            return addr;
        }
        // Fall back for Inet6addresses
        for (InetAddress addr : service.getInet6Addresses()) {
            return addr;
        }
        return address;
    }
}
