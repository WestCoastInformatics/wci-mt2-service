/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
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
  private static final List<UserRole> ALL_ROLES = new ArrayList<>();

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

    if (ALL_ROLES.isEmpty()) {

      ALL_ROLES.add(ADMIN);
      ALL_ROLES.add(AUTHOR);
      ALL_ROLES.add(REVIEWER);
      ALL_ROLES.add(VIEWER);
    }
    return ALL_ROLES;
  }

  /**
   * Returns the role string.
   *
   * @param userRole the user role
   * @return the UserRole as a string
   */
  public static String getRoleString(final UserRole userRole) {

    return (userRole != null) ? userRole.getValue() : null;

  }
}
