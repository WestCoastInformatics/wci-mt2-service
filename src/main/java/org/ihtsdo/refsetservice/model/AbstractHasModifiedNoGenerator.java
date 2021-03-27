
package org.ihtsdo.refsetservice.model;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.DateBridge;
import org.hibernate.search.annotations.EncodingType;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.FieldBridge;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Resolution;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;
import org.hibernate.search.bridge.builtin.BooleanBridge;

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
    @FieldBridge(impl = BooleanBridge.class)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    public boolean isActive() {
        return active;
    }

    /* see superclass */
    @Override
    public void setActive(final boolean active) {
        this.active = active;
    }

    /* see superclass */
    @Override
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @DateBridge(resolution = Resolution.DAY, encoding = EncodingType.STRING)
    @SortableField
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
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @DateBridge(resolution = Resolution.DAY, encoding = EncodingType.STRING)
    @SortableField
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
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
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
