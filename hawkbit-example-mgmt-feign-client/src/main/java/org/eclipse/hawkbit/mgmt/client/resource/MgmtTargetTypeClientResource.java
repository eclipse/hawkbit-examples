/**
 * Copyright (c) 2021 Bosch.IO GmbH and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.hawkbit.mgmt.client.resource;

import org.eclipse.hawkbit.mgmt.rest.api.MgmtTargetTypeRestApi;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * Client binding for the TargetType resource of the management API.
 */
@FeignClient(name = "MgmtTargetTypeClient", url = "${hawkbit.url:localhost:8080}")
public interface MgmtTargetTypeClientResource extends MgmtTargetTypeRestApi {
}
