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
package org.openhab.binding.qolsysiq.internal.client.dto.events;

import java.util.List;

import org.openhab.binding.qolsysiq.internal.client.dto.models.Partition;

/**
 *
 * @author Dan Cunningham - Initial contribution
 */
public class SummaryInfoEvent extends InfoEvent {
    public List<Partition> partitionList;

    public SummaryInfoEvent() {
        super(InfoEventType.SUMMARY);
    }
}
