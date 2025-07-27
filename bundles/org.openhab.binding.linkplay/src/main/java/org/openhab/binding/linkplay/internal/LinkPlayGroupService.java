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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.linkplay.internal.client.dto.Slave;
import org.openhab.binding.linkplay.internal.client.dto.SlaveListResponse;
import org.openhab.core.types.State;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for managing LinkPlay groups.
 * 
 * @author Dan Cunningham - Initial contribution
 */
@NonNullByDefault
@Component(service = LinkPlayGroupService.class, scope = ServiceScope.SINGLETON)
public class LinkPlayGroupService {

    private final Logger logger = LoggerFactory.getLogger(LinkPlayGroupService.class);
    private final Map<String, List<Slave>> groups = new ConcurrentHashMap<>();
    private final Map<String, Map<String, State>> groupStateCache = new ConcurrentHashMap<>();

    private final Map<String, LinkPlayGroupParticipant> participants = new ConcurrentHashMap<>();

    @Deactivate
    public void deactivate() {
        logger.debug("LinkPlayGroupService deactivated");
        groups.clear();
        participants.clear();
    }

    public void registerParticipant(LinkPlayGroupParticipant participant) {
        participants.put(participant.getIpAddress(), participant);
        participants.values().forEach(listener -> listener.groupParticipantsUpdated(participants.values()));
    }

    public void unregisterParticipant(LinkPlayGroupParticipant participant) {
        groups.remove(participant.getIpAddress());
        participants.remove(participant.getIpAddress());
        participants.values().forEach(listener -> listener.groupParticipantsUpdated(participants.values()));
        groupStateCache.remove(participant.getIpAddress());
    }

    public @Nullable LinkPlayGroupParticipant getLeader(LinkPlayGroupParticipant member) {
        if (groups.containsKey(member.getIpAddress())) {
            return member;
        }
        for (Map.Entry<String, List<Slave>> entry : groups.entrySet()) {
            if (entry.getValue().stream().anyMatch(slave -> slave.ip.equals(member.getIpAddress()))) {
                return participants.get(entry.getKey());
            }
        }
        return null;
    }

    public void joinGroup(LinkPlayGroupParticipant member, String leaderIpAddress) {
        // first remove the member from any existing group, including their own
        LinkPlayGroupParticipant oldLeader = getLeader(member);
        if (oldLeader != null) {
            try {
                // if (oldLeader.getIpAddress().equals(leaderIpAddress)) {
                // oldLeader.getApiClient().multiroomUngroup().get(5, TimeUnit.SECONDS);
                // } else {
                // oldLeader.getApiClient().multiroomSlaveKickout(member.getIpAddress()).get(5, TimeUnit.SECONDS);
                // }
                member.getApiClient().multiroomUngroup().get();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error joining group: {}", e.getMessage(), e);
            }

        }
        try {
            member.getApiClient().multiroomJoinGroupMaster(leaderIpAddress).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error joining group: {}", e.getMessage(), e);
        }
    }

    public void unGroup(LinkPlayGroupParticipant member) {
        try {
            member.getApiClient().multiroomUngroup().get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error ungrouping: {}", e.getMessage(), e);
        }
    }

    // public void leaveGroup(LinkPlayGroupParticipant member) {
    // // If the member is a leader, ungroup everyone
    // logger.debug("Leaving group: {}", member.getIpAddress());
    // if (groups.containsKey(member.getIpAddress())) {
    // try {
    // member.getApiClient().multiroomUngroup().get(5, TimeUnit.SECONDS);
    // } catch (InterruptedException | ExecutionException | TimeoutException e) {
    // logger.error("Error ungrouping: {}", e.getMessage(), e);
    // }
    // return;
    // }

    // LinkPlayGroupParticipant leader = getLeader(member);
    // if (leader != null) {
    // try {
    // leader.getApiClient().multiroomSlaveKickout(member.getIpAddress()).get(5, TimeUnit.SECONDS);
    // } catch (InterruptedException | ExecutionException | TimeoutException e) {
    // logger.error("Error leaving group: {}", e.getMessage(), e);
    // }
    // }
    // }

    public void addRemoveMember(LinkPlayGroupParticipant leader, String memberIpAddress) {
        LinkPlayGroupParticipant member = participants.get(memberIpAddress);
        if (member != null) {
            addRemoveMember(leader, member);
        }
    }

    public void addAllMembers(LinkPlayGroupParticipant leader) {
        // unregisterGroup(leader);
        if (!groups.containsKey(leader.getIpAddress())) {
            unGroup(leader);
        }
        participants.values().forEach(participant -> {
            if (leader == participant) {
                return;
            }
            try {
                participant.getApiClient().multiroomJoinGroupMaster(leader.getIpAddress()).get(5, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error adding member:{} to group:{}", participant.getGroupParticipantLabel(),
                        leader.getGroupParticipantLabel(), e);
            }
            refreshMemberSlaveList(leader);
        });
    }

    public void updateGroupState(LinkPlayGroupParticipant leader, String channelId, State state) {
        logger.debug("{}: Updating state {} {}", leader.getGroupParticipantLabel(), channelId, state);
        groupStateCache.computeIfAbsent(leader.getIpAddress(), k -> new ConcurrentHashMap<String, State>())
                .put(channelId, state);
        groups.getOrDefault(leader.getIpAddress(), List.of()).forEach(slave -> {
            if (slave.ip.equals(leader.getIpAddress())) {
                return;
            }
            LinkPlayGroupParticipant participant = participants.get(slave.ip);
            if (participant != null) {
                participant.groupProxyUpdateState(channelId, state);
            }
        });
    }

    public List<Slave> getGroupList(LinkPlayGroupParticipant member) {
        LinkPlayGroupParticipant leader = getLeader(member);
        if (leader != null) {
            return groups.getOrDefault(leader.getIpAddress(), List.of());
        }
        return List.of();
    }

    // to be called when we get the UPNP event "Slave"
    public void refreshMemberSlaveList(LinkPlayGroupParticipant member) {
        try {
            SlaveListResponse slaveList = member.getApiClient().multiroomGetSlaveList().get(30, TimeUnit.SECONDS);
            List<Slave> slaves = slaveList.slaveList == null ? List.of() : slaveList.slaveList;

            if (slaves.isEmpty()) {
                unregisterGroup(member);
                return;
            }

            List<Slave> oldSlaves = groups.put(member.getIpAddress(), slaves);
            if (oldSlaves != null) {
                oldSlaves.forEach(slave -> {
                    if (!slaves.stream().anyMatch(s -> s.ip.equals(slave.ip))) {
                        LinkPlayGroupParticipant listener = participants.get(slave.ip);
                        if (listener != null) {
                            listener.removedFromGroup(member);
                        }
                    }
                });
            }
            slaves.forEach(slave -> {
                LinkPlayGroupParticipant listener = participants.get(slave.ip);
                if (listener != null) {
                    listener.addedToOrUpdatedGroup(member, slaves);
                }
                groupStateCache.computeIfAbsent(member.getIpAddress(), k -> new ConcurrentHashMap<String, State>())
                        .forEach((channelId, state) -> {
                            listener.groupProxyUpdateState(channelId, state);
                        });
            });
            member.addedToOrUpdatedGroup(member, slaves);

        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error getting slave list: {}", e.getMessage(), e);
        }
    }

    // public void updateSlaveList(LinkPlayGroupParticipant member, SlaveListResponse slaveList) {
    // logger.debug("{}: Multiroom slave list: {}", member.getGroupParticipantLabel(), slaveList);
    // boolean isLeader = groups.containsKey(member.getIpAddress());
    // if (slaveList.isMaster()) {
    // updateGroupParticipants(member, slaveList.slaveList);
    // // if (!isLeader) {
    // // member.addedToGroup(member);
    // // }
    // } else if (isLeader) {
    // unregisterGroup(member);
    // }
    // }

    // private void updateGroupParticipants(LinkPlayGroupParticipant member, List<Slave> slaves) {
    // logger.debug("{}: Updating group participants: {}", member.getGroupParticipantLabel(), slaves);
    // // if there is an existing group, remove the members that are not in the new list
    // List<Slave> oldSlaves = groups.get(member.getIpAddress());
    // if (oldSlaves != null) {
    // oldSlaves.forEach(slave -> {
    // if (!slaves.stream().anyMatch(s -> s.ip.equals(slave.ip))) {
    // LinkPlayGroupParticipant listener = participants.get(slave.ip);
    // if (listener != null) {
    // listener.removedFromGroup(member);
    // }
    // }
    // });
    // }

    // if (slaves.isEmpty() && oldSlaves != null) {
    // unregisterGroup(member);
    // } else {
    // groups.put(member.getIpAddress(), slaves);
    // slaves.forEach(slave -> {
    // LinkPlayGroupParticipant listener = participants.get(slave.ip);
    // if (listener != null) {
    // listener.addedToOrUpdatedGroup(member, slaves);
    // }
    // groupStateCache.computeIfAbsent(member.getIpAddress(), k -> new ConcurrentHashMap<String, State>())
    // .forEach((channelId, state) -> {
    // listener.groupProxyUpdateState(channelId, state);
    // });
    // });
    // member.addedToOrUpdatedGroup(member, slaves);
    // }
    // }

    private void unregisterGroup(LinkPlayGroupParticipant leader) {
        List<Slave> slaves = groups.remove(leader.getIpAddress());
        if (slaves != null) {
            slaves.forEach(slave -> {
                LinkPlayGroupParticipant listener = participants.get(slave.ip);
                if (listener != null) {
                    listener.removedFromGroup(leader);
                }
            });
            leader.removedFromGroup(leader);
        }
        groupStateCache.remove(leader.getIpAddress());
    }

    private void addRemoveMember(LinkPlayGroupParticipant leader, LinkPlayGroupParticipant member) {
        try {
            List<Slave> slaves = groups.get(leader.getIpAddress());
            if (slaves != null) {
                if (slaves.stream().anyMatch(slave -> slave.ip.equals(member.getIpAddress()))) {
                    leader.getApiClient().multiroomSlaveKickout(member.getIpAddress()).get(5, TimeUnit.SECONDS);
                    return;
                }
            }
            member.getApiClient().multiroomJoinGroupMaster(leader.getIpAddress()).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            logger.error("Error adding member: {}", e.getMessage(), e);
        }
        refreshMemberSlaveList(leader);
    }
}
