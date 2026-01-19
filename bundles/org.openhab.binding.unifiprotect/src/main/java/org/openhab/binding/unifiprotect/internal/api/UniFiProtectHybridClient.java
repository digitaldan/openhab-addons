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
package org.openhab.binding.unifiprotect.internal.api;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.api.Session;
import org.openhab.binding.unifiprotect.internal.api.priv.client.UniFiProtectPrivateClient;
import org.openhab.binding.unifiprotect.internal.api.priv.client.UniFiProtectPrivateWebSocket;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.system.ApiKey;
import org.openhab.binding.unifiprotect.internal.api.priv.dto.system.Bootstrap;
import org.openhab.binding.unifiprotect.internal.api.pub.client.UniFiProtectPublicClient;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.Camera;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.Chime;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.Light;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.Nvr;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.ProtectVersionInfo;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.RtspsStreams;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.Sensor;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.TalkbackSession;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.ws.DeviceAdd;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.ws.DeviceRemove;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.ws.DeviceUpdate;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.ws.EventAdd;
import org.openhab.binding.unifiprotect.internal.api.pub.dto.ws.EventUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

/**
 * Unified client that uses both Public Integration API and Private API.
 *
 * This client provides a hybrid approach:
 * - Public API: Official, stable, token-based authentication for device data
 * - Private API: Full-featured, cookie-based authentication for advanced features
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class UniFiProtectHybridClient implements Closeable {
    private final Logger logger = LoggerFactory.getLogger(UniFiProtectHybridClient.class);

    private final UniFiProtectPublicClient publicClient;
    private final UniFiProtectPrivateClient privateClient;

    /**
     * Create a hybrid client with both public and private API support
     *
     * @param httpClient The HTTP client
     * @param baseUri The base URI for public API
     * @param gson Gson instance for JSON serialization
     * @param apiToken The API token for public API
     * @param executorService Scheduled executor for background tasks
     * @param privateHost Hostname for private API
     * @param privatePort Port for private API (typically 443)
     * @param privateUsername Username for private API authentication
     * @param privatePassword Password for private API authentication
     */
    public UniFiProtectHybridClient(HttpClient httpClient, java.net.URI baseUri, Gson gson, String apiToken,
            ScheduledExecutorService executorService, String privateHost, int privatePort, String privateUsername,
            String privatePassword) {
        this.publicClient = new UniFiProtectPublicClient(httpClient, baseUri, gson, apiToken, executorService);
        this.privateClient = new UniFiProtectPrivateClient(httpClient, executorService, privateHost, privatePort,
                privateUsername, privatePassword);
        logger.info("Hybrid client initialized with both public and private API support");
    }

    @Override
    public void close() throws IOException {
        logger.debug("Closing hybrid client");
        publicClient.close();
        privateClient.close();
    }

    /**
     * Check if a feature is supported
     *
     * @param feature The feature name
     * @return true if the feature is supported (all features now supported with required private API)
     */
    public boolean supportsFeature(String feature) {
        // All features are supported with hybrid client (private API is required)
        return true;
    }

    // ==========================================
    // CAMERA OPERATIONS
    // ==========================================

    /**
     * Get camera snapshot
     * Uses public API (simpler, official)
     *
     * @param id Camera ID
     * @param highQuality True for high quality snapshot
     * @return Snapshot image bytes
     * @throws IOException on error
     */
    public byte[] getCameraSnapshot(String id, Boolean highQuality) throws IOException {
        return publicClient.getSnapshot(id, highQuality);
    }

    /**
     * Update camera settings via public API
     *
     * @param id Camera ID
     * @param patch JSON patch object
     * @return Updated camera
     * @throws IOException on error
     */
    public Camera patchCamera(String id, com.google.gson.JsonObject patch) throws IOException {
        return publicClient.patchCamera(id, patch);
    }

    /**
     * PTZ go to preset (available in both APIs)
     * Uses public API by default
     *
     * @param cameraId Camera ID
     * @param slot Preset slot
     * @throws IOException on error
     */
    public void ptzGotoPreset(String cameraId, String slot) throws IOException {
        publicClient.ptzGotoPreset(cameraId, slot);
    }

    /**
     * PTZ relative move (via private API)
     * Provides fine-grained pan/tilt/zoom control
     *
     * @param cameraId Camera ID
     * @param pan Pan position change
     * @param tilt Tilt position change
     * @param panSpeed Pan speed (1-100)
     * @param tiltSpeed Tilt speed (1-100)
     * @param scale Scale factor
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> ptzRelativeMove(String cameraId, float pan, float tilt, int panSpeed, int tiltSpeed,
            int scale) {
        return privateClient.ptzRelativeMove(cameraId, pan, tilt, panSpeed, tiltSpeed, scale);
    }

    /**
     * PTZ center on coordinates (via private API)
     *
     * @param cameraId Camera ID
     * @param x X coordinate (0-1000)
     * @param y Y coordinate (0-1000)
     * @param z Zoom level (0-1000)
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> ptzCenter(String cameraId, int x, int y, int z) {
        return privateClient.ptzCenter(cameraId, x, y, z);
    }

    /**
     * Create PTZ preset (via private API)
     *
     * @param cameraId Camera ID
     * @param slot Preset slot number
     * @param name Preset name
     * @return CompletableFuture that completes when preset is created
     */
    public CompletableFuture<Void> ptzCreatePreset(String cameraId, int slot, String name) {
        return privateClient.ptzCreatePreset(cameraId, slot, name);
    }

    public CompletableFuture<Void> ptzDeletePreset(String cameraId, int slot) {
        return privateClient.ptzDeletePreset(cameraId, slot);
    }

    /**
     * PTZ zoom (via private API)
     *
     * @param cameraId Camera ID
     * @param zoom Zoom level (-1.0 to 1.0)
     * @param speed Zoom speed (0-10)
     * @return CompletableFuture that completes when zoom is executed
     */
    public CompletableFuture<Void> ptzZoom(String cameraId, float zoom, int speed) {
        return privateClient.ptzZoom(cameraId, zoom, speed);
    }

    /**
     * Set PTZ home position (via private API)
     *
     * @param cameraId Camera ID
     * @return CompletableFuture that completes when home position is set
     */
    public CompletableFuture<Void> ptzSetHome(String cameraId) {
        return privateClient.ptzSetHome(cameraId);
    }

    /**
     * Set camera recording mode (via private API)
     *
     * @param cameraId Camera ID
     * @param mode Recording mode ("always", "motion", "never")
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraRecordingMode(
            String cameraId, String mode) {
        return privateClient.setCameraRecordingMode(cameraId, mode);
    }

    /**
     * Set person detection (via private API)
     *
     * @param cameraId Camera ID
     * @param enabled Enable/disable person detection
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setPersonDetection(
            String cameraId, boolean enabled) {
        return privateClient.setPersonDetection(cameraId, enabled);
    }

    /**
     * Set vehicle detection (via private API)
     *
     * @param cameraId Camera ID
     * @param enabled Enable/disable vehicle detection
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setVehicleDetection(
            String cameraId, boolean enabled) {
        return privateClient.setVehicleDetection(cameraId, enabled);
    }

    /**
     * Set face detection (via private API)
     *
     * @param cameraId Camera ID
     * @param enabled Enable/disable face detection
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setFaceDetection(
            String cameraId, boolean enabled) {
        return privateClient.setFaceDetection(cameraId, enabled);
    }

    /**
     * Set license plate detection (via private API)
     *
     * @param cameraId Camera ID
     * @param enabled Enable/disable license plate detection
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setLicensePlateDetection(
            String cameraId, boolean enabled) {
        return privateClient.setLicensePlateDetection(cameraId, enabled);
    }

    /**
     * Set package detection (via private API)
     *
     * @param cameraId Camera ID
     * @param enabled Enable/disable package detection
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setPackageDetection(
            String cameraId, boolean enabled) {
        return privateClient.setPackageDetection(cameraId, enabled);
    }

    /**
     * Set animal detection (via private API)
     *
     * @param cameraId Camera ID
     * @param enabled Enable/disable animal detection
     * @return CompletableFuture with updated camera
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setAnimalDetection(
            String cameraId, boolean enabled) {
        return privateClient.setAnimalDetection(cameraId, enabled);
    }

    /**
     * Reboot a device (via private API)
     *
     * @param modelType Device model type (e.g., "camera", "light", "sensor")
     * @param deviceId Device ID
     * @return CompletableFuture that completes when reboot command is sent
     */
    public CompletableFuture<Void> rebootDevice(String modelType, String deviceId) {
        return privateClient.rebootDevice(modelType, deviceId);
    }

    // ==========================================
    // LIGHT OPERATIONS
    // ==========================================

    /**
     * Get light details via public API
     *
     * @param id Light ID
     * @return Light device
     * @throws IOException on error
     */
    public Light getLight(String id) throws IOException {
        return publicClient.getLight(id);
    }

    /**
     * Update light settings via public API
     *
     * @param id Light ID
     * @param patch JSON patch object
     * @return Updated light
     * @throws IOException on error
     */
    public Light patchLight(String id, com.google.gson.JsonObject patch) throws IOException {
        return publicClient.patchLight(id, patch);
    }

    /**
     * Set light mode (via private API)
     * Provides more control than public API PATCH
     *
     * @param lightId Light ID
     * @param mode Light mode ("off", "motion", "dark", "always")
     * @param enableAt When to enable ("sunrise", "sunset", null)
     * @return CompletableFuture with updated light
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Light> setLightMode(
            String lightId, String mode, @Nullable String enableAt) {
        return privateClient.setLightMode(lightId, mode, enableAt);
    }

    // ==========================================
    // CHIME OPERATIONS
    // ==========================================

    /**
     * Get chime details via public API
     *
     * @param id Chime ID
     * @return Chime device
     * @throws IOException on error
     */
    public Chime getChime(String id) throws IOException {
        return publicClient.getChime(id);
    }

    /**
     * Update chime settings via public API
     *
     * @param id Chime ID
     * @param patch JSON patch object
     * @return Updated chime
     * @throws IOException on error
     */
    public Chime patchChime(String id, com.google.gson.JsonObject patch) throws IOException {
        return publicClient.patchChime(id, patch);
    }

    /**
     * Play chime (via private API)
     *
     * @param chimeId Chime ID
     * @param volume Volume (0-100), null for default
     * @param repeatTimes Repeat times (1-6), null for default
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> playChime(String chimeId, @Nullable Integer volume, @Nullable Integer repeatTimes) {
        return privateClient.playChime(chimeId, volume, repeatTimes);
    }

    /**
     * Play chime buzzer (via private API)
     *
     * @param chimeId Chime ID
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> playChimeBuzzer(String chimeId) {
        return privateClient.playChimeBuzzer(chimeId);
    }

    // ==========================================
    // DOORLOCK OPERATIONS (Private API Only)
    // ==========================================

    /**
     * Unlock doorlock (via private API)
     *
     * @param doorlockId Doorlock ID
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> unlockDoorlock(String doorlockId) {
        return privateClient.unlockDoorlock(doorlockId);
    }

    /**
     * Lock doorlock (via private API)
     *
     * @param doorlockId Doorlock ID
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> lockDoorlock(String doorlockId) {
        return privateClient.lockDoorlock(doorlockId);
    }

    /**
     * Calibrate doorlock (via private API)
     * Door must be open and lock unlocked
     *
     * @param doorlockId Doorlock ID
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> calibrateDoorlock(String doorlockId) {
        return privateClient.calibrateDoorlock(doorlockId);
    }

    // ==========================================
    // SENSOR OPERATIONS
    // ==========================================

    /**
     * Get sensor details via public API
     *
     * @param id Sensor ID
     * @return Sensor device
     * @throws IOException on error
     */
    public Sensor getSensor(String id) throws IOException {
        return publicClient.getSensor(id);
    }

    /**
     * Update sensor settings via public API
     *
     * @param id Sensor ID
     * @param patch JSON patch object
     * @return Updated sensor
     * @throws IOException on error
     */
    public Sensor patchSensor(String id, com.google.gson.JsonObject patch) throws IOException {
        return publicClient.patchSensor(id, patch);
    }

    /**
     * Clear sensor tamper flag (via private API)
     *
     * @param sensorId Sensor ID
     * @return CompletableFuture that completes when command is sent
     */
    public CompletableFuture<Void> clearSensorTamper(String sensorId) {
        return privateClient.clearSensorTamper(sensorId);
    }

    // ==========================================
    // DEVICE MANAGEMENT (Private API Only)
    // ==========================================

    /**
     * Adopt/manage a device (via private API)
     *
     * @param modelType Model type (camera, light, sensor, etc.)
     * @param deviceId Device ID
     * @return CompletableFuture that completes when device is adopted
     */
    public CompletableFuture<Void> adoptDevice(String modelType, String deviceId) {
        return privateClient.adoptDevice(modelType, deviceId);
    }

    // ==========================================
    // BOOTSTRAP (Private API Only)
    // ==========================================

    /**
     * Get bootstrap (via private API)
     * Provides complete system state in a single request
     *
     * @return CompletableFuture with bootstrap data
     */
    public CompletableFuture<Bootstrap> getBootstrap() {
        return privateClient.getBootstrap();
    }

    /**
     * Get cached bootstrap (via private API)
     * Returns immediately with cached data
     *
     * @return Cached bootstrap data, or null if not cached
     */
    public @Nullable Bootstrap getCachedBootstrap() {
        return privateClient.getCachedBootstrap();
    }

    // ==========================================
    // WEBSOCKET OPERATIONS
    // ==========================================

    /**
     * Subscribe to device updates via public API WebSocket
     *
     * @param onAdd Handler for device additions
     * @param onUpdate Handler for device updates
     * @param onRemove Handler for device removals
     * @param onOpen Handler for connection open
     * @param onClosed Handler for connection closed
     * @param onError Handler for errors
     * @return CompletableFuture with WebSocket session
     */
    public CompletableFuture<Session> subscribeDevices(Consumer<DeviceAdd> onAdd, Consumer<DeviceUpdate> onUpdate,
            Consumer<DeviceRemove> onRemove, Runnable onOpen, BiConsumer<Integer, String> onClosed,
            Consumer<Throwable> onError) {
        return publicClient.subscribeDevices(onAdd, onUpdate, onRemove, onOpen, onClosed, onError);
    }

    /**
     * Subscribe to event updates via public API WebSocket
     *
     * @param onAdd Handler for event additions
     * @param onUpdate Handler for event updates
     * @param onOpen Handler for connection open
     * @param onClosed Handler for connection closed
     * @param onError Handler for errors
     * @return CompletableFuture with WebSocket session
     */
    public CompletableFuture<Session> subscribeEvents(Consumer<EventAdd> onAdd, Consumer<EventUpdate> onUpdate,
            Runnable onOpen, BiConsumer<Integer, String> onClosed, Consumer<Throwable> onError) {
        return publicClient.subscribeEvents(onAdd, onUpdate, onOpen, onClosed, onError);
    }

    /**
     * Enable WebSocket for real-time updates via private API
     * More efficient than public API for large deployments (incremental updates)
     *
     * @param updateHandler Handler for WebSocket updates
     * @return CompletableFuture that completes when WebSocket is connected
     */
    public CompletableFuture<Void> enablePrivateWebSocket(
            Consumer<UniFiProtectPrivateWebSocket.WebSocketUpdate> updateHandler) {
        return privateClient.enableWebSocket(updateHandler);
    }

    // ==========================================
    // PUBLIC API DELEGATION METHODS
    // ==========================================

    /**
     * Get NVR information
     *
     * @return NVR information
     * @throws IOException on error
     */
    public Nvr getNvr() throws IOException {
        return publicClient.getNvr();
    }

    /**
     * Get camera by ID
     *
     * @param id Camera ID
     * @return Camera information
     * @throws IOException on error
     */
    public Camera getCamera(String id) throws IOException {
        return publicClient.getCamera(id);
    }

    /**
     * Get camera from Private API (async)
     *
     * @param id Camera ID
     * @return CompletableFuture with Private API Camera object
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> getPrivateCamera(
            String id) {
        return privateClient.getBootstrap().thenApply(bootstrap -> {
            org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera camera = bootstrap.cameras.get(id);
            if (camera == null) {
                throw new IllegalArgumentException("Camera not found: " + id);
            }
            return camera;
        });
    }

    /**
     * List all cameras
     *
     * @return List of cameras
     * @throws IOException on error
     */
    public List<Camera> listCameras() throws IOException {
        return publicClient.listCameras();
    }

    /**
     * List all lights
     *
     * @return List of lights
     * @throws IOException on error
     */
    public List<Light> listLights() throws IOException {
        return publicClient.listLights();
    }

    /**
     * List all sensors
     *
     * @return List of sensors
     * @throws IOException on error
     */
    public List<Sensor> listSensors() throws IOException {
        return publicClient.listSensors();
    }

    /**
     * Get UniFi Protect version and metadata
     *
     * @return Version information
     * @throws IOException on error
     */
    public ProtectVersionInfo getMetaInfo() throws IOException {
        return publicClient.getMetaInfo();
    }

    // ==========================================
    // PUBLIC API EXCLUSIVE FEATURES
    // ==========================================

    /**
     * Create RTSPS stream (public API only)
     *
     * @param id Camera ID
     * @param qualities List of channel qualities
     * @return RTSPS stream information
     * @throws IOException on error
     */
    public RtspsStreams createRtspsStream(String id,
            List<org.openhab.binding.unifiprotect.internal.api.pub.dto.ChannelQuality> qualities) throws IOException {
        return publicClient.createRtspsStream(id, qualities);
    }

    /**
     * Create talkback session (public API only)
     *
     * @param id Camera ID
     * @return Talkback session information
     * @throws IOException on error
     */
    public TalkbackSession createTalkbackSession(String id) throws IOException {
        return publicClient.createTalkbackSession(id);
    }

    /**
     * Trigger alarm webhook (public API only)
     *
     * @param id Alarm webhook ID
     * @throws IOException on error
     */
    public void triggerAlarmWebhook(String id) throws IOException {
        publicClient.triggerAlarmWebhook(id);
    }

    // ==========================================
    // ADDITIONAL PRIVATE API CAMERA CONTROLS
    // ==========================================

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraMicEnabled(
            String cameraId, boolean enabled) {
        return privateClient.setCameraMicEnabled(cameraId, enabled);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraIRMode(
            String cameraId, String mode) {
        return privateClient.setCameraIRMode(cameraId, mode);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraMotionDetection(
            String cameraId, boolean enabled) {
        return privateClient.setCameraMotionDetection(cameraId, enabled);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraUseGlobal(
            String cameraId, boolean useGlobal) {
        return privateClient.setCameraUseGlobal(cameraId, useGlobal);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraSpeakerVolume(
            String cameraId, int volume) {
        return privateClient.setCameraSpeakerVolume(cameraId, volume);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraZoom(
            String cameraId, int zoom) {
        return privateClient.setCameraZoom(cameraId, zoom);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraWDR(
            String cameraId, int level) {
        return privateClient.setCameraWDR(cameraId, level);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setDoorbellRingVolume(
            String cameraId, int volume) {
        return privateClient.setDoorbellRingVolume(cameraId, volume);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setDoorbellChimeDuration(
            String cameraId, int duration) {
        return privateClient.setDoorbellChimeDuration(cameraId, duration);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraVideoMode(
            String cameraId, String mode) {
        return privateClient.setCameraVideoMode(cameraId, mode);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Camera> setCameraHDR(
            String cameraId, boolean enabled) {
        return privateClient.setCameraHDR(cameraId, enabled);
    }

    // ==========================================
    // UTILITY METHODS
    // ==========================================

    /**
     * Check if private API is enabled
     *
     * @return true if private API is available
     */
    public boolean isPrivateApiEnabled() {
        return true; // Private API is always enabled
    }

    /**
     * Get the public API client (for advanced usage)
     *
     * @return Public API client
     */
    public UniFiProtectPublicClient getPublicClient() {
        return publicClient;
    }

    /**
     * Get the private API client (for advanced usage)
     *
     * @return Private API client, or null if not enabled
     */
    public @Nullable UniFiProtectPrivateClient getPrivateClient() {
        return privateClient;
    }

    // ==========================================
    // DOORLOCK OPERATIONS (Private API)
    // ==========================================

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Doorlock> getPrivateDoorlock(
            String id) {
        return privateClient.getBootstrap().thenApply(bootstrap -> {
            org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Doorlock doorlock = bootstrap.doorlocks
                    .get(id);
            if (doorlock == null) {
                throw new IllegalArgumentException("Doorlock not found: " + id);
            }
            return doorlock;
        });
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Doorlock> setDoorlockAutoCloseTime(
            String doorlockId, int durationSeconds) {
        return privateClient.setDoorlockAutoCloseTime(doorlockId, durationSeconds);
    }

    // ==========================================
    // CHIME OPERATIONS (Private API)
    // ==========================================

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Chime> getPrivateChime(
            String id) {
        return privateClient.getBootstrap().thenApply(bootstrap -> {
            org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Chime chime = bootstrap.chimes.get(id);
            if (chime == null) {
                throw new IllegalArgumentException("Chime not found: " + id);
            }
            return chime;
        });
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Chime> setChimeVolume(
            String chimeId, int volume) {
        return privateClient.setChimeVolume(chimeId, volume);
    }

    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.devices.Chime> setChimeRepeatTimes(
            String chimeId, int repeatTimes) {
        return privateClient.setChimeRepeatTimes(chimeId, repeatTimes);
    }

    // ==========================================
    // API KEY MANAGEMENT
    // ==========================================

    /**
     * List all API keys for a user
     */
    public CompletableFuture<List<ApiKey>> listApiKeys(String userId) {
        return privateClient.listApiKeys(userId);
    }

    /**
     * Create a new API key
     */
    public CompletableFuture<ApiKey> createApiKey(String userId, String name) {
        return privateClient.createApiKey(userId, name);
    }

    /**
     * Delete an API key
     */
    public CompletableFuture<Void> deleteApiKey(String keyId) {
        return privateClient.deleteApiKey(keyId);
    }

    /**
     * Get the current user's ID
     */
    public CompletableFuture<String> getCurrentUserId() {
        return privateClient.getCurrentUserId();
    }

    /**
     * Find an API key by name
     */
    public CompletableFuture<ApiKey> findApiKeyByName(String userId, String name) {
        return privateClient.findApiKeyByName(userId, name);
    }

    /**
     * Get or create an API key (deletes and recreates if exists)
     */
    public CompletableFuture<org.openhab.binding.unifiprotect.internal.api.priv.dto.system.ApiKey> getOrCreateApiKey(
            String userId, String name) {
        return privateClient.getOrCreateApiKey(userId, name);
    }
}
