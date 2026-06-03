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
package com.alibaba.higress.console.model;

import java.util.List;

import com.alibaba.higress.sdk.model.consumer.Credential;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API response DTO merging gateway Consumer + Console ConsumerInfo + member list.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerDetail {

    // From SDK Consumer (gateway policy layer)
    private String name;
    private List<Credential> credentials;

    // From ConsumerInfo (console management layer)
    private String nameCn;
    private String nameEn;
    private String shortName;
    private String description;
    private String infoStatus;

    // Member list (usernames belonging to this consumer group)
    private List<String> members;
}
