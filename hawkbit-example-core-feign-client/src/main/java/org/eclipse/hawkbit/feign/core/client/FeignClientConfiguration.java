/**
 * Copyright (c) 2015 Bosch Software Innovations GmbH and others
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.feign.core.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import feign.Contract;
import feign.Feign;

/**
 * Spring annotated java configuration class which defines necessary beans for
 * configure the feign-client.
 */
@Configuration
@ConditionalOnClass(Feign.class)
@Import(FeignClientsConfiguration.class)
public class FeignClientConfiguration {

    @Bean
    public ApplicationJsonRequestHeaderInterceptor jsonHeaderInterceptor() {
        return new ApplicationJsonRequestHeaderInterceptor();
    }

    @Bean
    public Contract feignContract() {
        return new IgnoreMultipleConsumersProducersSpringMvcContract();
    }
}
