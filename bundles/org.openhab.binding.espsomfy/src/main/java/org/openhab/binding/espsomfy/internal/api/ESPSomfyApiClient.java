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
package org.openhab.binding.espsomfy.internal.api;

import static org.openhab.binding.espsomfy.internal.ESPSomfyBindingConstants.API_TIMEOUT_MS;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyGroupDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyRoomDTO;
import org.openhab.binding.espsomfy.internal.dto.ESPSomfyShadeDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * HTTP API client for communicating with ESPSomfy-RTS devices.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class ESPSomfyApiClient {

    private final Logger logger = LoggerFactory.getLogger(ESPSomfyApiClient.class);
    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private final String baseUrl;
    private @Nullable String apiKey;

    public ESPSomfyApiClient(HttpClient httpClient, String hostname, int port) {
        this.httpClient = httpClient;
        this.baseUrl = "http://" + hostname + ":" + port;
    }

    /**
     * Check the authentication type required by the device.
     *
     * @return the auth type (0=none, 1=PIN, 2=password)
     */
    public int getAuthType() throws ESPSomfyApiException {
        String response = sendGetRequest("/loginContext");
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        return json.has("type") ? json.get("type").getAsInt() : 0;
    }

    /**
     * Authenticate with the device if security is enabled.
     */
    public void login(String password) throws ESPSomfyApiException {
        int authType = getAuthType();
        if (authType == 0) {
            return;
        }
        JsonObject body = new JsonObject();
        if (authType == 1) {
            body.addProperty("pin", password);
        } else {
            body.addProperty("username", "admin");
            body.addProperty("password", password);
        }
        String response = sendPostRequest("/login", body.toString());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        if (json.has("apiKey")) {
            apiKey = json.get("apiKey").getAsString();
            logger.debug("ESPSomfy login successful");
        } else {
            throw new ESPSomfyApiException("Login failed: no apiKey in response");
        }
    }

    /**
     * Get the full controller state including shades, groups, and rooms.
     */
    public JsonObject getController() throws ESPSomfyApiException {
        String response = sendGetRequest("/controller");
        return JsonParser.parseString(response).getAsJsonObject();
    }

    /**
     * Get all shades from the controller.
     */
    public ESPSomfyShadeDTO[] getShades() throws ESPSomfyApiException {
        String response = sendGetRequest("/shades");
        return Objects.requireNonNull(gson.fromJson(response, ESPSomfyShadeDTO[].class));
    }

    /**
     * Get all groups from the controller.
     */
    public ESPSomfyGroupDTO[] getGroups() throws ESPSomfyApiException {
        String response = sendGetRequest("/groups");
        return Objects.requireNonNull(gson.fromJson(response, ESPSomfyGroupDTO[].class));
    }

    /**
     * Get a single shade by ID.
     */
    public ESPSomfyShadeDTO getShade(int shadeId) throws ESPSomfyApiException {
        String response = sendGetRequest("/shade?shadeId=" + shadeId);
        return Objects.requireNonNull(gson.fromJson(response, ESPSomfyShadeDTO.class));
    }

    /**
     * Get a single group by ID.
     */
    public ESPSomfyGroupDTO getGroup(int groupId) throws ESPSomfyApiException {
        String response = sendGetRequest("/group?groupId=" + groupId);
        return Objects.requireNonNull(gson.fromJson(response, ESPSomfyGroupDTO.class));
    }

    /**
     * Get all rooms.
     */
    public ESPSomfyRoomDTO[] getRooms() throws ESPSomfyApiException {
        String response = sendGetRequest("/rooms");
        return Objects.requireNonNull(gson.fromJson(response, ESPSomfyRoomDTO[].class));
    }

    /**
     * Parse shades from the controller response JSON.
     */
    public ESPSomfyShadeDTO[] parseShadesFromController(JsonObject controller) {
        return parseArrayFromController(controller, "shades", ESPSomfyShadeDTO[].class, new ESPSomfyShadeDTO[0]);
    }

    /**
     * Parse groups from the controller response JSON.
     */
    public ESPSomfyGroupDTO[] parseGroupsFromController(JsonObject controller) {
        return parseArrayFromController(controller, "groups", ESPSomfyGroupDTO[].class, new ESPSomfyGroupDTO[0]);
    }

    /**
     * Parse rooms from the controller response JSON.
     */
    public ESPSomfyRoomDTO[] parseRoomsFromController(JsonObject controller) {
        return parseArrayFromController(controller, "rooms", ESPSomfyRoomDTO[].class, new ESPSomfyRoomDTO[0]);
    }

    private <T> T parseArrayFromController(JsonObject controller, String key, Class<T> type, T defaultValue) {
        if (controller.has(key)) {
            JsonArray arr = controller.getAsJsonArray(key);
            T result = gson.fromJson(arr, type);
            return result != null ? result : defaultValue;
        }
        return defaultValue;
    }

    /**
     * Send a command to a shade (e.g. Up, Down, My, Prog).
     */
    public void sendShadeCommand(int shadeId, String command) throws ESPSomfyApiException {
        JsonObject body = new JsonObject();
        body.addProperty("shadeId", shadeId);
        body.addProperty("command", command);
        sendPostRequest("/shadeCommand", body.toString());
    }

    /**
     * Send a target position to a shade.
     */
    public void sendShadePosition(int shadeId, int position) throws ESPSomfyApiException {
        JsonObject body = new JsonObject();
        body.addProperty("shadeId", shadeId);
        body.addProperty("target", position);
        sendPostRequest("/shadeCommand", body.toString());
    }

    /**
     * Send a command to a group.
     */
    public void sendGroupCommand(int groupId, String command) throws ESPSomfyApiException {
        JsonObject body = new JsonObject();
        body.addProperty("groupId", groupId);
        body.addProperty("command", command);
        sendPostRequest("/groupCommand", body.toString());
    }

    /**
     * Send a target position to a group.
     */
    public void sendGroupPosition(int groupId, int position) throws ESPSomfyApiException {
        JsonObject body = new JsonObject();
        body.addProperty("groupId", groupId);
        body.addProperty("target", position);
        sendPostRequest("/groupCommand", body.toString());
    }

    /**
     * Send a tilt command to a shade.
     */
    public void sendTiltCommand(int shadeId, String command) throws ESPSomfyApiException {
        JsonObject body = new JsonObject();
        body.addProperty("shadeId", shadeId);
        body.addProperty("command", command);
        sendPostRequest("/tiltCommand", body.toString());
    }

    /**
     * Set both position and tilt position for a shade.
     */
    public void setPositions(int shadeId, int position, int tiltPosition) throws ESPSomfyApiException {
        JsonObject body = new JsonObject();
        body.addProperty("shadeId", shadeId);
        body.addProperty("position", position);
        body.addProperty("tiltPosition", tiltPosition);
        sendPostRequest("/setPositions", body.toString());
    }

    private String sendGetRequest(String path) throws ESPSomfyApiException {
        Request request = httpClient.newRequest(baseUrl + path);
        request.method(HttpMethod.GET);
        return sendRequest(request, path);
    }

    private String sendPostRequest(String path, String json) throws ESPSomfyApiException {
        Request request = httpClient.newRequest(baseUrl + path);
        request.method(HttpMethod.POST);
        request.content(new StringContentProvider(json), "application/json");
        return sendRequest(request, path);
    }

    private String sendRequest(Request request, String path) throws ESPSomfyApiException {
        try {
            request.timeout(API_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            String key = apiKey;
            if (key != null) {
                request.header("apikey", key);
            }
            ContentResponse response = request.send();
            if (response.getStatus() != 200) {
                throw new ESPSomfyApiException(
                        "HTTP " + request.getMethod() + " " + path + " returned status " + response.getStatus());
            }
            return response.getContentAsString();
        } catch (TimeoutException e) {
            throw new ESPSomfyApiException("Timeout on " + path, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ESPSomfyApiException("Interrupted on " + path, e);
        } catch (ExecutionException e) {
            throw new ESPSomfyApiException("Error on " + path, e);
        }
    }
}
