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
package org.openhab.binding.espsomfy.internal.discovery;

import static org.openhab.binding.espsomfy.internal.ESPSomfyBindingConstants.*;

import java.net.Inet4Address;
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
 * The {@link ESPSomfyDiscoveryParticipant} discovers ESPSomfy-RTS controllers via mDNS.
 * The firmware registers a {@code _espsomfy_rts._tcp} service with TXT records for
 * serverId, model, and version.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
@Component(service = MDNSDiscoveryParticipant.class)
public class ESPSomfyDiscoveryParticipant implements MDNSDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyDiscoveryParticipant.class);

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return SUPPORTED_BRIDGE_TYPES;
    }

    @Override
    public String getServiceType() {
        return MDNS_SERVICE_TYPE;
    }

    @Override
    public @Nullable DiscoveryResult createResult(ServiceInfo service) {
        ThingUID thingUID = getThingUID(service);
        if (thingUID == null) {
            return null;
        }

        String address = extractAddress(service);
        if (address.isEmpty()) {
            logger.debug("No IPv4 address found for ESPSomfy service: {}", service.getName());
            return null;
        }

        Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_HOSTNAME, address);

        String version = service.getPropertyString("version");
        String label = "ESPSomfy-RTS (" + service.getName() + ")";

        DiscoveryResult result = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                .withRepresentationProperty(CONFIG_HOSTNAME).withLabel(label).build();

        logger.debug("Discovered ESPSomfy controller: {} at {} (version: {})", service.getName(), address, version);
        return result;
    }

    @Override
    public @Nullable ThingUID getThingUID(ServiceInfo service) {
        String serverId = service.getPropertyString("serverId");
        if (serverId == null || serverId.isEmpty()) {
            return null;
        }
        // Use serverId as unique identifier, sanitized for ThingUID
        String id = serverId.replaceAll("[^a-zA-Z0-9]", "");
        if (id.isEmpty()) {
            return null;
        }
        return new ThingUID(THING_TYPE_CONTROLLER, id);
    }

    private String extractAddress(ServiceInfo service) {
        Inet4Address[] addresses = service.getInet4Addresses();
        if (addresses.length > 0) {
            return addresses[0].getHostAddress();
        }
        return "";
    }
}
