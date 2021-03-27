
package org.ihtsdo.refsetservice.model;

import javax.persistence.Column;
import javax.persistence.Table;

import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.Field;
import org.hibernate.search.annotations.Index;
import org.hibernate.search.annotations.SortableField;
import org.hibernate.search.annotations.Store;
import org.ihtsdo.refsetservice.util.ModelUtility;

/**
 * JPA enabled scored implementation of {@link TypeKeyValue}.
 */
@Table(name = "type_key_values")
// @Audited
// @Indexed
public class TypeKeyValue extends AbstractHasModified implements Comparable<TypeKeyValue> {

    /** The type. */
    @Column(nullable = false, length = 1000)
    private String type;

    /** The key. */
    @Column(name = "keyField", nullable = false, length = 4000)
    private String key;

    /** The value. */
    @Column(nullable = true, length = 4000)
    private String value;

    /**
     * Instantiates an empty {@link TypeKeyValue}.
     */
    public TypeKeyValue() {
        // do nothing
    }

    /**
     * Instantiates a {@link TypeKeyValue} from the specified parameters.
     *
     * @param typeKeyValue the type key value
     */
    public TypeKeyValue(final TypeKeyValue typeKeyValue) {
        populateFrom(typeKeyValue);
    }

    /**
     * Populate from.
     *
     * @param typeKeyValue the type key value
     */
    public void populateFrom(final TypeKeyValue typeKeyValue) {
        super.populateFrom(typeKeyValue);
        type = typeKeyValue.getType();
        key = typeKeyValue.getKey();
        value = typeKeyValue.getValue();
    }

    /**
     * Instantiates a {@link TypeKeyValue} from the specified parameters.
     *
     * @param type the type
     * @param key the key
     * @param value the value
     */
    public TypeKeyValue(final String type, final String key, final String value) {
        this.type = type;
        this.value = value;
        this.key = key;
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
    public String getType() {
        return type;
    }

    /**
     * Sets the type.
     *
     * @param type the type
     */
    public void setType(final String type) {
        this.type = type;
    }

    /**
     * Returns the key.
     *
     * @return the key
     */
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
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
    @Field(index = Index.YES, analyze = Analyze.NO, store = Store.NO)
    @SortableField
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
     * Compare to.
     *
     * @param tkv the tkv
     * @return the int
     */
    @Override
    public int compareTo(final TypeKeyValue tkv) {
        int i = type.compareTo(tkv.getType());
        if (i == 0) {
            i = key.compareTo(tkv.getKey());
            if (i == 0) {
                i = value.compareTo(tkv.getValue());
            }
        }
        return i;
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
        result = prime * result + ((type == null) ? 0 : type.hashCode());
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
        final TypeKeyValue other = (TypeKeyValue) obj;
        if (key == null) {
            if (other.key != null) {
                return false;
            }
        } else if (!key.equals(other.key)) {
            return false;
        }
        if (type == null) {
            if (other.type != null) {
                return false;
            }
        } else if (!type.equals(other.type)) {
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

    /* see superclass */
    @Override
    public void lazyInit() {
        // n/a
    }

    /**
     * Get the string representation of the object.
     *
     * @return the string
     */
    @Override
    public String toString() {
        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }

}
