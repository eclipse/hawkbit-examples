/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.simulator;

import org.eclipse.hawkbit.dmf.json.model.DmfUpdateMode;
import org.eclipse.hawkbit.simulator.amqp.DmfSenderService;

/**
 * A simulated device using the DMF API of the hawkBit update server.
 */
public class DMFSimulatedDevice extends AbstractSimulatedDevice {
    private final DmfSenderService spSenderService;

    /**
     * @param id
     *            the ID of the device
     * @param tenant
     *            the tenant of the simulated device
     */
    public DMFSimulatedDevice(final String id, final String tenant, final DmfSenderService spSenderService,
            final int pollDelaySec) {
        super(id, tenant, Protocol.DMF_AMQP, pollDelaySec);
        this.spSenderService = spSenderService;
    }

    @Override
    public void poll() {
        spSenderService.createOrUpdateThing(super.getTenant(), super.getId());
    }

    @Override
    public void updateAttribute(final String mode, final String key, final String value) {

        final DmfUpdateMode updateMode;

        switch (mode.toLowerCase()) {

            case "replace" :
                updateMode = DmfUpdateMode.REPLACE;
                break;
            case "remove" :
                updateMode = DmfUpdateMode.REMOVE;
                break;
            case "merge" :
            default :
                updateMode = DmfUpdateMode.MERGE;
                break;
        }

        spSenderService.updateAttributesOfThing(super.getTenant(), super.getId(), updateMode, key, value);
    }

}
