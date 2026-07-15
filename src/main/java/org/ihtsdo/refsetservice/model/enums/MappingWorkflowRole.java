/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.enums;

import java.util.Arrays;
import java.util.List;

import org.ihtsdo.refsetservice.helpers.MapUserRole;

/**
 * Per-mapping workflow roles used as permutation-file keys. Distinct from {@link MapUserRole}, which stores project
 * membership; {@code ADMIN} maps to {@link MapUserRole#ADMINISTRATOR}.
 */
public enum MappingWorkflowRole {

    /** Specialist role. */
    SPECIALIST,

    /** Lead role. */
    LEAD,

    /** Admin role. */
    ADMIN;

    /**
     * From string.
     *
     * @param text the text
     * @return the mapping workflow role
     */
    public static MappingWorkflowRole fromString(final String text) {

        for (final MappingWorkflowRole role : MappingWorkflowRole.values()) {
            if (role.toString().equalsIgnoreCase(text)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown MappingWorkflowRole: " + text);
    }

    /**
     * Map a {@link MapUserRole} to its workflow role, or null if it grants no workflow role.
     *
     * @param mapUserRole the map user role
     * @return the mapping workflow role, or null
     */
    public static MappingWorkflowRole fromMapUserRole(final MapUserRole mapUserRole) {

        if (mapUserRole == null) {
            return null;
        }

        switch (mapUserRole) {
            case SPECIALIST:
                return SPECIALIST;
            case LEAD:
                return LEAD;
            case ADMINISTRATOR:
                return ADMIN;
            default:
                return null;
        }
    }

    /**
     * Returns the values.
     *
     * @return the values
     */
    public static List<MappingWorkflowRole> getValues() {

        return Arrays.asList(MappingWorkflowRole.values());
    }
}
