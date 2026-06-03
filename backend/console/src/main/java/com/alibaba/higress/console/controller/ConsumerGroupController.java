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
package com.alibaba.higress.console.controller;

import java.util.List;

import javax.annotation.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.higress.console.aop.RequirePermission;
import com.alibaba.higress.console.controller.dto.ConsumerGroupRequest;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.controller.util.ControllerUtil;
import com.alibaba.higress.console.repository.entity.ConsumerGroupApiGrantEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupMemberEntity;
import com.alibaba.higress.console.service.ConsumerGroupService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/v1/consumer-groups")
@Validated
@Tag(name = "Consumer Group APIs")
public class ConsumerGroupController {

    @Resource
    private ConsumerGroupService consumerGroupService;

    @GetMapping
    @Operation(summary = "List consumer groups")
    @RequirePermission(resource = "consumer_group", action = "read")
    public ResponseEntity<Response<List<ConsumerGroupEntity>>> list() {
        return ControllerUtil.buildResponseEntity(consumerGroupService.listGroups());
    }

    @PostMapping
    @Operation(summary = "Create a consumer group")
    @RequirePermission(resource = "consumer_group", action = "write")
    public ResponseEntity<Response<ConsumerGroupEntity>> create(@RequestBody ConsumerGroupRequest request) {
        ConsumerGroupEntity entity = ConsumerGroupEntity.builder()
            .nameCn(request.getNameCn())
            .nameEn(request.getNameEn())
            .shortName(request.getShortName())
            .description(request.getDescription())
            .build();
        return ControllerUtil.buildResponseEntity(consumerGroupService.createGroup(entity));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get consumer group detail")
    @RequirePermission(resource = "consumer_group", action = "read")
    public ResponseEntity<Response<ConsumerGroupEntity>> get(@PathVariable Long id) {
        return ControllerUtil.buildResponseEntity(consumerGroupService.getGroup(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a consumer group")
    @RequirePermission(resource = "consumer_group", action = "write")
    public ResponseEntity<Response<ConsumerGroupEntity>> update(@PathVariable Long id,
        @RequestBody ConsumerGroupRequest request) {
        ConsumerGroupEntity entity = consumerGroupService.getGroup(id);
        entity.setNameCn(request.getNameCn());
        entity.setNameEn(request.getNameEn());
        entity.setShortName(request.getShortName());
        entity.setDescription(request.getDescription());
        return ControllerUtil.buildResponseEntity(consumerGroupService.updateGroup(entity));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a consumer group")
    @RequirePermission(resource = "consumer_group", action = "write")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        consumerGroupService.deleteGroup(id);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    // ===== Member management =====

    @GetMapping("/{id}/members")
    @Operation(summary = "List consumer group members")
    @RequirePermission(resource = "consumer_group", action = "read")
    public ResponseEntity<Response<List<ConsumerGroupMemberEntity>>> listMembers(@PathVariable Long id) {
        return ControllerUtil.buildResponseEntity(consumerGroupService.listMembers(id));
    }

    @PostMapping("/{id}/members")
    @Operation(summary = "Add members to consumer group")
    @RequirePermission(resource = "consumer_group", action = "write")
    public ResponseEntity<?> addMembers(@PathVariable Long id, @RequestBody List<String> usernames) {
        consumerGroupService.addMembers(id, usernames);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    @DeleteMapping("/{id}/members/{username}")
    @Operation(summary = "Remove a member from consumer group")
    @RequirePermission(resource = "consumer_group", action = "write")
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable String username) {
        consumerGroupService.removeMember(id, username);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    // ===== API grant management =====

    @GetMapping("/{id}/grants")
    @Operation(summary = "List API grants of consumer group")
    @RequirePermission(resource = "consumer_group", action = "read")
    public ResponseEntity<Response<List<ConsumerGroupApiGrantEntity>>> listGrants(@PathVariable Long id) {
        return ControllerUtil.buildResponseEntity(consumerGroupService.listGrants(id));
    }

    @PutMapping("/{id}/grants")
    @Operation(summary = "Update API grants of consumer group")
    @RequirePermission(resource = "consumer_group", action = "write")
    public ResponseEntity<?> updateGrants(@PathVariable Long id,
        @RequestBody List<ConsumerGroupApiGrantEntity> grants) {
        consumerGroupService.updateGrants(id, grants);
        return ControllerUtil.buildSuccessResponseEntity();
    }
}
