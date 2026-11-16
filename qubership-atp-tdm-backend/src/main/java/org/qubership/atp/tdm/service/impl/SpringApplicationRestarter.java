/*
 *  Copyright 2024-2025 NetCracker Technology Corporation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.qubership.atp.tdm.service.impl;

import org.qubership.atp.tdm.service.ApplicationRestarter;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Closes the Spring context and exits the JVM.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SpringApplicationRestarter implements ApplicationRestarter {

    private static final long DELAY_MILLIS = 1500;

    private final ApplicationContext context;

    @Override
    public void restartSoon() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            log.info("Exiting so that the platform restarts the service and the approved database import runs.");
            System.exit(SpringApplication.exit(context, () -> 0));
        }, "database-transfer-restart");
        thread.setDaemon(false);
        thread.start();
    }
}
