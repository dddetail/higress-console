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
import javax.validation.Valid;
import javax.validation.ValidationException;
import javax.validation.constraints.NotBlank;

import org.apache.commons.lang3.StringUtils;
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
import com.alibaba.higress.console.controller.dto.ConsumerCreateRequest;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.controller.util.ControllerUtil;
import com.alibaba.higress.console.model.ConsumerDetail;
import com.alibaba.higress.console.service.ConsumerInfoService;
import com.alibaba.higress.sdk.model.consumer.Consumer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController("ConsumersController")
@RequestMapping("/v1/consumers")
@Validated
@Tag(name = "Consumer APIs")
@RequirePermission(resource = "consumer", action = "read")
public class ConsumersController {

    private ConsumerInfoService consumerInfoService;

    @Resource
    public void setConsumerInfoService(ConsumerInfoService consumerInfoService) {
        this.consumerInfoService = consumerInfoService;
    }

    @GetMapping
    @Operation(summary = "List consumers with management info and members")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Consumers listed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<List<ConsumerDetail>>> list() {
        List<ConsumerDetail> consumers = consumerInfoService.listConsumers();
        return ControllerUtil.buildResponseEntity(consumers);
    }

    @PostMapping
    @RequirePermission(resource = "consumer", action = "write")
    @Operation(summary = "Add a consumer with management info")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Consumer added successfully"),
        @ApiResponse(responseCode = "400", description = "Consumer data is not valid"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<ConsumerDetail>> add(@Valid @RequestBody ConsumerCreateRequest request) {
        if (StringUtils.isBlank(request.getName())) {
            throw new ValidationException("name cannot be blank.");
        }
        if (StringUtils.isBlank(request.getNameCn())) {
            throw new ValidationException("nameCn cannot be blank.");
        }
        if (StringUtils.isBlank(request.getNameEn())) {
            throw new ValidationException("nameEn cannot be blank.");
        }
        if (StringUtils.isBlank(request.getShortName())) {
            throw new ValidationException("shortName cannot be blank.");
        }
        ConsumerDetail detail = consumerInfoService.createConsumer(request.getName(), request.getCredentials(),
            request.getNameCn(), request.getNameEn(), request.getShortName(), request.getDescription());
        return ControllerUtil.buildResponseEntity(detail);
    }

    @GetMapping(value = "/{name}")
    @Operation(summary = "Get consumer by name with management info and members")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Consumer found"),
        @ApiResponse(responseCode = "404", description = "Consumer not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<ConsumerDetail>> query(@PathVariable("name") @NotBlank String name) {
        ConsumerDetail detail = consumerInfoService.getConsumer(name);
        return ControllerUtil.buildResponseEntity(detail);
    }

    @PutMapping("/{name}")
    @RequirePermission(resource = "consumer", action = "write")
    @Operation(summary = "Update a consumer with management info")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Consumer updated successfully"),
        @ApiResponse(responseCode = "400", description = "Consumer data is not valid"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<ConsumerDetail>> put(@PathVariable("name") @NotBlank String name,
        @Valid @RequestBody ConsumerCreateRequest request) {
        if (StringUtils.isEmpty(request.getName())) {
            request.setName(name);
        } else if (!StringUtils.equals(name, request.getName())) {
            throw new ValidationException("Consumer name in the URL doesn't match the one in the body.");
        }
        ConsumerDetail detail = consumerInfoService.updateConsumer(name, request.getCredentials(),
            request.getNameCn(), request.getNameEn(), request.getShortName(), request.getDescription());
        return ControllerUtil.buildResponseEntity(detail);
    }

    @DeleteMapping("/{name}")
    @RequirePermission(resource = "consumer", action = "write")
    @Operation(summary = "Delete a consumer with management info and members")
    @ApiResponses(value = {@ApiResponse(responseCode = "204", description = "Consumer deleted successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<Consumer>> delete(@PathVariable("name") @NotBlank String name) {
        consumerInfoService.deleteConsumer(name);
        return ResponseEntity.noContent().build();
    }

    // ===== Member management =====

    @GetMapping("/{name}/members")
    @Operation(summary = "List members of a consumer group")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Members listed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<List<String>>> listMembers(@PathVariable("name") @NotBlank String name) {
        List<String> members = consumerInfoService.listMembers(name);
        return ControllerUtil.buildResponseEntity(members);
    }

    @PostMapping("/{name}/members")
    @RequirePermission(resource = "consumer", action = "write")
    @Operation(summary = "Add members to a consumer group")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Members added successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> addMembers(@PathVariable("name") @NotBlank String name,
        @RequestBody List<String> usernames) {
        consumerInfoService.addMembers(name, usernames);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    @DeleteMapping("/{name}/members/{username}")
    @RequirePermission(resource = "consumer", action = "write")
    @Operation(summary = "Remove a member from a consumer group")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Member removed successfully"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> removeMember(@PathVariable("name") @NotBlank String name,
        @PathVariable("username") @NotBlank String username) {
        consumerInfoService.removeMember(name, username);
        return ControllerUtil.buildSuccessResponseEntity();
    }
}
