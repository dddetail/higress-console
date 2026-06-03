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
package com.alibaba.higress.console.service;

import java.util.List;

import com.alibaba.higress.console.model.ConsumerDetail;
import com.alibaba.higress.sdk.model.consumer.Credential;

/**
 * Console management layer consumer info service.
 * Manages ConsumerInfo (DB metadata) and ConsumerMember (member relationships),
 * syncing with gateway layer Consumer via ConsumerService.
 */
public interface ConsumerInfoService {

    /**
     * List all consumers, merging SDK gateway data and console management data.
     */
    List<ConsumerDetail> listConsumers();

    /**
     * Get a single consumer detail (including members).
     */
    ConsumerDetail getConsumer(String consumerName);

    /**
     * Create a consumer (gateway Consumer + Console ConsumerInfo).
     */
    ConsumerDetail createConsumer(String name, List<Credential> credentials,
        String nameCn, String nameEn, String shortName, String description);

    /**
     * Update a consumer (gateway + console data).
     */
    ConsumerDetail updateConsumer(String consumerName, List<Credential> credentials,
        String nameCn, String nameEn, String shortName, String description);

    /**
     * Delete a consumer (gateway + console ConsumerInfo + members).
     */
    void deleteConsumer(String consumerName);

    /**
     * List consumer group members (usernames).
     */
    List<String> listMembers(String consumerName);

    /**
     * Add members to a consumer group.
     */
    void addMembers(String consumerName, List<String> usernames);

    /**
     * Remove a member from a consumer group.
     */
    void removeMember(String consumerName, String username);
}
