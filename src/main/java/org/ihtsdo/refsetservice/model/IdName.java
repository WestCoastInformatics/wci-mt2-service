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

/**
 * The Class IdName. Use in other modules when collections of id and name are
 * required and not the full object.
 * 
 */
public class IdName {

  /** The id. */
  private String id;

  /** The name. */
  private String name;

  /**
   * Instantiates a {@link IdName} from the specified parameters.
   *
   * @param id the id
   * @param name the name
   */
  public IdName(final String id, final String name) {

    super();
    this.id = id;
    this.name = name;
  }

  /**
   * Returns the id.
   *
   * @return the id
   */
  public String getId() {

    return id;
  }

  /**
   * Sets the id.
   *
   * @param id the id to set
   */
  public void setId(final String id) {

    this.id = id;
  }

  /**
   * Returns the name.
   *
   * @return the name
   */
  public String getName() {

    return name;
  }

  /**
   * Sets the name.
   *
   * @param name the name to set
   */
  public void setName(final String name) {

    this.name = name;
  }

  /* see superclass */
  @Override
  public int hashCode() {

    final int prime = 31;
    int result = 1;
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    return result;
  }

  /* see superclass */
  @Override
  public boolean equals(final Object obj) {

    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IdName)) {
      return false;
    }
    IdName other = (IdName) obj;
    if (id == null) {
      if (other.id != null) {
        return false;
      }
    } else if (!id.equals(other.id)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }

  /* see superclass */
  @Override
  public String toString() {

    return "IdName [id=" + id + ", name=" + name + "]";
  }

}
