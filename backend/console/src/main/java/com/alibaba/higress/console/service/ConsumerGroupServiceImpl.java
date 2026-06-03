/*
 * Copyright (c) 2022-2023 Alibaba Group Holding Ltd.
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
import java.util.List;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.higress.console.repository.ConsumerGroupApiGrantRepository;
import com.alibaba.higress.console.repository.ConsumerGroupMemberRepository;
import com.alibaba.higress.console.repository.ConsumerGroupRepository;
import com.alibaba.higress.console.repository.entity.ConsumerGroupApiGrantEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupMemberEntity;
import com.alibaba.higress.sdk.exception.NotFoundException;

@Service
public class ConsumerGroupServiceImpl implements ConsumerGroupService {

    @Resource
    private ConsumerGroupRepository groupRepository;

    @Resource
    private ConsumerGroupMemberRepository memberRepository;

    @Resource
    private ConsumerGroupApiGrantRepository grantRepository;

    @Override
    public List<ConsumerGroupEntity> listGroups() {
        return groupRepository.findAll();
    }

    @Override
    public ConsumerGroupEntity getGroup(Long id) {
        return groupRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Consumer group not found: " + id));
    }

    @Override
    public ConsumerGroupEntity createGroup(ConsumerGroupEntity entity) {
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setStatus("active");
        return groupRepository.save(entity);
    }

    @Override
    public ConsumerGroupEntity updateGroup(ConsumerGroupEntity entity) {
        ConsumerGroupEntity existing = getGroup(entity.getId());
        existing.setNameCn(entity.getNameCn());
        existing.setNameEn(entity.getNameEn());
        existing.setShortName(entity.getShortName());
        existing.setDescription(entity.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        return groupRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteGroup(Long id) {
        memberRepository.deleteByGroupId(id);
        grantRepository.deleteByGroupId(id);
        groupRepository.deleteById(id);
    }

    @Override
    public List<ConsumerGroupMemberEntity> listMembers(Long groupId) {
        getGroup(groupId);
        return memberRepository.findByGroupId(groupId);
    }

    @Override
    @Transactional
    public void addMembers(Long groupId, List<String> usernames) {
        getGroup(groupId);
        LocalDateTime now = LocalDateTime.now();
        for (String username : usernames) {
            if (!memberRepository.existsByGroupIdAndUsername(groupId, username)) {
                memberRepository.save(ConsumerGroupMemberEntity.builder()
                    .groupId(groupId)
                    .username(username)
                    .createdAt(now)
                    .build());
            }
        }
    }

    @Override
    public void removeMember(Long groupId, String username) {
        getGroup(groupId);
        memberRepository.deleteByGroupIdAndUsername(groupId, username);
    }

    @Override
    public List<ConsumerGroupApiGrantEntity> listGrants(Long groupId) {
        getGroup(groupId);
        return grantRepository.findByGroupId(groupId);
    }

    @Override
    @Transactional
    public void updateGrants(Long groupId, List<ConsumerGroupApiGrantEntity> grants) {
        getGroup(groupId);
        grantRepository.deleteByGroupId(groupId);
        LocalDateTime now = LocalDateTime.now();
        for (ConsumerGroupApiGrantEntity grant : grants) {
            grant.setGroupId(groupId);
            grant.setCreatedAt(now);
            grantRepository.save(grant);
        }
    }
}
