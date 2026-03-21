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
package org.openhab.binding.espsomfy.internal;

import static org.openhab.binding.espsomfy.internal.ESPSomfyBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.espsomfy.internal.handler.ESPSomfyControllerHandler;
import org.openhab.binding.espsomfy.internal.handler.ESPSomfyGroupHandler;
import org.openhab.binding.espsomfy.internal.handler.ESPSomfyShadeHandler;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.io.net.http.WebSocketFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link ESPSomfyHandlerFactory} is responsible for creating things and thing handlers.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.espsomfy", service = ThingHandlerFactory.class)
public class ESPSomfyHandlerFactory extends BaseThingHandlerFactory {

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyHandlerFactory.class);
    private final HttpClient httpClient;
    private final WebSocketClient webSocketClient;

    @Activate
    public ESPSomfyHandlerFactory(@Reference HttpClientFactory httpClientFactory,
            @Reference WebSocketFactory webSocketFactory, ComponentContext componentContext) {
        super.activate(componentContext);
        this.httpClient = httpClientFactory.getCommonHttpClient();
        this.webSocketClient = webSocketFactory.createWebSocketClient("espsomfy");
        this.webSocketClient.setConnectTimeout(API_TIMEOUT_MS);
        this.webSocketClient.setStopTimeout(1000);
        try {
            this.webSocketClient.start();
        } catch (Exception e) {
            logger.debug("Failed to start WebSocket client: {}", e.getMessage());
            throw new IllegalStateException("Unable to start WebSocket client", e);
        }
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return ALL_SUPPORTED_TYPES.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_CONTROLLER.equals(thingTypeUID)) {
            return new ESPSomfyControllerHandler((Bridge) thing, httpClient, webSocketClient);
        } else if (THING_TYPE_SHADE.equals(thingTypeUID)) {
            return new ESPSomfyShadeHandler(thing);
        } else if (THING_TYPE_GROUP.equals(thingTypeUID)) {
            return new ESPSomfyGroupHandler(thing);
        }

        return null;
    }

    @Deactivate
    @Override
    protected void deactivate(ComponentContext componentContext) {
        try {
            webSocketClient.stop();
        } catch (Exception e) {
            logger.debug("Failed to stop WebSocket client: {}", e.getMessage());
        }
        super.deactivate(componentContext);
    }
}
