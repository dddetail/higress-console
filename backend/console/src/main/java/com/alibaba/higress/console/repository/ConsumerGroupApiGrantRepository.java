package com.alibaba.higress.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.ConsumerGroupApiGrantEntity;

public interface ConsumerGroupApiGrantRepository extends JpaRepository<ConsumerGroupApiGrantEntity, Long> {
    List<ConsumerGroupApiGrantEntity> findByGroupId(Long groupId);
    void deleteByGroupId(Long groupId);
}
