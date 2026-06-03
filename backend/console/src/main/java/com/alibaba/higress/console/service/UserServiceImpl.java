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
import java.util.stream.Collectors;

import javax.annotation.Resource;

import org.springframework.stereotype.Service;

import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.repository.UserRepository;
import com.alibaba.higress.console.repository.entity.UserEntity;
import com.alibaba.higress.sdk.exception.NotFoundException;

/**
 * @author Higress
 */
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserRepository userRepository;

    @Override
    public List<User> listUsers() {
        return userRepository.findAll().stream()
            .map(this::toModel)
            .collect(Collectors.toList());
    }

    @Override
    public User getUser(String username) {
        return userRepository.findByUsername(username)
            .map(this::toModel)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
    }

    @Override
    public User updateUserStatus(String username, String status) {
        UserEntity entity = userRepository.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        entity.setStatus(status);
        userRepository.save(entity);
        return toModel(entity);
    }

    @Override
    public void deleteUser(String username) {
        UserEntity entity = userRepository.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        userRepository.delete(entity);
    }

    @Override
    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
            .map(this::toModel)
            .orElse(null);
    }

    @Override
    public User updateUserRole(String username, String role) {
        UserEntity entity = userRepository.findByUsername(username)
            .orElseThrow(() -> new NotFoundException("User not found: " + username));
        entity.setRole(role);
        userRepository.save(entity);
        return toModel(entity);
    }

    private User toModel(UserEntity entity) {
        return User.builder()
            .name(entity.getUsername())
            .displayName(entity.getDisplayName())
            .employeeId(entity.getEmployeeId())
            .type(entity.getType())
            .status(entity.getStatus())
            .role(entity.getRole())
            .build();
    }
}
