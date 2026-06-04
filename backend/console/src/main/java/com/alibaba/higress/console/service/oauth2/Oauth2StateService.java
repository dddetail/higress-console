/*
 * Copyright (c) 2022-2026 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.alibaba.higress.console.service.oauth2;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Higress
 */
@Slf4j
@Service
public class Oauth2StateService {

    private static final long STATE_TTL_MINUTES = 10L;
    private static final int CLEANER_POOL_SIZE = 1;

    private final ConcurrentHashMap<String, StateEntry> stateStore = new ConcurrentHashMap<>();

    private final ScheduledExecutorService cleaner =
        new ScheduledThreadPoolExecutor(CLEANER_POOL_SIZE,
            runnable -> {
                Thread thread = new Thread(runnable, "oauth2-state-cleaner");
                thread.setDaemon(true);
                return thread;
            });

    public Oauth2StateService() {
        cleaner.scheduleAtFixedRate(this::cleanExpired, 1L, 1L, TimeUnit.MINUTES);
    }

    public String generateState(String providerKey) {
        String state = UUID.randomUUID().toString().replace("-", "");
        stateStore.put(state, new StateEntry(System.currentTimeMillis(), providerKey));
        return state;
    }

    public boolean validateAndConsumeState(String state) {
        if (state == null || state.isEmpty()) {
            return false;
        }
        StateEntry entry = stateStore.remove(state);
        if (entry == null) {
            return false;
        }
        return System.currentTimeMillis() - entry.timestamp < STATE_TTL_MINUTES * 60L * 1000L;
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        stateStore.entrySet().removeIf(e -> now - e.getValue().timestamp > STATE_TTL_MINUTES * 60L * 1000L);
    }

    private static class StateEntry {
        final long timestamp;
        final String providerKey;

        StateEntry(long timestamp, String providerKey) {
            this.timestamp = timestamp;
            this.providerKey = providerKey;
        }
    }
}
