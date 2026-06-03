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

import java.util.List;

import com.alibaba.higress.console.repository.entity.ConsumerGroupApiGrantEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupMemberEntity;

public interface ConsumerGroupService {

    List<ConsumerGroupEntity> listGroups();

    ConsumerGroupEntity getGroup(Long id);

    ConsumerGroupEntity createGroup(ConsumerGroupEntity entity);

    ConsumerGroupEntity updateGroup(ConsumerGroupEntity entity);

    void deleteGroup(Long id);

    List<ConsumerGroupMemberEntity> listMembers(Long groupId);

    void addMembers(Long groupId, List<String> usernames);

    void removeMember(Long groupId, String username);

    List<ConsumerGroupApiGrantEntity> listGrants(Long groupId);

    void updateGrants(Long groupId, List<ConsumerGroupApiGrantEntity> grants);
}
