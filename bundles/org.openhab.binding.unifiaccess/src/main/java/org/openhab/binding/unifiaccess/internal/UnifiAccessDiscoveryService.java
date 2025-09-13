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
package org.openhab.binding.unifiaccess.internal;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.unifiaccess.internal.api.UniFiAccessApiClient;
import org.openhab.binding.unifiaccess.internal.dto.Door;
import org.openhab.binding.unifiaccess.internal.handler.UnifiAccessBridgeHandler;
import org.openhab.core.config.discovery.AbstractThingHandlerDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

/**
 * Discovery service for UniFi Access Door things.
 * 
 * @author Dan Cunningham - Initial contribution
 */
@Component(scope = ServiceScope.PROTOTYPE, service = UnifiAccessDiscoveryService.class)
@NonNullByDefault
public class UnifiAccessDiscoveryService extends AbstractThingHandlerDiscoveryService<UnifiAccessBridgeHandler> {

    public UnifiAccessDiscoveryService() {
        super(UnifiAccessBridgeHandler.class, Set.of(UnifiAccessBindingConstants.DOOR_THING_TYPE), 30, false);
    }

    @Override
    protected void startScan() {
        removeOlderResults(getTimestampOfLastScan());
        final UniFiAccessApiClient client = thingHandler.getApiClient();
        if (client == null) {
            return;
        }
        try {
            List<Door> doors = client.getDoors();
            if (doors == null) {
                return;
            }
            for (Door d : doors) {
                if (d == null || d.id == null) {
                    continue;
                }
                ThingUID uid = new ThingUID(UnifiAccessBindingConstants.DOOR_THING_TYPE,
                        thingHandler.getThing().getUID(), d.id);
                Map<String, Object> props = Map.of(UnifiAccessBindingConstants.CONFIG_DOOR_ID, d.id);
                DiscoveryResult result = DiscoveryResultBuilder.create(uid).withBridge(thingHandler.getThing().getUID())
                        .withThingType(UnifiAccessBindingConstants.DOOR_THING_TYPE).withProperties(props)
                        .withRepresentationProperty(UnifiAccessBindingConstants.CONFIG_DOOR_ID)
                        .withLabel("Door: " + d.name).build();
                thingDiscovered(result);
            }
        } catch (Exception e) {
            // ignore discovery errors; scan is best-effort
        }
    }
}
