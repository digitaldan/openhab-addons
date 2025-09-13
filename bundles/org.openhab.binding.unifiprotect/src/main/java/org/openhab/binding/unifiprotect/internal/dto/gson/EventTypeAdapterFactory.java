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

import org.openhab.binding.unifiprotect.internal.dto.events.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

/**
 * Polymorphic adapter for events based on type discriminator.
 *
 * @author Dan Cunningham - Initial contribution
 */
public class EventTypeAdapterFactory implements TypeAdapterFactory {
    @Override
    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        if (!BaseEvent.class.isAssignableFrom(type.getRawType())) {
            return null;
        }

        // Use delegate adapters to avoid this factory being applied recursively to subtypes
        TypeAdapter<RingEvent> ring = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(RingEvent.class));
        TypeAdapter<SensorExtremeValueEvent> sev = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorExtremeValueEvent.class));
        TypeAdapter<SensorWaterLeakEvent> swl = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorWaterLeakEvent.class));
        TypeAdapter<SensorTamperEvent> st = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorTamperEvent.class));
        TypeAdapter<SensorBatteryLowEvent> sbl = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorBatteryLowEvent.class));
        TypeAdapter<SensorAlarmEvent> sa = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorAlarmEvent.class));
        TypeAdapter<SensorOpenEvent> so = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorOpenEvent.class));
        TypeAdapter<SensorClosedEvent> sc = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorClosedEvent.class));
        TypeAdapter<SensorMotionEvent> sm = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(SensorMotionEvent.class));
        TypeAdapter<LightMotionEvent> lm = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(LightMotionEvent.class));
        TypeAdapter<CameraMotionEvent> cm = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(CameraMotionEvent.class));
        TypeAdapter<CameraSmartDetectAudioEvent> csda = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(CameraSmartDetectAudioEvent.class));
        TypeAdapter<CameraSmartDetectZoneEvent> csdz = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(CameraSmartDetectZoneEvent.class));
        TypeAdapter<CameraSmartDetectLineEvent> csdl = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(CameraSmartDetectLineEvent.class));
        TypeAdapter<CameraSmartDetectLoiterEvent> csdlo = gson.getDelegateAdapter(EventTypeAdapterFactory.this,
                TypeToken.get(CameraSmartDetectLoiterEvent.class));

        return (TypeAdapter<T>) new TypeAdapter<BaseEvent>() {
            @Override
            public void write(JsonWriter out, BaseEvent value) throws IOException {
                @SuppressWarnings({ "rawtypes", "null" })
                TypeAdapter<BaseEvent> delegate = (TypeAdapter) gson.getAdapter((Class) value.getClass());
                delegate.write(out, value);
            }

            @Override
            public BaseEvent read(JsonReader in) throws IOException {
                JsonObject obj = JsonParser.parseReader(in).getAsJsonObject();
                String type = obj.has("type") ? obj.get("type").getAsString() : "";
                switch (type) {
                    case "ring":
                        return ring.fromJsonTree(obj);
                    case "sensorExtremeValues":
                        return sev.fromJsonTree(obj);
                    case "sensorWaterLeak":
                        return swl.fromJsonTree(obj);
                    case "sensorTamper":
                        return st.fromJsonTree(obj);
                    case "sensorBatteryLow":
                        return sbl.fromJsonTree(obj);
                    case "sensorAlarm":
                        return sa.fromJsonTree(obj);
                    case "sensorOpened":
                        return so.fromJsonTree(obj);
                    case "sensorClosed":
                        return sc.fromJsonTree(obj);
                    case "sensorMotion":
                        return sm.fromJsonTree(obj);
                    case "lightMotion":
                        return lm.fromJsonTree(obj);
                    case "motion":
                        return cm.fromJsonTree(obj);
                    case "smartAudioDetect":
                        return csda.fromJsonTree(obj);
                    case "smartDetectZone":
                        return csdz.fromJsonTree(obj);
                    case "smartDetectLine":
                        return csdl.fromJsonTree(obj);
                    case "smartDetectLoiterZone":
                        return csdlo.fromJsonTree(obj);
                    default:
                        throw new IOException("Unknown event type '" + type + "' for Event payload");
                }
            }
        };
    }
}
