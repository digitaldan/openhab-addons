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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.linkplay.internal.client.LinkPlayHTTPClient;
import org.openhab.binding.linkplay.internal.client.dto.Slave;
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
    private final Map<String, List<Slave>> groups = new HashMap<>();
    private final Map<String, GroupParticipant> participants = new ConcurrentHashMap<>();

    @Deactivate
    public void deactivate() {
        logger.debug("LinkPlayGroupService deactivated");
        groups.clear();
        participants.clear();
    }

    public void registerParticipant(GroupParticipant participant) {
        participants.put(participant.getIpAddress(), participant);
        participants.values().forEach(listener -> listener.groupParticipantsUpdated(participants.values()));
    }

    public void unregisterParticipant(GroupParticipant participant) {
        groups.remove(participant.getIpAddress());
        participants.remove(participant.getIpAddress());
        participants.values().forEach(listener -> listener.groupParticipantsUpdated(participants.values()));
    }

    public void updateGroupParticipants(GroupParticipant leader, List<Slave> slaves) {
        List<Slave> oldSlaves = groups.put(leader.getIpAddress(), slaves);
        if (oldSlaves != null) {
            oldSlaves.forEach(slave -> {
                if (!slaves.stream().anyMatch(s -> s.ip.equals(slave.ip))) {
                    GroupParticipant listener = participants.get(slave.ip);
                    if (listener != null) {
                        listener.removedFromGroup(leader);
                    }
                }
            });
        }
        slaves.forEach(slave -> {
            GroupParticipant listener = participants.get(slave.ip);
            if (listener != null) {
                listener.addedToGroup(leader);
            }
        });
    }

    public void unregisterGroup(GroupParticipant leader) {
        List<Slave> slaves = groups.remove(leader.getIpAddress());
        slaves.forEach(slave -> {
            GroupParticipant listener = participants.get(slave.ip);
            if (listener != null) {
                listener.removedFromGroup(leader);
            }
        });
    }

    public @Nullable GroupParticipant getLeader(GroupParticipant member) {
        for (Map.Entry<String, List<Slave>> entry : groups.entrySet()) {
            if (entry.getValue().stream().anyMatch(slave -> slave.ip.equals(member.getIpAddress()))) {
                return participants.get(entry.getKey());
            }
        }
        return null;
    }

    public void leaveGroup(GroupParticipant member) {
        // If the member is a leader, ungroup everyone
        if (groups.containsKey(member.getIpAddress())) {
            try {
                member.getApiClient().multiroomUngroup().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error ungrouping: {}", e.getMessage(), e);
            }
            return;
        }

        GroupParticipant leader = getLeader(member);
        if (leader != null) {
            try {
                leader.getApiClient().multiroomSlaveKickout(member.getIpAddress()).get(5,
                        java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error leaving group: {}", e.getMessage(), e);
            }
        }
    }

    public void addRemoveMember(GroupParticipant leader, String memberIpAddress) {
        GroupParticipant member = participants.get(memberIpAddress);
        if (member != null) {
            try {
                List<Slave> slaves = groups.get(leader.getIpAddress());
                if (slaves == null) {
                    if (slaves.stream().anyMatch(slave -> slave.ip.equals(memberIpAddress))) {
                        member.getApiClient().multiroomSlaveKickout(memberIpAddress).get(5,
                                java.util.concurrent.TimeUnit.SECONDS);
                        return;
                    }
                }
                member.getApiClient().multiroomJoinGroupMaster(leader.getIpAddress()).get(5,
                java.util.concurrent.TimeUnit.SECONDS);
                
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error adding member: {}", e.getMessage(), e);
            }
        }
    }

    public void removeMember(GroupParticipant leader, String ipAddress) {
        GroupParticipant member = participants.get(ipAddress);
        if (member != null) {
            try {
                leader.getApiClient().multiroomSlaveKickout(member.getIpAddress()).get(5,
                        java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error leaving group: {}", e.getMessage(), e);
            }
        }
    }

    public void ungroup(GroupParticipant member) {
        GroupParticipant leader = groups.containsKey(member.getIpAddress()) ? member : getLeader(member);
        if (leader != null) {
            try {
                leader.getApiClient().multiroomUngroup().get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                logger.error("Error ungrouping: {}", e.getMessage(), e);
            }
        }
    }

    public void updateGroupState(GroupParticipant leader, String groupId, String channelId, State state) {
        groups.getOrDefault(leader.getIpAddress(), List.of()).forEach(slave -> {
            if (slave.ip.equals(leader.getIpAddress())) {
                return;
            }
            GroupParticipant participant = participants.get(slave.ip);
            if (participant != null) {
                participant.groupProxyUpdateState(groupId, channelId, state);
            }
        });
    }

    public List<Slave> getGroup(GroupParticipant member) {
        return groups.getOrDefault(member.getIpAddress(), List.of());
    }

    public interface GroupParticipant {
        void addedToGroup(GroupParticipant leader);

        void removedFromGroup(GroupParticipant leader);

        void groupParticipantsUpdated(Collection<GroupParticipant> participants);

        String getIpAddress();

        void groupProxyUpdateState(String groupId, String channelId, State state);

        LinkPlayHTTPClient getApiClient();

        String getGroupParticipantLabel();
    }
}
