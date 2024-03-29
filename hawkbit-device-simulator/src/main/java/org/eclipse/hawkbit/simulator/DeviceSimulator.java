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

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;

/**
 * The main-method to start the Spring-Boot application.
 *
 */
@SpringBootApplication
@EnableScheduling
public class DeviceSimulator {

    public DeviceSimulator() {
        // utility class
    }

    /**
     * @return central ScheduledExecutorService
     */
    @Bean
    ScheduledExecutorService threadPool() {
        return Executors.newScheduledThreadPool(4);
    }

    @Bean
    TaskScheduler taskScheduler() {
        return new ConcurrentTaskScheduler(threadPool());
    }

    /**
     * Start the Spring Boot Application.
     *
     * @param args
     *            the args
     */
    // Exception squid:S2095 - Spring boot standard behavior
    @SuppressWarnings({ "squid:S2095" })
    public static void main(final String[] args) {
        SpringApplication.run(DeviceSimulator.class, args);
    }
}
