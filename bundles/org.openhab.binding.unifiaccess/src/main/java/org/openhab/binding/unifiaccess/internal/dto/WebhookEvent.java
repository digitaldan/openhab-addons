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
package org.openhab.binding.unifiaccess.internal.dto;

import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Generic Webhook event envelope with typed {@code data}.
 *
 * <p>
 * Use as WebhookEvent&lt;YourEventData&gt;. For example:
 * {@code WebhookEvent<WebhookEvent.DoorUnlockEventData>}.
 * </p>
 *
 * <p>
 * Event names commonly look like "access.door.unlock", etc.
 * </p>
 *
 * @author Dan Cunningham - Initial contribution
 */
public class WebhookEvent<T> {
    /** Event name string, e.g., "access.door.unlock". */
    public String event;

    /** Primary object related to the event (door, device, etc.). */
    public String eventObjectId;

    /** Receiver (webhook endpoint) ID when present. */
    public String receiverId;

    /** Whether this event was saved to history. */
    public Boolean saveToHistory;

    /** Event-specific payload. */
    public @Nullable T data;

    /**
     * Optional event creation time (varies by event; kept as String and
     * parsed via {@link #eventInstant()} when present).
     */
    public String createdAt;

    /** Null-safe Instant conversion for {@code created_at} if provided. */
    public Instant eventInstant() {
        return UaTime.parseInstant(createdAt);
    }

    /** True if the backend indicates this was persisted. */
    public boolean isPersisted() {
        return Boolean.TRUE.equals(saveToHistory);
    }

    /* ===================== Common Event Data Shapes ===================== */

    /**
     * Example payload for door unlock events.
     * Use as {@code WebhookEvent<WebhookEvent.DoorUnlockEventData>}.
     */
    public static class DoorUnlockEventData {
        public Location location;
        public Device device;
        /** Sometimes present as "start_time" or "time". Keep both. */
        public String startTime;
        public String time;

        /** Instant view of the event time (tries start_time, then time). */
        public Instant startInstant() {
            Instant i = UaTime.parseInstant(startTime);
            return (i != null) ? i : UaTime.parseInstant(time);
        }
    }

    /** Minimal location summary included in some events. */
    public static class Location {
        public String id;
        public String locationType; // e.g., "door"
        public String name;
        public String upId;
        public Extras extras;
    }

    /** Optional extras bag (fields vary by event). */
    public static class Extras {
        public String doorThumbnail;
    }

    /** Minimal device summary commonly sent with events. */
    public static class Device {
        public String id;
        public String name;
        public String alias;
        public String ip;
        public String deviceType;
        public String firmware;
        public String version;
        public String startTime;

        /** Convert device boot/start time to Instant when available. */
        public Instant startInstant() {
            return UaTime.parseInstant(startTime);
        }
    }
}
