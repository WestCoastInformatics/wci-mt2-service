/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Arrays;
import java.util.List;

/**
 * The Enum UserRole.
 *
 */
public enum UserRole {

    /** The viewer. */
    VIEWER("Viewer"),

    /** The author. */
    AUTHOR("Author"),

    /** The reviewer. */
    REVIEWER("Reviewer"),

    /** The administrator. */
    ADMIN("Admin");

    /** The value. */
    private String value;

    /** Enums as list. */
    private static final List<UserRole> ALL_ROLES = Arrays.asList(UserRole.values());

    /**
     * Instantiates a {@link UserRole} from the specified parameters.
     *
     * @param value the value
     */
    private UserRole(final String value) {

        this.value = value;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public String getValue() {

        return value;
    }

    /**
     * Returns the all roles.
     *
     * @return the all roles
     */
    public static List<UserRole> getAllRoles() {

        return ALL_ROLES;
    }

    // /**
    // * Checks for privileges of.
    // *
    // * @param role the role
    // * @return true, if successful
    // */
    // public boolean hasPrivilegesOf(UserRole role) {
    // if (this == UserRole.VIEWER && role == UserRole.VIEWER)
    // return true;
    // else if (this == UserRole.AUTHOR && (role == UserRole.VIEWER || role == UserRole.AUTHOR))
    // return true;
    // else if (this == UserRole.REVIEWER && (role == UserRole.VIEWER || role == UserRole.USER
    // || role == UserRole.AUTHOR || role == UserRole.REVIEWER))
    // return true;
    // else if (this == UserRole.USER && (role == UserRole.VIEWER || role == UserRole.USER || role == UserRole.AUTHOR))
    // return true;
    // else if (this == UserRole.LEAD && (role == UserRole.VIEWER || role == UserRole.USER || role == UserRole.AUTHOR
    // || role == UserRole.REVIEWER || role == UserRole.LEAD))
    // return true;
    // else if (this == UserRole.ADMIN)
    // return true;
    // else
    // return false;
    // }
}
