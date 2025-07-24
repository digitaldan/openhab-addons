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
package org.openhab.binding.linkplay.internal.client.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Model representing the payload returned by the /getStatusEx endpoint.
 * <p>
 * The class mirrors (1-to-1) the <code>DeviceStatus</code> object defined in
 * openapi.json so that callers can access well-typed fields directly instead
 * of parsing the raw map.
 *
 * <p>
 * NOTE: Most properties are delivered as strings even when they represent
 * numeric values. For simplicity we therefore model every attribute as
 * <code>String</code> except for the very few that are explicitly <code>integer</code>
 * in the specification.
 * 
 * @author Dan Cunningham - Initial contribution
 */
public class DeviceStatus {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    @SerializedName("language")
    public String language;
    @SerializedName("ssid")
    public String ssid;
    @SerializedName("hideSSID")
    public String hideSsid;
    @SerializedName("SSIDStrategy")
    public String ssidStrategy;
    @SerializedName("branch")
    public String branch;
    @SerializedName("firmware")
    public String firmware;
    @SerializedName("build")
    public String build;
    @SerializedName("project")
    public String project;
    @SerializedName("priv_prj")
    public String privPrj;
    @SerializedName("project_build_name")
    public String projectBuildName;
    @SerializedName("Release")
    public String release;
    @SerializedName("FW_Release_version")
    public String fwReleaseVersion;
    @SerializedName("PCB_version")
    public String pcbVersion;
    @SerializedName("cast_enable")
    public Integer castEnable; // integer in spec
    @SerializedName("cast_usage_report")
    public Integer castUsageReport; // integer in spec
    @SerializedName("group")
    public String group;
    @SerializedName("master_uuid")
    public String masterUuid;
    @SerializedName("slave_mask")
    public String slaveMask;
    @SerializedName("wmrm_version")
    public String wmrmVersion;
    @SerializedName("wmrm_sub_ver")
    public String wmrmSubVer;
    @SerializedName("expired")
    public String expired;
    @SerializedName("internet")
    public String internet;
    @SerializedName("uuid")
    public String uuid;
    @SerializedName("MAC")
    public String mac;
    @SerializedName("STA_MAC")
    public String staMac;
    @SerializedName("BTMAC")
    public String btMac;
    @SerializedName("AP_MAC")
    public String apMac;
    @SerializedName("ETH_MAC")
    public String ethMac;
    @SerializedName("InitialConfiguration")
    public String initialConfiguration;
    @SerializedName("temperature_power_control")
    public String temperaturePowerControl;
    @SerializedName("temperature_cpu")
    public String temperatureCpu;
    @SerializedName("temperature_tmp102")
    public String temperatureTmp102;
    @SerializedName("CountryCode")
    public String countryCode;
    @SerializedName("CountryRegion")
    public String countryRegion;
    @SerializedName("date")
    public String date;
    @SerializedName("time")
    public String time;
    @SerializedName("tz")
    public String tz;
    @SerializedName("dst_enable")
    public String dstEnable;
    @SerializedName("netstat")
    public String netstat;
    @SerializedName("essid")
    public String essid;
    @SerializedName("apcli0")
    public String apcli0;
    @SerializedName("eth0")
    public String eth0;
    @SerializedName("eth2")
    public String eth2;
    @SerializedName("eth_dhcp")
    public String ethDhcp;
    @SerializedName("eth_static_ip")
    public String ethStaticIp;
    @SerializedName("eth_static_mask")
    public String ethStaticMask;
    @SerializedName("eth_static_gateway")
    public String ethStaticGateway;
    @SerializedName("eth_static_dns1")
    public String ethStaticDns1;
    @SerializedName("eth_static_dns2")
    public String ethStaticDns2;
    @SerializedName("hardware")
    public String hardware;
    @SerializedName("VersionUpdate")
    public String versionUpdate;
    @SerializedName("NewVer")
    public String newVer;
    @SerializedName("mcu_ver")
    public String mcuVer;
    @SerializedName("mcu_ver_new")
    public String mcuVerNew;
    @SerializedName("hdmi_ver")
    public String hdmiVer;
    @SerializedName("hdmi_ver_new")
    public String hdmiVerNew;
    @SerializedName("update_check_count")
    public String updateCheckCount;
    @SerializedName("BleRemote_update_checked_counter")
    public String bleRemoteUpdateCheckedCounter;
    @SerializedName("ra0")
    public String ra0;
    @SerializedName("temp_uuid")
    public String tempUuid;
    @SerializedName("cap1")
    public String cap1;
    @SerializedName("capability")
    public String capability;
    @SerializedName("languages")
    public String languages;
    @SerializedName("prompt_status")
    public String promptStatus;
    @SerializedName("iot_ver")
    public String iotVer;
    @SerializedName("alexa_ver")
    public String alexaVer;
    @SerializedName("alexa_beta_enable")
    public String alexaBetaEnable;
    @SerializedName("alexa_force_beta_cfg")
    public String alexaForceBetaCfg;
    @SerializedName("dsp_ver")
    public String dspVer;
    @SerializedName("dsp_ver_new")
    public String dspVerNew;
    @SerializedName("ModuleColorNumber")
    public String moduleColorNumber;
    @SerializedName("ModuleColorString")
    public String moduleColorString;
    @SerializedName("uboot_verinfo")
    public String ubootVerinfo;
    @SerializedName("streams_all")
    public String streamsAll;
    @SerializedName("streams")
    public String streams;
    @SerializedName("region")
    public String region;
    @SerializedName("volume_control")
    public String volumeControl;
    @SerializedName("external")
    public String external;
    @SerializedName("preset_key")
    public String presetKey;
    @SerializedName("spotify_active")
    public String spotifyActive;
    @SerializedName("plm_support")
    public String plmSupport;
    @SerializedName("mqtt_support")
    public String mqttSupport;
    @SerializedName("lbc_support")
    public String lbcSupport;
    @SerializedName("WifiChannel")
    public String wifiChannel;
    @SerializedName("RSSI")
    public String rssi;
    @SerializedName("BSSID")
    public String bssid;
    @SerializedName("wlanSnr")
    public String wlanSnr;
    @SerializedName("wlanNoise")
    public String wlanNoise;
    @SerializedName("wlanFreq")
    public String wlanFreq;
    @SerializedName("wlanDataRate")
    public String wlanDataRate;
    @SerializedName("battery")
    public String battery;
    @SerializedName("battery_percent")
    public String batteryPercent;
    @SerializedName("securemode")
    public String secureMode;
    @SerializedName("auth")
    public String auth;
    @SerializedName("encry")
    public String encry;
    @SerializedName("ota_interface_ver")
    public String otaInterfaceVer;
    @SerializedName("ota_api_ver")
    public String otaApiVer;
    @SerializedName("upnp_version")
    public String upnpVersion;
    @SerializedName("upnp_uuid")
    public String upnpUuid;
    @SerializedName("uart_pass_port")
    public String uartPassPort;
    @SerializedName("communication_port")
    public String communicationPort;
    @SerializedName("web_firmware_update_hide")
    public String webFirmwareUpdateHide;
    @SerializedName("ignore_talkstart")
    public String ignoreTalkstart;
    @SerializedName("silence_ota_flag")
    public String silenceOtaFlag;
    @SerializedName("silenceOTATime")
    public String silenceOtaTime;
    @SerializedName("ignore_silenceOTATime")
    public String ignoreSilenceOtaTime;
    @SerializedName("new_tunein_preset_and_alarm")
    public String newTuneinPresetAndAlarm;
    @SerializedName("iheartradio_new")
    public String iHeartRadioNew;
    @SerializedName("new_iheart_podcast")
    public String newIheartPodcast;
    @SerializedName("tidal_version")
    public String tidalVersion;
    @SerializedName("service_version")
    public String serviceVersion;
    @SerializedName("EQ_support")
    public String eqSupport;
    @SerializedName("EQVersion")
    public String eqVersion;
    @SerializedName("HiFiSRC_version")
    public String hiFiSrcVersion;
    @SerializedName("audio_channel_config")
    public String audioChannelConfig;
    @SerializedName("app_timezone_id")
    public String appTimezoneId;
    @SerializedName("avs_timezone_id")
    public String avsTimezoneId;
    @SerializedName("tz_info_ver")
    public String tzInfoVer;
    @SerializedName("max_volume")
    public String maxVolume;
    @SerializedName("power_mode")
    public String powerMode;
    @SerializedName("security")
    public String security;
    @SerializedName("security_version")
    public String securityVersion;
    @SerializedName("security_capabilities")
    public SecurityCapabilities securityCapabilities;
    @SerializedName("public_https_version")
    public String publicHttpsVersion;
    @SerializedName("BleRemoteControl")
    public String bleRemoteControl;
    @SerializedName("BleRemoteConnected")
    public String bleRemoteConnected;
    @SerializedName("BleRemoteException")
    public String bleRemoteException;
    @SerializedName("udisk")
    public String udisk;
    @SerializedName("umount")
    public String umount;
    @SerializedName("autoSenseVersion")
    public String autoSenseVersion;
    @SerializedName("set_play_mode_enable")
    public String setPlayModeEnable;
    @SerializedName("set_play_mode_gain")
    public String setPlayModeGain;
    @SerializedName("audioOutputModeVer")
    public String audioOutputModeVer;
    @SerializedName("privacy_mode")
    public String privacyMode;
    @SerializedName("DeviceName")
    public String deviceName;
    @SerializedName("GroupName")
    public String groupName;

    public static class SecurityCapabilities {
        @SerializedName("ver")
        public String ver;
        @SerializedName("aes_ver")
        public String aesVer;

        @Override
        public String toString() {
            return "SecurityCapabilities" + GSON.toJson(this);
        }
    }

    @Override
    public String toString() {
        return "DeviceStatus" + GSON.toJson(this);
    }
}
