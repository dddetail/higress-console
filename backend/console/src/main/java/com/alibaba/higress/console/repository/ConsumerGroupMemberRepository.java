package com.alibaba.higress.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.ConsumerGroupMemberEntity;

public interface ConsumerGroupMemberRepository extends JpaRepository<ConsumerGroupMemberEntity, Long> {
    List<ConsumerGroupMemberEntity> findByGroupId(Long groupId);
    void deleteByGroupIdAndUsername(Long groupId, String username);
    boolean existsByGroupIdAndUsername(Long groupId, String username);
    List<ConsumerGroupMemberEntity> findByUsername(String username);
}
