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
package org.openhab.binding.linkplay.internal;

import static org.openhab.binding.linkplay.internal.LinkPlayBindingConstants.GROUP_PROXY_CHANNELS;
import static org.openhab.binding.linkplay.internal.LinkPlayBindingConstants.PROPERTY_DEVICE_NAME;
import static org.openhab.binding.linkplay.internal.LinkPlayBindingConstants.PROPERTY_GROUP_NAME;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.jupnp.UpnpService;
import org.jupnp.controlpoint.ControlPoint;
import org.jupnp.model.message.header.UDNHeader;
import org.jupnp.model.meta.RemoteDevice;
import org.jupnp.model.types.UDN;
import org.openhab.binding.linkplay.internal.client.LinkPlayHTTPClient;
import org.openhab.binding.linkplay.internal.client.dto.DeviceStatus;
import org.openhab.binding.linkplay.internal.client.dto.PlayMode;
import org.openhab.binding.linkplay.internal.client.dto.PlayerStatus;
import org.openhab.binding.linkplay.internal.client.dto.PresetList;
import org.openhab.binding.linkplay.internal.client.dto.Slave;
import org.openhab.binding.linkplay.internal.client.dto.SourceInputMode;
import org.openhab.binding.linkplay.internal.client.dto.TrackMetadata;
import org.openhab.binding.linkplay.internal.client.dto.TransportState;
import org.openhab.binding.linkplay.internal.utils.UpnpEntry;
import org.openhab.binding.linkplay.internal.utils.UpnpXMLParser;
import org.openhab.core.io.net.http.HttpUtil;
import org.openhab.core.io.transport.upnp.UpnpIOParticipant;
import org.openhab.core.io.transport.upnp.UpnpIOService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.NextPreviousType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.PlayPauseType;
import org.openhab.core.library.types.RewindFastforwardType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandDescriptionBuilder;
import org.openhab.core.types.CommandOption;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link LinkPlayHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class LinkPlayHandler extends BaseThingHandler implements UpnpIOParticipant, LinkPlayGroupParticipant {

    private final Logger logger = LoggerFactory.getLogger(LinkPlayHandler.class);

    private static final String SERVICE_AV_TRANSPORT = "AVTransport";
    private static final String SERVICE_RENDERING_CONTROL = "RenderingControl";
    protected static final String CONNECTION_MANAGER = "ConnectionManager";

    private static final Collection<String> SERVICE_SUBSCRIPTIONS = Arrays.asList(SERVICE_AV_TRANSPORT,
            SERVICE_RENDERING_CONTROL);

    private static final int SUBSCRIPTION_DURATION = 1800; // this is the maxAgeSeconds for the device
    private static final int COMMAND_TIMEOUT = 30; // seconds
    private @Nullable LinkPlayConfiguration config;
    private final HttpClient httpClient;
    private @Nullable ScheduledFuture<?> pollJobFast;
    private @Nullable ScheduledFuture<?> pollJobSlow;

    private @Nullable ScheduledFuture<?> keepAliveJob;
    private LinkPlayHTTPClient apiClient;
    private final UpnpIOService upnpIOService;
    private final UpnpService upnpService;
    private final LinkPlayGroupService linkPlayGroupService;
    private final LinkPlayCommandDescriptionProvider linkPlayCommandDescriptionProvider;
    private @Nullable String previousAlbumArtUri;
    private volatile @Nullable RemoteDevice device;
    private @Nullable String deviceUUID;
    private final Object upnpLock = new Object();
    private Map<String, Boolean> subscriptionState = new HashMap<>();
    private boolean inGroup = false;
    private boolean isLeader = false;
    private int currentPosition = 0;
    private int currentDuration = 0;
    private Collection<LinkPlayGroupParticipant> allParticipants = new ArrayList<>();
    private Map<String, State> groupStateCache = new HashMap<>();
    private String groupName;
    private String deviceName;
    // Guard to ensure poll executions do not overlap
    private final AtomicBoolean isPolling = new AtomicBoolean(false);

    public LinkPlayHandler(Thing thing, HttpClient httpClient, UpnpIOService upnpIOService, UpnpService upnpService,
            LinkPlayGroupService linkPlayGroupService,
            LinkPlayCommandDescriptionProvider linkPlayCommandDescriptionProvider) {
        super(thing);
        this.upnpIOService = upnpIOService;
        this.upnpService = upnpService;
        this.linkPlayGroupService = linkPlayGroupService;
        this.linkPlayCommandDescriptionProvider = linkPlayCommandDescriptionProvider;
        this.httpClient = httpClient;
        // Get this in constructor, so the UDN and IP is immediately available from the config. The concrete classes
        // should update the config from the initialize method.
        config = getConfigAs(LinkPlayConfiguration.class);
        apiClient = new LinkPlayHTTPClient(httpClient, config.ipAddress);
        groupName = getThing().getProperties().getOrDefault(PROPERTY_GROUP_NAME,
                Objects.requireNonNullElse(getThing().getLabel(), getThing().getUID().getAsString()));
        deviceName = getThing().getProperties().getOrDefault(PROPERTY_DEVICE_NAME,
                Objects.requireNonNullElse(getThing().getLabel(), getThing().getUID().getAsString()));
    }

    @Override
    public void initialize() {
        config = getConfigAs(LinkPlayConfiguration.class);
        logger.debug("{}: initialize: {}", deviceName, config);
        // try {
        // httpClient.start();
        // } catch (Exception e) {
        // throw new IllegalStateException("Could not create HTTP client", e);
        // }
        apiClient.setHostname(config.ipAddress);
        upnpIOService.registerParticipant(this);
        linkPlayGroupService.registerParticipant(this);
        pollJobFast = scheduler.scheduleWithFixedDelay(this::pollFast, 0, Math.max(5, config.refreshInterval),
                TimeUnit.SECONDS);
        pollJobSlow = scheduler.scheduleWithFixedDelay(this::pollSlow, 5, Math.max(10, config.refreshInterval * 5),
                TimeUnit.SECONDS);
        updateStatus(ThingStatus.UNKNOWN);
    }

    @Override
    public void dispose() {
        linkPlayGroupService.unregisterParticipant(this);
        cancelPollJobs();
        cancelKeepAliveJob();
        removeSubscription();
        upnpIOService.removeStatusListener(this);
        upnpIOService.unregisterParticipant(this);
        // try {
        // httpClient.stop();
        // } catch (Exception e) {
        // logger.debug("Failed to stop HTTP client: {}", e.getMessage(), e);
        // }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("{}: handleCommand: {} {}", deviceName, channelUID, command);
        if (command instanceof RefreshType && !isPolling.get()) {
            // execute immediately in a new thread so we don't block
            scheduler.schedule(this::pollFast, 0, TimeUnit.MILLISECONDS);
            return;
        }
        try {
            switch (channelUID.getIdWithoutGroup()) {
                case LinkPlayBindingConstants.CHANNEL_PLAYER_CONTROL:
                    if (command instanceof PlayPauseType playPauseType) {
                        if (playPauseType == PlayPauseType.PLAY) {
                            apiClient.setPlayerCmdResume().get();
                        } else {
                            apiClient.setPlayerCmdPause().get();
                        }
                    }
                    if (command instanceof RewindFastforwardType rewindFastforwardType) {
                        switch (rewindFastforwardType) {
                            case REWIND:
                                apiClient.setPlayerCmdSeekPosition(Math.max(0, currentPosition - 10)).get();
                                break;
                            case FASTFORWARD:
                                apiClient.setPlayerCmdSeekPosition(Math.min(currentDuration, currentPosition + 10))
                                        .get();
                        }
                    }
                    if (command instanceof NextPreviousType nextPreviousType) {
                        switch (nextPreviousType) {
                            case NEXT:
                                apiClient.setPlayerCmdNext().get();
                                break;
                            case PREVIOUS:
                                apiClient.setPlayerCmdPrev().get();
                                break;
                        }
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_VOLUME:
                    if (command instanceof PercentType percentType) {
                        if (channelUID.getGroupId() instanceof String group) {
                            if (group.equals(LinkPlayBindingConstants.GROUP_MULTIROOM)) {
                                apiClient.setPlayerCmdSlaveVol(percentType.intValue()).get(COMMAND_TIMEOUT,
                                        TimeUnit.SECONDS);
                            } else {
                                apiClient.setPlayerCmdVol(percentType.intValue()).get(COMMAND_TIMEOUT,
                                        TimeUnit.SECONDS);
                            }
                        }
                        apiClient.setPlayerCmdVol(percentType.intValue()).get();
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_MUTE:
                    if (command instanceof OnOffType onOffType) {
                        if (channelUID.getGroupId() instanceof String group) {
                            if (group.equals(LinkPlayBindingConstants.GROUP_MULTIROOM)) {
                                if (onOffType == OnOffType.ON) {
                                    apiClient.setPlayerCmdSlaveMute().get();
                                } else {
                                    apiClient.setPlayerCmdSlaveUnmute().get();
                                }
                            } else {
                                apiClient.setPlayerCmdMute(onOffType == OnOffType.ON ? 1 : 0).get(COMMAND_TIMEOUT,
                                        TimeUnit.SECONDS);
                            }
                        }
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_PRESET_PLAY:
                    if (channelUID.getGroupId() instanceof String group) {
                        if (group.equals(LinkPlayBindingConstants.GROUP_PRESETS)) {
                            if (command instanceof DecimalType decimalType) {
                                if (decimalType.intValue() > 0) {
                                    apiClient.mcuKeyShortClick(decimalType.intValue()).get(COMMAND_TIMEOUT,
                                            TimeUnit.SECONDS);
                                }
                            }
                        } else {
                            if (command instanceof OnOffType onOffType) {
                                if (onOffType == OnOffType.ON) {
                                    try {
                                        int presetNum = Integer.parseInt(
                                                group.substring(LinkPlayBindingConstants.GROUP_PRESET.length()));
                                        apiClient.mcuKeyShortClick(presetNum).get();
                                    } catch (NumberFormatException e) {
                                        logger.debug("Invalid preset number: {}", group);
                                    }
                                }
                                updateState(channelUID, OnOffType.OFF);
                            }
                        }
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_MULTIROOM_JOIN:
                    if (command instanceof StringType stringType) {
                        if (stringType.toString().equals("LEAVE")) {
                            linkPlayGroupService.unGroup(this);
                        } else {
                            linkPlayGroupService.joinGroup(this, stringType.toString());
                        }
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_MULTIROOM_LEAVE:
                    linkPlayGroupService.unGroup(this);
                    break;
                case LinkPlayBindingConstants.CHANNEL_MULTIROOM_MANAGE:
                    if (command instanceof StringType stringType) {
                        switch (stringType.toString()) {
                            case "LEAVE":
                                linkPlayGroupService.unGroup(this);
                                break;
                            case "ADD_ALL":
                                linkPlayGroupService.addAllMembers(this);
                                break;
                            default:
                                linkPlayGroupService.addRemoveMember(this, stringType.toString());
                                break;
                        }
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_MULTIROOM_UNGROUP:
                    linkPlayGroupService.unGroup(this);
                    break;

                // ---------------- Additional commandable channels ----------------

                case LinkPlayBindingConstants.CHANNEL_REPEAT_SHUFFLE_MODE:
                    if (command instanceof DecimalType decimalType) {
                        apiClient.setPlayerCmdLoopmode(decimalType.intValue()).get();
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_EQ_PRESET:
                    if (command instanceof StringType stringType) {
                        apiClient.loadEQByName(stringType.toString()).get();
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_EQ_ENABLED:
                    if (command instanceof OnOffType onOffType) {
                        if (onOffType == OnOffType.ON) {
                            apiClient.setEQOn().get();
                        } else {
                            apiClient.setEQOff().get();
                        }
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_SOURCE_INPUT:
                    if (command instanceof StringType stringType) {
                        SourceInputMode mode = Arrays.stream(SourceInputMode.values())
                                .filter(m -> m.toString().equalsIgnoreCase(stringType.toString())).findFirst()
                                .orElse(null);
                        if (mode != null) {
                            apiClient.setPlayerCmdSwitchMode(mode).get();
                        } else {
                            logger.debug("Unsupported source input mode: {}", stringType);
                        }
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_CHANNEL_BALANCE:
                    if (command instanceof DecimalType decimalType) {
                        apiClient.setChannelBalance(decimalType.doubleValue()).get();
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_SPDIF_DELAY:
                    if (command instanceof DecimalType decimalType) {
                        apiClient.setSpdifOutSwitchDelayMs(decimalType.intValue()).get(COMMAND_TIMEOUT,
                                TimeUnit.SECONDS);
                    }
                    break;
                case LinkPlayBindingConstants.CHANNEL_LED_ENABLED:
                    if (command instanceof OnOffType onOffType) {
                        apiClient.setLedSwitch(onOffType == OnOffType.ON ? 1 : 0).get(COMMAND_TIMEOUT,
                                TimeUnit.SECONDS);
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_TOUCH_KEYS_ENABLED:
                    if (command instanceof OnOffType onOffType) {
                        apiClient.setTouchControls(onOffType == OnOffType.ON ? 1 : 0).get(COMMAND_TIMEOUT,
                                TimeUnit.SECONDS);
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_SHUTDOWN_TIMER:
                    if (command instanceof DecimalType decimalType) {
                        apiClient.setShutdownTimer(decimalType.intValue()).get();
                    }
                    break;

                case LinkPlayBindingConstants.CHANNEL_REBOOT:
                    if (command instanceof OnOffType onOffType && onOffType == OnOffType.ON) {
                        apiClient.rebootDevice().get();
                        updateState(channelUID, OnOffType.OFF); // reset switch
                    }
                    break;

                default:
                    logger.debug("{}: No handler implemented for channel {}", deviceName, channelUID);
                    break;
            }
        } catch (Exception e) {
            logger.debug("Error while handling command: {}", e.getMessage(), e);
        }
    }

    // UPnP IO Participant methods

    @Override
    public void onValueReceived(@Nullable String variable, @Nullable String value, @Nullable String service) {
        logger.debug("{}: onValueReceived: {} {} {}", deviceName, variable, value, service);
        if (value == null) {
            return;
        }
        switch (service) {
            case SERVICE_AV_TRANSPORT:
                Map<String, String> avt = UpnpXMLParser.getAVTransportFromXML(value);
                handleAvTransportEvent(avt);
                break;
            case SERVICE_RENDERING_CONTROL:
                Map<String, @Nullable String> rc = UpnpXMLParser.getRenderingControlFromXML(value);
                handleRenderingControlEvent(rc);
                break;
        }
    }

    @Override
    public void onServiceSubscribed(@Nullable String service, boolean succeeded) {
        logger.debug("{}: onServiceSubscribed: {} {}", deviceName, service, succeeded);
    }

    @Override
    public void onStatusChanged(boolean status) {
        logger.debug("{}: onStatusChanged: {}", deviceName, status);
    }

    @Override
    public String getUDN() {
        return config.udn;
    }

    // LinkPlayGroupService methods
    @Override
    public String getIpAddress() {
        return config.ipAddress;
    }

    @Override
    public void addedToOrUpdatedGroup(LinkPlayGroupParticipant leader, List<Slave> slaves) {
        logger.debug("{}: multiroomAddedToGroup: {}", deviceName, leader.getIpAddress());
        inGroup = true;
        boolean oldLeader = isLeader;
        isLeader = leader == this;
        if (!oldLeader && isLeader) {
            // we are now the leader
            groupStateCache.entrySet().forEach(entry -> {
                logger.debug("{}: Leader, updating state {} {}", deviceName, entry.getKey(), entry.getValue());
                linkPlayGroupService.updateGroupState(this, entry.getKey(), entry.getValue());
            });
        }
        updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_LEADER,
                isLeader ? OnOffType.ON : OnOffType.OFF);
        updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_ACTIVE,
                OnOffType.ON);
        updateJoinGroupCommandDescription();
        updateAddRemoveMemberCommandDescription(slaves);
    }

    @Override
    public void removedFromGroup(LinkPlayGroupParticipant leader) {
        logger.debug("{}: multiroomRemovedFromGroup: {}", deviceName, leader.getIpAddress());
        inGroup = false;
        isLeader = false;
        updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_LEADER,
                OnOffType.OFF);
        updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_ACTIVE,
                OnOffType.OFF);
        pollFast();
        updateJoinGroupCommandDescription();
        updateAddRemoveMemberCommandDescription();
    }

    @Override
    public void groupParticipantsUpdated(Collection<LinkPlayGroupParticipant> participants) {
        allParticipants = participants;
        updateJoinGroupCommandDescription();
        updateAddRemoveMemberCommandDescription();
    }

    @Override
    public void groupProxyUpdateState(String channelId, State state) {
        logger.debug("{}: groupProxyUpdateState: {} {} {}", deviceName, channelId, state, getGroupParticipantLabel());
        super.updateState(channelId, state);
    }

    @Override
    public LinkPlayHTTPClient getApiClient() {
        return apiClient;
    }

    @Override
    public String getGroupParticipantLabel() {
        return groupName;
    }

    /**
     * Update the device configuration when a new device is discovered by the Handler Factory.
     * 
     * @param device The remote device
     */
    public synchronized void updateDeviceConfig(RemoteDevice device) {
        logger.debug("{}: updateDeviceConfig: {}", deviceName, device.getIdentity().getUdn().getIdentifierString());
        this.device = device;
        int maxAgeSeconds = device.getIdentity().getMaxAgeSeconds();
        logger.debug("{}: maxAgeSeconds: {} {}", deviceName, device.getIdentity().getUdn().getIdentifierString(),
                maxAgeSeconds);
        cancelKeepAliveJob();
        if (maxAgeSeconds > 0) {
            keepAliveJob = scheduler.scheduleWithFixedDelay(this::sendDeviceSearchRequest, maxAgeSeconds, maxAgeSeconds,
                    TimeUnit.SECONDS);
        }
    }

    private void cancelPollJobs() {
        for (ScheduledFuture<?> job : Arrays.asList(pollJobFast, pollJobSlow)) {
            if (job != null) {
                job.cancel(true);
            }
        }
    }

    private void cancelKeepAliveJob() {
        ScheduledFuture<?> job = keepAliveJob;

        if (job != null) {
            job.cancel(true);
        }
        keepAliveJob = null;
    }

    private void pollFast() {
        // Ensure that only one poll execution runs at a time. If the previous call
        // is still in progress, this invocation is skipped.
        if (!isPolling.compareAndSet(false, true)) {
            logger.debug("{}: Poll already in progress, skipping this execution", deviceName);
            return;
        }
        try {
            LinkPlayHTTPClient apiClient = this.apiClient;
            if (apiClient == null || httpClient.isStopped()) {
                return;
            }
            sendDeviceSearchRequest();
            if (!isUpnpDeviceRegistered()) {
                logger.debug("{}: UPnP device {} not yet registered", deviceName, getUDN());
                synchronized (upnpLock) {
                    subscriptionState = new HashMap<>();
                }
                // sendDeviceSearchRequest();
            } else {
                addSubscription();
            }

            try {
                updatePlayerStatus();
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.debug("Fatal Error while parsing player status: {}", e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE);
            }

            if (isLeader || !inGroup) {
                try {
                    updateTrackMetadata();
                } catch (InterruptedException | ExecutionException | TimeoutException e) {
                    // if no track metadata, we're not playing anything
                    logger.trace("Error while parsing metadata: {}", e.getMessage(), e);
                }
            }

            updateMultiroom();
        } finally {
            isPolling.set(false);
        }
    }

    private void pollSlow() {
        LinkPlayHTTPClient apiClient = this.apiClient;
        if (apiClient == null || httpClient.isStopped()) {
            return;
        }
        try {
            updatePresetInfo();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.trace("Error while retrieving preset info: {}", e.getMessage(), e);
        }
        try {
            updateDeviceStatus();
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.trace("Error while retrieving device status: {}", e.getMessage(), e);
        }
    }

    public void updatePlayerStatus() throws InterruptedException, ExecutionException, TimeoutException {
        PlayerStatus playerStatus = apiClient.getPlayerStatus().get();
        logger.debug("{}: Player status: {}", deviceName, playerStatus);

        updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_PLAYBACK_STATE,
                playerStatus.status != null ? new StringType(playerStatus.status.name()) : UnDefType.NULL);

        updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_PLAYER_CONTROL,
                playerStatus.status != null
                        ? (playerStatus.status == PlayerStatus.PlaybackStatus.PLAYING
                                || playerStatus.status == PlayerStatus.PlaybackStatus.BUFFERING ? PlayPauseType.PLAY
                                        : PlayPauseType.PAUSE)
                        : UnDefType.NULL);

        updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_VOLUME,
                stateOrNull(playerStatus.volume, PercentType.class));

        if (playerStatus.mute != null) {
            OnOffType muteState = "1".equals(playerStatus.mute) ? OnOffType.ON : OnOffType.OFF;
            updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_MUTE, muteState);
        }

        State position = stateOrNull(playerStatus.currentPosition, DecimalType.class);
        updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_TRACK_POSITION,
                stateOrNull(playerStatus.currentPosition, DecimalType.class));
        if (position instanceof DecimalType decimalType) {
            currentPosition = decimalType.intValue();
        }

        State duration = stateOrNull(playerStatus.totalLength, DecimalType.class);
        updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_TRACK_DURATION, duration);
        if (duration instanceof DecimalType decimalType) {
            currentDuration = decimalType.intValue();
        }

        updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_REPEAT_SHUFFLE_MODE,
                stateOrNull(playerStatus.loop, StringType.class));

        updateState(LinkPlayBindingConstants.GROUP_EQUALISER, LinkPlayBindingConstants.CHANNEL_EQ_PRESET,
                stateOrNull(playerStatus.eq, StringType.class));

        updateState(LinkPlayBindingConstants.GROUP_INPUT, LinkPlayBindingConstants.CHANNEL_SOURCE_INPUT,
                stateOrNull(playerStatus.mode, StringType.class));

        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    public void updateTrackMetadata() throws InterruptedException, ExecutionException, TimeoutException {
        TrackMetadata trackMetadata = apiClient.getMetaInfo().get();
        logger.debug("{}: Track metadata: {}", deviceName, trackMetadata);
        if (trackMetadata.metaData != null) {
            var md = trackMetadata.metaData;

            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_TRACK_TITLE,
                    stateOrNull(md.title, StringType.class));

            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_TRACK_ARTIST,
                    stateOrNull(md.artist, StringType.class));

            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_TRACK_ALBUM,
                    stateOrNull(md.album, StringType.class));

            updateAlbumArtChannels(md.albumArtURI);

            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_SAMPLE_RATE,
                    stateOrNull(md.sampleRate, DecimalType.class));

            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_BIT_DEPTH,
                    stateOrNull(md.bitDepth, DecimalType.class));

            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_BIT_RATE,
                    stateOrNull(md.bitRate, DecimalType.class));

        }
    }

    public void updatePresetInfo() throws InterruptedException, ExecutionException, TimeoutException {
        PresetList presetInfo = apiClient.getPresetInfo().get();
        if (presetInfo != null && presetInfo.presetList != null) {
            updateState(LinkPlayBindingConstants.GROUP_PRESETS, LinkPlayBindingConstants.CHANNEL_PRESET_COUNT,
                    stateOrNull(presetInfo.presetNum, DecimalType.class));
            List<CommandOption> commandOptions = new ArrayList<>();
            for (PresetList.Preset p : presetInfo.presetList) {
                String groupId = "preset" + p.number; // preset1..preset12
                updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_NAME,
                        stateOrNull(p.name, StringType.class));
                updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_URL, stateOrNull(p.url, StringType.class));
                updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_SOURCE,
                        stateOrNull(p.source, StringType.class));
                updatePresetPicChannels(groupId, p.picUrl);

                commandOptions.add(new CommandOption(String.valueOf(p.number), p.name));
            }
            updateCommandDescription(new ChannelUID(getThing().getUID(), LinkPlayBindingConstants.GROUP_PRESETS,
                    LinkPlayBindingConstants.CHANNEL_PLAY_PRESET), commandOptions);
        }
    }

    private void updateDeviceStatus() throws InterruptedException, ExecutionException, TimeoutException {
        DeviceStatus deviceStatus = apiClient.getStatusEx().get();
        if (deviceStatus.groupName != null && !deviceStatus.groupName.equals(groupName)) {
            getThing().setProperty(PROPERTY_GROUP_NAME, deviceStatus.groupName);
            groupName = deviceStatus.groupName;
            updateJoinGroupCommandDescription();
            updateAddRemoveMemberCommandDescription();
        }
        logger.debug("Device status: {}", deviceStatus);
    }

    private void updateMultiroom() {
        linkPlayGroupService.refreshMemberSlaveList(this);
    }

    // private void updateMultiroom() {
    // try {
    // SlaveListResponse slaveList = apiClient.multiroomGetSlaveList().get(5, TimeUnit.SECONDS);
    // logger.debug("{}: Multiroom slave list: {}", deviceName, slaveList);
    // if (slaveList.isMaster()) {
    // if (slaveList.slaveList != null && !slaveList.slaveList.isEmpty()) {
    // linkPlayGroupService.updateGroupParticipants(this, slaveList.slaveList);
    // }
    // // if we were not a leader before push current state
    // if (!isLeader) {
    // // push the cached states to the group
    // groupStateCache.entrySet().forEach(entry -> {
    // logger.debug("{}: Leader, updating state {} {}", deviceName, entry.getKey(), entry.getValue());
    // linkPlayGroupService.updateGroupState(this, entry.getKey(), entry.getValue());
    // });
    // }
    // isLeader = true;
    // inGroup = true;
    // updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_LEADER,
    // OnOffType.ON);
    // updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_ACTIVE,
    // OnOffType.ON);
    // updateJoinGroupCommandDescription();
    // updateAddRemoveMemberCommandDescription();
    // } else if (isLeader) {
    // // we are no loner in a group
    // isLeader = false;
    // inGroup = false;
    // linkPlayGroupService.unregisterGroup(this);
    // updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_LEADER,
    // OnOffType.OFF);
    // updateState(LinkPlayBindingConstants.GROUP_MULTIROOM, LinkPlayBindingConstants.CHANNEL_MULTIROOM_ACTIVE,
    // OnOffType.OFF);
    // updateJoinGroupCommandDescription();
    // updateAddRemoveMemberCommandDescription();
    // }
    // } catch (InterruptedException | ExecutionException | TimeoutException e) {
    // logger.debug("Error while parsing slave list: {}", e.getMessage(), e);
    // }
    // }

    private void sendDeviceSearchRequest() {
        ControlPoint controlPoint = upnpService.getControlPoint();
        if (controlPoint != null) {
            controlPoint.search(new UDNHeader(new UDN(getUDN())));
            logger.debug("M-SEARCH query sent for device UDN: {}", getUDN());
        }
    }

    private boolean isUpnpDeviceRegistered() {
        return upnpIOService.isRegistered(this);
    }

    private void addSubscription() {
        synchronized (upnpLock) {
            // Set up GENA Subscriptions
            if (upnpIOService.isRegistered(this)) {
                for (String subscription : SERVICE_SUBSCRIPTIONS) {
                    Boolean state = subscriptionState.get(subscription);
                    if (state == null || !state) {
                        logger.debug("{}: Subscribing to service {}...", getUDN(), subscription);
                        upnpIOService.addSubscription(this, subscription, SUBSCRIPTION_DURATION);
                        subscriptionState.put(subscription, true);
                    }
                }
            }
        }
    }

    private void removeSubscription() {
        synchronized (upnpLock) {
            // Set up GENA Subscriptions
            if (upnpIOService.isRegistered(this)) {
                for (String subscription : SERVICE_SUBSCRIPTIONS) {
                    Boolean state = subscriptionState.get(subscription);
                    if (state != null && state) {
                        logger.debug("{}: Unsubscribing from service {}...", getUDN(), subscription);
                        upnpIOService.removeSubscription(this, subscription);
                    }
                }
            }
            subscriptionState = new HashMap<>();
        }
    }

    private void handleAvTransportEvent(Map<String, String> avt) {
        // Playback state mapping
        TransportState transportState = TransportState.fromString(avt.get("TransportState"));
        if (transportState != null) {
            PlayPauseType playPauseType = transportState == TransportState.PLAYING
                    || transportState == TransportState.TRANSITIONING ? PlayPauseType.PLAY : PlayPauseType.PAUSE;

            PlayerStatus.PlaybackStatus status = switch (transportState) {
                case PLAYING -> PlayerStatus.PlaybackStatus.PLAYING;
                case PAUSED_PLAYBACK -> PlayerStatus.PlaybackStatus.PAUSED;
                case STOPPED -> PlayerStatus.PlaybackStatus.STOPPED;
                case TRANSITIONING -> PlayerStatus.PlaybackStatus.PLAYING;
                default -> PlayerStatus.PlaybackStatus.STOPPED;
            };
            updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_PLAYBACK_STATE,
                    new StringType(status.name()));
            updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_PLAYER_CONTROL,
                    playPauseType);
        }

        // Repeat / shuffle mode mapping
        PlayMode playMode = PlayMode.fromString(avt.get("CurrentPlayMode"));
        if (playMode != null) {
            String mappedMode = playMode.getMappedMode();
            if (mappedMode != null) {
                updateState(LinkPlayBindingConstants.GROUP_PLAYBACK,
                        LinkPlayBindingConstants.CHANNEL_REPEAT_SHUFFLE_MODE, new StringType(mappedMode));
            }
        }

        // Track position
        String relPos = avt.get("RelativeTimePosition");
        if (isValidUpnpResponse(relPos)) {
            int seconds = parseTimeStringToSeconds(relPos);
            if (seconds >= 0) {
                updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_TRACK_POSITION,
                        new DecimalType(seconds));
                currentPosition = seconds;
            }
        }

        // Track duration
        String duration = avt.get("CurrentTrackDuration");
        if (isValidUpnpResponse(duration)) {
            int seconds = parseTimeStringToSeconds(duration);
            if (seconds >= 0) {
                updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_TRACK_DURATION,
                        new DecimalType(seconds));
                currentDuration = seconds;
            }
        }

        // Track metadata
        String mdXml = avt.get("CurrentTrackMetaData");
        if (isValidUpnpResponse(mdXml)) {
            List<UpnpEntry> entries = UpnpXMLParser.getEntriesFromXML(Objects.requireNonNull(mdXml));
            if (!entries.isEmpty()) {
                UpnpEntry entry = entries.get(0);
                if (!entry.getTitle().isEmpty()) {
                    updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_TRACK_TITLE,
                            new StringType(entry.getTitle()));
                }
                String artist = !entry.getArtist().isEmpty() ? entry.getArtist() : entry.getCreator();
                if (!artist.isEmpty()) {
                    updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_TRACK_ARTIST,
                            new StringType(artist));
                }
                if (!entry.getAlbum().isEmpty()) {
                    updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_TRACK_ALBUM,
                            new StringType(entry.getAlbum()));
                }
                if (!entry.getAlbumArtUri().isEmpty()) {
                    updateAlbumArtChannels(entry.getAlbumArtUri());
                }
            }
        }
    }

    private void handleRenderingControlEvent(Map<String, @Nullable String> rc) {
        for (Map.Entry<String, @Nullable String> e : rc.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            logger.debug("{}: handleRenderingControlEvent: {} {} {}", deviceName, key, value, getUDN());
            if (value == null) {
                continue;
            }
            try {
                if (key.endsWith("Volume")) {
                    int vol = Integer.parseInt(value);
                    updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_VOLUME,
                            new PercentType(vol));
                } else if (key.endsWith("Mute")) {
                    OnOffType muteState = "1".equals(value) ? OnOffType.ON : OnOffType.OFF;
                    updateState(LinkPlayBindingConstants.GROUP_PLAYBACK, LinkPlayBindingConstants.CHANNEL_MUTE,
                            muteState);
                } else if (key.equals("Slave")) {
                    updateMultiroom();
                }
            } catch (Exception ignored) {
                // ignore parse errors
            }
        }
    }

    private boolean isValidUpnpResponse(@Nullable String value) {
        return value != null && !value.isBlank() && !"NOT_IMPLEMENTED".equals(value);
    }

    private void updateAlbumArtChannels(@Nullable String albumArtUri) {
        if (albumArtUri == null || !albumArtUri.trim().startsWith("http")) {
            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_ALBUM_ART_URL,
                    UnDefType.NULL);
            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_ALBUM_ART,
                    UnDefType.NULL);
            previousAlbumArtUri = null;
            return;
        }

        if (albumArtUri.equals(previousAlbumArtUri)) {
            return;
        }

        previousAlbumArtUri = albumArtUri;

        updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_ALBUM_ART_URL,
                new StringType(albumArtUri));

        try {
            State albumArt = HttpUtil.downloadImage(albumArtUri.trim());
            updateState(LinkPlayBindingConstants.GROUP_METADATA, LinkPlayBindingConstants.CHANNEL_ALBUM_ART,
                    albumArt != null ? albumArt : UnDefType.NULL);
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid album art URI: {}", albumArtUri, e);
        }
    }

    private void updatePresetPicChannels(String groupId, @Nullable String picUrl) {
        if (picUrl == null || !picUrl.trim().startsWith("http")) {
            updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_PIC_URL, UnDefType.NULL);
            updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_PIC, UnDefType.NULL);
            return;
        }

        updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_PIC_URL, new StringType(picUrl));

        try {
            State image = HttpUtil.downloadImage(picUrl.trim());
            updateState(groupId, LinkPlayBindingConstants.CHANNEL_PRESET_PIC, image != null ? image : UnDefType.NULL);
        } catch (IllegalArgumentException e) {
            logger.debug("Invalid preset image URI: {}", picUrl, e);
        }
    }

    private void updateState(String groupId, String channelId, State state) {
        boolean isGroupChannel = GROUP_PROXY_CHANNELS.contains(channelId);
        // group proxy channels are set by the leader when in a group
        String groupIdChannel = groupId + "#" + channelId;
        if (inGroup && isGroupChannel) {
            if (isLeader) {
                logger.debug("{}: Leader, updating state {} {} in group {}", deviceName, channelId, state, groupId);
                linkPlayGroupService.updateGroupState(this, groupIdChannel, state);
            } else {
                // if we are not the leader, we these will come from the group proxy
                logger.debug("{}: Not leader, skipping update of state {} {} in group {} isLeader {}", deviceName,
                        channelId, state, inGroup, isLeader);
                return;
            }
        }
        super.updateState(groupIdChannel, state);

        // even when not in a group, we cache the state for the group proxy if we do become a leader
        if (isGroupChannel) {
            groupStateCache.put(groupIdChannel, state);
        }
    }

    private int parseTimeStringToSeconds(@Nullable String time) {
        if (time == null || time.isBlank()) {
            return -1;
        }
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                return h * 3600 + m * 60 + s;
            } else if (parts.length == 2) {
                int m = Integer.parseInt(parts[0]);
                int s = Integer.parseInt(parts[1]);
                return m * 60 + s;
            }
        } catch (Exception ignored) {
        }
        return -1; // error
    }

    /**
     * LinkPlay uses some strange return types when values are unknown. This tries to handle those when setting states.
     * 
     * @param value
     * @param stateClass
     * @return State or UnDefType.NULL if the value is null or unknown
     */
    private State stateOrNull(@Nullable Object value, Class<? extends State> stateClass) {
        if (value == null) {
            return UnDefType.NULL;
        }
        String strValue = value.toString();
        if ("unknow".equalsIgnoreCase(strValue) || "un_known".equalsIgnoreCase(strValue)
                || "unknown".equalsIgnoreCase(strValue)) {
            return UnDefType.NULL;
        }
        try {
            // Try constructor that matches the value's class first
            try {
                Constructor<? extends State> ctor = stateClass.getConstructor(value.getClass());
                return ctor.newInstance(value);
            } catch (NoSuchMethodException ignored) {
                // Fallback to String constructor
                Constructor<? extends State> ctor = stateClass.getConstructor(String.class);
                return ctor.newInstance(strValue);
            }
        } catch (Exception e) {
            logger.debug("Failed to instantiate {} for value {}: {}", stateClass.getSimpleName(), value, e.getMessage(),
                    e);
            return UnDefType.NULL;
        }
    }

    public void updateJoinGroupCommandDescription() {
        logger.debug("{}: Updating join group command description for {} participants", deviceName,
                allParticipants.size());
        List<CommandOption> commandOptions = new ArrayList<>();
        // filter out ourself and participants that are in a group
        allParticipants.stream()
                .filter(participant -> participant != this && linkPlayGroupService.getLeader(participant) == null)
                .forEach(participant -> commandOptions
                        .add(new CommandOption(participant.getIpAddress(), participant.getGroupParticipantLabel())));

        updateCommandDescription(new ChannelUID(getThing().getUID(), LinkPlayBindingConstants.GROUP_MULTIROOM,
                LinkPlayBindingConstants.CHANNEL_MULTIROOM_JOIN), commandOptions);
    }

    public void updateAddRemoveMemberCommandDescription() {
        updateAddRemoveMemberCommandDescription(null);
    }

    public void updateAddRemoveMemberCommandDescription(@Nullable List<Slave> slaves) {
        logger.debug("{}: Updating add member command description for {} participants", deviceName,
                allParticipants.size());
        List<CommandOption> commandOptions = new ArrayList<>();
        if (slaves == null) {
            slaves = linkPlayGroupService.getGroupList(this);
        }
        logger.info("{}: Slaves: {}", deviceName, slaves);
        if (isLeader && !slaves.isEmpty()) {
            commandOptions.add(new CommandOption("LEAVE", "-- Remove all players --"));
        }
        commandOptions.add(new CommandOption("ADD_ALL", "-- Add all players --"));
        for (LinkPlayGroupParticipant participant : allParticipants) {
            if (participant == this) {
                continue;
            }
            if (isLeader && slaves.stream().anyMatch(slave -> slave.ip.equals(participant.getIpAddress()))) {
                commandOptions.add(new CommandOption(participant.getIpAddress(),
                        "Remove: " + participant.getGroupParticipantLabel()));
            } else {
                commandOptions.add(new CommandOption(participant.getIpAddress(),
                        "Add: " + participant.getGroupParticipantLabel()));
            }
        }
        updateCommandDescription(new ChannelUID(getThing().getUID(), LinkPlayBindingConstants.GROUP_MULTIROOM,
                LinkPlayBindingConstants.CHANNEL_MULTIROOM_MANAGE), commandOptions);
    }

    protected void updateCommandDescription(ChannelUID channelUID, List<CommandOption> commandOptionList) {
        CommandDescription commandDescription = CommandDescriptionBuilder.create().withCommandOptions(commandOptionList)
                .build();
        linkPlayCommandDescriptionProvider.setDescription(channelUID, commandDescription);
    }
}
