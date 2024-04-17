/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.helpers;

/**
 * Enum representing what type of relation style a map project uses.
 */
public enum RelationStyle {

  /** The map category style. */
  MAP_CATEGORY_STYLE("Map Category Style"),

  /** The relationship style. */
  RELATIONSHIP_STYLE("Relationship Style"),

  /** For simple maps. */
  NONE("No Relationships");

  /** The display name. */
  private String displayName = null;

  /**
   * Instantiates a {@link RelationStyle} from the specified parameters.
   *
   * @param displayName the display name
   */
  private RelationStyle(final String displayName) {
    this.displayName = displayName;
  }

  /**
   * Returns the display name.
   *
   * @return the display name
   */
  public String getDisplayName() {
    return displayName;
  }
}
