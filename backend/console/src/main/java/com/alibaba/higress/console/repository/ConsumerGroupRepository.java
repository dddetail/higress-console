package com.alibaba.higress.console.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.alibaba.higress.console.repository.entity.ConsumerGroupEntity;

public interface ConsumerGroupRepository extends JpaRepository<ConsumerGroupEntity, Long> {
    List<ConsumerGroupEntity> findByStatus(String status);
}
