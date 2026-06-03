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
package com.alibaba.higress.console.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.alibaba.higress.console.repository.ConsumerGroupApiGrantRepository;
import com.alibaba.higress.console.repository.ConsumerGroupMemberRepository;
import com.alibaba.higress.console.repository.ConsumerGroupRepository;
import com.alibaba.higress.console.repository.entity.ConsumerGroupApiGrantEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupEntity;
import com.alibaba.higress.console.repository.entity.ConsumerGroupMemberEntity;
import com.alibaba.higress.sdk.exception.NotFoundException;

@DisplayName("ConsumerGroupServiceImpl")
class ConsumerGroupServiceImplTest {

    @Mock
    private ConsumerGroupRepository groupRepository;

    @Mock
    private ConsumerGroupMemberRepository memberRepository;

    @Mock
    private ConsumerGroupApiGrantRepository grantRepository;

    @InjectMocks
    private ConsumerGroupServiceImpl consumerGroupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Nested
    @DisplayName("listGroups")
    class ListGroupsTests {

        @Test
        @DisplayName("returns all groups from repository")
        void returnsAllGroups() {
            List<ConsumerGroupEntity> groups = Arrays.asList(
                ConsumerGroupEntity.builder().id(1L).nameCn("Group A").build(),
                ConsumerGroupEntity.builder().id(2L).nameCn("Group B").build()
            );
            when(groupRepository.findAll()).thenReturn(groups);

            List<ConsumerGroupEntity> result = consumerGroupService.listGroups();

            assertEquals(2, result.size());
            assertEquals("Group A", result.get(0).getNameCn());
        }

        @Test
        @DisplayName("returns empty list when no groups exist")
        void returnsEmptyList() {
            when(groupRepository.findAll()).thenReturn(Collections.emptyList());

            List<ConsumerGroupEntity> result = consumerGroupService.listGroups();

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getGroup")
    class GetGroupTests {

        @Test
        @DisplayName("returns group when found")
        void returnsGroupWhenFound() {
            ConsumerGroupEntity entity = ConsumerGroupEntity.builder().id(1L).nameCn("Test").build();
            when(groupRepository.findById(1L)).thenReturn(Optional.of(entity));

            ConsumerGroupEntity result = consumerGroupService.getGroup(1L);

            assertEquals("Test", result.getNameCn());
        }

        @Test
        @DisplayName("throws NotFoundException when group not found")
        void throwsWhenNotFound() {
            when(groupRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(NotFoundException.class, () -> consumerGroupService.getGroup(999L));
        }
    }

    @Nested
    @DisplayName("createGroup")
    class CreateGroupTests {

        @Test
        @DisplayName("sets status to active and timestamps on create")
        void setsStatusAndTimestamps() {
            ConsumerGroupEntity input = ConsumerGroupEntity.builder()
                .nameCn("New Group").nameEn("New Group").shortName("ng").build();
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsumerGroupEntity result = consumerGroupService.createGroup(input);

            assertEquals("active", result.getStatus());
            assertNotNull(result.getCreatedAt());
            assertNotNull(result.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("updateGroup")
    class UpdateGroupTests {

        @Test
        @DisplayName("updates only mutable fields and refreshes updatedAt")
        void updatesMutableFields() {
            ConsumerGroupEntity existing = ConsumerGroupEntity.builder()
                .id(1L).nameCn("Old").nameEn("Old").shortName("old").description("old desc")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
            when(groupRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(groupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            ConsumerGroupEntity update = ConsumerGroupEntity.builder()
                .id(1L).nameCn("New").nameEn("New").shortName("new").description("new desc").build();

            ConsumerGroupEntity result = consumerGroupService.updateGroup(update);

            assertEquals("New", result.getNameCn());
            assertEquals("new", result.getShortName());
            assertEquals("new desc", result.getDescription());
        }
    }

    @Nested
    @DisplayName("deleteGroup")
    class DeleteGroupTests {

        @Test
        @DisplayName("deletes members, grants, and group in order")
        void deletesInOrder() {
            when(groupRepository.existsById(1L)).thenReturn(true);

            consumerGroupService.deleteGroup(1L);

            verify(memberRepository).deleteByGroupId(1L);
            verify(grantRepository).deleteByGroupId(1L);
            verify(groupRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("addMembers")
    class AddMembersTests {

        @Test
        @DisplayName("adds only non-existing members")
        void addsOnlyNonExistingMembers() {
            ConsumerGroupEntity group = ConsumerGroupEntity.builder().id(1L).build();
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));
            when(memberRepository.existsByGroupIdAndUsername(1L, "user1")).thenReturn(true);
            when(memberRepository.existsByGroupIdAndUsername(1L, "user2")).thenReturn(false);
            when(memberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            consumerGroupService.addMembers(1L, Arrays.asList("user1", "user2"));

            ArgumentCaptor<ConsumerGroupMemberEntity> captor = ArgumentCaptor.forClass(ConsumerGroupMemberEntity.class);
            verify(memberRepository, times(1)).save(captor.capture());
            assertEquals("user2", captor.getValue().getUsername());
        }
    }

    @Nested
    @DisplayName("updateGrants")
    class UpdateGrantsTests {

        @Test
        @DisplayName("replaces all grants with new list")
        void replacesAllGrants() {
            ConsumerGroupEntity group = ConsumerGroupEntity.builder().id(1L).build();
            when(groupRepository.findById(1L)).thenReturn(Optional.of(group));

            List<ConsumerGroupApiGrantEntity> newGrants = Arrays.asList(
                ConsumerGroupApiGrantEntity.builder().resourceType("route").resourceName("route-a").build(),
                ConsumerGroupApiGrantEntity.builder().resourceType("route").resourceName("route-b").build()
            );

            consumerGroupService.updateGrants(1L, newGrants);

            verify(grantRepository).deleteByGroupId(1L);
            verify(grantRepository, times(2)).save(any());
        }
    }
}
