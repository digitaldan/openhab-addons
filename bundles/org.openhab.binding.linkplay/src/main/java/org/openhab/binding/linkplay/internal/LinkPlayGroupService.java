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

    /**
     * Register a potential group participant (a player)`
     * 
     * @param participant
     */
    public void registerParticipant(LinkPlayGroupParticipant participant) {
        participants.put(participant.getIpAddress(), participant);
        participants.values().forEach(listener -> listener.groupParticipantsUpdated(participants.values()));
    }

    /**
     * Unregister a participant
     * 
     * @param participant
     */
    public void unregisterParticipant(LinkPlayGroupParticipant participant) {
        groups.remove(participant.getIpAddress());
        participants.remove(participant.getIpAddress());
        participants.values().forEach(listener -> listener.groupParticipantsUpdated(participants.values()));
        groupStateCache.remove(participant.getIpAddress());
    }

    /**
     * Get the leader of a group
     * 
     * @param member
     * @return the leader of the group (which could also be the member)
     */
    public @Nullable LinkPlayGroupParticipant getLeader(LinkPlayGroupParticipant member) {
        // member is the leader
        if (groups.containsKey(member.getIpAddress())) {
            return member;
        }
        // member is a slave
        for (Map.Entry<String, List<Slave>> entry : groups.entrySet()) {
            if (entry.getValue().stream().anyMatch(slave -> slave.ip.equals(member.getIpAddress()))) {
                return participants.get(entry.getKey());
            }
        }
        return null;
    }

    /**
     * Join a member to a group
     * 
     * @param member
     * @param leaderIpAddress the IP address of the leader
     */
    public void joinGroup(LinkPlayGroupParticipant member, String leaderIpAddress) {
        // first remove the member from any existing group, including their own
        LinkPlayGroupParticipant oldLeader = getLeader(member);
        if (oldLeader != null) {
            unGroup(oldLeader);
        }
        try {
            member.getApiClient().multiroomJoinGroupMaster(leaderIpAddress).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error joining group: {}", e.getMessage(), e);
        }
        LinkPlayGroupParticipant leader = participants.get(leaderIpAddress);
        if (leader != null) {
            refreshMemberSlaveList(leader);
        }
    }

    /**
     * Remove the member from participating in a group
     * 
     * @param member
     */
    public void unGroup(LinkPlayGroupParticipant member) {
        LinkPlayGroupParticipant leader = getLeader(member);
        try {
            member.getApiClient().multiroomUngroup().get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error ungrouping: {}", e.getMessage(), e);
        }
        if (leader != null) {
            refreshMemberSlaveList(leader);
        }
    }

    /**
     * Adds or moves a member to a group
     * 
     * @param leader
     * @param memberIpAddress
     */
    public void addOrMoveMember(LinkPlayGroupParticipant leader, String memberIpAddress) {
        LinkPlayGroupParticipant member = participants.get(memberIpAddress);
        if (member != null) {
            addOrMoveMember(leader, member);
            refreshMemberSlaveList(leader);
        }
    }

    /**
     * Adds all registered players to a single group (play everywhere)
     * 
     * @param leader
     */
    public void addAllMembers(LinkPlayGroupParticipant leader) {
        // unregisterGroup(leader);
        if (!groups.containsKey(leader.getIpAddress())) {
            unGroup(leader);
        }
        participants.values().forEach(participant -> {
            if (leader.equals(participant)) {
                return;
            }
            // try {
            // participant.getApiClient().multiroomJoinGroupMaster(leader.getIpAddress()).get();
            // } catch (InterruptedException | ExecutionException e) {
            // logger.error("Error adding member:{} to group:{}", participant.getGroupParticipantLabel(),
            // leader.getGroupParticipantLabel(), e);
            // }
            // refreshMemberSlaveList(leader);
            addOrMoveMember(leader, participant.getIpAddress());
        });
        refreshMemberSlaveList(leader);
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

    /**
     * Get the list of slaves in a group
     * 
     * @param member
     * @return the list of slaves in the group
     */
    public List<Slave> getGroupList(LinkPlayGroupParticipant member) {
        LinkPlayGroupParticipant leader = getLeader(member);
        if (leader != null) {
            return groups.getOrDefault(leader.getIpAddress(), List.of());
        }
        return List.of();
    }

    /**
     * Refresh the list of slaves in a group
     * to be called when we get the UPNP event "Slave"
     * 
     * @param member
     */
    public void refreshMemberSlaveList(LinkPlayGroupParticipant member) {
        try {
            SlaveListResponse slaveList = member.getApiClient().multiroomGetSlaveList().get();
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

        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error getting slave list: {}", e.getMessage(), e);
        }
    }

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

    private void addOrMoveMember(LinkPlayGroupParticipant leader, LinkPlayGroupParticipant member) {
        try {
            List<Slave> slaves = groups.get(leader.getIpAddress());
            if (slaves != null) {
                if (slaves.stream().anyMatch(slave -> slave.ip.equals(member.getIpAddress()))) {
                    leader.getApiClient().multiroomSlaveKickout(member.getIpAddress()).get();
                    return;
                }
            }
            member.getApiClient().multiroomJoinGroupMaster(leader.getIpAddress()).get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error adding member: {}", e.getMessage(), e);
        }
    }
}
