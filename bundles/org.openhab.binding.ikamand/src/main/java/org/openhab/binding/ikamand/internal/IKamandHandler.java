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
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.QuantityType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.library.unit.ImperialUnits;
import org.openhab.core.library.unit.SIUnits;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.Type;
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
    private static final BigDecimal TEMPERATURE_UNDEFINED_MAX = new BigDecimal(400);
    private static final BigDecimal TEMPERATURE_UNDEFINED_MIN = new BigDecimal(-400);

    private IKamandConfiguration config = new IKamandConfiguration();
    private @Nullable ScheduledFuture<?> pollFuture;
    private List<String> probeTargetTemperaturesChannels = Arrays.asList(CHANNEL_TEMPERATURE_PROBE1_TARGET,
            CHANNEL_TEMPERATURE_PROBE2_TARGET, CHANNEL_TEMPERATURE_PROBE3_TARGET);

    private final ReentrantLock pollLock = new ReentrantLock();
    private int cachedTargetGrillTemperature = 0;

    public IKamandHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        config = getConfigAs(IKamandConfiguration.class);
        cachedTargetGrillTemperature = config.defaultGrillTemperature;
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PIT_TARGET, String.valueOf(cachedTargetGrillTemperature));
        probeTargetTemperaturesChannels.forEach(channel -> updateState(channel, UnDefType.UNDEF));
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
                case CHANNEL_COOK_START_PROBE_1:
                case CHANNEL_COOK_START_PROBE_2:
                case CHANNEL_COOK_START_PROBE_3:
                    if (command instanceof QuantityType<?> quantityType) {
                        String probeString = id.substring(id.length() - 1);
                        int probe = Integer.parseInt(probeString);
                        Integer targetTemperature = temperatureToValue(quantityType);
                        if (targetTemperature != null && targetTemperature > 0) {
                            startCook(probe, targetTemperature);
                            updateTemperatureChannel(probeTargetTemperaturesChannels.get(probe - 1), probeString);
                        } else {
                            updateState(probeTargetTemperaturesChannels.get(probe - 1), UnDefType.UNDEF);
                        }
                    }
                    break;

                case CHANNEL_COOK_STOP:
                    if (command instanceof DecimalType decimalType) {
                        int probe = decimalType.intValue();
                        stopCook(probe);
                        updateState(new ChannelUID(getThing().getUID(), id), DecimalType.ZERO);
                        updateState(probeTargetTemperaturesChannels.get(probe - 1), UnDefType.UNDEF);
                    }
                    break;
                case CHANNEL_GRILL_START:
                    if (command instanceof QuantityType<?> quantityType) {
                        Integer targetTemperature = temperatureToValue(quantityType);
                        if (targetTemperature == null || targetTemperature <= 0) {
                            targetTemperature = config.defaultGrillTemperature;
                        }
                        startGrill(targetTemperature);
                        updateTemperatureChannel(CHANNEL_TEMPERATURE_PIT_TARGET, String.valueOf(targetTemperature));
                    }
                    break;

                case CHANNEL_GRILL_RUNNING:
                    if (command instanceof OnOffType onoff) {
                        if (onoff == OnOffType.OFF) {
                            stopGrill();
                        } else {
                            startGrill(cachedTargetGrillTemperature);
                            updateTemperatureChannel(CHANNEL_TEMPERATURE_PIT_TARGET,
                                    String.valueOf(cachedTargetGrillTemperature));
                        }
                    }
                    break;

                default:
                    logger.debug("Unhandled command for channel {}", id);
                    break;
            }
            initPolling(1, config.refreshInterval);
        } catch (Exception e) {
            logger.warn("Error while handling command {} for channel {}", command, id, e);
        }
    }

    private synchronized void initPolling(int initialDelay, int refresh) {
        clearPolling();
        logger.debug("initPolling: {}, {}", initialDelay, refresh);
        pollFuture = scheduler.scheduleWithFixedDelay(this::pollDevice, initialDelay, refresh, TimeUnit.SECONDS);
    }

    private void clearPolling() {
        ScheduledFuture<?> pollFuture = this.pollFuture;
        if (pollFuture != null && !pollFuture.isCancelled()) {
            pollFuture.cancel(true);
        }
    }

    private void pollDevice() {
        if (!pollLock.tryLock()) {
            return; // another poll is already running
        }
        try {
            String dataUrl = "http://" + config.hostname + "/cgi-bin/data";

            String body = fetchWithSocket(dataUrl, null);

            if (body != null) {
                Map<String, String> rawData = convertToMap(body);
                updateChannels(rawData);
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        } finally {
            pollLock.unlock();
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
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PIT_CURRENT, data.get(KEY_PIT_TEMP));
        if (data.get(KEY_TARGET_PIT_TEMP) instanceof String pitTempString) {
            int temp = Integer.parseInt(pitTempString);
            if (temp > 32 && temp <= 260) {
                cachedTargetGrillTemperature = temp;
            } else {
                cachedTargetGrillTemperature = config.defaultGrillTemperature;
            }
            updateTemperatureChannel(CHANNEL_TEMPERATURE_PIT_TARGET, String.valueOf(cachedTargetGrillTemperature));
        }

        updateTemperatureChannel(CHANNEL_TEMPERATURE_PROBE1_CURRENT, data.get(KEY_PROBE_1));
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PROBE2_CURRENT, data.get(KEY_PROBE_2));
        updateTemperatureChannel(CHANNEL_TEMPERATURE_PROBE3_CURRENT, data.get(KEY_PROBE_3));

        updateStringChannel(CHANNEL_RM, data.get(KEY_UNKNOWN_RECEIVE_VAR1));
        updateStringChannel(CHANNEL_CM, data.get(KEY_UNKNOWN_RECEIVE_VAR2));
        updateStringChannel(CHANNEL_AS, data.get(KEY_UNKNOWN_SEND_VAR1));

        // same channel for both grill and cook status
        // updateSwitchChannel(CHANNEL_COOK_START, data.get(KEY_COOK_START));
        updateSwitchChannel(CHANNEL_GRILL_RUNNING, data.get(KEY_COOK_START));

        // updateTemperatureChannel(CHANNEL_TARGET_FOOD_TEMP, data.get(KEY_TARGET_FOOD_TEMP));

        // updateNumberChannel(CHANNEL_FOOD_PROBE, data.getOrDefault(KEY_FOOD_PROBE, "0"));

        updateDateChannel(CHANNEL_CURRENT_TIME, data.get(KEY_CURRENT_TIME));
        updateDateChannel(CHANNEL_COOK_END_TIME, data.get(KEY_COOK_END_TIME));
        updateDateChannel(CHANNEL_GRILL_END_TIME, data.get(KEY_GRILL_END_TIME));

        // updateStringChannel(CHANNEL_COOK_ID, data.get(KEY_COOK_ID));
    }

    private void updateTemperatureChannel(String channelId, @Nullable String valueStr) {
        if (valueStr == null || valueStr.isEmpty()) {
            updateState(new ChannelUID(getThing().getUID(), channelId), UnDefType.UNDEF);
            return;
        }
        try {
            BigDecimal value = new BigDecimal(valueStr);
            if (value.compareTo(TEMPERATURE_UNDEFINED_MIN) == 0 || value.compareTo(TEMPERATURE_UNDEFINED_MAX) == 0) {
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

    private void startCook(int probe, int targetTemperature) {
        long currentTime = Instant.now().getEpochSecond();
        long currentTimePlusOneDay = currentTime + 86400;

        String url = "http://" + config.hostname + "/cgi-bin/cook";
        StringBuilder payload = new StringBuilder();
        appendQuery(payload, KEY_COOK_START, "1");
        appendQuery(payload, KEY_COOK_ID, String.valueOf(probe));
        appendQuery(payload, KEY_TARGET_PIT_TEMP, String.valueOf(cachedTargetGrillTemperature));
        appendQuery(payload, KEY_COOK_END_TIME, String.valueOf(currentTimePlusOneDay));
        appendQuery(payload, KEY_FOOD_PROBE, String.valueOf(probe));
        appendQuery(payload, KEY_TARGET_FOOD_TEMP, String.valueOf(targetTemperature));
        // appendQuery(payload, KEY_UNKNOWN_SEND_VAR1, "0");
        appendQuery(payload, KEY_CURRENT_TIME, String.valueOf(currentTime));

        sendPost(url, payload.toString());
    }

    private void stopCook(int probe) {

        String url = "http://" + config.hostname + "/cgi-bin/cook";
        long currentTime = Instant.now().getEpochSecond();
        // long currentTimePlusOneDay = currentTime + 86400;

        StringBuilder payload = new StringBuilder();
        appendQuery(payload, KEY_COOK_START, "0");
        appendQuery(payload, KEY_COOK_ID, String.valueOf(probe));
        // appendQuery(payload, KEY_TARGET_PIT_TEMP, String.valueOf(targetPitTemperature));
        // appendQuery(payload, KEY_COOK_END_TIME, String.valueOf(currentTimePlusOneDay));
        appendQuery(payload, KEY_TARGET_FOOD_TEMP, "0");
        // appendQuery(payload, KEY_UNKNOWN_SEND_VAR1, "0");
        // appendQuery(payload, KEY_CURRENT_TIME, String.valueOf(currentTime));

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
        appendQuery(payload, "ssid", java.util.Base64.getEncoder().encodeToString(ssid.getBytes()));
        appendQuery(payload, "pass", java.util.Base64.getEncoder().encodeToString(pass.getBytes()));
        appendQuery(payload, "user", java.util.Base64.getEncoder().encodeToString(user.getBytes()));
        sendPost(url, payload.toString());
    }

    private void startGrill(int targetPitTemperature) {
        long currentTime = Instant.now().getEpochSecond();
        long cookEnd = currentTime + 86400L;

        StringBuilder payload = new StringBuilder();
        appendQuery(payload, KEY_COOK_START, "1");
        appendQuery(payload, KEY_COOK_ID, "");
        appendQuery(payload, KEY_TARGET_PIT_TEMP, String.valueOf(targetPitTemperature));
        appendQuery(payload, KEY_COOK_END_TIME, String.valueOf(cookEnd));
        appendQuery(payload, KEY_FOOD_PROBE, "0");
        appendQuery(payload, KEY_TARGET_FOOD_TEMP, "0");
        appendQuery(payload, KEY_UNKNOWN_SEND_VAR1, "0");
        appendQuery(payload, KEY_CURRENT_TIME, String.valueOf(currentTime));

        sendPost("http://" + config.hostname + "/cgi-bin/cook", payload.toString());
    }

    private void stopGrill() {
        long currentTime = Instant.now().getEpochSecond();

        StringBuilder payload = new StringBuilder();
        appendQuery(payload, KEY_COOK_START, "0");
        appendQuery(payload, KEY_COOK_ID, "");
        appendQuery(payload, KEY_TARGET_PIT_TEMP, "0");
        appendQuery(payload, KEY_COOK_END_TIME, String.valueOf(currentTime));
        appendQuery(payload, KEY_FOOD_PROBE, "0");
        appendQuery(payload, KEY_TARGET_FOOD_TEMP, "0");
        appendQuery(payload, KEY_UNKNOWN_SEND_VAR1, "0");
        appendQuery(payload, KEY_CURRENT_TIME, String.valueOf(currentTime));

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
                    writer.write("User-Agent: ikamand\r\n");
                    writer.write("Connection: close\r\n\r\n");
                } else {
                    byte[] bodyBytes = postBody.getBytes(StandardCharsets.US_ASCII);
                    writer.write("POST " + path + " HTTP/1.0\r\n");
                    writer.write("Host: " + host + "\r\n");
                    writer.write("Content-Type: application/x-www-form-urlencoded\r\n");
                    writer.write("Content-Length: " + bodyBytes.length + "\r\n");
                    writer.write("User-Agent: ikamand\r\n");
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

    private StringBuilder appendQuery(StringBuilder sb, String key, String value) {
        if (sb.length() > 0) {
            sb.append("&");
        }
        sb.append(key).append("=").append(value);
        return sb;
    }

    public static @Nullable Integer temperatureToValue(Type type) {
        BigDecimal value = null;
        if (type instanceof QuantityType<?> quantity) {
            if (quantity.getUnit() == SIUnits.CELSIUS) {
                value = quantity.toBigDecimal();
            } else if (quantity.getUnit() == ImperialUnits.FAHRENHEIT) {
                QuantityType<?> celsius = quantity.toUnit(SIUnits.CELSIUS);
                if (celsius != null) {
                    value = celsius.toBigDecimal();
                }
            }
        } else if (type instanceof Number number) {
            // No scale, so assumed to be Celsius
            value = BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return null;
        }
        // originally this used RoundingMode.CEILING, if there are accuracy problems, we may want to revisit that
        return value.setScale(2, RoundingMode.HALF_UP).intValue();
    }
}
