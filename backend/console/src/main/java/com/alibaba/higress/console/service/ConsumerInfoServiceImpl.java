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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.higress.console.model.ConsumerDetail;
import com.alibaba.higress.console.repository.ConsumerInfoRepository;
import com.alibaba.higress.console.repository.ConsumerMemberRepository;
import com.alibaba.higress.console.repository.entity.ConsumerInfoEntity;
import com.alibaba.higress.console.repository.entity.ConsumerMemberEntity;
import com.alibaba.higress.sdk.exception.NotFoundException;
import com.alibaba.higress.sdk.model.CommonPageQuery;
import com.alibaba.higress.sdk.model.PaginatedResult;
import com.alibaba.higress.sdk.model.consumer.Consumer;
import com.alibaba.higress.sdk.model.consumer.Credential;
import com.alibaba.higress.sdk.service.consumer.ConsumerService;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class ConsumerInfoServiceImpl implements ConsumerInfoService {

    @Resource
    private ConsumerInfoRepository consumerInfoRepository;

    @Resource
    private ConsumerMemberRepository consumerMemberRepository;

    @Resource
    private ConsumerService consumerService;

    @Override
    public List<ConsumerDetail> listConsumers() {
        PaginatedResult<Consumer> gatewayConsumers = consumerService.list(new CommonPageQuery());
        List<Consumer> consumers = gatewayConsumers.getData();
        if (consumers == null) {
            consumers = new ArrayList<>();
        }

        List<ConsumerDetail> result = new ArrayList<>(consumers.size());
        for (Consumer consumer : consumers) {
            ConsumerDetail detail = buildConsumerDetail(consumer);
            result.add(detail);
        }
        return result;
    }

    @Override
    public ConsumerDetail getConsumer(String consumerName) {
        Consumer consumer = consumerService.query(consumerName);
        if (consumer == null) {
            throw new NotFoundException("Consumer not found: " + consumerName);
        }
        return buildConsumerDetail(consumer);
    }

    @Override
    @Transactional
    public ConsumerDetail createConsumer(String name, List<Credential> credentials,
        String nameCn, String nameEn, String shortName, String description) {
        // 1. Create gateway consumer
        Consumer consumer = Consumer.builder().name(name).credentials(credentials).build();
        consumer.validate(false);
        consumerService.addOrUpdate(consumer);

        // 2. Create console consumer info
        LocalDateTime now = LocalDateTime.now();
        ConsumerInfoEntity infoEntity = ConsumerInfoEntity.builder()
            .consumerName(name)
            .nameCn(nameCn)
            .nameEn(nameEn)
            .shortName(shortName)
            .description(description)
            .status("active")
            .createdAt(now)
            .updatedAt(now)
            .build();
        consumerInfoRepository.save(infoEntity);

        return buildConsumerDetail(name, credentials);
    }

    @Override
    @Transactional
    public ConsumerDetail updateConsumer(String consumerName, List<Credential> credentials,
        String nameCn, String nameEn, String shortName, String description) {
        // 1. Update gateway consumer if credentials changed
        Consumer existingConsumer = consumerService.query(consumerName);
        if (existingConsumer == null) {
            throw new NotFoundException("Consumer not found: " + consumerName);
        }
        if (credentials != null) {
            Consumer consumerToUpdate = Consumer.builder()
                .name(consumerName).credentials(credentials).build();
            consumerService.addOrUpdate(consumerToUpdate);
        }

        // 2. Update console consumer info
        ConsumerInfoEntity infoEntity = consumerInfoRepository.findByConsumerName(consumerName)
            .orElseThrow(() -> new NotFoundException("ConsumerInfo not found: " + consumerName));
        infoEntity.setNameCn(nameCn);
        infoEntity.setNameEn(nameEn);
        infoEntity.setShortName(shortName);
        infoEntity.setDescription(description);
        infoEntity.setUpdatedAt(LocalDateTime.now());
        consumerInfoRepository.save(infoEntity);

        return getConsumer(consumerName);
    }

    @Override
    @Transactional
    public void deleteConsumer(String consumerName) {
        // 1. Delete console consumer info + members
        consumerInfoRepository.findByConsumerName(consumerName)
            .ifPresent(consumerInfoRepository::delete);
        consumerMemberRepository.deleteByConsumerName(consumerName);

        // 2. Delete gateway consumer
        consumerService.delete(consumerName);
    }

    @Override
    public List<String> listMembers(String consumerName) {
        ensureConsumerExists(consumerName);
        return consumerMemberRepository.findByConsumerName(consumerName)
            .stream()
            .map(ConsumerMemberEntity::getUsername)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addMembers(String consumerName, List<String> usernames) {
        ensureConsumerExists(consumerName);
        LocalDateTime now = LocalDateTime.now();
        for (String username : usernames) {
            if (!consumerMemberRepository.existsByConsumerNameAndUsername(consumerName, username)) {
                consumerMemberRepository.save(ConsumerMemberEntity.builder()
                    .consumerName(consumerName)
                    .username(username)
                    .createdAt(now)
                    .build());
            }
        }
    }

    @Override
    public void removeMember(String consumerName, String username) {
        ensureConsumerExists(consumerName);
        consumerMemberRepository.deleteByConsumerNameAndUsername(consumerName, username);
    }

    private void ensureConsumerExists(String consumerName) {
        Consumer consumer = consumerService.query(consumerName);
        if (consumer == null) {
            throw new NotFoundException("Consumer not found: " + consumerName);
        }
    }

    private ConsumerDetail buildConsumerDetail(Consumer consumer) {
        return buildConsumerDetail(consumer.getName(), consumer.getCredentials());
    }

    /**
     * Build ConsumerDetail by merging gateway consumer data + console ConsumerInfo + members.
     */
    private ConsumerDetail buildConsumerDetail(String consumerName, List<Credential> credentials) {
        ConsumerDetail.ConsumerDetailBuilder builder = ConsumerDetail.builder()
            .name(consumerName)
            .credentials(credentials);

        // Query console management info
        consumerInfoRepository.findByConsumerName(consumerName).ifPresent(info -> {
            builder.nameCn(info.getNameCn())
                .nameEn(info.getNameEn())
                .shortName(info.getShortName())
                .description(info.getDescription())
                .infoStatus(info.getStatus());
        });

        // Query member list
        List<String> members = consumerMemberRepository.findByConsumerName(consumerName)
            .stream()
            .map(ConsumerMemberEntity::getUsername)
            .collect(Collectors.toList());
        builder.members(members);

        return builder.build();
    }
}
