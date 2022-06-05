/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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
package org.openhab.binding.grandstreamgds.internal;

import static org.openhab.binding.grandstreamgds.internal.GDSConfigParameter.*;
import static org.openhab.binding.grandstreamgds.internal.GrandstreamGDSBindingConstants.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.util.Fields;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openhab.binding.grandstreamgds.internal.dto.GDSEventDTO;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.net.NetworkAddressService;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.openhab.core.util.HexUtils;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link GrandstreamGDSHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class GrandstreamGDSHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(GrandstreamGDSHandler.class);
    private static final String API_CMD_LOGIN_URL = "%s/goform/apicmd?cmd=0&user=%s";
    private static final String CONFIG_LOGIN_CHALLENGE_URL = "%s/goform/login?cmd=login&user=%s&type=0";
    private static final String CONFIG_LOGIN_RESPONSE_URL = "%s/goform/login?cmd=login&user=%s&authcode=%s&type=0";
    private static final String CONFIG_SET_URL = "%s/goform/config";
    private static final String CONFIG_EXPORT_URL = "%s/goform/config?cmd=export&type=0&data_type=1";
    private static final String CONFIG_EVENT_STREAM_URL = "%s:%s/%s/%s";
    private static final String CONFIG_EVENT_PAYLOAD_FORMAT = "{\"mac\":\"${MAC}\",\"type\":\"${TYPE}\",\"content\":\"${WARNING_MSG}\",\"sipnum\":\"${SIPNUM}\",\"username\":\"${USERNAME}\",\"date\":\"${DATE}\",\"cardid\":\"${CARDID}\"}";
    private static final String CONFIG_CHALLENGE_CODE = "Configuration/ChallengeCode";
    private final Gson gson = new GsonBuilder().create();

    private String gdsBaseURL = "";
    private String userName = "";
    private String password = "";
    private int refreshDurationSeconds = 60;

    private HttpClient httpClient;
    private NetworkAddressService networkAddressService;
    private final HttpService httpService;
    private final String servletPath;
    private @Nullable ScheduledFuture<?> pollFuture;

    public GrandstreamGDSHandler(Thing thing, HttpService httpService, NetworkAddressService networkAddressService) {
        super(thing);
        this.networkAddressService = networkAddressService;
        this.httpService = httpService;
        httpClient = new HttpClient(new SslContextFactory.Client(true));
        servletPath = GrandstreamGDSBindingConstants.BASE_SERVLET_PATH + "/" + getThing().getUID().getId();
        try {
            httpService.registerServlet(servletPath, new GDSEventServlet(), null,
                    httpService.createDefaultHttpContext());
            logger.debug("GrandstreamGDSEventServlet started at '{}'", servletPath);
        } catch (NamespaceException | ServletException | IllegalArgumentException e) {
            logger.warn("Could not start GrandstreamGDSEventServlet", e);
        }
    }

    @Override
    public void initialize() {
        if (!httpClient.isStarted()) {
            try {
                httpClient.start();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        GrandstreamGDSConfiguration config = getConfigAs(GrandstreamGDSConfiguration.class);
        gdsBaseURL = config.url;
        userName = config.username;
        password = config.password;
        refreshDurationSeconds = config.refreshSeconds;
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> {
            try {
                if (config.modifyGdsConfig) {
                    updateEventStreamConfig();
                }
                initPolling(0);
            } catch (InterruptedException | ExecutionException | TimeoutException | GDSResponseException e) {
                logger.debug("Could not update GDS", e);
            }
        });
    }

    @Override
    public void dispose() {
        clearPolling();

        try {
            httpService.unregister(servletPath);
        } catch (IllegalArgumentException e) {
            logger.debug("Error unregestering servlet", e);
        }

        // if (httpClient.isStarted()) {
        try {
            httpClient.stop();
        } catch (Exception e) {
            logger.debug("Error stopping httpClient", e);
        }
        // }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("handleCommand {} {}", channelUID, command);
        if (command instanceof RefreshType) {
            initPolling(0);
            return;
        }
        if (CHANNEL_DOOR_OPEN.equals(channelUID.getId())) {
            if (command instanceof StringType) {
                String remoteCode;
                try {
                    remoteCode = getRemoteCode();
                    if (remoteCode != null) {
                        openDoor(remoteCode);
                    }
                } catch (InterruptedException | ExecutionException | TimeoutException | GDSResponseException e) {
                    logger.debug("Could not open gate", e);
                }
                initPolling(5);
            }

        } else if (CHANNEL_KEEP_DOOR_OPEN.equals(channelUID.getId())) {
            if (command instanceof DecimalType) {
                try {
                    int len = keepDoorOpen(((DecimalType) command).intValue());
                    updateState(channelUID, new DecimalType(len));
                } catch (InterruptedException | ExecutionException | TimeoutException | GDSResponseException e) {
                    logger.debug("Could not open gate", e);
                }
            }
            initPolling(5);
        } else if (channelUID.getId().startsWith("call_")) {
            String account = "auto";
            switch (channelUID.getId()) {
                case CHANNEL_CALL_HANGUP:
                    try {
                        hangup();
                    } catch (InterruptedException | ExecutionException | TimeoutException | GDSResponseException e) {
                        logger.debug("Could not hangup", e);
                    }
                    break;
                case CHANNEL_CALL_ACCOUNT1:
                    account = "1";
                case CHANNEL_CALL_ACCOUNT2:
                    account = "2";
                case CHANNEL_CALL_ACCOUNT3:
                    account = "3";
                case CHANNEL_CALL_ACCOUNT4:
                    account = "4";
                default:
                    try {
                        call(account, command.toString());
                    } catch (InterruptedException | ExecutionException | TimeoutException | GDSResponseException e) {
                        logger.debug("Could not call", e);
                    }

            }
        }
    }

    private void handleGDSEvent(String data) {
        GDSEventDTO event = gson.fromJson(data, GDSEventDTO.class);
        if (event != null) {
            logger.debug("Event type {} : {} {}", event.type, event.date, event.content);

            GDSEventType type = GDSEventType.fromType(event.type);
            if (type != null) {
                triggerChannel("event_all", data);
                triggerChannel("event_" + type.name().toLowerCase());

                switch (type) {
                    case DI_ALARM:
                    case KEEP_DOOR_OPEN_IMMEDIATELY:
                    case KEEP_DOOR_OPEN_SCHEDULED:
                        // umm, this needs to be reworked to handle D1 and D2
                        updateState(CHANNEL_DI_1, OpenClosedType.OPEN);
                        initPolling(5);
                        break;
                    default:
                }
            }
        }
    }

    private synchronized void initPolling(int initalDelay) {
        clearPolling();
        pollFuture = scheduler.scheduleWithFixedDelay(this::poll, initalDelay, refreshDurationSeconds,
                TimeUnit.SECONDS);
    }

    /**
     * Stops/clears this thing's polling future
     */
    private void clearPolling() {
        ScheduledFuture<?> pollFuture = this.pollFuture;
        if (pollFuture != null) {
            logger.trace("Canceling future");
            pollFuture.cancel(true);
        }
    }

    private void poll() {
        try {
            String cookie = configLogin();
            logger.debug("Cookie {}", cookie);
            if (cookie != null) {
                updateDigitalInputState(cookie);
                updateKeepDoorOpen(cookie);
                if (getThing().getStatus() != ThingStatus.ONLINE) {
                    updateStatus(ThingStatus.ONLINE);
                }
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.debug("Could not update DI states", e);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
        } catch (GDSResponseException e) {
            String message = e.responseCode.getDescription();
            switch (e.responseCode) {
                case AUTHENTICATION_FAILED:
                case RETRIEVE_PASSWORD_NO_ACCOUNT:
                case USER_DOES_NOT_EXIST:
                case PASSWORD_ERROR:
                    clearPolling();
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, message);
                    break;
                default:
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, message);
            }
        }
    }

    private void updateEventStreamConfig()
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        String ip = networkAddressService.getPrimaryIpv4HostAddress();
        if (ip == null) {
            logger.debug("Could not determine IP address, not setting event stream config");
            return;
        }

        @SuppressWarnings("null")
        int httpLisenerPort = Integer.getInteger("org.osgi.service.http.port", 8080);

        // if (httpLisenerPort <= 0) {
        // logger.warn("Http listening port not found, aborting GDS update");
        // return;
        // }

        String cookie = configLogin();
        if (cookie == null) {
            logger.warn("Unexpected response, aborting GDS update");
            return;
        }
        String eventUrl = String.format(CONFIG_EVENT_STREAM_URL, ip, httpLisenerPort,
                GrandstreamGDSBindingConstants.BINDING_ID, this.getThing().getUID().getId());
        Fields fields = new Fields();
        fields.put("cmd", "set");
        // set HTTP (1) or HTTPS (2)
        fields.put(EVENT_NOTIFICATION_EVENT_UPGRADE_TYPE.getId(), "1");
        // set enable events
        fields.put(EVENT_NOTIFICATION_ENABLE_EVENT_NOTIFICATION.getId(), "1");
        // set the callback url (host/path)
        fields.put(EVENT_NOTIFICATION_HTTP_SERVER_URL.getId(), eventUrl);
        // set the payload template
        fields.put(EVENT_NOTIFICATION_URL_TEMPLATE.getId(), CONFIG_EVENT_PAYLOAD_FORMAT);
        ContentResponse response = doPost(String.format(CONFIG_SET_URL, gdsBaseURL), cookie, fields);
        logger.debug("Config event url set response {}", response.getContentAsString());
    }

    private @Nullable String getRemoteCode()
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        String config = getGDSConfig();
        if (config != null) {
            return getXMLValue(config, "gs_provision/config/" + DOOR_SYSTEM_REMOTE_PIN_TO_OPEN_THE_DOOR.getId());
        }
        return null;
    }

    private void updateDigitalInputState(String cookie)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        Fields fields = new Fields();
        fields.put("cmd", "get");
        fields.put("type", "event");
        fields.put("t", String.valueOf(System.currentTimeMillis()));
        ContentResponse response = doPost(String.format(CONFIG_SET_URL, gdsBaseURL), cookie, fields);
        String content = response.getContentAsString();
        // String digital1_NO_NC = getXMLValue(content, "Configuration/" + EVENT_DIGIT_INPUT_1_STATUS.getId());
        // String digital2_NO_NC = getXMLValue(content, "Configuration/" + EVENT_DIGIT_INPUT_2_STATUS.getId());

        String digital1 = getXMLValue(content, "Configuration/ALMIN1_STATUS");
        String digital2 = getXMLValue(content, "Configuration/ALMIN2_STATUS");
        logger.debug("DI_1 {} : DI_2 {}", digital1, digital2);
        updateState(CHANNEL_DI_1, diToState(digital1));
        // we should probably add a config option to tie a DI channel to the doorstate, doing this now for homekit
        logger.debug("Sending {} to {}", diToState(digital1), CHANNEL_DOOR_OPEN);
        updateState(CHANNEL_DOOR_OPEN, new StringType(diToState(digital1).toString()));
        updateState(CHANNEL_DI_2, diToState(digital2));
    }

    private State diToState(@Nullable String value) {
        if (value == null) {
            return UnDefType.UNDEF;
        }
        if ("0".equals(value)) {
            return OpenClosedType.CLOSED;
        }
        if ("1".equals(value)) {
            return OpenClosedType.OPEN;
        }
        return UnDefType.UNDEF;
    }

    private void updateKeepDoorOpen(String cookie)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        Fields fields = new Fields();
        fields.put("cmd", "get");
        fields.put("type", "sch_open_door");
        fields.put("t", String.valueOf(System.currentTimeMillis()));
        ContentResponse response = doPost(String.format(CONFIG_SET_URL, gdsBaseURL), cookie, fields);
        String content = response.getContentAsString();
        String openState = getXMLValue(content, "Configuration/" + FORCED_OPENING_FORCED_OPENING_DOOR_1.getId());
        String openLength = getXMLValue(content, "Configuration/" + FORCED_OPENING_LENGTH_DOOR_1.getId());
        logger.debug("openState {} : openLength {}", openState, openLength);
        if (openState != null && openLength != null) {
            updateState(CHANNEL_KEEP_DOOR_OPEN,
                    new DecimalType("0".equals(openState) ? 0 : Integer.parseInt(openLength)));
        }
    }

    private int keepDoorOpen(int minutes)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        String cookie = configLogin();
        logger.debug("Cookie {}", cookie);
        if (cookie == null) {
            throw new GDSResponseException(GDSResponseCode.UNKNOWN);
        }

        int len = minutes <= 0 ? 0 : Math.min(480, Math.max(5, minutes));// 5-480
        Fields fields = new Fields();
        fields.put("cmd", "set");
        if (len == 0) {
            fields.put("P15429", "0");
        } else {
            fields.put("P15429", "1");
            fields.put("P15430", String.valueOf(len));
        }
        ContentResponse response = doPost(String.format(CONFIG_SET_URL, gdsBaseURL), cookie, fields);
        String content = response.getContentAsString();
        logger.debug("keepDoorOpen response {}", content);
        return len;
    }

    private void call(String account, String dialString)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        String cookie = configLogin();
        logger.debug("Cookie {}", cookie);
        if (cookie == null) {
            throw new GDSResponseException(GDSResponseCode.UNKNOWN);
        }
        Fields fields = new Fields();
        fields.put("cmd", "call");
        fields.put("call_type", "1");
        fields.put("account", account);
        fields.put("call_num", dialString);

        ContentResponse response = doPost(String.format(CONFIG_SET_URL, gdsBaseURL), cookie, fields);
        String content = response.getContentAsString();
        logger.debug("call response {}", content);
    }

    private void hangup() throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        String cookie = configLogin();
        logger.debug("Cookie {}", cookie);
        if (cookie == null) {
            throw new GDSResponseException(GDSResponseCode.UNKNOWN);
        }
        Fields fields = new Fields();
        fields.put("cmd", "call");
        fields.put("call_type", "0");

        ContentResponse response = doPost(String.format(CONFIG_SET_URL, gdsBaseURL), cookie, fields);
        String content = response.getContentAsString();
        logger.debug("hangup response {}", content);
    }

    private @Nullable String getGDSConfig()
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        String cookie = configLogin();
        logger.debug("Cookie {}", cookie);
        if (cookie != null) {
            String doorConfigUrl = String.format(CONFIG_EXPORT_URL, gdsBaseURL);
            // ContentResponse response = doGet(doorConfigUrl, cookie);
            // we don't want GDS response checking, so call httpClient directly
            ContentResponse response = httpClient.newRequest(doorConfigUrl).header("Cookie", cookie).send();
            return response.getContentAsString();
        }
        return null;
    }

    private @Nullable String configLogin()
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        ContentResponse response = doGet(String.format(CONFIG_LOGIN_CHALLENGE_URL, gdsBaseURL, userName));
        String content = response.getContentAsString();
        String challengeCode = getXMLValue(content, CONFIG_CHALLENGE_CODE);
        String secret = String.format("%s:%s:%s", challengeCode, "GDS3710lZpRsFzCbM", password);
        logger.debug("Secret {}", secret);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Coult not load md5 instance", e);
        }

        md.update(secret.getBytes());
        String hash = HexUtils.bytesToHex(md.digest()).toLowerCase();
        String loginUrl = String.format(CONFIG_LOGIN_RESPONSE_URL, gdsBaseURL, userName, hash);
        logger.debug("loginUrl {}", loginUrl);
        response = httpClient.GET(loginUrl);
        return response.getHeaders().get("Set-Cookie");
    }

    private void openDoor(String remoteCode)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        ContentResponse response = doGet(String.format(API_CMD_LOGIN_URL, gdsBaseURL, userName));
        String content = response.getContentAsString();

        String challengeCode = getXMLValue(content, CONFIG_CHALLENGE_CODE);
        logger.debug("Challenge Code {}", challengeCode);

        String idCode = getXMLValue(content, "Configuration/IDCode");
        String secret = String.format("%s:%s:%s", challengeCode, remoteCode, password);

        MessageDigest md;
        try {
            md = MessageDigest.getInstance("MD5");
            md.update(secret.getBytes());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Coult not load md5 instance", e);
        }

        String hash = HexUtils.bytesToHex(md.digest()).toLowerCase();
        String doorOpenUrl = String.format("%s/goform/apicmd?cmd=1&user=%s&authcode=%s&idcode=%s&type=1", gdsBaseURL,
                userName, hash, idCode);
        doGet(doorOpenUrl);
    }

    private ContentResponse doGet(String url)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        return doGet(url, null);
    }

    private ContentResponse doGet(String url, @Nullable String cookie)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        logger.trace("doGet Request {}", url);
        Request request = httpClient.newRequest(url);
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        ContentResponse response = request.send();
        String content = response.getContentAsString();
        logger.trace("doGet Response {}", content);
        checkGDSResponse(content);
        return response;
    }

    private ContentResponse doPost(String url, String cookie, Fields formData)
            throws InterruptedException, ExecutionException, TimeoutException, GDSResponseException {
        logger.trace("doPost Request {}", url);
        ContentResponse response = httpClient.newRequest(String.format(CONFIG_SET_URL, gdsBaseURL)).method("POST")
                .header("Cookie", cookie).content(new FormContentProvider(formData)).send();
        String content = response.getContentAsString();
        logger.trace("doPost Response {}", content);
        checkGDSResponse(content);
        return response;
    }

    private @Nullable String getXMLValue(String doc, String path) {
        XPathFactory xpathFactory = XPathFactory.newInstance();
        XPath xpath = xpathFactory.newXPath();
        InputSource is = new InputSource(new StringReader(doc));
        try {
            return xpath.evaluate(path, is);
        } catch (XPathExpressionException e) {
            return null;
        }
    }

    private void checkGDSResponse(String data) throws GDSResponseException {
        String value = getXMLValue(data, "Configuration/ResCode");
        GDSResponseCode responseCode = GDSResponseCode.fromCode(value);
        if (responseCode != GDSResponseCode.SUCCESS) {
            throw new GDSResponseException(responseCode);
        }
    }

    private class GDSEventServlet extends HttpServlet {
        private static final long serialVersionUID = 196528887872920765L;

        @Override
        protected void service(@Nullable HttpServletRequest request, @Nullable HttpServletResponse resp)
                throws ServletException, IOException, IllegalArgumentException {

            if (request == null || resp == null) {
                logger.debug("bad request");
                return;
            }

            String body = request.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
            logger.debug("GDS Event {}", body);
            handleGDSEvent(body);
            resp.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            resp.getWriter().write("ok");
        }
    }

    private class GDSResponseException extends Exception {
        private GDSResponseCode responseCode;

        public GDSResponseException(GDSResponseCode responseCode) {
            super();
            this.responseCode = responseCode;
        }

        public GDSResponseCode getGSDResponseCode() {
            return responseCode;
        }

        @Override
        public String toString() {
            return responseCode.getDescription();
        }
    }
}
