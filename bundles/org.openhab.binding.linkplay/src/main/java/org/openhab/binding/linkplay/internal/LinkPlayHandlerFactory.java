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
package org.openhab.binding.linkplay.internal;

import static org.openhab.binding.linkplay.internal.LinkPlayBindingConstants.*;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.jupnp.UpnpService;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.registry.Registry;
import org.jupnp.registry.RegistryListener;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LinkPlayHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.linkplay", service = ThingHandlerFactory.class)
public class LinkPlayHandlerFactory extends BaseThingHandlerFactory implements RegistryListener {
    private final Logger logger = LoggerFactory.getLogger(LinkPlayHandlerFactory.class);
    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_PLAYER);
    private final UpnpIOService upnpIOService;
    private final LinkPlayGroupService linkPlayGroupService;
    private final UpnpService upnpService;
    private final LinkPlayCommandDescriptionProvider linkPlayCommandDescriptionProvider;
    private ConcurrentMap<String, RemoteDevice> devices = new ConcurrentHashMap<>();
    private ConcurrentMap<String, LinkPlayHandler> handlers = new ConcurrentHashMap<>();

    @Activate
    public LinkPlayHandlerFactory(final @Reference UpnpIOService upnpIOService, @Reference UpnpService upnpService,
            final @Reference LinkPlayGroupService linkPlayGroupService,
            final @Reference LinkPlayCommandDescriptionProvider linkPlayCommandDescriptionProvider) {
        this.upnpIOService = upnpIOService;
        this.linkPlayGroupService = linkPlayGroupService;
        this.upnpService = upnpService;
        this.linkPlayCommandDescriptionProvider = linkPlayCommandDescriptionProvider;
        upnpService.getRegistry().addListener(this);
    }

    @Deactivate
    protected void deActivate() {
        upnpService.getRegistry().removeListener(this);
        devices.clear();
        handlers.clear();
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_PLAYER.equals(thingTypeUID)) {
            LinkPlayHandler handler = new LinkPlayHandler(thing, upnpIOService, upnpService, linkPlayGroupService,
                    linkPlayCommandDescriptionProvider);
            String udn = handler.getUDN();
            if (udn != null) {
                handlers.put(udn, handler);
                remoteDeviceUpdated(null, devices.get(udn));
            }
            return handler;
        }

        return null;
    }

    @Override
    protected void removeHandler(ThingHandler handler) {
        handlers.remove(handler.getThing().getUID().getAsString());
        super.removeHandler(handler);
    }

    @Override
    public void remoteDeviceDiscoveryStarted(@Nullable Registry registry, @Nullable RemoteDevice device) {
    }

    @Override
    public void remoteDeviceDiscoveryFailed(@Nullable Registry registry, @Nullable RemoteDevice device,
            @Nullable Exception e) {
    }

    @Override
    public void remoteDeviceAdded(@Nullable Registry registry, @Nullable RemoteDevice device) {
        if (device == null) {
            return;
        }

        String udn = device.getIdentity().getUdn().getIdentifierString();
        logger.debug("remoteDeviceAdded: {}", udn);
        if ("MediaRenderer".equals(device.getType().getType())) {
            devices.put(udn, device);
        }

        if (handlers.containsKey(udn)) {
            remoteDeviceUpdated(registry, device);
        }
    }

    @Override
    public void remoteDeviceUpdated(@Nullable Registry registry, @Nullable RemoteDevice device) {
        if (device == null) {
            return;
        }

        String udn = device.getIdentity().getUdn().getIdentifierString();
        LinkPlayHandler handler = handlers.get(udn);
        if (handler != null) {
            handler.updateDeviceConfig(device);
        }
    }

    @Override
    public void remoteDeviceRemoved(@Nullable Registry registry, @Nullable RemoteDevice device) {
        if (device == null) {
            return;
        }
        devices.remove(device.getIdentity().getUdn().getIdentifierString());
    }

    @Override
    public void localDeviceAdded(@Nullable Registry registry, @Nullable LocalDevice device) {
    }

    @Override
    public void localDeviceRemoved(@Nullable Registry registry, @Nullable LocalDevice device) {
    }

    @Override
    public void beforeShutdown(@Nullable Registry registry) {
        devices.clear();
    }

    @Override
    public void afterShutdown() {
    }
}
