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
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.higress.console.controller.dto.ChangePasswordRequest;
import com.alibaba.higress.console.controller.dto.Response;
import com.alibaba.higress.console.controller.dto.UserStatusRequest;
import com.alibaba.higress.console.controller.util.ControllerUtil;
import com.alibaba.higress.console.model.User;
import com.alibaba.higress.console.service.SessionService;
import com.alibaba.higress.console.service.SessionUserHelper;
import com.alibaba.higress.console.service.UserService;
import com.alibaba.higress.sdk.exception.ValidationException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author CH3CHO
 */
@RestController("UserController")
@RequestMapping("/user")
@Tag(name = "User APIs")
public class UserController {

    private SessionService sessionService;

    @Resource
    private UserService userService;

    @Resource
    public void setSessionService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/info")
    @Operation(summary = "Get user info")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User info retrieved successfully."),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<User>> getUserInfo() {
        User user = SessionUserHelper.getCurrentUser();
        return ControllerUtil.buildResponseEntity(user);
    }

    @PostMapping("/changePassword")
    @Operation(summary = "Change password")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Password changed successfully."),
        @ApiResponse(responseCode = "404", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> logout(@RequestBody ChangePasswordRequest request, HttpServletResponse response) {
        if (StringUtils.isEmpty(request.getOldPassword())) {
            throw new ValidationException("Missing old password.");
        }
        if (StringUtils.isEmpty(request.getNewPassword())) {
            throw new ValidationException("Missing new password.");
        }
        User user = SessionUserHelper.getCurrentUser();
        sessionService.changePassword(user.getName(), request.getOldPassword(), request.getNewPassword());
        sessionService.clearSession(response);
        return ControllerUtil.buildSuccessResponseEntity();
    }

    @GetMapping("/list")
    @Operation(summary = "List all users")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Users listed successfully."),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<List<User>>> listUsers() {
        return ResponseEntity.ok(Response.success(userService.listUsers()));
    }

    @GetMapping("/{username}")
    @Operation(summary = "Get user by username")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User found."),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<User>> getUser(@PathVariable String username) {
        return ControllerUtil.buildResponseEntity(userService.getUser(username));
    }

    @PutMapping("/{username}/status")
    @Operation(summary = "Update user status")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User status updated."),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<Response<User>> updateUserStatus(@PathVariable String username,
        @RequestBody UserStatusRequest request) {
        return ControllerUtil.buildResponseEntity(
            userService.updateUserStatus(username, request.getStatus()));
    }

    @DeleteMapping("/{username}")
    @Operation(summary = "Delete user")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "User deleted."),
        @ApiResponse(responseCode = "404", description = "User not found"),
        @ApiResponse(responseCode = "500", description = "Internal server error")})
    public ResponseEntity<?> deleteUser(@PathVariable String username) {
        userService.deleteUser(username);
        return ControllerUtil.buildSuccessResponseEntity();
    }
}
