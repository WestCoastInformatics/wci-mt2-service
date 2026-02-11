
package org.ihtsdo.refsetservice.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;

/**
 * Abstractly represents something that changes over time.
 */
@MappedSuperclass
public abstract class AbstractHasModifiedNoGenerator extends AbstractHasIdNoGenerator
    implements HasModified {

  /** The modified. */
  @Column(nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date modified;

  /** The created. */
  @Column(nullable = false)
  @Temporal(TemporalType.TIMESTAMP)
  private Date created;

  /** The modified by. */
  @Column(nullable = false, length = 256)
  private String modifiedBy;

  /** The local. */
  @Column(nullable = false)
  private boolean local = false;

  /** The active. */
  private boolean active = true;

  /**
   * Instantiates an empty {@link AbstractHasModifiedNoGenerator}.
   */
  protected AbstractHasModifiedNoGenerator() {

    super();
  }

  /**
   * Instantiates a {@link AbstractHasModifiedNoGenerator} from the specified
   * parameters.
   *
   * @param other the other
   */
  protected AbstractHasModifiedNoGenerator(final HasModified other) {

    populateFrom(other);
  }

  /**
   * Populate from.
   *
   * @param other the other
   */
  public void populateFrom(final HasModified other) {

    // Only copy this stuff if the object has an id
    if (other.getId() != null) {
      super.populateFrom(other);
      created = other.getCreated();
      modified = other.getModified();
      modifiedBy = other.getModifiedBy();
    }
    active = other.isActive();
  }

  /* see superclass */
  @Override
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
  public boolean isActive() {

    return active;
  }

  /* see superclass */
  @Override
  public void setActive(final Boolean active) {

    this.active = active;
  }

  /* see superclass */
  @Override
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
  // @DateBridge(resolution = Resolution.DAY, encoding = EncodingType.STRING)
  public Date getModified() {

    return modified;
  }

  /* see superclass */
  @Override
  public void setModified(final Date modified) {

    this.modified = modified;
  }

  /* see superclass */
  @Override
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
  // @DateBridge(resolution = Resolution.DAY, encoding = EncodingType.STRING)
  public Date getCreated() {

    return created;
  }

  /* see superclass */
  @Override
  public void setCreated(final Date created) {

    this.created = created;
  }

  /* see superclass */
  @Override
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
  public String getModifiedBy() {

    return modifiedBy;
  }

  /* see superclass */
  @Override
  public void setModifiedBy(final String modifiedBy) {

    this.modifiedBy = modifiedBy;
  }

  /**
   * Clear tracking fields.
   */
  @Override
  public void clearTrackingFields() {

    setId(null);
    created = null;
    modified = null;
    modifiedBy = null;
    active = true;
  }
  // equals/hashcode by superclass
}
