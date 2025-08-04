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
package org.openhab.binding.linkplay.internal.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.openhab.binding.linkplay.internal.client.adaptors.PlayerStatusAdapter;
import org.openhab.binding.linkplay.internal.client.dto.AlarmClockInfo;
import org.openhab.binding.linkplay.internal.client.dto.AudioOutputHardwareMode;
import org.openhab.binding.linkplay.internal.client.dto.BTPairStatus;
import org.openhab.binding.linkplay.internal.client.dto.BluetoothDeviceList;
import org.openhab.binding.linkplay.internal.client.dto.DeviceStatus;
import org.openhab.binding.linkplay.internal.client.dto.EQBandResponse;
import org.openhab.binding.linkplay.internal.client.dto.EQStatResponse;
import org.openhab.binding.linkplay.internal.client.dto.PlayerStatus;
import org.openhab.binding.linkplay.internal.client.dto.PresetList;
import org.openhab.binding.linkplay.internal.client.dto.SlaveListResponse;
import org.openhab.binding.linkplay.internal.client.dto.SourceInputMode;
import org.openhab.binding.linkplay.internal.client.dto.StaticIpInfo;
import org.openhab.binding.linkplay.internal.client.dto.StatusResponse;
import org.openhab.binding.linkplay.internal.client.dto.TrackMetadata;
import org.openhab.binding.linkplay.internal.client.dto.WlanConnectState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * HTTP client for LinkPlay devices.
 * 
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class LinkPlayHTTPClient {

    private final HttpClient httpClient;
    private String hostname;
    private final Gson gson = new GsonBuilder().registerTypeAdapter(PlayerStatus.class, new PlayerStatusAdapter())
            .create();
    private final Logger logger = LoggerFactory.getLogger(LinkPlayHTTPClient.class);

    public LinkPlayHTTPClient(HttpClient httpClient, String hostname) {
        this.hostname = hostname;
        this.httpClient = httpClient;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * Attempts to call the LinkPlay REST API using the following order until one succeeds:
     * 1. HTTPS on port 443
     * 2. HTTPS on port 4443 (seen on some firmwares)
     * 3. HTTP on port 80 (fallback)
     *
     * A request is considered successful if the TCP/SSL handshake succeeds and the server responds
     * with any HTTP status (usually 200). If all endpoints fail, the last exception is propagated.
     */
    private <T> CompletableFuture<T> sendGetRequest(String command, Class<T> responseType) {
        Executor executor = httpClient.getExecutor();
        return CompletableFuture.supplyAsync(() -> {
            Exception lastException = null;

            String[] urls = new String[] {
                    String.format("https://%s:%d/httpapi.asp?command=%s", hostname, 443, command),
                    String.format("https://%s:%d/httpapi.asp?command=%s", hostname, 4443, command),
                    String.format("http://%s:%d/httpapi.asp?command=%s", hostname, 80, command) };

            for (String url : urls) {
                try {
                    logger.trace("Sending GET request to {}", url);
                    ContentResponse response = httpClient.GET(url);
                    String payload = response.getContentAsString();
                    logger.trace("Response: {}", payload);
                    if (responseType == String.class) {
                        @SuppressWarnings("unchecked")
                        T casted = (T) payload;
                        return casted;
                    }
                    if(payload.equals("Failed")) {
                        throw new RuntimeException("Response Failed");
                    }

                    T result = gson.fromJson(payload, responseType);
                    if (result == null) {
                        throw new RuntimeException("Response is null");
                    }
                    return result;
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    logger.trace("Failed to send GET request to {}: {}", url, e.getMessage());
                    // Remember and try next candidate.
                    lastException = e;
                }
            }

            // If we reach here, all attempts failed.
            throw new RuntimeException("Failed to execute request after trying multiple endpoints", lastException);
        }, executor);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Generic command – allows sending an arbitrary command that is not explicitly covered by the helper methods.
     *
     * @param command the raw command to send (e.g. "getStatusEx" or "setPlayerCmd:pause")
     * @return a CompletableFuture with the raw response as String
     */
    public CompletableFuture<String> genericCommand(String command) {
        return sendGetRequest(command, String.class);
    }

    /**
     * Corresponds to /getStatusEx – Retrieves detailed information about the device.
     */
    public CompletableFuture<DeviceStatus> getStatusEx() {
        return sendGetRequest("getStatusEx", DeviceStatus.class);
    }

    /**
     * Corresponds to /getMetaInfo – Retrieves current track metadata.
     */
    public CompletableFuture<TrackMetadata> getMetaInfo() {
        return sendGetRequest("getMetaInfo", TrackMetadata.class);
    }

    /**
     * Corresponds to /getPlayerStatus – Retrieves current playback status.
     */
    public CompletableFuture<PlayerStatus> getPlayerStatus() {
        return sendGetRequest("getPlayerStatus", PlayerStatus.class);
    }

    /**
     * Corresponds to /setPlayerCmd:hex_playlist:url:{index} – Play a specific track in a hex-encoded playlist.
     *
     * @param index playlist index to start from
     * @return a CompletableFuture with the raw response (typically "OK")
     */
    public CompletableFuture<String> setPlayerCmdHexPlaylistUrl(String index) {
        return sendGetRequest("setPlayerCmd:hex_playlist:url:" + index, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:pause – Pause playback.
     */
    public CompletableFuture<String> setPlayerCmdPause() {
        return sendGetRequest("setPlayerCmd:pause", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:resume – Resume playback.
     */
    public CompletableFuture<String> setPlayerCmdResume() {
        return sendGetRequest("setPlayerCmd:resume", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:onepause – Toggle pause/resume depending on current state.
     */
    public CompletableFuture<String> setPlayerCmdOnePause() {
        return sendGetRequest("setPlayerCmd:onepause", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:play:{url} – Play an audio stream by URL.
     *
     * @param url Stream URL to play (will be URL-encoded before sending).
     */
    public CompletableFuture<String> setPlayerCmdPlayUrl(String url) {
        return sendGetRequest("setPlayerCmd:play:" + encode(url), String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:playlist:{url}:{index} – Play an audio playlist and start at a given index.
     *
     * @param url Playlist URL (m3u, ASX, etc.) to be URL-encoded.
     * @param index Start index inside the playlist (use "0" for first item)
     */
    public CompletableFuture<String> setPlayerCmdPlaylistUrl(String url, String index) {
        return sendGetRequest("setPlayerCmd:playlist:" + encode(url) + ":" + index, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:prev – Skip to previous item.
     */
    public CompletableFuture<String> setPlayerCmdPrev() {
        return sendGetRequest("setPlayerCmd:prev", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:next – Skip to next item.
     */
    public CompletableFuture<String> setPlayerCmdNext() {
        return sendGetRequest("setPlayerCmd:next", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:seek:{position} – Seek to an absolute position (seconds).
     *
     * @param position target position in seconds (0 – duration)
     */
    public CompletableFuture<String> setPlayerCmdSeekPosition(int position) {
        return sendGetRequest("setPlayerCmd:seek:" + position, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:stop – Stop playback.
     */
    public CompletableFuture<String> setPlayerCmdStop() {
        return sendGetRequest("setPlayerCmd:stop", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:vol:{value} – Set volume (0-100).
     *
     * @param value volume percentage (0–100)
     */
    public CompletableFuture<String> setPlayerCmdVol(int value) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException("Volume must be between 0 and 100");
        }
        return sendGetRequest("setPlayerCmd:vol:" + value, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:mute:{n} – Mute or unmute device.
     * 
     * @param n 1 to mute, 0 to unmute
     */
    public CompletableFuture<String> setPlayerCmdMute(int n) {
        if (n != 0 && n != 1) {
            throw new IllegalArgumentException("n must be 0 (unmute) or 1 (mute)");
        }
        return sendGetRequest("setPlayerCmd:mute:" + n, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:loopmode:{n} – Set loop/shuffle mode.
     * See API documentation for meaning of values (-1..5).
     */
    public CompletableFuture<String> setPlayerCmdLoopmode(int n) {
        if (n < -1 || n > 5) {
            throw new IllegalArgumentException("loopmode must be between -1 and 5");
        }
        return sendGetRequest("setPlayerCmd:loopmode:" + n, String.class);
    }

    /**
     * Corresponds to /getStaticIpInfo – Retrieve LAN/WLAN static IP configuration.
     */
    public CompletableFuture<StaticIpInfo> getStaticIpInfo() {
        return sendGetRequest("getStaticIpInfo", StaticIpInfo.class);
    }

    /**
     * Corresponds to /getStaticIP (deprecated) – Query networking status as raw string.
     */
    public CompletableFuture<String> getStaticIP() {
        return sendGetRequest("getStaticIP", String.class);
    }

    /**
     * Corresponds to /wlanGetConnectState – Get WiFi connection state.
     */
    public CompletableFuture<WlanConnectState> wlanGetConnectState() {
        return sendGetRequest("wlanGetConnectState", String.class).thenApply(WlanConnectState::fromString);
    }

    /**
     * Corresponds to /setWlanStaticIp:{IpAddress}:{GatewayIp}:{DnsServerIp} – Configure static WLAN IP.
     */
    public CompletableFuture<String> setWlanStaticIp(String ipAddress, String gatewayIp, String dnsServerIp) {
        return sendGetRequest(
                "setWlanStaticIp:" + encode(ipAddress) + ":" + encode(gatewayIp) + ":" + encode(dnsServerIp),
                String.class);
    }

    /**
     * Corresponds to /EQOn – Turn EQ on.
     */
    public CompletableFuture<StatusResponse> setEQOn() {
        return sendGetRequest("EQOn", StatusResponse.class);
    }

    /**
     * Corresponds to /EQOff – Turn EQ off.
     */
    public CompletableFuture<StatusResponse> setEQOff() {
        return sendGetRequest("EQOff", StatusResponse.class);
    }

    /**
     * Corresponds to /EQGetStat – Check if EQ is ON or OFF.
     */
    public CompletableFuture<EQStatResponse> getEQStat() {
        return sendGetRequest("EQGetStat", EQStatResponse.class);
    }

    /**
     * Corresponds to /EQGetList – Retrieve all available EQ presets.
     */
    public CompletableFuture<String[]> getEQList() {
        return sendGetRequest("EQGetList", String[].class);
    }

    /**
     * Corresponds to /EQGetBand – Get current EQ band configuration.
     */
    public CompletableFuture<EQBandResponse> getEQBand() {
        return sendGetRequest("EQGetBand", EQBandResponse.class);
    }

    /**
     * Corresponds to /EQLoad:{name} – Load a named EQ preset.
     * Accepts one of the names returned by getEQList().
     */
    public CompletableFuture<StatusResponse> loadEQByName(String name) {
        return sendGetRequest("EQLoad:" + encode(name), StatusResponse.class);
    }

    /**
     * Corresponds to /reboot – Reboot the device.
     */
    public CompletableFuture<StatusResponse> rebootDevice() {
        return sendGetRequest("reboot", StatusResponse.class);
    }

    /**
     * Corresponds to /setShutdown:{sec} – Schedule (or cancel) shutdown.
     *
     * @param sec seconds until shutdown (0 immediately, -1 cancel)
     */
    public CompletableFuture<StatusResponse> setShutdownTimer(int sec) {
        return sendGetRequest("setShutdown:" + sec, StatusResponse.class);
    }

    /**
     * Corresponds to /getShutdown – Retrieve current shutdown timer seconds.
     */
    public CompletableFuture<Integer> getShutdownTimer() {
        return sendGetRequest("getShutdown", Integer.class);
    }

    /**
     * Corresponds to /LED_SWITCH_SET:{n} – Turn status LED on/off.
     * 
     * @param n 1 on, 0 off
     */
    public CompletableFuture<String> setLedSwitch(int n) {
        if (n != 0 && n != 1) {
            throw new IllegalArgumentException("n must be 0 or 1");
        }
        return sendGetRequest("LED_SWITCH_SET:" + n, String.class);
    }

    /**
     * Corresponds to /Button_Enable_SET:{n} – Enable/disable touch controls.
     * 
     * @param n 1 on, 0 off
     */
    public CompletableFuture<String> setTouchControls(int n) {
        if (n != 0 && n != 1) {
            throw new IllegalArgumentException("n must be 0 or 1");
        }
        return sendGetRequest("Button_Enable_SET:" + n, String.class);
    }

    /**
     * Corresponds to /timeSync:{YYYYMMDDHHMMSS} – Sync device time (UTC).
     * Pass dateTime string exactly in required format.
     */
    public CompletableFuture<String> setTimeSync(String yyyymmddhhmmss) {
        return sendGetRequest("timeSync:" + yyyymmddhhmmss, String.class);
    }

    /**
     * Corresponds to /setAlarmClock:{n}:{trig}:{op}:{time}:{day}:{url}
     */
    public CompletableFuture<String> setAlarmClock(int number, int trigger, int operation, String time, String day,
            String url) {
        String path = String.format("setAlarmClock:%d:%d:%d:%s:%s:%s", number, trigger, operation, time, day,
                encode(url));
        return sendGetRequest(path, String.class);
    }

    /**
     * Corresponds to /getAlarmClock:{n}
     */
    public CompletableFuture<AlarmClockInfo> getAlarmClock(int number) {
        return sendGetRequest("getAlarmClock:" + number, AlarmClockInfo.class);
    }

    /**
     * Corresponds to /alarmStop – Stop current alarm.
     */
    public CompletableFuture<String> stopAlarmClock() {
        return sendGetRequest("alarmStop", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:switchmode:{mode} – Switch input source.
     */
    public CompletableFuture<String> setPlayerCmdSwitchMode(SourceInputMode mode) {
        return sendGetRequest("setPlayerCmd:switchmode:" + mode.toString(), String.class);
    }

    /**
     * Corresponds to /MCUKeyShortClick:{n} – Play preset by number.
     */
    public CompletableFuture<String> mcuKeyShortClick(int n) {
        if (n < 1 || n > 12) {
            throw new IllegalArgumentException("Preset number must be 1-12");
        }
        return sendGetRequest("MCUKeyShortClick:" + n, String.class);
    }

    /**
     * Corresponds to /getPresetInfo – Retrieve preset list.
     */
    public CompletableFuture<PresetList> getPresetInfo() {
        return sendGetRequest("getPresetInfo", PresetList.class);
    }

    /**
     * Corresponds to /getNewAudioOutputHardwareMode – Get audio output hardware mode.
     */
    public CompletableFuture<AudioOutputHardwareMode> getNewAudioOutputHardwareMode() {
        return sendGetRequest("getNewAudioOutputHardwareMode", AudioOutputHardwareMode.class);
    }

    /**
     * Corresponds to /setAudioOutputHardwareMode:{n} – Set audio output hardware mode.
     * 1: SPDIF, 2: AUX, 3: COAX
     */
    public CompletableFuture<String> setAudioOutputHardwareMode(int n) {
        if (n < 1 || n > 3) {
            throw new IllegalArgumentException("Hardware mode must be 1 (SPDIF), 2 (AUX) or 3 (COAX)");
        }
        return sendGetRequest("setAudioOutputHardwareMode:" + n, String.class);
    }

    /**
     * Corresponds to /getSpdifOutSwitchDelayMs – Get SPDIF sample-rate switch latency in ms.
     */
    public CompletableFuture<Integer> getSpdifOutSwitchDelayMs() {
        return sendGetRequest("getSpdifOutSwitchDelayMs", Integer.class);
    }

    /**
     * Corresponds to /setSpdifOutSwitchDelayMs:{Delay} – Set SPDIF switch latency.
     * Upper bound 3000 ms according to spec.
     */
    public CompletableFuture<String> setSpdifOutSwitchDelayMs(int delayMs) {
        if (delayMs < 0 || delayMs > 3000) {
            throw new IllegalArgumentException("Delay must be between 0 and 3000 milliseconds");
        }
        return sendGetRequest("setSpdifOutSwitchDelayMs:" + delayMs, String.class);
    }

    /**
     * Corresponds to /getChannelBalance – Get left/right channel balance.
     * Returns value as string ranging -1.0 to 1.0; converted to Double.
     */
    public CompletableFuture<Double> getChannelBalance() {
        return sendGetRequest("getChannelBalance", String.class).thenApply(Double::valueOf);
    }

    /**
     * Corresponds to /setChannelBalance:{n} – Set left/right channel balance (-1.0 left to 1.0 right).
     */
    public CompletableFuture<String> setChannelBalance(double balance) {
        if (balance < -1.0 || balance > 1.0) {
            throw new IllegalArgumentException("Balance must be between -1.0 and 1.0");
        }
        // format to avoid scientific notation
        String value = String.format(java.util.Locale.US, "%s", balance);
        return sendGetRequest("setChannelBalance:" + value, String.class);
    }

    // -------- Bluetooth Endpoints --------

    /**
     * Corresponds to /startbtdiscovery:{arg} – Start Bluetooth device scan.
     * The app uses 3, but other integers accepted.
     */
    public CompletableFuture<String> startBtDiscovery(int arg) {
        return sendGetRequest("startbtdiscovery:" + arg, String.class);
    }

    /**
     * Corresponds to /getbtdiscoveryresult – Fetch Bluetooth scan result.
     */
    public CompletableFuture<BluetoothDeviceList> getBtDiscoveryResult() {
        return sendGetRequest("getbtdiscoveryresult", BluetoothDeviceList.class);
    }

    /**
     * Corresponds to /clearbtdiscoveryresult – Clear BT scan results.
     */
    public CompletableFuture<String> clearBtDiscoveryResult() {
        return sendGetRequest("clearbtdiscoveryresult", String.class);
    }

    /**
     * Corresponds to /getbthistory – Retrieve paired BT devices.
     */
    public CompletableFuture<BluetoothDeviceList> getBtHistory() {
        return sendGetRequest("getbthistory", BluetoothDeviceList.class);
    }

    /**
     * Corresponds to /connectbta2dpsynk:{MAC} – Connect to BT device (A2DP sink).
     */
    public CompletableFuture<String> connectBtA2dpSynk(String macAddress) {
        return sendGetRequest("connectbta2dpsynk:" + encode(macAddress), String.class);
    }

    /**
     * Corresponds to /disconnectbta2dpsynk:{MAC} – Disconnect BT device.
     */
    public CompletableFuture<String> disconnectBtA2dpSynk(String macAddress) {
        return sendGetRequest("disconnectbta2dpsynk:" + encode(macAddress), String.class);
    }

    /**
     * Corresponds to /getbtpairstatus – Get BT pairing status.
     */
    public CompletableFuture<BTPairStatus> getBtPairStatus() {
        return sendGetRequest("getbtpairstatus", BTPairStatus.class);
    }

    // -------- Miscellaneous "Other" endpoints --------

    /**
     * Corresponds to /getMvRemoteSilenceUpdateTime – Gets remote silence update time.
     */
    public CompletableFuture<String> getMvRemoteSilenceUpdateTime() {
        return sendGetRequest("getMvRemoteSilenceUpdateTime", String.class);
    }

    /**
     * Corresponds to /getNetworkPreferDNS – Get preferred DNS setting.
     */
    public CompletableFuture<String> getNetworkPreferDNS() {
        return sendGetRequest("getNetworkPreferDNS", String.class);
    }

    /**
     * Corresponds to /getWlanBandConfig – Retrieve WLAN band configuration.
     */
    public CompletableFuture<String> getWlanBandConfig() {
        return sendGetRequest("getWlanBandConfig", String.class);
    }

    /**
     * Corresponds to /getWlanRoamConfig – Retrieve WLAN roaming configuration.
     */
    public CompletableFuture<String> getWlanRoamConfig() {
        return sendGetRequest("getWlanRoamConfig", String.class);
    }

    /**
     * Corresponds to /getIPV6Enable – Check if IPv6 is enabled.
     */
    public CompletableFuture<String> getIpv6Enable() {
        return sendGetRequest("getIPV6Enable", String.class);
    }

    /**
     * Corresponds to /getSpdifOutMaxCap – Retrieve SPDIF maximum capability.
     */
    public CompletableFuture<String> getSpdifOutMaxCap() {
        return sendGetRequest("getSpdifOutMaxCap", String.class);
    }

    /**
     * Corresponds to /getCoaxOutMaxCap – Retrieve COAX maximum capability.
     */
    public CompletableFuture<String> getCoaxOutMaxCap() {
        return sendGetRequest("getCoaxOutMaxCap", String.class);
    }

    /**
     * Corresponds to /GetFadeFeature – Retrieve fade feature information.
     */
    public CompletableFuture<String> getFadeFeature() {
        return sendGetRequest("GetFadeFeature", String.class);
    }

    /**
     * Corresponds to /getAuxVoltageSupportList – Get AUX voltage support list.
     */
    public CompletableFuture<String> getAuxVoltageSupportList() {
        return sendGetRequest("getAuxVoltageSupportList", String.class);
    }

    /**
     * Corresponds to /audio_cast:get_speaker_list – Deprecated, retrieve speaker list for audio cast.
     */
    public CompletableFuture<String> getAudioCastSpeakerList() {
        return sendGetRequest("audio_cast:get_speaker_list", String.class);
    }

    /**
     * Corresponds to /getSoundCardModeSupportList – Get sound card mode support list.
     */
    public CompletableFuture<String> getSoundCardModeSupportList() {
        return sendGetRequest("getSoundCardModeSupportList", String.class);
    }

    /**
     * Corresponds to /getActiveSoundCardOutputMode – Get active sound card output mode.
     */
    public CompletableFuture<String> getActiveSoundCardOutputMode() {
        return sendGetRequest("getActiveSoundCardOutputMode", String.class);
    }

    /**
     * Corresponds to /setLightOperationBrightConfig:{json} – WiiM Ultra LCD enable/disable config.
     * Wrapper around parameters s (auto_sense_enable), b (default_bright), d (disable).
     */
    public CompletableFuture<String> setLightOperationBrightConfig(int autoSenseEnable, int defaultBright,
            int disable) {
        String json = String.format("%%7B\"auto_sense_enable\":%d,\"default_bright\":%d,\"disable\":%d%%7D",
                autoSenseEnable, defaultBright, disable);
        return sendGetRequest("setLightOperationBrightConfig:" + json, String.class);
    }

    /**
     * Corresponds to /multiroom:getSlaveList – Fetch list of available LinkPlay slaves.
     */
    public CompletableFuture<SlaveListResponse> multiroomGetSlaveList() {
        return sendGetRequest("multiroom:getSlaveList", SlaveListResponse.class);
    }

    /**
     * Corresponds to /ConnectMasterAp:JoinGroupMaster:eth:{ip} –Join a group by IP.
     */
    public CompletableFuture<String> multiroomJoinGroupMaster(String ip) {
        return sendGetRequest("ConnectMasterAp:JoinGroupMaster:eth" + encode(ip) + ":wifi0.0.0.0", String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveKickout:{ip} – Remove device from multiroom by IP.
     */
    public CompletableFuture<String> multiroomSlaveKickout(String ip) {
        return sendGetRequest("multiroom:SlaveKickout:" + encode(ip), String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveMask:{ip} – Hide the IP address of a LinkPlay device (mask).
     */
    public CompletableFuture<String> multiroomSlaveMask(String ip) {
        return sendGetRequest("multiroom:SlaveMask:" + encode(ip), String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveUnMask:{ip} – Unmask a previously masked device.
     */
    public CompletableFuture<String> multiroomSlaveUnMask(String ip) {
        return sendGetRequest("multiroom:SlaveUnMask:" + encode(ip), String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveVolume:{ip}:{volume} – Adjust individual slave volume (1-100).
     */
    public CompletableFuture<String> multiroomSlaveVolume(String ip, int volume) {
        if (volume < 1 || volume > 100) {
            throw new IllegalArgumentException("Volume must be 1-100");
        }
        return sendGetRequest("multiroom:SlaveVolume:" + encode(ip) + ":" + volume, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:slave_vol:{volume} – Adjust overall multi-room volume.
     */
    public CompletableFuture<String> setPlayerCmdSlaveVol(int volume) {
        if (volume < 1 || volume > 100) {
            throw new IllegalArgumentException("Volume must be 1-100");
        }
        return sendGetRequest("setPlayerCmd:slave_vol:" + volume, String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveMute:{ip}:{mute} – Mute/unmute individual slave (1 mute, 0 unmute).
     */
    public CompletableFuture<String> multiroomSlaveMute(String ip, int mute) {
        if (mute != 0 && mute != 1) {
            throw new IllegalArgumentException("mute must be 0 or 1");
        }
        return sendGetRequest("multiroom:SlaveMute:" + encode(ip) + ":" + mute, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:slave_mute:mute – Mute all devices in multi-room.
     */
    public CompletableFuture<String> setPlayerCmdSlaveMute() {
        return sendGetRequest("setPlayerCmd:slave_mute:mute", String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:slave_mute:unmute – Unmute all devices in multi-room.
     */
    public CompletableFuture<String> setPlayerCmdSlaveUnmute() {
        return sendGetRequest("setPlayerCmd:slave_mute:unmute", String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveChannel:{ip}:{channel} – Set individual slave channel (0 left, 1 right).
     */
    public CompletableFuture<String> multiroomSlaveChannel(String ip, int channel) {
        if (channel != 0 && channel != 1) {
            throw new IllegalArgumentException("channel must be 0 (left) or 1 (right)");
        }
        return sendGetRequest("multiroom:SlaveChannel:" + encode(ip) + ":" + channel, String.class);
    }

    /**
     * Corresponds to /setPlayerCmd:slave_channel:{channel} – Set overall channel for all devices.
     */
    public CompletableFuture<String> setPlayerCmdSlaveChannel(int channel) {
        if (channel != 0 && channel != 1) {
            throw new IllegalArgumentException("channel must be 0 (left) or 1 (right)");
        }
        return sendGetRequest("setPlayerCmd:slave_channel:" + channel, String.class);
    }

    /**
     * Corresponds to /multiroom:SlaveSetDeviceName:{ip}:{name} – Set name of individual device in multi-room.
     */
    public CompletableFuture<String> multiroomSlaveSetDeviceName(String ip, String name) {
        return sendGetRequest("multiroom:SlaveSetDeviceName:" + encode(ip) + ":" + encode(name), String.class);
    }

    /**
     * Corresponds to /multiroom:Ungroup – Disable multi-room mode (ungroup).
     */
    public CompletableFuture<String> multiroomUngroup() {
        return sendGetRequest("multiroom:Ungroup", String.class);
    }

    /**
     * Corresponds to /getMvRemoteUpdateStartCheck – Check if firmware update is available.
     */
    public CompletableFuture<String> getMvRemoteUpdateStartCheck() {
        return sendGetRequest("getMvRemoteUpdateStartCheck", String.class);
    }

    /**
     * Corresponds to /getMvRemoteUpdateStart – Trigger firmware update download process.
     */
    public CompletableFuture<String> getMvRemoteUpdateStart() {
        return sendGetRequest("getMvRemoteUpdateStart", String.class);
    }

    /**
     * Corresponds to /getMvRemoteUpdateStatus – Retrieve status of firmware update download.
     */
    public CompletableFuture<String> getMvRemoteUpdateStatus() {
        return sendGetRequest("getMvRemoteUpdateStatus", String.class);
    }

    /**
     * Corresponds to /getMvRomBurnPrecent – Retrieve firmware flashing progress (0-100%).
     */
    public CompletableFuture<String> getMvRomBurnPercent() {
        return sendGetRequest("getMvRomBurnPrecent", String.class);
    }

    /**
     * Corresponds to /setSSID:{value} – Change device SSID (hex string value).
     */
    public CompletableFuture<String> setSSID(String hexValue) {
        return sendGetRequest("setSSID:" + encode(hexValue), String.class);
    }

    /**
     * Corresponds to /setNetwork:{n}:{password} – Configure WiFi password and security.
     * n = 1 secure (WPA/WPA2), 0 open
     */
    public CompletableFuture<String> setNetwork(int secureFlag, String password) {
        if (secureFlag != 0 && secureFlag != 1) {
            throw new IllegalArgumentException("secureFlag must be 0 (open) or 1 (WPA)");
        }
        return sendGetRequest("setNetwork:" + secureFlag + ":" + encode(password), String.class);
    }

    /**
     * Corresponds to /restoreToDefault – Restore factory settings.
     */
    public CompletableFuture<String> restoreToDefault() {
        return sendGetRequest("restoreToDefault", String.class);
    }

    /**
     * Corresponds to /setPowerWifiDown – Turn off WiFi signal.
     */
    public CompletableFuture<String> setPowerWifiDown() {
        return sendGetRequest("setPowerWifiDown", String.class);
    }

    /**
     * Corresponds to /setDeviceName:{name} – Set UPnP/AirPlay device name (hex encoded).
     */
    public CompletableFuture<String> setDeviceName(String nameHex) {
        return sendGetRequest("setDeviceName:" + encode(nameHex), String.class);
    }
}
