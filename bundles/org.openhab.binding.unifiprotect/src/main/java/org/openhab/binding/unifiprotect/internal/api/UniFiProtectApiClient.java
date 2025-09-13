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
package org.openhab.binding.unifiprotect.internal.api;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.ClientUpgradeRequest;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.openhab.binding.unifiprotect.internal.dto.*;
import org.openhab.binding.unifiprotect.internal.dto.ws.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

/**
 * Client for the UniFi Protect Integration API.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UniFiProtectApiClient implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(UniFiProtectApiClient.class);
    private final HttpClient httpClient;
    private final WebSocketClient wsClient;
    private final Gson gson;
    private final URI baseUri; // https://host:port/integration
    private final Map<String, String> defaultHeaders;
    private final ScheduledExecutorService heartbeatExecutor;

    public UniFiProtectApiClient(HttpClient httpClient, URI baseUri, Gson gson, String token) {
        this.httpClient = httpClient;
        this.baseUri = ensureTrailingSlash(baseUri);
        this.gson = gson;
        this.defaultHeaders = Map.of("X-API-KEY", token, "Accept", "application/json");
        this.wsClient = new WebSocketClient(httpClient);
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "unifiprotect-ws-heartbeat");
            t.setDaemon(true);
            return t;
        });
        try {
            this.wsClient.start();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start Jetty clients", e);
        }
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing UniFiProtectApiClient");
        Exception ex = null;
        try {
            wsClient.stop();
        } catch (Exception e) {
            logger.debug("Failed to stop WS client", e);
            ex = e;
        }
        try {
            heartbeatExecutor.shutdownNow();
        } catch (Exception e) {
            logger.debug("Failed to stop heartbeat executor", e);
            if (ex == null) {
                ex = e;
            }
        }
        if (ex != null) {
            throw new IOException("Failed to stop client", ex);
        }
    }

    public ProtectVersionInfo getMetaInfo() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/meta/info"), null);
        ensure2xx(resp);
        return parseJson(resp, ProtectVersionInfo.class);
    }

    public Viewer getViewer(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/viewers/" + id), null);
        ensure2xx(resp);
        return parseJson(resp, Viewer.class);
    }

    public Viewer patchViewer(String id, JsonObject patch) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.PATCH, "/v1/viewers/" + id), patch);
        ensure2xx(resp);
        return parseJson(resp, Viewer.class);
    }

    public List<Viewer> listViewers() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/viewers"), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<Viewer>>() {
        }.getType());
    }

    public Liveview getLiveview(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/liveviews/" + id), null);
        ensure2xx(resp);
        return parseJson(resp, Liveview.class);
    }

    public Liveview patchLiveview(String id, Liveview patch) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.PATCH, "/v1/liveviews/" + id), patch);
        ensure2xx(resp);
        return parseJson(resp, Liveview.class);
    }

    public List<Liveview> listLiveviews() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/liveviews"), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<Liveview>>() {
        }.getType());
    }

    public Liveview createLiveview(Liveview body) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/liveviews"), body);
        ensure2xx(resp);
        return parseJson(resp, Liveview.class);
    }

    public void ptzPatrolStart(String cameraId, String slot) throws IOException {
        ContentResponse resp = sendJson(
                newRequest(HttpMethod.POST, "/v1/cameras/" + cameraId + "/ptz/patrol/start/" + slot), null);
        ensure2xx(resp);
    }

    public void ptzPatrolStop(String cameraId) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/cameras/" + cameraId + "/ptz/patrol/stop"),
                null);
        ensure2xx(resp);
    }

    public void ptzGotoPreset(String cameraId, String slot) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/cameras/" + cameraId + "/ptz/goto/" + slot),
                null);
        ensure2xx(resp);
    }

    public void triggerAlarmWebhook(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/alarm-manager/webhook/" + id), null);
        ensure2xx(resp);
    }

    public Light getLight(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/lights/" + id), null);
        ensure2xx(resp);
        return parseJson(resp, Light.class);
    }

    public Light patchLight(String id, JsonObject patch) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.PATCH, "/v1/lights/" + id), patch);
        ensure2xx(resp);
        return parseJson(resp, Light.class);
    }

    public List<Light> listLights() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/lights"), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<Light>>() {
        }.getType());
    }

    public Camera getCamera(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/cameras/" + id), null);
        ensure2xx(resp);
        return parseJson(resp, Camera.class);
    }

    public Camera patchCamera(String id, JsonObject patch) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.PATCH, "/v1/cameras/" + id), patch);
        logger.debug("Patch camera obj {} response: {}", patch, resp.getStatus());
        ensure2xx(resp);
        return parseJson(resp, Camera.class);
    }

    public List<Camera> listCameras() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/cameras"), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<Camera>>() {
        }.getType());
    }

    public CreatedRtspsStreams createRtspsStream(String id, List<ChannelQuality> qualities) throws IOException {
        JsonObject body = new JsonObject();
        body.add("qualities", gson.toJsonTree(qualities));
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/cameras/" + id + "/rtsps-stream"), body);
        ensure2xx(resp);
        return parseJson(resp, CreatedRtspsStreams.class);
    }

    public void deleteRtspsStream(String id, List<ChannelQuality> qualities) throws IOException {
        String query = "?qualities=" + urlEncodeList(qualities);
        ContentResponse resp = sendJson(newRequest(HttpMethod.DELETE, "/v1/cameras/" + id + "/rtsps-stream" + query),
                null);
        ensure2xx(resp);
    }

    public ExistingRtspsStreams getRtspsStream(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/cameras/" + id + "/rtsps-stream"), null);
        ensure2xx(resp);
        return parseJson(resp, ExistingRtspsStreams.class);
    }

    public byte[] getSnapshot(String id, Boolean highQuality) throws IOException {
        String query = "?highQuality=" + highQuality.toString();
        Request req = newRequest(HttpMethod.GET, "/v1/cameras/" + id + "/snapshot" + query);
        req.header(HttpHeader.ACCEPT, "image/jpeg");
        ContentResponse resp = sendJson(req, null);
        ensure2xx(resp);
        return resp.getContent();
    }

    public Camera disableMicPermanently(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/cameras/" + id + "/disable-mic-permanently"),
                null);
        ensure2xx(resp);
        return parseJson(resp, Camera.class);
    }

    public TalkbackSession createTalkbackSession(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.POST, "/v1/cameras/" + id + "/talkback-session"), null);
        ensure2xx(resp);
        return parseJson(resp, TalkbackSession.class);
    }

    public Sensor getSensor(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/sensors/" + id), null);
        ensure2xx(resp);
        return parseJson(resp, Sensor.class);
    }

    public Sensor patchSensor(String id, JsonObject patch) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.PATCH, "/v1/sensors/" + id), patch);
        ensure2xx(resp);
        return parseJson(resp, Sensor.class);
    }

    public List<Sensor> listSensors() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/sensors"), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<Sensor>>() {
        }.getType());
    }

    public Nvr getNvr() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/nvrs"), null);
        ensure2xx(resp);
        return parseJson(resp, Nvr.class);
    }

    public FileSchema uploadFile(AssetFileType type, String filename, String contentType, InputStream data)
            throws IOException {
        String path = "/v1/files/" + toPathEnum(type);
        MultiPartContentProvider multi = new MultiPartContentProvider();
        HttpFields partHeaders = new HttpFields();
        partHeaders.put(HttpHeader.CONTENT_TYPE, contentType);
        multi.addFilePart("file", filename, new InputStreamContentProvider(data), partHeaders);
        multi.close();
        Request req = newRequest(HttpMethod.POST, path);
        req.content(multi);
        try {
            ContentResponse resp = req.send();
            ensure2xx(resp);
            return parseJson(resp, FileSchema.class);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new IOException("Failed to upload file", e);
        }
    }

    public List<FileSchema> listFiles(AssetFileType type) throws IOException {
        String path = "/v1/files/" + toPathEnum(type);
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, path), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<FileSchema>>() {
        }.getType());
    }

    public Chime getChime(String id) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/chimes/" + id), null);
        ensure2xx(resp);
        return parseJson(resp, Chime.class);
    }

    public Chime patchChime(String id, JsonObject patch) throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.PATCH, "/v1/chimes/" + id), patch);
        ensure2xx(resp);
        return parseJson(resp, Chime.class);
    }

    public List<Chime> listChimes() throws IOException {
        ContentResponse resp = sendJson(newRequest(HttpMethod.GET, "/v1/chimes"), null);
        ensure2xx(resp);
        return parseJson(resp, new TypeToken<List<Chime>>() {
        }.getType());
    }

    public CompletableFuture<Session> subscribeDevices(Consumer<DeviceAdd> onAdd, Consumer<DeviceUpdate> onUpdate,
            Consumer<DeviceRemove> onRemove, Runnable onOpen, BiConsumer<Integer, String> onClosed,
            Consumer<Throwable> onError) {
        return connectWebSocket("/v1/subscribe/devices", text -> {
            logger.trace("WebSocket devices message: {}", text);
            // The union uses discriminator property "type": add|update|remove
            JsonObject obj = gson.fromJson(text, JsonObject.class);
            if (obj == null) {
                logger.trace("WebSocket devices malformed message: {}", text);
                return;
            }
            String type = obj.get("type").getAsString();
            switch (type) {
                case "add": {
                    DeviceAdd payload = gson.fromJson(obj, DeviceAdd.class);
                    if (payload != null) {
                        onAdd.accept(payload);
                    }
                }
                    break;
                case "update": {
                    DeviceUpdate payload = gson.fromJson(obj, DeviceUpdate.class);
                    if (payload != null) {
                        onUpdate.accept(payload);
                    }
                }
                    break;
                case "remove": {
                    DeviceRemove payload = gson.fromJson(obj, DeviceRemove.class);
                    if (payload != null) {
                        onRemove.accept(payload);
                    }
                }
                    break;
                default:
                    // ignore unknown
            }
        }, onOpen, onClosed, onError);
    }

    public CompletableFuture<Session> subscribeEvents(Consumer<EventAdd> onAdd, Consumer<EventUpdate> onUpdate,
            Runnable onOpen, java.util.function.BiConsumer<Integer, String> onClosed, Consumer<Throwable> onError) {
        return connectWebSocket("/v1/subscribe/events", text -> {
            logger.trace("WebSocket events message: {}", text);
            JsonObject obj = gson.fromJson(text, JsonObject.class);
            if (obj == null) {
                logger.trace("WebSocket events malformed message: {}", text);
                return;
            }
            String type = obj.get("type").getAsString();
            if ("add".equals(type)) {
                EventAdd payload = gson.fromJson(obj, EventAdd.class);
                if (payload != null) {
                    onAdd.accept(payload);
                }
            } else if ("update".equals(type)) {
                EventUpdate payload = gson.fromJson(obj, EventUpdate.class);
                if (payload != null) {
                    onUpdate.accept(payload);
                }
            }
        }, onOpen, onClosed, onError);
    }

    private void ensure2xx(ContentResponse resp) throws IOException {
        int sc = resp.getStatus();
        if (sc < 200 || sc >= 300) {
            String body = resp.getContentAsString();
            try {
                GenericError ge = gson.fromJson(body, GenericError.class);
                if (ge != null) {
                    throw new IOException("HTTP " + resp.getStatus() + ": " + ge.name + ": " + ge.error);
                } else {
                    throw new IOException("HTTP " + resp.getStatus() + ": " + body);
                }
            } catch (Exception e) {
                throw new IOException("HTTP " + resp.getStatus() + ": " + body);
            }
        }
    }

    private Request newRequest(HttpMethod method, String path) {
        URI uri = resolvePath(path);
        logger.trace("New request {} {} {}", method, path, uri);
        Request request = httpClient.newRequest(uri).method(method).header(HttpHeader.ACCEPT, "application/json")
                .timeout(30, TimeUnit.SECONDS);
        for (Map.Entry<String, String> h : defaultHeaders.entrySet()) {
            request.header(h.getKey(), h.getValue());
        }
        return request;
    }

    private <T> T parseJson(ContentResponse resp, Class<T> clazz) throws IOException {
        try {
            String json = resp.getContentAsString();
            logger.trace("Parsing JSON {}", json);
            return gson.fromJson(json, clazz);
        } catch (Exception e) {
            throw new IOException("Failed to parse JSON to " + clazz.getSimpleName(), e);
        }
    }

    private <T> T parseJson(ContentResponse resp, Type type) throws IOException {
        try {
            String json = resp.getContentAsString();
            logger.trace("Parsing JSON {}", json);
            return gson.fromJson(json, type);
        } catch (Exception e) {
            throw new IOException("Failed to parse JSON", e);
        }
    }

    private ContentResponse sendJson(Request req, @Nullable Object body) throws IOException {
        if (body != null) {
            String json = gson.toJson(body);
            req.header(HttpHeader.CONTENT_TYPE, "application/json");
            req.content(new BytesContentProvider(json.getBytes(StandardCharsets.UTF_8)));
        }
        try {
            return req.send();
        } catch (Exception e) {
            throw new IOException("HTTP request failed", e);
        }
    }

    private CompletableFuture<Session> connectWebSocket(String path, Consumer<String> onText, Runnable onOpen,
            BiConsumer<Integer, String> onClosed, Consumer<Throwable> onError) {
        CompletableFuture<Session> future = new CompletableFuture<>();
        try {
            // Build WS(S) URI from HTTP(S) base
            URI httpUri = resolvePath(path);
            URI wsUri = toWebSocketUri(httpUri);
            logger.debug("Connecting WebSocket to {}", wsUri);

            ClientUpgradeRequest upgrade = new ClientUpgradeRequest();
            for (Map.Entry<String, String> h : defaultHeaders.entrySet()) {
                upgrade.setHeader(h.getKey(), h.getValue());
            }

            future.complete(wsClient.connect(new WebSocketAdapter() {
                private @Nullable ScheduledFuture<?> heartbeatTask;

                @Override
                public void onWebSocketConnect(@Nullable Session sess) {
                    if (sess == null) {
                        future.completeExceptionally(new IOException("WebSocket connection failed"));
                        return;
                    }
                    super.onWebSocketConnect(sess);
                    // Schedule periodic ping frames as heartbeat to keep NATs and intermediaries
                    // happy
                    try {
                        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
                            try {
                                Session s = getSession();
                                if (s != null && s.isOpen()) {
                                    s.getRemote().sendPing(ByteBuffer.allocate(0));
                                }
                            } catch (Throwable t) {
                                logger.trace("WebSocket heartbeat ping failed", t);
                                sess.close(1000, "WebSocket heartbeat ping failed");
                            }
                        }, 30, 30, TimeUnit.SECONDS);
                    } catch (Throwable t) {
                        logger.debug("Failed to schedule WebSocket heartbeat", t);
                    }
                    logger.debug("WebSocket connected: {}", wsUri);
                    future.complete(sess);
                    if (onOpen != null) {
                        try {
                            onOpen.run();
                        } catch (Throwable t) {
                            onError.accept(t);
                        }
                    }
                }

                @Override
                public void onWebSocketText(@Nullable String message) {
                    if (message != null) {
                        onText.accept(message);
                    }
                }

                @Override
                public void onWebSocketError(@Nullable Throwable cause) {
                    Throwable t = cause != null ? cause : new IOException("WebSocket connection failed");
                    if (!future.isDone()) {
                        future.completeExceptionally(t);
                    }
                    onError.accept(t);
                }

                @Override
                public void onWebSocketClose(int statusCode, @Nullable String reason) {
                    ScheduledFuture<?> hb = heartbeatTask;
                    if (hb != null) {
                        try {
                            hb.cancel(true);
                        } catch (Throwable t) {
                            // ignore
                        }
                        heartbeatTask = null;
                    }
                    if (onClosed != null) {
                        try {
                            onClosed.accept(statusCode, reason != null ? reason : "");
                        } catch (Throwable t) {
                            onError.accept(t);
                        }
                    }
                    super.onWebSocketClose(statusCode, reason);
                }
            }, wsUri, upgrade).get());
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private URI resolvePath(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return baseUri.resolve(normalized);
    }

    private URI toWebSocketUri(URI httpUri) throws Exception {
        String scheme = httpUri.getScheme();
        if ("https".equalsIgnoreCase(scheme)) {
            scheme = "wss";
        } else if ("http".equalsIgnoreCase(scheme)) {
            scheme = "ws";
        }
        return new URI(scheme, httpUri.getUserInfo(), httpUri.getHost(), httpUri.getPort(), httpUri.getPath(),
                httpUri.getQuery(), httpUri.getFragment());
    }

    private String toPathEnum(AssetFileType t) {
        switch (t) {
            case ANIMATIONS:
                return "animations";
            default:
                return t.name().toLowerCase();
        }
    }

    private String urlEncodeList(List<ChannelQuality> qualities) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qualities.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(toQuality(qualities.get(i)));
        }
        return sb.toString();
    }

    private String toQuality(ChannelQuality q) {
        switch (q) {
            case HIGH:
                return "high";
            case MEDIUM:
                return "medium";
            case LOW:
                return "low";
            case PACKAGE:
                return "package";
            default:
                return q.name().toLowerCase();
        }
    }

    /**
     * Build a JSON patch object from key/value pairs.
     * Keys may include dot notation for nested objects, e.g.
     * "osdSettings.isNameEnabled".
     * Supported values: null, Boolean, Number, String, JsonObject, ApiValueEnum,
     * other (toString()).
     */
    public static JsonObject buildPatch(Object... keysAndValues) throws IllegalArgumentException {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("buildPatch requires an even number of arguments (key/value pairs)");
        }
        JsonObject root = new JsonObject();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            String key = String.valueOf(keysAndValues[i]);
            Object value = keysAndValues[i + 1];
            putPath(root, key, value);
        }
        return root;
    }

    private static void putPath(JsonObject root, String path, Object value) {
        String[] parts = path.split("\\.");
        JsonObject current = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.has(part) || !current.get(part).isJsonObject()) {
                JsonObject child = new JsonObject();
                current.add(part, child);
                current = child;
            } else {
                current = current.getAsJsonObject(part);
            }
        }
        setValue(current, parts[parts.length - 1], value);
    }

    private static void setValue(JsonObject obj, String key, Object value) {
        if (value instanceof Boolean v) {
            obj.addProperty(key, v);
        } else if (value instanceof Number v) {
            obj.addProperty(key, v);
        } else if (value instanceof String v) {
            obj.addProperty(key, v);
        } else if (value instanceof JsonObject v) {
            obj.add(key, v);
        } else if (value instanceof ApiValueEnum v) {
            obj.addProperty(key, v.getApiValue());
        } else {
            obj.addProperty(key, String.valueOf(value));
        }
    }

    private static URI ensureTrailingSlash(URI uri) {
        String s = uri.toString();
        return s.endsWith("/") ? uri : URI.create(s + "/");
    }
}
