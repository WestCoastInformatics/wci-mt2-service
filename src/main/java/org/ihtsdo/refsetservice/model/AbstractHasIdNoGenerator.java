
package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;

import org.ihtsdo.refsetservice.util.ModelUtility;

/**
 * Abstractly represents something that has an id.
 */
@MappedSuperclass
public abstract class AbstractHasIdNoGenerator implements HasId {

    /** The id. */
    @Id
    @Column(updatable = false, nullable = false, length = 64)
    private String id;

    /**
     * Instantiates an empty {@link AbstractHasIdNoGenerator}.
     */
    protected AbstractHasIdNoGenerator() {
        // n/a
    }

    /**
     * Instantiates a {@link AbstractHasIdNoGenerator} from the specified
     * parameters.
     *
     * @param other the other
     */
    protected AbstractHasIdNoGenerator(final HasId other) {
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
     * behavior is correct. Moving it or removing it causes either other
     * problems or the field to not be indexed.
     * @return the id
     */
    @Override
    // @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    // @SortableField
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    /* see superclass */
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
        final AbstractHasIdNoGenerator other = (AbstractHasIdNoGenerator) obj;
        if (id == null) {
            if (other.id != null) {
                return false;
            }
        } else if (!id.equals(other.id)) {
            return false;
        }
        return true;
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
