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
package org.openhab.binding.ikamand.internal;

import static org.openhab.binding.ikamand.internal.IKamandBindingConstants.*;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link iKamandHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class IKamandHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(IKamandHandler.class);
    private static final BigDecimal TEMPERATURE_UNDEFINED = new BigDecimal(400);
    private IKamandConfiguration config = new IKamandConfiguration();
    private int targetPitTemperature = 160; // default to 160C or 320F
    private int targetFoodTemperature = 60; // default to 60C or 140F
    private int foodProbe = 0; // default to probe 0. or no food probe
    private @Nullable ScheduledFuture<?> pollFuture;

    public IKamandHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(IKamandConfiguration.class);
        initPolling(0, config.refreshInterval);
    }

    @Override
    public void dispose() {
        clearPolling();
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // Handle REFRESH commands by triggering an immediate poll
        if (command instanceof RefreshType) {
            pollDevice();
            return;
        }

        String id = channelUID.getId();

        try {
            switch (id) {
                case CHANNEL_COOK_START:
                    if (command instanceof OnOffType onoff) {
                        if (onoff == OnOffType.ON) {
                            startCook();
                        } else {
                            stopGrill();
                        }
                    }
                    break;

                case CHANNEL_TARGET_TEMPERATURE:
                    if (command instanceof QuantityType<?> decimalType) {
                        targetPitTemperature = decimalType.intValue();
                        startCook();
                    }
                    break;

                case CHANNEL_TARGET_FOOD_TEMP:
                    if (command instanceof QuantityType<?> decimalType) {
                        targetFoodTemperature = decimalType.intValue();
                        startCook();
                    }
                    break;

                case CHANNEL_GRILL_START:
                    if (command instanceof OnOffType onoff) {
                        if (onoff == OnOffType.ON) {
                            startGrill(config.grillStartDuration);
                        } else {
                            stopGrill();
                        }
                    }
                    break;

                case CHANNEL_FOOD_PROBE:
                    if (command instanceof DecimalType decimalType) {
                        foodProbe = decimalType.intValue();
                    }
                    break;

                default:
                    logger.debug("Unhandled command for channel {}", id);
                    break;
            }
            initPolling(2, config.refreshInterval);
        } catch (Exception e) {
            logger.warn("Error while handling command {} for channel {}", command, id, e);
        }
    }

    private synchronized void initPolling(int initialDelay, int refresh) {
        clearPolling();
        logger.debug("initPolling: {}, {}", initialDelay, refresh);
        pollFuture = scheduler.scheduleWithFixedDelay(this::pollDevice, initialDelay, refresh, TimeUnit.SECONDS);
    }

    /**
     * Stops/clears this thing's polling future
     */
    private void clearPolling() {
        ScheduledFuture<?> localFuture = pollFuture;
        if (isFutureValid(localFuture)) {
            if (localFuture != null) {
                localFuture.cancel(true);
            }
        }
    }

    private boolean isFutureValid(@Nullable ScheduledFuture<?> future) {
        return future != null && !future.isCancelled();
    }

    private synchronized void pollDevice() {
        String dataUrl = "http://" + config.hostname + "/cgi-bin/data";

        String body = fetchWithSocket(dataUrl, null);

        if (body != null) {
            Map<String, String> rawData = convertToMap(body);
            updateChannels(rawData);
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    private Map<String, String> convertToMap(String data) {
        Map<String, String> map = new HashMap<>();
        String[] pairs = data.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    private void updateChannels(Map<String, String> data) {
        data.forEach((key, value) -> {
            logger.debug("updateChannels: {}, {}", key, value);
        });

        updateNumberChannel(CHANNEL_FAN_SPEED, data.get(KEY_FAN_SPEED));
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PIT, data.get(KEY_PIT_TEMP));
        updateTemperatureChannel(CHANNEL_TARGET_TEMPERATURE, data.get(KEY_TARGET_PIT_TEMP));
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PROBE1, data.get(KEY_PROBE_1));
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PROBE2, data.get(KEY_PROBE_2));
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PROBE3, data.get(KEY_PROBE_3));

        updateStringChannel(CHANNEL_RM, data.get(KEY_UNKNOWN_RECEIVE_VAR1));
        updateStringChannel(CHANNEL_CM, data.get(KEY_UNKNOWN_RECEIVE_VAR2));
        updateStringChannel(CHANNEL_AS, data.get(KEY_UNKNOWN_SEND_VAR1));

        // same channel for both grill and cook status
        updateSwitchChannel(CHANNEL_COOK_START, data.get(KEY_COOK_START));
        updateSwitchChannel(CHANNEL_GRILL_START, data.get(KEY_COOK_START));

        updateTemperatureChannel(CHANNEL_TARGET_FOOD_TEMP, data.get(KEY_TARGET_FOOD_TEMP));

        updateNumberChannel(CHANNEL_FOOD_PROBE, data.getOrDefault(KEY_FOOD_PROBE, "0"));

        updateDateChannel(CHANNEL_CURRENT_TIME, data.get(KEY_CURRENT_TIME));
        updateDateChannel(CHANNEL_COOK_END_TIME, data.get(KEY_COOK_END_TIME));
        updateDateChannel(CHANNEL_GRILL_END_TIME, data.get(KEY_GRILL_END_TIME));

        updateStringChannel(CHANNEL_COOK_ID, data.get(KEY_COOK_ID));
    }

    private void updateTemperatureChannel(String channelId, @Nullable String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
            return;
        }
        try {
            BigDecimal value = new BigDecimal(valueStr);
            if (value.compareTo(TEMPERATURE_UNDEFINED) == 0) {
                updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
                return;
            }
            updateState(new ChannelUID(getThing().getUID(), channelId), new QuantityType<>(value, SIUnits.CELSIUS));
        } catch (NumberFormatException e) {
            logger.debug("Unable to parse numeric value '{}' for channel {}", valueStr, channelId);
        }
    }

    private void updateNumberChannel(String channelId, @Nullable String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
            return;
        }
        try {
            double value = Double.parseDouble(valueStr);
            updateState(new ChannelUID(getThing().getUID(), channelId), new DecimalType(value));
        } catch (NumberFormatException e) {
            logger.debug("Unable to parse numeric value '{}' for channel {}", valueStr, channelId);
        }
    }

    private void updateStringChannel(String channelId, @Nullable String valueStr) {
        if (valueStr == null) {
            updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
            return;
        }
        updateState(new ChannelUID(getThing().getUID(), channelId), new StringType(valueStr));
    }

    private void updateDateChannel(String channelId, @Nullable String epochStr) {
        if (epochStr == null || epochStr.isEmpty()) {
            updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
            return;
        }
        try {
            long epoch = Long.parseLong(epochStr);
            DateTimeType dt = new DateTimeType(Instant.ofEpochSecond(epoch).atZone(ZoneId.systemDefault()));
            updateState(new ChannelUID(getThing().getUID(), channelId), dt);
        } catch (NumberFormatException e) {
            logger.debug("Unable to parse epoch '{}' for channel {}", epochStr, channelId);
        }
    }

    private void updateSwitchChannel(String channelId, @Nullable String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
            return;
        }
        boolean on = "1".equals(valueStr) || "true".equalsIgnoreCase(valueStr);
        updateState(new ChannelUID(getThing().getUID(), channelId), on ? OnOffType.ON : OnOffType.OFF);
    }

    private void startCook() {

        String url = "http://" + config.hostname + "/cgi-bin/cook";
        long currentTime = Instant.now().getEpochSecond();
        long currentTimePlusOneDay = currentTime + 86400;

        StringBuilder payload = new StringBuilder();
        payload.append(KEY_COOK_START).append("=1");
        payload.append("&").append(KEY_COOK_ID).append("=").append(UUID.randomUUID());
        payload.append("&").append(KEY_TARGET_PIT_TEMP).append("=").append(targetPitTemperature);
        payload.append("&").append(KEY_COOK_END_TIME).append("=").append(currentTimePlusOneDay);
        payload.append("&").append(KEY_FOOD_PROBE).append("=").append(foodProbe);
        payload.append("&").append(KEY_TARGET_FOOD_TEMP).append("=").append(targetFoodTemperature);
        payload.append("&").append(KEY_UNKNOWN_SEND_VAR1).append("=0");
        payload.append("&").append(KEY_CURRENT_TIME).append("=").append(currentTime);

        sendPost(url, payload.toString());
    }

    private void sendPost(String url, String body) {
        fetchWithSocket(url, body); // fire and forget; ignore response
    }

    private @Nullable String getDeviceInfo() {
        String url = "http://" + config.hostname + "/cgi-bin/info";
        return fetchWithSocket(url, null);
    }

    private @Nullable String queryWifiNetworks() {
        String url = "http://" + config.hostname + "/cgi-bin/wifi_list";
        return fetchWithSocket(url, null);
    }

    private void setupWifi(String ssid, String pass, String user) {
        String url = "http://" + config.hostname + "/cgi-bin/netset";
        StringBuilder payload = new StringBuilder();
        payload.append("ssid=").append(java.util.Base64.getEncoder().encodeToString(ssid.getBytes()));
        payload.append("&pass=").append(java.util.Base64.getEncoder().encodeToString(pass.getBytes()));
        payload.append("&user=").append(java.util.Base64.getEncoder().encodeToString(user.getBytes()));
        sendPost(url, payload.toString());
    }

    /**
     * Fan boost at 100% for durationMinutes.
     */
    private void startGrill(int durationMinutes) {
        long currentTime = Instant.now().getEpochSecond();
        long cookEnd = currentTime + durationMinutes * 60L;

        StringBuilder payload = new StringBuilder();
        payload.append(KEY_COOK_START).append("=1");
        payload.append("&").append(KEY_COOK_ID).append("=");
        payload.append("&").append(KEY_TARGET_PIT_TEMP).append("=260");
        payload.append("&").append(KEY_COOK_END_TIME).append("=").append(cookEnd);
        payload.append("&p=0");
        payload.append("&").append(KEY_TARGET_FOOD_TEMP).append("=0");
        payload.append("&").append(KEY_UNKNOWN_SEND_VAR1).append("=0");
        payload.append("&").append(KEY_CURRENT_TIME).append("=").append(currentTime);

        sendPost("http://" + config.hostname + "/cgi-bin/cook", payload.toString());
    }

    private void stopGrill() {
        long currentTime = Instant.now().getEpochSecond();

        StringBuilder payload = new StringBuilder();
        payload.append(KEY_COOK_START).append("=0");
        payload.append("&").append(KEY_COOK_ID).append("=");
        payload.append("&").append(KEY_TARGET_PIT_TEMP).append("=0");
        payload.append("&").append(KEY_COOK_END_TIME).append("=").append(currentTime);
        payload.append("&p=0");
        payload.append("&").append(KEY_TARGET_FOOD_TEMP).append("=0");
        payload.append("&").append(KEY_UNKNOWN_SEND_VAR1).append("=0");
        payload.append("&").append(KEY_CURRENT_TIME).append("=").append(currentTime);

        sendPost("http://" + config.hostname + "/cgi-bin/cook", payload.toString());
    }

    // ---------------------------------------------------------------------
    // HTTP helpers tolerant of malformed responses
    // ---------------------------------------------------------------------

    private @Nullable String fetchWithSocket(String url, @Nullable String postBody) {
        try {
            logger.debug("fetchWithSocket: {}, {}", url, postBody);
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort() != -1 ? uri.getPort() : 80;
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (uri.getRawQuery() != null) {
                path += "?" + uri.getRawQuery();
            }

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 5000);
                socket.setSoTimeout(5000);

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
                if (postBody == null) {
                    writer.write("GET " + path + " HTTP/1.0\r\n");
                    writer.write("Host: " + host + "\r\n");
                    writer.write("Connection: close\r\n\r\n");
                } else {
                    byte[] bodyBytes = postBody.getBytes(StandardCharsets.US_ASCII);
                    writer.write("POST " + path + " HTTP/1.0\r\n");
                    writer.write("Host: " + host + "\r\n");
                    writer.write("Content-Type: application/x-www-form-urlencoded\r\n");
                    writer.write("Content-Length: " + bodyBytes.length + "\r\n");
                    writer.write("Connection: close\r\n\r\n");
                    writer.flush();
                    socket.getOutputStream().write(bodyBytes);
                }
                writer.flush();

                InputStream in = socket.getInputStream();
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                String raw = baos.toString(StandardCharsets.UTF_8);
                int idx = raw.indexOf("\r\n\r\n");
                if (idx != -1) {
                    return raw.substring(idx + 4);
                }
                logger.debug("fetchWithSocket result: {}", raw);
                return raw;
            }
        } catch (IOException e) {
            logger.debug("Socket fallback failed for url {}", url, e);
        }
        return null;
    }
}
