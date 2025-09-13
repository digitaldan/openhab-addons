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
package org.openhab.binding.unifiprotect.internal.dto.gson;

import java.io.IOException;

import org.openhab.binding.unifiprotect.internal.dto.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Polymorphic adapter for Device based on modelKey discriminator.
 *
 * @author Dan Cunningham - Initial contribution
 */
public class DeviceTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!Device.class.isAssignableFrom(type.getRawType())) {
            return null;
        }
        // Use delegate adapters to avoid this factory being applied recursively to subtypes
        TypeAdapter<Camera> cameraAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Camera.class));
        TypeAdapter<Nvr> nvrAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this, TypeToken.get(Nvr.class));
        TypeAdapter<Chime> chimeAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Chime.class));
        TypeAdapter<Light> lightAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Light.class));
        TypeAdapter<Viewer> viewerAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Viewer.class));
        TypeAdapter<Speaker> speakerAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Speaker.class));
        TypeAdapter<Bridge> bridgeAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Bridge.class));
        TypeAdapter<Doorlock> doorlockAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Doorlock.class));
        TypeAdapter<Sensor> sensorAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(Sensor.class));
        TypeAdapter<AiProcessor> aiProcessorAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(AiProcessor.class));
        TypeAdapter<AiPort> aiPortAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(AiPort.class));
        TypeAdapter<LinkStation> linkStationAdapter = gson.getDelegateAdapter(DeviceTypeAdapterFactory.this,
                TypeToken.get(LinkStation.class));

        return (TypeAdapter<T>) new TypeAdapter<Device>() {
            @Override
            public void write(JsonWriter out, Device value) throws IOException {
                @SuppressWarnings({ "rawtypes", "null" })
                TypeAdapter<Device> delegate = (TypeAdapter) gson.getAdapter((Class) value.getClass());
                delegate.write(out, value);
            }

            @Override
            public Device read(JsonReader in) throws IOException {
                JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                String modelKey = obj.has("modelKey") && obj.get("modelKey").isJsonPrimitive()
                        ? obj.get("modelKey").getAsString()
                        : null;

                if (modelKey == null) {
                    throw new IOException("Missing modelKey for Device payload");
                }
                switch (modelKey) {
                    case "camera":
                        return cameraAdapter.fromJsonTree(obj);
                    case "nvr":
                        return nvrAdapter.fromJsonTree(obj);
                    case "chime":
                        return chimeAdapter.fromJsonTree(obj);
                    case "light":
                        return lightAdapter.fromJsonTree(obj);
                    case "viewer":
                        return viewerAdapter.fromJsonTree(obj);
                    case "speaker":
                        return speakerAdapter.fromJsonTree(obj);
                    case "bridge":
                        return bridgeAdapter.fromJsonTree(obj);
                    case "doorlock":
                        return doorlockAdapter.fromJsonTree(obj);
                    case "sensor":
                        return sensorAdapter.fromJsonTree(obj);
                    case "aiprocessor":
                        return aiProcessorAdapter.fromJsonTree(obj);
                    case "aiport":
                        return aiPortAdapter.fromJsonTree(obj);
                    case "linkstation":
                        return linkStationAdapter.fromJsonTree(obj);
                    default:
                        throw new IOException("Unknown modelKey '" + modelKey + "' for Device payload");
                }
            }
        };
    }
}
