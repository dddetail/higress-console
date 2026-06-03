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
package com.alibaba.higress.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.ConsumerGroupMemberEntity;

public interface ConsumerGroupMemberRepository extends JpaRepository<ConsumerGroupMemberEntity, Long> {
    List<ConsumerGroupMemberEntity> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);
    void deleteByGroupIdAndUsername(Long groupId, String username);
    boolean existsByGroupIdAndUsername(Long groupId, String username);
    List<ConsumerGroupMemberEntity> findByUsername(String username);
}
