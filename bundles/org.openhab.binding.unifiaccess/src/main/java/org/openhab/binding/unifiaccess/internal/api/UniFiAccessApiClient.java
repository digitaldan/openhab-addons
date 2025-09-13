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
package org.openhab.binding.unifiaccess.internal.api;

import java.io.Closeable;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.unifiaccess.internal.dto.AccessPolicy;
import org.openhab.binding.unifiaccess.internal.dto.AccessPolicyHolidayGroup;
import org.openhab.binding.unifiaccess.internal.dto.AccessPolicySchedule;
import org.openhab.binding.unifiaccess.internal.dto.ApiResponse;
import org.openhab.binding.unifiaccess.internal.dto.Device;
import org.openhab.binding.unifiaccess.internal.dto.DeviceAccessMethodSettings;
import org.openhab.binding.unifiaccess.internal.dto.Door;
import org.openhab.binding.unifiaccess.internal.dto.DoorEmergencySettings;
import org.openhab.binding.unifiaccess.internal.dto.DoorLockRule;
import org.openhab.binding.unifiaccess.internal.dto.DoorUnlockRequest;
import org.openhab.binding.unifiaccess.internal.dto.NfcEnrollSession;
import org.openhab.binding.unifiaccess.internal.dto.NfcEnrollStatus;
import org.openhab.binding.unifiaccess.internal.dto.Notification;
import org.openhab.binding.unifiaccess.internal.dto.UniFiAccessHttpException;
import org.openhab.binding.unifiaccess.internal.dto.UniFiAccessParseException;
import org.openhab.binding.unifiaccess.internal.dto.User;
import org.openhab.binding.unifiaccess.internal.dto.Visitor;
import org.openhab.binding.unifiaccess.internal.dto.WebhookEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

/**
 * Minimal UniFi Access API client for openHAB bindings using Jetty.
 *
 * <p>
 * Design goals:
 * <ul>
 * <li>Reuse an existing {@link HttpClient} (managed by the
 * binding/lifecycle).</li>
 * <li>Wire-compatibility with the PDF API, using your Gson DTOs.</li>
 * <li>Handle both wrapped ({@code {code,data,msg}}) and raw JSON
 * responses.</li>
 * <li>Small, focused surface for the most common flows.</li>
 * </ul>
 *
 * <p>
 * Auth: By default we send {@code X-Auth-Token: <token>}. You can override or
 * add headers
 * via the builder. If your deployment uses Bearer tokens, call
 * {@link Builder#defaultHeader(String, String)} with
 * {@code Authorization, Bearer ...}.
 * </p>
 *
 * @author Dan Cunningham - Initial contribution
 */
public final class UniFiAccessApiClient implements Closeable {

    private Logger logger = LoggerFactory.getLogger(UniFiAccessApiClient.class);

    /* ============================== State ================================ */

    private final HttpClient httpClient;
    private final URI base;
    private final Gson gson;
    private final Duration timeout;
    private final Map<String, String> defaultHeaders;
    private final WebSocketClient wsClient;
    private  Session wsSession;
    private  long lastHeartbeatEpochMs;
    private  ScheduledExecutorService wsMonitorExecutor;
    private  ScheduledFuture<?> wsMonitorFuture;

    public UniFiAccessApiClient(HttpClient httpClient, URI base, Gson gson, Duration timeout, String token) {
        this.httpClient = httpClient;
        this.base = ensureTrailingSlash(base);
        this.gson = gson;
        this.timeout = timeout;
        this.defaultHeaders = Map.of("Authorization", "Bearer " + token, "Accept", "application/json");
        wsClient = new WebSocketClient(httpClient);
                try {
                    wsClient.start();
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to start Jetty ws client", e);
                }
    }

    @Override
    public synchronized void close() {
        try {
            Session s = wsSession;
            if (s != null) {
                try {
                    s.close();
                } finally {
                    wsSession = null;
                }
            }
        } catch (Exception e) {
            logger.debug("Error closing notifications WebSocket: {}", e.getMessage());
        }
        try {
            WebSocketClient c = wsClient;
            if (c != null) {
                try {
                    c.stop();
                } finally {
                    wsClient = null;
                }
            }
        } catch (Exception e) {
            logger.debug("Error stopping WebSocket client: {}", e.getMessage());
        }
        stopWsMonitor();
    }

    public List<User> getUsers() {
        Type wrapped = TypeToken
                .getParameterized(ApiResponse.class, TypeToken.getParameterized(List.class, User.class).getType())
                .getType();
        Type raw = TypeToken.getParameterized(List.class, User.class).getType();
        ContentResponse resp = execGet("/users");
        ensure2xx(resp, "getUsers");
        return parseMaybeWrapped(resp.getContentAsString(), wrapped, raw, "getUsers");
    }

    public User getUser(String userId) {
        Objects.requireNonNull(userId, "userId");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, User.class).getType();
        ContentResponse resp = execGet("/users/" + userId);
        ensure2xx(resp, "getUser");
        ApiResponse<User> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public Visitor createVisitor(Visitor payload) {
        Objects.requireNonNull(payload, "payload");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, Visitor.class).getType();
        ContentResponse resp = execPost("/visitors", payload);
        ensure2xx(resp, "createVisitor");
        ApiResponse<Visitor> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public Visitor updateVisitor(String visitorId, Visitor payload) {
        Objects.requireNonNull(visitorId, "visitorId");
        Objects.requireNonNull(payload, "payload");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, Visitor.class).getType();
        ContentResponse resp = execPut("/visitors/" + visitorId, payload);
        ensure2xx(resp, "updateVisitor");
        ApiResponse<Visitor> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public void deleteVisitor(String visitorId) {
        Objects.requireNonNull(visitorId, "visitorId");
        ContentResponse resp = execDelete("/visitors/" + visitorId);
        ensure2xx(resp, "deleteVisitor");
    }

    public List<AccessPolicy> getAccessPolicies() {
        Type wrapped = TypeToken.getParameterized(ApiResponse.class,
                TypeToken.getParameterized(List.class, AccessPolicy.class).getType()).getType();
        Type raw = TypeToken.getParameterized(List.class, AccessPolicy.class).getType();
        ContentResponse resp = execGet("/access-policies");
        ensure2xx(resp, "getAccessPolicies");
        return parseMaybeWrapped(resp.getContentAsString(), wrapped, raw, "getAccessPolicies");
    }

    public AccessPolicy createAccessPolicy(AccessPolicy policy) {
        Objects.requireNonNull(policy, "policy");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, AccessPolicy.class).getType();
        ContentResponse resp = execPost("/access-policies", policy);
        ensure2xx(resp, "createAccessPolicy");
        ApiResponse<AccessPolicy> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public AccessPolicy updateAccessPolicy(String policyId, AccessPolicy policy) {
        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(policy, "policy");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, AccessPolicy.class).getType();
        ContentResponse resp = execPut("/access-policies/" + policyId, policy);
        ensure2xx(resp, "updateAccessPolicy");
        ApiResponse<AccessPolicy> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public AccessPolicySchedule createSchedule(AccessPolicySchedule schedule) {
        Objects.requireNonNull(schedule, "schedule");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, AccessPolicySchedule.class).getType();
        ContentResponse resp = execPost("/schedules", schedule);
        ensure2xx(resp, "createSchedule");
        ApiResponse<AccessPolicySchedule> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public AccessPolicySchedule updateSchedule(String scheduleId, AccessPolicySchedule schedule) {
        Objects.requireNonNull(scheduleId, "scheduleId");
        Objects.requireNonNull(schedule, "schedule");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, AccessPolicySchedule.class).getType();
        ContentResponse resp = execPut("/schedules/" + scheduleId, schedule);
        ensure2xx(resp, "updateSchedule");
        ApiResponse<AccessPolicySchedule> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public AccessPolicyHolidayGroup createHolidayGroup(AccessPolicyHolidayGroup hg) {
        Objects.requireNonNull(hg, "holidayGroup");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, AccessPolicyHolidayGroup.class).getType();
        ContentResponse resp = execPost("/holiday-groups", hg);
        ensure2xx(resp, "createHolidayGroup");
        ApiResponse<AccessPolicyHolidayGroup> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public List<Device> getDevices() {
        Type wrapped = TypeToken
                .getParameterized(ApiResponse.class, TypeToken.getParameterized(List.class, Device.class).getType())
                .getType();
        Type raw = TypeToken.getParameterized(List.class, Device.class).getType();
        ContentResponse resp = execGet("/devices");
        ensure2xx(resp, "getDevices");
        String json = resp.getContentAsString();
        return parseListMaybeWrappedWithKeys(json, wrapped, raw, "getDevices", "devices");
    }

    public DeviceAccessMethodSettings getDeviceAccessMethodSettings(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, DeviceAccessMethodSettings.class).getType();
        ContentResponse resp = execGet("/devices/" + deviceId + "/settings");
        ensure2xx(resp, "getDeviceAccessMethodSettings");
        ApiResponse<DeviceAccessMethodSettings> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public DoorEmergencySettings getDoorEmergencySettings(String doorId) {
        Objects.requireNonNull(doorId, "doorId");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, DoorEmergencySettings.class).getType();
        ContentResponse resp = execGet("/doors/" + doorId + "/settings/emergency");
        ensure2xx(resp, "getDoorEmergencySettings");
        ApiResponse<DoorEmergencySettings> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public void setDoorEmergencySettings(String doorId, DoorEmergencySettings settings) {
        Objects.requireNonNull(doorId, "doorId");
        Objects.requireNonNull(settings, "settings");
        ContentResponse resp = execPut("/doors/" + doorId + "/settings/emergency", settings);
        ensure2xx(resp, "setDoorEmergencySettings");
    }

    /**
     * Starts a card enrollment session; returns session id/metadata.
     */
    public NfcEnrollSession createNfcEnrollSession() {
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, NfcEnrollSession.class).getType();
        ContentResponse resp = execPost("/nfc/enroll/session", Map.of());
        ensure2xx(resp, "createNfcEnrollSession");
        ApiResponse<NfcEnrollSession> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public NfcEnrollStatus getNfcEnrollStatus(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, NfcEnrollStatus.class).getType();
        ContentResponse resp = execGet("/nfc/enroll/session/" + sessionId);
        ensure2xx(resp, "getNfcEnrollStatus");
        ApiResponse<NfcEnrollStatus> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public void deleteNfcEnrollSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        ContentResponse resp = execDelete("/nfc/enroll/session/" + sessionId);
        ensure2xx(resp, "deleteNfcEnrollSession");
    }

    public List<WebhookEndpoint> listWebhooks() {
        Type wrapped = TypeToken.getParameterized(ApiResponse.class,
                TypeToken.getParameterized(List.class, WebhookEndpoint.class).getType()).getType();
        Type raw = TypeToken.getParameterized(List.class, WebhookEndpoint.class).getType();
        ContentResponse resp = execGet("/webhooks");
        ensure2xx(resp, "listWebhooks");
        return parseMaybeWrapped(resp.getContentAsString(), wrapped, raw, "listWebhooks");
    }

    public WebhookEndpoint createWebhook(WebhookEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        Type wrapped = TypeToken.getParameterized(ApiResponse.class, WebhookEndpoint.class).getType();
        ContentResponse resp = execPost("/webhooks", endpoint);
        ensure2xx(resp, "createWebhook");
        ApiResponse<WebhookEndpoint> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    public void deleteWebhook(String webhookId) {
        Objects.requireNonNull(webhookId, "webhookId");
        ContentResponse resp = execDelete("/webhooks/" + webhookId);
        ensure2xx(resp, "deleteWebhook");
    }

    public List<Door> getDoors() {
        var wrapped = com.google.gson.reflect.TypeToken.getParameterized(ApiResponse.class,
                TypeToken.getParameterized(List.class, Door.class).getType()).getType();
        var raw = TypeToken.getParameterized(List.class, Door.class).getType();
        var resp = execGet("/doors");
        ensure2xx(resp, "getDoors");
        String json = resp.getContentAsString();
        return parseListMaybeWrappedWithKeys(json, wrapped, raw, "getDoors", "doors");
    }

    public Door getDoor(String doorId) {
        Objects.requireNonNull(doorId, "doorId");
        var wrapped = TypeToken.getParameterized(ApiResponse.class, Door.class).getType();
        var resp = execGet("/doors/" + doorId);
        ensure2xx(resp, "getDoor");
        ApiResponse<Door> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar != null ? ar.data : null;
    }

    /**
     * Remote unlock: optionally provide actor id/name and arbitrary passthrough
     * "extra".
     */
    public boolean unlockDoor(String doorId, DoorUnlockRequest body) {
        Objects.requireNonNull(doorId, "doorId");
        var resp = execPut("/doors/" + doorId + "/unlock", body == null ? new HashMap<>() : body);
        ensure2xx(resp, "unlockDoor");
        var wrapped = TypeToken.getParameterized(ApiResponse.class, Object.class).getType();
        ApiResponse<?> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar == null || ar.isSuccess();
    }

    public boolean unlockDoor(String doorId, String actorId, String actorName, Map<String, Object> extra) {
        return unlockDoor(doorId, new DoorUnlockRequest(actorId, actorName, extra));
    }

    public boolean setDoorLockRule(String doorId, DoorLockRule rule) {
        Objects.requireNonNull(doorId, "doorId");
        Objects.requireNonNull(rule, "rule");
        var resp = execPut("/doors/" + doorId + "/lock_rule", rule);
        ensure2xx(resp, "setDoorLockRule");
        var wrapped = TypeToken.getParameterized(ApiResponse.class, Object.class).getType();
        ApiResponse<?> ar = gson.fromJson(resp.getContentAsString(), wrapped);
        return ar == null || ar.isSuccess();
    }

    public boolean keepDoorUnlocked(String doorId) {
        return setDoorLockRule(doorId, DoorLockRule.keepUnlock());
    }

    public boolean keepDoorLocked(String doorId) {
        return setDoorLockRule(doorId, DoorLockRule.keepLock());
    }

    public boolean unlockForMinutes(String doorId, int minutes) {
        if (minutes <= 0)
            throw new IllegalArgumentException("minutes must be > 0");
        return setDoorLockRule(doorId, DoorLockRule.customMinutes(minutes));
    }

    public boolean resetDoorLockRule(String doorId) {
        return setDoorLockRule(doorId, DoorLockRule.reset());
    }

    /** End an active keep-unlock/custom early (lock immediately). */
    public boolean lockEarly(String doorId) {
        return setDoorLockRule(doorId, DoorLockRule.lockEarly());
    }

    public synchronized void openNotifications(Runnable onOpen, Consumer<Notification> onMessage,
            Consumer<Throwable> onError, BiConsumer<Integer, String> onClosed) {
        if (wsSession != null && wsSession.isOpen()) {
            return;
        }
        try {
            if (wsClient == null) {
                wsClient = new WebSocketClient(httpClient);
                wsClient.start();
            }
            URI wsUri = toWebSocketUri("devices/notifications");
            ClientUpgradeRequest req = new ClientUpgradeRequest();
            defaultHeaders.forEach(req::setHeader);
            req.setHeader("Upgrade", "websocket");
            req.setHeader("Connection", "Upgrade");

            WebSocketAdapter socket = new WebSocketAdapter() {
                @Override
                public void onWebSocketConnect(Session sess) {
                    super.onWebSocketConnect(sess);
                    logger.info("Notifications WebSocket connected: {}", wsUri);
                    wsSession = sess;
                    try {
                        onOpen.run();
                    } catch (Exception ignored) {
                    }
                    lastHeartbeatEpochMs = System.currentTimeMillis();
                }

                @Override
                public void onWebSocketText(String message) {
                    try {
                        if (message != null && !message.isEmpty()) {
                            if (message.charAt(0) == '"') {
                                String normalized = message.trim();
                                lastHeartbeatEpochMs = System.currentTimeMillis();
                                logger.trace("Notifications heartbeat received: {}", normalized);
                                return;
                            } else {
                                Notification note = gson.fromJson(message, Notification.class);
                                if (note != null) {
                                    onMessage.accept(note);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Notifications handler failed: {}", e.getMessage());
                        try {
                            onError.accept(e);
                        } catch (Exception ignored) {
                        }
                    }
                }

                @Override
                public void onWebSocketError(Throwable cause) {
                    logger.warn("Notifications WebSocket error: {}", cause.getMessage(), cause);
                    try {
                        onError.accept(cause);
                    } catch (Exception ignored) {
                    }
                }

                @Override
                public void onWebSocketClose(int statusCode, String reason) {
                    logger.info("Notifications WebSocket closed: {} - {}", statusCode, reason);
                    try {
                        onClosed.accept(statusCode, reason);
                    } catch (Exception ignored) {
                    }
                    try {
                        wsSession = null;
                    } catch (Exception ignored) {
                    }
                }
            };

            wsClient.connect(socket, wsUri, req).get(15, TimeUnit.SECONDS);
            startWsMonitor();
        } catch (Exception e) {
            throw new UniFiAccessHttpException("WebSocket connect failed: " + e.getMessage(), e);
        }
    }

    private Request newRequest(HttpMethod method, String path, Consumer<Request> customizer) {
        URI uri = base.resolve(path.startsWith("/") ? path.substring(1) : path);
        Request req = httpClient.newRequest(uri).method(method).header(HttpHeader.ACCEPT, "application/json");
        // Default headers
        defaultHeaders.forEach(req::header);
        logger.debug("path: {} base: {} uri: {}", path, base, uri);
        req.getHeaders().forEach(header -> logger.debug("header {}: {}", header.getName(), header.getValue()));
        // Allow caller customizations (body, additional headers, etc.)
        if (customizer != null)
            customizer.accept(req);
        // Timeout
        req.timeout(timeout.toSeconds(), TimeUnit.SECONDS);
        return req;
    }

    private static URI ensureTrailingSlash(URI uri) {
        String s = uri.toString();
        return s.endsWith("/") ? uri : URI.create(s + "/");
    }

    private void ensure2xx(ContentResponse resp, String action) {
        if (logger.isTraceEnabled()) {
            logger.trace("ensure2xx status: {} resp: {}", resp.getStatus(), resp.getContentAsString());
        }
        int sc = resp.getStatus();
        if (sc < 200 || sc >= 300) {
            String msg = resp.getContentAsString();
            throw new UniFiAccessHttpException(sc, "Non 2xx response for " + action + ": " + sc + " - " + msg);
        }
    }

    /**
     * Parse responses that might be either:
     * <ul>
     * <li>Wrapped: {@code {"code":0,"data":...,"msg":"ok"}}</li>
     * <li>Raw: {@code {...}} or {@code [...]}</li>
     * </ul>
     */
    private <T> T parseMaybeWrapped(String json, Type wrappedType, Type rawType, String action) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            // Try wrapped first
            ApiResponse<T> wrapped = gson.fromJson(json, wrappedType);
            if (wrapped != null && (wrapped.data != null || wrapped.msg != null || wrapped.code != null)) {
                return wrapped.data;
            }
        } catch (Exception ignored) {
            // fallthrough to raw
        }
        try {
            return gson.fromJson(json, rawType);
        } catch (Exception e) {
            throw new UniFiAccessParseException("Failed to parse response for " + action + ": " + e.getMessage(), e);
        }
    }

    private String toJson(Object body) {
        return (body == null) ? "" : gson.toJson(body);
    }

    /**
     * For endpoints that conceptually return a list but may be wrapped or nested
     * inside an object, try multiple shapes:
     * - ApiResponse<List<E>>
     * - raw JSON array
     * - { "data": [ ... ] }
     * - { "data": { "list"|"items"|"records": [ ... ] } }
     * - { altArrayKeys[0]|altArrayKeys[1]|...: [ ... ] }
     */
    private <E> List<E> parseListMaybeWrappedWithKeys(String json, Type wrappedListType, Type rawListType,
            String action, String... altArrayKeys) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }

        // 1) Try standard wrapped format
        try {
            ApiResponse<List<E>> wrapped = gson.fromJson(json, wrappedListType);
            if (wrapped != null && wrapped.data != null && !wrapped.data.isEmpty()) {
                return wrapped.data;
            }
            // If code/msg present but empty data, still accept empty list
            if (wrapped != null && (wrapped.msg != null || wrapped.code != null) && wrapped.data == null) {
                return Collections.emptyList();
            }
        } catch (Exception ignored) {
        }

        // 2) If it's already a raw array (possibly nested arrays)
        try {
            JsonElement root = JsonParser.parseString(json);
            if (root.isJsonArray()) {
                JsonElement flat = flattenArrayIfNeeded(root.getAsJsonArray());
                return gson.fromJson(flat, rawListType);
            }

            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();

                Function<JsonElement, List<E>> parseArray = je -> gson.fromJson(je,
                        rawListType);
                String[] arrayKeys = new String[] { "list", "items", "records", "rows" };

                // 3) Containers such as data/result/payload
                for (String container : new String[] { "data", "result", "payload" }) {
                    if (!obj.has(container)) {
                        continue;
                    }
                    JsonElement data = obj.get(container);
                    if (data.isJsonArray()) {
                        JsonElement flat = flattenArrayIfNeeded(data.getAsJsonArray());
                        return parseArray.apply(flat);
                    }
                    if (data.isJsonObject()) {
                        JsonObject dObj = data.getAsJsonObject();
                        // common array fields directly under container
                        for (String k : arrayKeys) {
                            if (dObj.has(k) && dObj.get(k).isJsonArray()) {
                                JsonElement flat = flattenArrayIfNeeded(dObj.get(k).getAsJsonArray());
                                return parseArray.apply(flat);
                            }
                        }
                        // altArrayKeys under container (e.g., devices)
                        if (altArrayKeys != null) {
                            for (String key : altArrayKeys) {
                                if (!dObj.has(key)) {
                                    continue;
                                }
                                JsonElement alt = dObj.get(key);
                                if (alt.isJsonArray()) {
                                    JsonElement flat = flattenArrayIfNeeded(alt.getAsJsonArray());
                                    return parseArray.apply(flat);
                                }
                                if (alt.isJsonObject()) {
                                    JsonObject aObj = alt.getAsJsonObject();
                                    for (String k : arrayKeys) {
                                        if (aObj.has(k) && aObj.get(k).isJsonArray()) {
                                            JsonElement flat = flattenArrayIfNeeded(aObj.get(k).getAsJsonArray());
                                            return parseArray.apply(flat);
                                        }
                                    }
                                }
                            }
                        }
                        // as a last resort: first array value under container
                        for (var entry : dObj.entrySet()) {
                            if (entry.getValue().isJsonArray()) {
                                JsonElement flat = flattenArrayIfNeeded(entry.getValue().getAsJsonArray());
                                return parseArray.apply(flat);
                            }
                        }
                    }
                }

                // 4) { altArrayKey: [ ... ] } at root
                if (altArrayKeys != null) {
                    for (String key : altArrayKeys) {
                        if (!obj.has(key)) {
                            continue;
                        }
                        JsonElement alt = obj.get(key);
                        if (alt.isJsonArray()) {
                            JsonElement flat = flattenArrayIfNeeded(alt.getAsJsonArray());
                            return parseArray.apply(flat);
                        }
                        if (alt.isJsonObject()) {
                            JsonObject aObj = alt.getAsJsonObject();
                            for (String k : arrayKeys) {
                                if (aObj.has(k) && aObj.get(k).isJsonArray()) {
                                    JsonElement flat = flattenArrayIfNeeded(aObj.get(k).getAsJsonArray());
                                    return parseArray.apply(flat);
                                }
                            }
                            for (var entry : aObj.entrySet()) {
                                if (entry.getValue().isJsonArray()) {
                                    JsonElement flat = flattenArrayIfNeeded(entry.getValue().getAsJsonArray());
                                    return parseArray.apply(flat);
                                }
                            }
                        }
                    }
                }

                // 5) any direct array at root
                for (var entry : obj.entrySet()) {
                    if (entry.getValue().isJsonArray()) {
                        JsonElement flat = flattenArrayIfNeeded(entry.getValue().getAsJsonArray());
                        return parseArray.apply(flat);
                    }
                }
            }
        } catch (Exception ignored) {
        }

        try {
            String snippet = json.length() > 512 ? json.substring(0, 512) + "..." : json;
            logger.warn("{}: unexpected JSON shape: {}", action, snippet);
        } catch (Exception ignored2) {
        }
        throw new UniFiAccessParseException("Failed to parse list response for " + action + ": unexpected JSON shape",
                null);
    }

    private static JsonElement flattenArrayIfNeeded(JsonArray array) {
        boolean hasNested = false;
        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).isJsonArray()) {
                hasNested = true;
                break;
            }
        }
        if (!hasNested) {
            return array;
        }
        JsonArray flat = new JsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement el = array.get(i);
            if (el.isJsonArray()) {
                JsonArray inner = el.getAsJsonArray();
                for (int j = 0; j < inner.size(); j++) {
                    flat.add(inner.get(j));
                }
            } else if (el.isJsonObject()) {
                flat.add(el);
            }
        }
        return flat;
    }

    private ContentResponse execGet(String path) {
        try {
            return newRequest(HttpMethod.GET, path, null).send();
        } catch (Exception e) {
            throw new UniFiAccessHttpException("GET failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private ContentResponse execDelete(String path) {
        try {
            return newRequest(HttpMethod.DELETE, path, null).send();
        } catch (Exception e) {
            throw new UniFiAccessHttpException("DELETE failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private ContentResponse execPost(String path, Object body) {
        try {
            return newRequest(HttpMethod.POST, path, req -> {
                String json = toJson(body);
                req.header(HttpHeader.CONTENT_TYPE, "application/json");
                req.content(new org.eclipse.jetty.client.util.StringContentProvider(json, StandardCharsets.UTF_8));
            }).send();
        } catch (Exception e) {
            throw new UniFiAccessHttpException("POST failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private ContentResponse execPut(String path, Object body) {
        try {
            return newRequest(HttpMethod.PUT, path, req -> {
                String json = toJson(body);
                req.header(HttpHeader.CONTENT_TYPE, "application/json");
                req.content(new org.eclipse.jetty.client.util.StringContentProvider(json, StandardCharsets.UTF_8));
            }).send();
        } catch (Exception e) {
            throw new UniFiAccessHttpException("PUT failed for " + path + ": " + e.getMessage(), e);
        }
    }

    private URI toWebSocketUri(String relativePath) {
        String scheme = "wss";
        if ("http".equalsIgnoreCase(base.getScheme())) {
            scheme = "ws";
        }
        String path = base.getPath();
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        String fullPath = path + relativePath;
        return URI
                .create(scheme + "://" + base.getHost() + (base.getPort() > 0 ? ":" + base.getPort() : "") + fullPath);
    }

    private synchronized void startWsMonitor() {
        if (wsMonitorExecutor == null) {
            wsMonitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "UA-WebSocket-Monitor");
                t.setDaemon(true);
                return t;
            });
        }
        if (wsMonitorFuture == null || wsMonitorFuture.isCancelled()) {
            wsMonitorFuture = wsMonitorExecutor.scheduleAtFixedRate(() -> {
                try {
                    Session s = wsSession;
                    if (s != null && s.isOpen()) {
                        long sinceMs = System.currentTimeMillis() - lastHeartbeatEpochMs;
                        if (sinceMs > 10_000L) {
                            logger.debug("Notifications heartbeat missing ({} ms). Reconnecting...", sinceMs);
                            try {
                                s.close();
                            } catch (Exception ignored) {
                            } finally {
                                wsSession = null;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.debug("WS monitor error: {}", e.getMessage());
                }
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    private synchronized void stopWsMonitor() {
        try {
            ScheduledFuture<?> f = wsMonitorFuture;
            if (f != null) {
                f.cancel(true);
                wsMonitorFuture = null;
            }
        } catch (Exception ignored) {
        }
        try {
            ScheduledExecutorService ex = wsMonitorExecutor;
            if (ex != null) {
                ex.shutdownNow();
                wsMonitorExecutor = null;
            }
        } catch (Exception ignored) {
        }
    }
}
