/**
 * Copyright (c) 2010-2022 Contributors to the openHAB project
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
package org.openhab.binding.qolsysiq.internal.handler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.qolsysiq.internal.QolsysIQBindingConstants;
import org.openhab.binding.qolsysiq.internal.client.dto.action.AlarmAction;
import org.openhab.binding.qolsysiq.internal.client.dto.action.AlarmActionType;
import org.openhab.binding.qolsysiq.internal.client.dto.action.ArmingAction;
import org.openhab.binding.qolsysiq.internal.client.dto.action.ArmingActionType;
import org.openhab.binding.qolsysiq.internal.client.dto.event.AlarmEvent;
import org.openhab.binding.qolsysiq.internal.client.dto.event.ArmingEvent;
import org.openhab.binding.qolsysiq.internal.client.dto.event.ErrorEvent;
import org.openhab.binding.qolsysiq.internal.client.dto.event.SecureArmInfoEvent;
import org.openhab.binding.qolsysiq.internal.client.dto.model.AlarmType;
import org.openhab.binding.qolsysiq.internal.client.dto.model.Partition;
import org.openhab.binding.qolsysiq.internal.client.dto.model.PartitionStatus;
import org.openhab.binding.qolsysiq.internal.client.dto.model.Zone;
import org.openhab.binding.qolsysiq.internal.config.QolsysIQPartitionConfiguration;
import org.openhab.binding.qolsysiq.internal.discovery.QolsysIQChildDiscoveryService;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseBridgeHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
public class QolsysIQPartitionHandler extends BaseBridgeHandler implements QolsysIQChildDiscoveryHandler {
    private final Logger logger = LoggerFactory.getLogger(QolsysIQPartitionHandler.class);
    private final static Map<AlarmType, String> ALARMTYPE_CHANNELS = Map.of(AlarmType.AUXILIARY,
            QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_AUXILIARY, AlarmType.FIRE,
            QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_FIRE, AlarmType.NONE,
            QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_NONE, AlarmType.POLICE,
            QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_POLICE, AlarmType.ZONEOPEN,
            QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_ZONE);
    private @Nullable QolsysIQChildDiscoveryService discoveryService;
    private @Nullable ScheduledFuture<?> delayFuture;
    private @Nullable ScheduledFuture<?> armStateFuture;
    private @Nullable Partition partitionCache;
    private @Nullable String armCode;
    private AlarmType alarmCache = AlarmType.NONE;
    private List<Zone> zones = Collections.synchronizedList(new LinkedList<Zone>());
    private int partitionId;

    public QolsysIQPartitionHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        QolsysIQPartitionConfiguration config = getConfigAs(QolsysIQPartitionConfiguration.class);
        partitionId = config.id;
        armCode = config.armCode.strip().length() == 0 ? null : config.armCode;
        logger.debug("initialize partition {}", partitionId);
        refresh();
    }

    @Override
    public void dispose() {
        cancelExitDelayJob();
        cancelArmStateRefresh();
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        super.bridgeStatusChanged(bridgeStatusInfo);
        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            cancelExitDelayJob();
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refresh();
            return;
        }

        QolsysIQPanelHandler panel = panelHandler();
        if (panel != null) {
            if (channelUID.getId().equals(QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_STATE)) {
                try {
                    panel.sendAction(new AlarmAction(AlarmActionType.valueOf(command.toString()), ""));
                } catch (IllegalArgumentException e) {
                    logger.debug("Unknown alarm type {} to channel {}", command, channelUID);
                }
                return;
            }

            // support ARM_AWAY and ARM_AWAY:123456 , same for other arm / disarm modes
            if (channelUID.getId().equals(QolsysIQBindingConstants.CHANNEL_PARTITION_ARM_STATE)) {

                // if we don't get an update quickly, we need to reset the channel to the last known state
                startArmStateRefresh();

                String armingTypeName = command.toString();
                String code = armCode;
                if (armingTypeName.contains(":")) {
                    String[] split = armingTypeName.split(":");
                    armingTypeName = split[0];
                    if (split.length > 1 && split[1].length() > 0) {
                        code = split[1];
                    }
                }

                try {
                    ArmingActionType armingType = ArmingActionType.valueOf(armingTypeName);
                    updateState(channelUID, new StringType(armingType.toString()));
                    panel.sendAction(new ArmingAction(armingType, "", partitionId(), code));
                } catch (IllegalArgumentException e) {
                    logger.debug("Unknown arm type {} to channel {}", armingTypeName, channelUID);
                }
            }
        }
    }

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(QolsysIQChildDiscoveryService.class);
    }

    @Override
    public void setDiscoveryService(QolsysIQChildDiscoveryService service) {
        this.discoveryService = service;
    }

    @Override
    public void startDiscovery() {
        refresh();
    }

    /**
     * The partition id
     *
     * @return
     */
    public int partitionId() {
        return partitionId;
    }

    protected void alarmEvent(AlarmEvent event) {
        // is this needed? I need to trigger the alarm and see if two updates are sent
        if (event.alarmType != AlarmType.NONE && event.alarmType != AlarmType.ZONEOPEN) {
            updatePartitionStatus(PartitionStatus.ALARM);
        }
        updateAlarmState(event.alarmType);
    }

    protected void armingEvent(ArmingEvent event) {
        updatePartitionStatus(event.armingType);
        updateDelay(event.delay == null ? 0 : event.delay);
        Partition partitionCache = this.partitionCache;
        if (partitionCache != null) {
            partitionCache.status = event.armingType;
        }
    }

    protected void errorEvent(ErrorEvent event) {
        logger.debug("error event: {}", event);
    }

    protected void secureArmInfoEvent(SecureArmInfoEvent event) {
        setSecureArm(event.value);
    }

    protected void updatePartition(Partition partition) {
        if (getThing().getStatus() != ThingStatus.ONLINE) {
            updateStatus(ThingStatus.ONLINE);
        }
        this.partitionCache = partition;
        updatePartitionStatus(partition.status);
        setSecureArm(partition.secureArm);
        if (partition.status != PartitionStatus.ALARM) {
            updateAlarmState(AlarmType.NONE);
        }
        synchronized (zones) {
            zones.clear();
            zones.addAll(partition.zoneList);
            zones.forEach(z -> {
                QolsysIQZoneHandler zoneHandler = zoneHandler(z.zoneId);
                if (zoneHandler != null) {
                    zoneHandler.updateZone(z);
                }
            });
        }
        discoverChildDevices();
    }

    protected @Nullable Zone getZone(Integer zoneId) {
        synchronized (zones) {
            return zones.stream().filter(z -> z.zoneId.equals(zoneId)).findAny().orElse(null);
        }
    }

    private void refresh() {
        QolsysIQPanelHandler panel = panelHandler();
        if (panel != null) {
            panel.refresh();
        }
    }

    private void updatePartitionStatus(PartitionStatus status) {
        cancelArmStateRefresh();
        updateState(QolsysIQBindingConstants.CHANNEL_PARTITION_ARM_STATE, new StringType(status.toString()));
        if (status == PartitionStatus.DISARM) {
            updateAlarmState(AlarmType.NONE);
            updateDelay(0);
        }
    }

    private void setSecureArm(Boolean secure) {
        Map<String, String> props = new HashMap<String, String>();
        props.put("secureArm", String.valueOf(secure));
        getThing().setProperties(props);
    }

    private void updateDelay(Integer delay) {
        logger.debug("updateDelay {}", delay);
        cancelExitDelayJob();
        if (delay <= 0) {
            updateState(QolsysIQBindingConstants.CHANNEL_PARTITION_COMMAND_DELAY, new DecimalType(0));
            return;
        }

        final long endTime = System.currentTimeMillis() + (delay * 1000);
        delayFuture = scheduler.scheduleAtFixedRate(() -> {
            long remaining = endTime - System.currentTimeMillis();
            logger.debug("updateDelay remaining {}", remaining / 1000);
            if (remaining <= 0) {
                cancelExitDelayJob();
            } else {
                updateState(QolsysIQBindingConstants.CHANNEL_PARTITION_COMMAND_DELAY,
                        new DecimalType(remaining / 1000));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }

    private void updateAlarmState(AlarmType alarmType) {
        alarmCache = alarmType;
        updateState(QolsysIQBindingConstants.CHANNEL_PARTITION_ALARM_STATE, new StringType(alarmType.toString()));
        ALARMTYPE_CHANNELS.forEach((type, channel) -> {
            updateState(channel, type == alarmType ? OnOffType.ON : OnOffType.OFF);
        });
    }

    private void cancelExitDelayJob() {
        ScheduledFuture<?> delayFuture = this.delayFuture;
        if (delayFuture != null && !delayFuture.isDone()) {
            delayFuture.cancel(false);
        }
        updateState(QolsysIQBindingConstants.CHANNEL_PARTITION_COMMAND_DELAY, new DecimalType(0));
    }

    private void startArmStateRefresh() {
        ScheduledFuture<?> armStateRefreshFuture = this.armStateFuture;
        if (armStateRefreshFuture == null || armStateRefreshFuture.isDone()) {
            this.armStateFuture = scheduler.schedule(() -> {
                Partition p = this.partitionCache;
                if (p != null) {
                    updateState(QolsysIQBindingConstants.CHANNEL_PARTITION_ARM_STATE,
                            new StringType(p.status.toString()));

                }
            }, 2, TimeUnit.SECONDS);

        }
    }

    private void cancelArmStateRefresh() {
        ScheduledFuture<?> armStateRefreshFuture = this.armStateFuture;
        if (armStateRefreshFuture != null && !armStateRefreshFuture.isDone()) {
            armStateRefreshFuture.cancel(false);
        }
    }

    private void discoverChildDevices() {
        synchronized (zones) {
            zones.forEach(z -> {
                QolsysIQChildDiscoveryService discoveryService = this.discoveryService;
                if (discoveryService != null) {
                    ThingUID bridgeUID = getThing().getUID();
                    ThingUID thingUID = new ThingUID(QolsysIQBindingConstants.THING_TYPE_ZONE, bridgeUID,
                            String.valueOf(z.zoneId));
                    discoveryService.discoverQolsysIQChildThing(thingUID, bridgeUID, z.zoneId,
                            "Qolsys IQ Zone: " + z.name);
                }
            });
        }
    }

    private @Nullable QolsysIQZoneHandler zoneHandler(int zoneId) {
        for (Thing thing : getThing().getThings()) {
            ThingHandler handler = thing.getHandler();
            if (handler != null && handler instanceof QolsysIQZoneHandler) {
                if (((QolsysIQZoneHandler) handler).zoneId() == zoneId) {
                    return (QolsysIQZoneHandler) handler;
                }
            }
        }
        return null;
    }

    private @Nullable QolsysIQPanelHandler panelHandler() {
        Bridge bridge = getBridge();
        if (bridge != null) {
            BridgeHandler handler = bridge.getHandler();
            if (handler != null && handler instanceof QolsysIQPanelHandler) {
                return (QolsysIQPanelHandler) handler;
            }
        }
        return null;
    }
}
