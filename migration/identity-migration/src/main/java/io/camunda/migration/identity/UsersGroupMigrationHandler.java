/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.UserGroups;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.MappingEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UsersGroupMigrationHandler implements MigrationHandler {

  private static final Logger LOG = LoggerFactory.getLogger(UsersGroupMigrationHandler.class);
  private static final String USERNAME_CLAIM = "sub";

  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;
  private final GroupServices groupServices;
  private final MappingServices mappingServices;

  public UsersGroupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer,
      final GroupServices groupServices,
      final MappingServices mappingServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
    this.groupServices = groupServices;
    this.mappingServices = mappingServices;
  }

  @Override
  public void migrate() {
    List<UserGroups> userGroups;
    do {
      userGroups = managementIdentityClient.fetchUserGroups(SIZE);
      managementIdentityClient.updateMigrationStatus(
          userGroups.stream().map(this::createGroupUser).toList());
    } while (!userGroups.isEmpty());
  }

  private MigrationStatusUpdateRequest createGroupUser(final UserGroups userGroups) {

    try {
      final var mapping =
          new MappingDTO(USERNAME_CLAIM, userGroups.username(), userGroups.username() + "_mapping");

      final var mappingKey =
          mappingServices
              .findMapping(mapping)
              .map(MappingEntity::mappingKey)
              .orElseGet(() -> mappingServices.createMapping(mapping).join().getMappingKey());
      for (final Group userGroup : userGroups.groups()) {
        final var groupKey = groupServices.getGroupByName(userGroup.name()).groupKey();
        assignMemberToGroup(groupKey, mappingKey);
      }
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userGroups, null);
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(userGroups, e);
    }
  }

  private void assignMemberToGroup(final long groupKey, final long mappingKey) {
    try {
      groupServices.assignMember(groupKey, mappingKey, EntityType.MAPPING).join();
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw e;
      }
    }
  }
}
