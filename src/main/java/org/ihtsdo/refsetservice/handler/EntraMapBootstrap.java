/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowRole;
import org.ihtsdo.refsetservice.util.PropertyUtility;

/**
 * Shared helpers for Entra mapping-role bootstrap lists (admin / spec / lead).
 */
public final class EntraMapBootstrap {

    /** Bootstrap role granted to admin list members. */
    public static final String ROLE_ADMIN = "all-all-all-admin";

    /** Bootstrap role granted to spec list members. */
    public static final String ROLE_SPEC = "all-all-all-specialist";

    /** Legacy spec role string. */
    public static final String ROLE_SPEC_LEGACY = "all-all-all-spec";

    /** Bootstrap role granted to lead list members. */
    public static final String ROLE_LEAD = "all-all-all-lead";

    private EntraMapBootstrap() {

    }

    /**
     * Splits a configured user list on commas and semicolons.
     *
     * @param list raw property value
     * @return trimmed non-blank entries
     */
    public static List<String> splitConfiguredList(final String list) {

        if (StringUtils.isBlank(list)) {
            return Collections.emptyList();
        }

        final String[] parts = list.split("[,;]");
        final List<String> result = new ArrayList<>(parts.length);
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Map session {@code User.roles} strings to mapping workflow roles.
     *
     * @param userRoles the session user roles
     * @return workflow roles; empty if none match
     */
    public static List<MappingWorkflowRole> toWorkflowRoles(final Set<String> userRoles) {

        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }

        final List<MappingWorkflowRole> roles = new ArrayList<>();
        if (hasSpecialistRole(userRoles)) {
            roles.add(MappingWorkflowRole.SPECIALIST);
        }
        if (userRoles.contains(ROLE_LEAD)) {
            roles.add(MappingWorkflowRole.LEAD);
        }
        if (userRoles.contains(ROLE_ADMIN)) {
            roles.add(MappingWorkflowRole.ADMIN);
        }
        return roles;
    }

    /**
     * Map session {@code User.roles} strings to a {@link MapUserRole}. Highest wins: admin, then lead, then spec.
     *
     * @param userRoles the session user roles
     * @return the map user role
     */
    public static MapUserRole toMapUserRole(final Set<String> userRoles) {

        if (userRoles == null || userRoles.isEmpty()) {
            return MapUserRole.VIEWER;
        }
        if (userRoles.contains(ROLE_ADMIN)) {
            return MapUserRole.ADMINISTRATOR;
        }
        if (userRoles.contains(ROLE_LEAD)) {
            return MapUserRole.LEAD;
        }
        if (hasSpecialistRole(userRoles)) {
            return MapUserRole.SPECIALIST;
        }
        return MapUserRole.VIEWER;
    }

    /**
     * User names from {@code ENTRAID_ADMIN_USERS} ({@code all-all-all-admin}).
     *
     * @return configured admin user names
     */
    public static List<String> adminUserNames() {

        return configuredUserNames("security.handler.ENTRAID.users.admin", "ENTRAID_ADMIN_USERS");
    }

    /**
     * User names from {@code ENTRAID_LEAD_USERS} ({@code all-all-all-lead}).
     *
     * @return configured lead user names
     */
    public static List<String> leadUserNames() {

        return configuredUserNames("security.handler.ENTRAID.users.lead", "ENTRAID_LEAD_USERS");
    }

    /**
     * User names from {@code ENTRAID_SPEC_USERS} ({@code all-all-all-specialist}).
     *
     * @return configured specialist user names
     */
    public static List<String> specialistUserNames() {

        return configuredUserNames("security.handler.ENTRAID.users.spec", "ENTRAID_SPEC_USERS");
    }

    /**
     * Whether the user name is in a configured Entra list (case-insensitive).
     *
     * @param configured the configured names
     * @param userName the user name
     * @return true if present
     */
    public static boolean listContainsUser(final List<String> configured, final String userName) {

        if (userName == null || configured == null) {
            return false;
        }
        for (final String entry : configured) {
            if (userName.equalsIgnoreCase(entry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether session roles include specialist.
     *
     * @param userRoles the session roles
     * @return true if specialist
     */
    private static boolean hasSpecialistRole(final Set<String> userRoles) {

        return userRoles.contains(ROLE_SPEC) || userRoles.contains(ROLE_SPEC_LEGACY);
    }

    /**
     * Read a configured user list from properties.
     *
     * @param primaryKey spring property key
     * @param fallbackKey env-style fallback key
     * @return trimmed names
     */
    private static List<String> configuredUserNames(final String primaryKey, final String fallbackKey) {

        String value = PropertyUtility.getProperty(primaryKey);
        if (StringUtils.isBlank(value)) {
            value = PropertyUtility.getProperty(fallbackKey);
        }
        return splitConfiguredList(value);
    }
}
