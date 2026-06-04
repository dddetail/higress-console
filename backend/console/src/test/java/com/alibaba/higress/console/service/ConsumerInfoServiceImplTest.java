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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.alibaba.higress.console.model.ConsumerDetail;
import com.alibaba.higress.console.repository.ConsumerInfoRepository;
import com.alibaba.higress.console.repository.ConsumerMemberRepository;
import com.alibaba.higress.console.repository.entity.ConsumerInfoEntity;
import com.alibaba.higress.console.repository.entity.ConsumerMemberEntity;
import com.alibaba.higress.sdk.exception.NotFoundException;
import com.alibaba.higress.sdk.model.CommonPageQuery;
import com.alibaba.higress.sdk.model.PaginatedResult;
import com.alibaba.higress.sdk.model.consumer.Consumer;
import com.alibaba.higress.sdk.model.consumer.Credential;
import com.alibaba.higress.sdk.model.consumer.KeyAuthCredential;
import com.alibaba.higress.sdk.service.consumer.ConsumerService;

/**
 * Tests for ConsumerInfoServiceImpl - the core service that bridges
 * gateway Consumer (WasmPlugin) with Console ConsumerInfo (DB) and ConsumerMember.
 */
@DisplayName("ConsumerInfoServiceImpl")
class ConsumerInfoServiceImplTest {

    @Mock
    private ConsumerInfoRepository consumerInfoRepository;

    @Mock
    private ConsumerMemberRepository consumerMemberRepository;

    @Mock
    private ConsumerService consumerService;

    @InjectMocks
    private ConsumerInfoServiceImpl consumerInfoService;

    private Consumer gatewayConsumer;
    private ConsumerInfoEntity consumerInfoEntity;
    private ConsumerMemberEntity memberEntity;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        KeyAuthCredential credential = new KeyAuthCredential();
        credential.setType("key-auth");
        credential.setSource("BEARER");
        credential.setValues(Collections.singletonList("test-token"));

        gatewayConsumer = Consumer.builder()
            .name("test-group")
            .credentials(Collections.singletonList(credential))
            .build();

        consumerInfoEntity = ConsumerInfoEntity.builder()
            .id(1L)
            .consumerName("test-group")
            .nameCn("Test Group CN")
            .nameEn("Test Group EN")
            .shortName("tg")
            .description("A test consumer group")
            .status("active")
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        memberEntity = ConsumerMemberEntity.builder()
            .id(1L)
            .consumerName("test-group")
            .username("user1")
            .createdAt(LocalDateTime.now())
            .build();
    }

    // ===== listConsumers =====

    @Nested
    @DisplayName("listConsumers()")
    class ListConsumersTests {

        @Test
        @DisplayName("returns empty list when no consumers exist")
        void returnsEmptyList() {
            when(consumerService.list(any(CommonPageQuery.class)))
                .thenReturn(PaginatedResult.createFromFullList(Collections.emptyList(), null));

            List<ConsumerDetail> result = consumerInfoService.listConsumers();

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("merges gateway consumer with console info and members via batch query")
        void mergesGatewayWithConsoleAndMembers() {
            when(consumerService.list(any(CommonPageQuery.class)))
                .thenReturn(PaginatedResult.createFromFullList(
                    Collections.singletonList(gatewayConsumer), null));
            // Batch query returns all info and members at once
            when(consumerInfoRepository.findByConsumerNameIn(Collections.singletonList("test-group")))
                .thenReturn(Collections.singletonList(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerNameIn(Collections.singletonList("test-group")))
                .thenReturn(Collections.singletonList(memberEntity));

            List<ConsumerDetail> result = consumerInfoService.listConsumers();

            assertEquals(1, result.size());
            ConsumerDetail detail = result.get(0);
            assertEquals("test-group", detail.getName());
            assertEquals("Test Group CN", detail.getNameCn());
            assertEquals("Test Group EN", detail.getNameEn());
            assertEquals("tg", detail.getShortName());
            assertEquals(1, detail.getMembers().size());
            assertEquals("user1", detail.getMembers().get(0));
        }

        @Test
        @DisplayName("returns consumer without console info when info not found")
        void returnsConsumerWithoutInfo() {
            when(consumerService.list(any(CommonPageQuery.class)))
                .thenReturn(PaginatedResult.createFromFullList(
                    Collections.singletonList(gatewayConsumer), null));
            when(consumerInfoRepository.findByConsumerNameIn(Collections.singletonList("test-group")))
                .thenReturn(Collections.emptyList());
            when(consumerMemberRepository.findByConsumerNameIn(Collections.singletonList("test-group")))
                .thenReturn(Collections.emptyList());

            List<ConsumerDetail> result = consumerInfoService.listConsumers();

            assertEquals(1, result.size());
            ConsumerDetail detail = result.get(0);
            assertEquals("test-group", detail.getName());
            // Console info fields should be null
            assertEquals(null, detail.getNameCn());
            assertEquals(null, detail.getShortName());
            assertTrue(detail.getMembers().isEmpty());
        }

        @Test
        @DisplayName("uses batch query instead of per-consumer queries")
        void usesBatchQueryNotPerConsumer() {
            when(consumerService.list(any(CommonPageQuery.class)))
                .thenReturn(PaginatedResult.createFromFullList(
                    Collections.singletonList(gatewayConsumer), null));
            when(consumerInfoRepository.findByConsumerNameIn(any()))
                .thenReturn(Collections.singletonList(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerNameIn(any()))
                .thenReturn(Collections.singletonList(memberEntity));

            consumerInfoService.listConsumers();

            // Verify batch methods were called
            verify(consumerInfoRepository).findByConsumerNameIn(any());
            verify(consumerMemberRepository).findByConsumerNameIn(any());
            // Verify per-consumer methods were NOT called for list
            verify(consumerInfoRepository, never()).findByConsumerName(anyString());
            verify(consumerMemberRepository, never()).findByConsumerName(anyString());
        }

        @Test
        @DisplayName("correctly merges multiple consumers with mixed info and members")
        void mergesMultipleConsumers() {
            Consumer consumer2 = Consumer.builder()
                .name("group-2")
                .credentials(Collections.emptyList())
                .build();
            ConsumerInfoEntity info2 = ConsumerInfoEntity.builder()
                .consumerName("group-2")
                .nameCn("Group 2 CN")
                .nameEn("Group 2 EN")
                .shortName("g2")
                .description("Second group")
                .status("active")
                .build();
            ConsumerMemberEntity member2 = ConsumerMemberEntity.builder()
                .consumerName("group-2")
                .username("user2")
                .build();

            when(consumerService.list(any(CommonPageQuery.class)))
                .thenReturn(PaginatedResult.createFromFullList(
                    Arrays.asList(gatewayConsumer, consumer2), null));
            when(consumerInfoRepository.findByConsumerNameIn(any()))
                .thenReturn(Arrays.asList(consumerInfoEntity, info2));
            when(consumerMemberRepository.findByConsumerNameIn(any()))
                .thenReturn(Arrays.asList(memberEntity, member2));

            List<ConsumerDetail> result = consumerInfoService.listConsumers();

            assertEquals(2, result.size());
            // First consumer
            assertEquals("test-group", result.get(0).getName());
            assertEquals("Test Group CN", result.get(0).getNameCn());
            assertEquals(1, result.get(0).getMembers().size());
            assertEquals("user1", result.get(0).getMembers().get(0));
            // Second consumer
            assertEquals("group-2", result.get(1).getName());
            assertEquals("Group 2 CN", result.get(1).getNameCn());
            assertEquals(1, result.get(1).getMembers().size());
            assertEquals("user2", result.get(1).getMembers().get(0));
        }

        @Test
        @DisplayName("handles multiple members per consumer correctly")
        void handlesMultipleMembersPerConsumer() {
            ConsumerMemberEntity member2 = ConsumerMemberEntity.builder()
                .consumerName("test-group")
                .username("user2")
                .build();

            when(consumerService.list(any(CommonPageQuery.class)))
                .thenReturn(PaginatedResult.createFromFullList(
                    Collections.singletonList(gatewayConsumer), null));
            when(consumerInfoRepository.findByConsumerNameIn(any()))
                .thenReturn(Collections.singletonList(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerNameIn(any()))
                .thenReturn(Arrays.asList(memberEntity, member2));

            List<ConsumerDetail> result = consumerInfoService.listConsumers();

            assertEquals(1, result.size());
            assertEquals(2, result.get(0).getMembers().size());
            assertTrue(result.get(0).getMembers().contains("user1"));
            assertTrue(result.get(0).getMembers().contains("user2"));
        }
    }

    // ===== getConsumer =====

    @Nested
    @DisplayName("getConsumer()")
    class GetConsumerTests {

        @Test
        @DisplayName("returns detail for existing consumer")
        void returnsDetail() {
            when(consumerService.query("test-group")).thenReturn(gatewayConsumer);
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.of(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerName("test-group"))
                .thenReturn(Collections.singletonList(memberEntity));

            ConsumerDetail detail = consumerInfoService.getConsumer("test-group");

            assertNotNull(detail);
            assertEquals("test-group", detail.getName());
            assertEquals("Test Group CN", detail.getNameCn());
            assertEquals("user1", detail.getMembers().get(0));
        }

        @Test
        @DisplayName("throws NotFoundException for non-existent consumer")
        void throwsNotFound() {
            when(consumerService.query("nonexistent")).thenReturn(null);

            assertThrows(NotFoundException.class,
                () -> consumerInfoService.getConsumer("nonexistent"));
        }
    }

    // ===== createConsumer =====

    @Nested
    @DisplayName("createConsumer()")
    class CreateConsumerTests {

        @Test
        @DisplayName("creates gateway consumer and console info")
        void createsGatewayAndConsole() {
            when(consumerService.addOrUpdate(any(Consumer.class))).thenReturn(gatewayConsumer);
            when(consumerInfoRepository.save(any(ConsumerInfoEntity.class)))
                .thenReturn(consumerInfoEntity);
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.of(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerName("test-group"))
                .thenReturn(Collections.emptyList());

            ConsumerDetail detail = consumerInfoService.createConsumer(
                "test-group", gatewayConsumer.getCredentials(),
                "Test Group CN", "Test Group EN", "tg", "A test consumer group");

            assertNotNull(detail);
            verify(consumerService).addOrUpdate(any(Consumer.class));
            verify(consumerInfoRepository).save(any(ConsumerInfoEntity.class));
        }

        @Test
        @DisplayName("sets status to active for new consumer info")
        void setsActiveStatus() {
            when(consumerService.addOrUpdate(any(Consumer.class))).thenReturn(gatewayConsumer);
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.of(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerName("test-group"))
                .thenReturn(Collections.emptyList());

            consumerInfoService.createConsumer(
                "test-group", gatewayConsumer.getCredentials(),
                "CN", "EN", "tg", "desc");

            verify(consumerInfoRepository).save(any(ConsumerInfoEntity.class));
        }
    }

    // ===== updateConsumer =====

    @Nested
    @DisplayName("updateConsumer()")
    class UpdateConsumerTests {

        @Test
        @DisplayName("updates both gateway and console data")
        void updatesGatewayAndConsole() {
            when(consumerService.query("test-group")).thenReturn(gatewayConsumer);
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.of(consumerInfoEntity));
            when(consumerMemberRepository.findByConsumerName("test-group"))
                .thenReturn(Collections.singletonList(memberEntity));

            consumerInfoService.updateConsumer("test-group",
                gatewayConsumer.getCredentials(),
                "Updated CN", "Updated EN", "tg2", "Updated desc");

            verify(consumerService).addOrUpdate(any(Consumer.class));
            verify(consumerInfoRepository).save(any(ConsumerInfoEntity.class));
        }

        @Test
        @DisplayName("throws NotFoundException when consumer not found in gateway")
        void throwsWhenGatewayNotFound() {
            when(consumerService.query("nonexistent")).thenReturn(null);

            assertThrows(NotFoundException.class,
                () -> consumerInfoService.updateConsumer("nonexistent",
                    gatewayConsumer.getCredentials(), "CN", "EN", "tg", "desc"));
        }

        @Test
        @DisplayName("throws NotFoundException when console info not found")
        void throwsWhenConsoleInfoNotFound() {
            when(consumerService.query("test-group")).thenReturn(gatewayConsumer);
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.empty());

            assertThrows(NotFoundException.class,
                () -> consumerInfoService.updateConsumer("test-group",
                    gatewayConsumer.getCredentials(), "CN", "EN", "tg", "desc"));
        }
    }

    // ===== deleteConsumer =====

    @Nested
    @DisplayName("deleteConsumer()")
    class DeleteConsumerTests {

        @Test
        @DisplayName("deletes console info, members, and gateway consumer")
        void deletesAllData() {
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.of(consumerInfoEntity));

            consumerInfoService.deleteConsumer("test-group");

            verify(consumerInfoRepository).delete(consumerInfoEntity);
            verify(consumerMemberRepository).deleteByConsumerName("test-group");
            verify(consumerService).delete("test-group");
        }

        @Test
        @DisplayName("deletes gateway consumer even when console info missing")
        void deletesGatewayWithoutConsoleInfo() {
            when(consumerInfoRepository.findByConsumerName("test-group"))
                .thenReturn(Optional.empty());

            consumerInfoService.deleteConsumer("test-group");

            verify(consumerInfoRepository, never()).delete(any());
            verify(consumerMemberRepository).deleteByConsumerName("test-group");
            verify(consumerService).delete("test-group");
        }
    }

    // ===== Member management =====

    @Nested
    @DisplayName("Member management")
    class MemberTests {

        @Test
        @DisplayName("listMembers returns usernames")
        void listMembersReturnsUsernames() {
            when(consumerService.query("test-group")).thenReturn(gatewayConsumer);
            when(consumerMemberRepository.findByConsumerName("test-group"))
                .thenReturn(Arrays.asList(
                    ConsumerMemberEntity.builder().consumerName("test-group").username("user1").build(),
                    ConsumerMemberEntity.builder().consumerName("test-group").username("user2").build()));

            List<String> members = consumerInfoService.listMembers("test-group");

            assertEquals(2, members.size());
            assertEquals("user1", members.get(0));
            assertEquals("user2", members.get(1));
        }

        @Test
        @DisplayName("addMembers skips existing members")
        void addMembersSkipsExisting() {
            when(consumerService.query("test-group")).thenReturn(gatewayConsumer);
            when(consumerMemberRepository.existsByConsumerNameAndUsername("test-group", "user1"))
                .thenReturn(true);
            when(consumerMemberRepository.existsByConsumerNameAndUsername("test-group", "user2"))
                .thenReturn(false);

            consumerInfoService.addMembers("test-group", Arrays.asList("user1", "user2"));

            verify(consumerMemberRepository, times(1)).save(any(ConsumerMemberEntity.class));
        }

        @Test
        @DisplayName("removeMember delegates to repository")
        void removeMemberDelegates() {
            when(consumerService.query("test-group")).thenReturn(gatewayConsumer);

            consumerInfoService.removeMember("test-group", "user1");

            verify(consumerMemberRepository).deleteByConsumerNameAndUsername("test-group", "user1");
        }

        @Test
        @DisplayName("addMembers throws when consumer not found")
        void addMembersThrowsWhenNotFound() {
            when(consumerService.query("nonexistent")).thenReturn(null);

            assertThrows(NotFoundException.class,
                () -> consumerInfoService.addMembers("nonexistent", Arrays.asList("user1")));
        }

        @Test
        @DisplayName("listMembers throws when consumer not found")
        void listMembersThrowsWhenNotFound() {
            when(consumerService.query("nonexistent")).thenReturn(null);

            assertThrows(NotFoundException.class,
                () -> consumerInfoService.listMembers("nonexistent"));
        }
    }
}
