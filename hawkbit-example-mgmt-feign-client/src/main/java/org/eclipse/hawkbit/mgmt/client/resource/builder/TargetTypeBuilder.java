/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.mgmt.client.resource.builder;

import org.eclipse.hawkbit.mgmt.json.model.distributionsettype.MgmtDistributionSetTypeAssignment;
import org.eclipse.hawkbit.mgmt.json.model.distributionsettype.MgmtDistributionSetTypeRequestBodyPost;
import org.eclipse.hawkbit.mgmt.json.model.targettype.MgmtTargetTypeRequestBodyPost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 
 * Builder pattern for building {@link MgmtTargetTypeRequestBodyPost}.
 *
 */
public class TargetTypeBuilder {

    private String name;
    private String description;
    private String color;
    private final List<MgmtDistributionSetTypeAssignment> compatibledistributionsettypes = new ArrayList<>();

    /**
     * @param name
     *            the name of the target type
     * @return the builder itself
     */
    public TargetTypeBuilder name(final String name) {
        this.name = name;
        return this;
    }

    /**
     * @param color
     *            the color of the target type
     * @return the builder itself
     */
    public TargetTypeBuilder color(final String color) {
        this.color = color;
        return this;
    }

    /**
     * @param description
     *            the description
     * @return the builder itself
     */
    public TargetTypeBuilder description(final String description) {
        this.description = description;
        return this;
    }

    /**
     * @param distributionSetTypeIds
     *            the IDs of the distribution set types which should be compatible
     *            for the target type
     * @return the builder itself
     */
    public TargetTypeBuilder compatibleDsSetTypes(final Long... distributionSetTypeIds) {
        for (final Long id : distributionSetTypeIds) {
            final MgmtDistributionSetTypeAssignment distributionSetTypeAssignment = new MgmtDistributionSetTypeAssignment();
            distributionSetTypeAssignment.setId(id);
            this.compatibledistributionsettypes.add(distributionSetTypeAssignment);
        }
        return this;
    }

    /**
     * Builds a list with a single entry of
     * {@link MgmtTargetTypeRequestBodyPost} which can directly be used
     * in the RESTful-API.
     * 
     * @return a single entry list of
     *         {@link MgmtDistributionSetTypeRequestBodyPost}
     */
    public List<MgmtTargetTypeRequestBodyPost> build() {
        return Collections.singletonList(doBuild(""));
    }

    /**
     * Builds a list of multiple {@link MgmtTargetTypeRequestBodyPost}
     * to create multiple target types at once. An increasing number
     * will be added to the name of the target type. The
     * compatible dsSet types will remain the same.
     * 
     * @param count
     *            the amount of target type body which should be
     *            created
     * @return a list of {@link MgmtTargetTypeRequestBodyPost}
     */
    public List<MgmtTargetTypeRequestBodyPost> buildAsList(final int count) {
        final List<MgmtTargetTypeRequestBodyPost> bodyList = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            bodyList.add(doBuild(String.valueOf(index)));
        }
        return bodyList;

    }

    private MgmtTargetTypeRequestBodyPost doBuild(final String suffix) {
        final MgmtTargetTypeRequestBodyPost body = new MgmtTargetTypeRequestBodyPost();
        body.setName(name + suffix);
        body.setColour(color);
        body.setDescription(description);
        body.setCompatibleDsTypes(compatibledistributionsettypes);
        return body;
    }

}
