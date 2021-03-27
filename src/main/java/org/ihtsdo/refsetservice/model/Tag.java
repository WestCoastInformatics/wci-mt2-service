
package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.Indexed;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * JAXB-enabled implementation of Tag.
 */
@Schema(description = "Key/value pair")
@Entity
@Table(name = "tags")
@Indexed
public class Tag extends AbstractHasModified {

    /** The key. */
    @Column(nullable = false, length = 256)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String key;

    /** The value. */
    @Column(nullable = false, length = 256)
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    private String value;

    /**
     * Instantiates an empty {@link Tag}.
     */
    public Tag() {
        // n/a
    }

    /**
     * Instantiates a {@link Tag} from the specified parameters.
     *
     * @param other the other
     */
    public Tag(final Tag other) {
        populateFrom(other);
    }

    /**
     * Instantiates a {@link Tag} from the specified parameters.
     *
     * @param key the key
     * @param value the value
     */
    public Tag(final String key, final String value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Tag other) {
        super.populateFrom(other);
        key = other.getKey();
        value = other.getValue();
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the key.
     *
     * @param key the key
     */
    public void setKey(final String key) {
        this.key = key;
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
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((key == null) ? 0 : key.hashCode());
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

        final Tag other = (Tag) obj;

        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
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

    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
