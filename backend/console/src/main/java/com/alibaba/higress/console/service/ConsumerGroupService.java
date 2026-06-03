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
