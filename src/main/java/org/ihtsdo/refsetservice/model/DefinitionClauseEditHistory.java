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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

/**
 * Represents the ECL definition clauses for an intensional refset.
 */
@Entity
@Table(name = "definition_clauses_history")
public class DefinitionClauseEditHistory extends AbstractHasModified {

  /** The value. */
  @Column(nullable = false, length = 4000)
  private String value;

  /** The negated. */
  @Column(nullable = false)
  private boolean negated = false;

  /**
   * Instantiates an empty {@link DefinitionClauseEditHistory}.
   */
  public DefinitionClauseEditHistory() {

    // n/a
  }

  /**
   * Instantiates a {@link DefinitionClauseEditHistory} from the specified
   * parameters.
   *
   * @param other the other
   */
  public DefinitionClauseEditHistory(final DefinitionClauseEditHistory other) {

    populateFrom(other);
  }

  /**
   * Instantiates a {@link DefinitionClauseEditHistory} from the specified
   * parameters.
   *
   * @param other the other
   */
  public DefinitionClauseEditHistory(final DefinitionClause other) {

    populateFrom(other);
  }

  /**
   * Instantiates a {@link DefinitionClauseEditHistory} from the specified
   * parameters.
   *
   * @param value the value
   * @param negated the negated
   */
  public DefinitionClauseEditHistory(final String value, final boolean negated) {

    this.value = value;
    this.negated = negated;
  }

  /**
   * Populate from.
   *
   * @param other the other
   */
  public void populateFrom(final DefinitionClauseEditHistory other) {

    super.populateFrom(other);
    value = other.getValue();
    negated = other.getNegated();
  }

  /**
   * Populate from.
   *
   * @param other the other
   */
  public void populateFrom(final DefinitionClause other) {

    super.populateFrom(other);
    value = other.getValue();
    negated = other.getNegated();
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
   * Sets the value.
   *
   * @param value the value
   */
  public void setValue(final String value) {

    this.value = value;
  }

  /**
   * Returns the negated.
   *
   * @return the negated
   */
  public boolean getNegated() {

    return negated;
  }

  /**
   * Sets the negated.
   *
   * @param negated the negated
   */
  public void setNegated(final boolean negated) {

    this.negated = negated;
  }

  /**
   * Equals.
   *
   * @param obj the obj
   * @return true, if successful
   */
  @Override
  public boolean equals(final Object obj) {

    if (this == obj) {
      return true;
    }

    if (obj == null) {
      return false;
    }

    if (getClass() != obj.getClass()) {
      return false;
    }

    final DefinitionClauseEditHistory other = (DefinitionClauseEditHistory) obj;

    if (value == null) {
      if (other.value != null) {
        return false;
      }
    } else if (!value.equals(other.value)) {
      return false;
    }

    if (negated != other.negated) {
      return false;
    }

    return true;
  }

  /**
   * Hash code.
   *
   * @return the int
   */
  @Override
  public int hashCode() {

    final int prime = 31;
    int result = 1;
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    result = prime * result + (negated ? 1 : 0);

    return result;
  }

  /* see superclass */
  @Override
  public void lazyInit() {
    // n/a

  }
}
