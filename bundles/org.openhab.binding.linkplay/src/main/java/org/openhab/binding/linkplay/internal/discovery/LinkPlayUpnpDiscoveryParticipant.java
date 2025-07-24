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
package org.openhab.binding.linkplay.internal.discovery;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.meta.Service;
import org.jupnp.model.types.ServiceId;
import org.jupnp.model.types.UDAServiceId;
import org.openhab.binding.linkplay.internal.LinkPlayBindingConstants;
import org.openhab.binding.linkplay.internal.client.LinkPlayHTTPClient;
import org.openhab.binding.linkplay.internal.client.dto.DeviceStatus;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.config.discovery.upnp.UpnpDiscoveryParticipant;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.ThingUID;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A UPnP discovery participant for LinkPlay devices.
 * 
 * Discovery workflow:
 * 1. Detects UPnP devices with MediaRenderer/MediaServer capabilities
 * 2. Validates required UPnP services (AVTransport, RenderingControl)
 * 3. Extracts device IP and performs HTTP validation
 * 4. Creates Thing discovery result if validation succeeds
 *
 * @author Michael Cumming - Initial contribution
 * @author Dan Cunningham - Refactored
 */
@NonNullByDefault
@Component(service = UpnpDiscoveryParticipant.class)
public class LinkPlayUpnpDiscoveryParticipant implements UpnpDiscoveryParticipant {

    private final Logger logger = LoggerFactory.getLogger(LinkPlayUpnpDiscoveryParticipant.class);

    private static final int DISCOVERY_RESULT_TTL_SECONDS = 300;
    private static final ServiceId SERVICE_ID_AV_TRANSPORT = new UDAServiceId("AVTransport");
    private static final ServiceId SERVICE_ID_RENDERING_CONTROL = new UDAServiceId("RenderingControl");

    private final HttpClient httpClient;

    @Activate
    public LinkPlayUpnpDiscoveryParticipant() {
        try {
            httpClient = new HttpClient(new SslContextFactory.Client(true));
            httpClient.start();
        } catch (Exception e) {
            throw new IllegalStateException("Could not create HTTP client", e);
        }
    }

    @Deactivate
    public void deactivate() {
        try {
            httpClient.stop();
        } catch (Exception e) {
            logger.debug("Failed to stop HTTP client: {}", e.getMessage(), e);
        }
    }

    @Override
    public Set<ThingTypeUID> getSupportedThingTypeUIDs() {
        return LinkPlayBindingConstants.SUPPORTED_THING_TYPES_UIDS;
    }

    @Override
    public @Nullable ThingUID getThingUID(RemoteDevice device) {
        try {
            if (!hasRequiredServices(device)) {
                logger.trace("Device {} does not have required UPnP services", device.getDetails().getFriendlyName());
                return null;
            }

            String host = device.getIdentity().getDescriptorURL().getHost();

            if (host == null || host.isEmpty()) {
                logger.trace("no host for device {}", device.getDetails().getFriendlyName());
                return null;
            }

            try {
                LinkPlayHTTPClient httpClient = new LinkPlayHTTPClient(this.httpClient, host);
                DeviceStatus deviceStatus = httpClient.getStatusEx().get(5000, TimeUnit.MILLISECONDS);
                if (deviceStatus.uuid != null) {
                    String deviceId = device.getIdentity().getUdn().getIdentifierString();
                    deviceId = deviceId.replace("uuid:", "").replace("-", "");
                    logger.debug("Creating ThingUID with deviceId: {}", deviceId);
                    ThingUID thingUID = new ThingUID(LinkPlayBindingConstants.THING_TYPE_PLAYER, deviceId);
                    return thingUID;
                }
            } catch (Exception e) {
                logger.trace("Could not validate device at {}: {}", host, e.getMessage());
            }
            return null;
        } catch (Exception e) {
            logger.debug("Discovery error for device {}: {}", device.getDetails().getFriendlyName(), e.getMessage());
            return null;
        }
    }

    @Override
    public @Nullable DiscoveryResult createResult(RemoteDevice device) {
        ThingUID thingUID = getThingUID(device);
        if (thingUID == null) {
            return null;
        }

        String ipAddress = device.getIdentity().getDescriptorURL().getHost();
        String friendlyName = device.getDetails().getFriendlyName();
        String manufacturer = device.getDetails().getManufacturerDetails().getManufacturer();
        String modelName = device.getDetails().getModelDetails().getModelName();
        String deviceUDN = device.getIdentity().getUdn().getIdentifierString();
        String normalizedUDN = deviceUDN.replace("uuid:", "");

        Map<String, Object> properties = new HashMap<>();
        properties.put(LinkPlayBindingConstants.CONFIG_IP_ADDRESS, ipAddress);
        properties.put(LinkPlayBindingConstants.CONFIG_UDN, normalizedUDN);
        properties.put(LinkPlayBindingConstants.PROPERTY_DEVICE_NAME, friendlyName);
        properties.put(LinkPlayBindingConstants.PROPERTY_MODEL, modelName);
        properties.put(LinkPlayBindingConstants.PROPERTY_MANUFACTURER, manufacturer);
        properties.put(LinkPlayBindingConstants.PROPERTY_UDN, normalizedUDN);

        String label = String.format("LinkPlay: %s", friendlyName);
        logger.debug("Building discovery result for {}: label={}, properties={}", thingUID, label, properties);

        return DiscoveryResultBuilder.create(thingUID).withLabel(label).withProperties(properties)
                .withRepresentationProperty(LinkPlayBindingConstants.CONFIG_UDN).withTTL(DISCOVERY_RESULT_TTL_SECONDS)
                .build();
    }

    private boolean hasRequiredServices(@Nullable RemoteDevice device) {
        if (device == null) {
            return false;
        }

        Service<?, ?> avTransportService = device.findService(SERVICE_ID_AV_TRANSPORT);
        Service<?, ?> renderingControlService = device.findService(SERVICE_ID_RENDERING_CONTROL);

        boolean hasRequiredServices = (avTransportService != null && renderingControlService != null);
        logger.trace("Device {} has required services: AVTransport={}, RenderingControl={}",
                device.getDetails().getFriendlyName(), avTransportService != null, renderingControlService != null);

        return hasRequiredServices;
    }
}
