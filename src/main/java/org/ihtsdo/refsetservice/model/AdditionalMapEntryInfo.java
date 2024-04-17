package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The additional Map Entry Info.
 */
@Entity
@Table(name = "additional_map_entry_info")
@JsonIgnoreProperties(ignoreUnknown = true)
@Indexed
public class AdditionalMapEntryInfo extends AbstractHasId {

  /**
   * The name. This is a combination of field + "|" + value Although this is
   * duplication of information, it is needed for proper sorting and filtering
   * in the UI
   */
  @Column(nullable = false, length = 4000)
  private String name;

  /** The field. */
  @Column(nullable = false, length = 4000)
  private String field;

  /** The value. */
  @Column(nullable = false, length = 4000)
  private String value;

  /**
   * Default constructor.
   */
  public AdditionalMapEntryInfo() {

  }

  /**
   * Constructor.
   *
   * @param id the id
   * @param name the name
   * @param field the field
   * @param value the value
   */
  public AdditionalMapEntryInfo(final String id, final String name, final String field,
      final String value) {

    super();
    this.name = name;
    this.field = field;
    this.value = value;
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  public String getName() {

    return name;
  }

  /**
   * Sets the name.
   *
   * @param name the new name
   */
  public void setName(final String name) {

    this.name = name;
  }

  /**
   * Gets the field.
   *
   * @return the field
   */
  public String getField() {

    return field;
  }

  /**
   * Sets the field.
   *
   * @param field the new field
   */
  public void setField(final String field) {

    this.field = field;
  }

  /**
   * Gets the value.
   *
   * @return the value
   */
  public String getValue() {

    return value;
  }

  /**
   * Sets the value.
   *
   * @param value the new value
   */
  public void setValue(final String value) {

    this.value = value;
  }

  /**
   * To string.
   *
   * @return the string
   */
  @Override
  public String toString() {

    return "AdditionalMapEntryInfo [id=" + super.getId() + ", name=" + name + ", field=" + field
        + ", value=" + value + "]";
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
    result = prime * result + ((name == null) ? 0 : name.hashCode());
    result = prime * result + ((field == null) ? 0 : field.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
    return result;
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
    final AdditionalMapEntryInfo other = (AdditionalMapEntryInfo) obj;
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    if (field == null) {
      if (other.field != null) {
        return false;
      }
    } else if (!field.equals(other.field)) {
      return false;
    }
    if (value == null) {
      if (other.value != null) {
        return false;
      }
    } else if (!value.equals(other.value)) {
      return false;
    }
    return true;
  }

}
