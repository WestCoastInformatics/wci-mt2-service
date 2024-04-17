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
 * Enum specifying what type of workflow a map project uses.
 */
public enum WorkflowType {

  /** Simplest possible workflow, single user edits with no review. */
  SIMPLE_PATH("Simple Workflow Path"),

  /** Specialist work compared with existing record, possible lead review. */
  LEGACY_PATH("Legacy Workflow Path"),

  /** Two specialists map, lead reviews conflicts. */
  CONFLICT_PROJECT("Conflict Project"),

  /** One specialist maps, lead reviews result. */
  REVIEW_PROJECT("Review Project"),

  /**
   * Two specialists map, two leads review (with first being conflict review if
   * applicable).
   */
  CONFLICT_AND_REVIEW_PATH("Conflict and Review Path"),

  /**
   * Specialist checks pre-populated map. If they agree, FINISH map as-is and it
   * goes straight to READY_FOR_PUBLICATION. If disagree, add a MapNote before
   * FINISHing, and it will go to REVIEW
   */
  CONDITIONAL_REVIEW_PATH("Conditional Review Path");

  /** The display name. */
  private String displayName = null;

  /**
   * Instantiates a {@link WorkflowType} from the specified parameters.
   *
   * @param displayName the display name
   */
  private WorkflowType(final String displayName) {
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
