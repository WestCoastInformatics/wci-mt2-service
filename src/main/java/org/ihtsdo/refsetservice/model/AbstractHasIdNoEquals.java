
package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.ihtsdo.refsetservice.util.ModelUtility;

/**
 * Abstract JPA and JAXB-enabled implementation of HasId.
 */
@MappedSuperclass
public abstract class AbstractHasIdNoEquals implements HasId {

  /** The id. */
  @Id
  @GeneratedValue(generator = "uuid")
  @GenericGenerator(name = "uuid", strategy = "uuid2")
  @Column(updatable = false, nullable = false, length = 64)
  private String id;

  /**
   * Instantiates an empty {@link AbstractHasIdNoEquals}.
   */
  protected AbstractHasIdNoEquals() {

    // n/a
  }

  /**
   * Instantiates a {@link AbstractHasIdNoEquals} from the specified parameters.
   *
   * @param other the other
   */
  protected AbstractHasIdNoEquals(final HasId other) {

    populateFrom(other);
  }

  /**
   * Populate from.
   *
   * @param other the other
   */
  public void populateFrom(final HasId other) {

    this.id = other.getId();
  }

  /**
   * Returns the id. NOTE: this causes a hibernate warning, but the background
   * behavior is correct. Moving it or removing it causes either other problems
   * or the field to not be indexed.
   * @return the id
   */
  @Override
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
  public String getId() {

    return id;
  }

  /**
   * Sets the id.
   *
   * @param id the id
   */
  @Override
  public void setId(final String id) {

    this.id = id;
  }

  /* see superclass */
  @Override
  public String toString() {

    try {
      return ModelUtility.toJson(this);
    } catch (final Exception e) {
      return e.getMessage();
    }
  }

}
