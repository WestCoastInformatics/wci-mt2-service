
package org.ihtsdo.refsetservice.model;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import jakarta.xml.bind.DatatypeConverter;

import org.hibernate.type.SerializationException;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.usertype.UserType;

/**
 * Custom data type for postgres.
 */
public class JsonBType implements UserType<String> {

    /**
     * Gets the sql type.
     *
     * @return the sql type
     */
    /* see superclass */
    @Override
    public int getSqlType() {

        return Types.OTHER;
    }

    /**
     * Returned class.
     *
     * @return the class
     */
    /* see superclass */
    @Override
    public Class<String> returnedClass() {

        return String.class;
    }

    /**
     * Null safe get.
     *
     * @param rs the rs
     * @param position the position
     * @param options the options
     * @return the string
     * @throws SQLException the SQL exception
     */
    /* see superclass */
    @Override
    public String nullSafeGet(final ResultSet rs, final int position, final WrapperOptions options) throws SQLException {

        final Object value = rs.getObject(position);
        if (value instanceof String) {
            final String s = value.toString();
            try {
                final byte[] bytes = DatatypeConverter.parseHexBinary(s.substring(14));
                return new String(bytes, "UTF-8");
            } catch (final UnsupportedEncodingException e) {
                return s;
            }
        }

        return null;
    }

    /**
     * Null safe set.
     *
     * @param st the st
     * @param value the value
     * @param index the index
     * @param options the options
     * @throws SQLException the SQL exception
     */
    /* see superclass */
    @Override
    public void nullSafeSet(final PreparedStatement st, final String value, final int index, final WrapperOptions options) throws SQLException {

        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value, Types.OTHER);
        }
    }

    /**
     * Deep copy.
     *
     * @param originalValue the original value
     * @return the string
     */
    /* see superclass */
    @Override
    public String deepCopy(final String originalValue) {

        if (originalValue == null) {
            return null;
        }
        return new String(originalValue);
    }

    /**
     * Checks if is mutable.
     *
     * @return true, if is mutable
     */
    /* see superclass */
    @Override
    public boolean isMutable() {

        return true;
    }

    /**
     * Disassemble.
     *
     * @param value the value
     * @return the serializable
     */
    /* see superclass */
    @Override
    public Serializable disassemble(final String value) {

        final String copy = deepCopy(value);

        if (copy instanceof Serializable) {
            return copy;
        }

        throw new SerializationException(String.format("Cannot serialize '%s', %s is not Serializable.", value, value.getClass()), null);
    }

    /**
     * Assemble.
     *
     * @param cached the cached
     * @param owner the owner
     * @return the string
     */
    /* see superclass */
    @Override
    public String assemble(final Serializable cached, final Object owner) {

        return deepCopy((String) cached);
    }

    /**
     * Replace.
     *
     * @param original the original
     * @param target the target
     * @param owner the owner
     * @return the string
     */
    /* see superclass */
    @Override
    public String replace(final String original, final String target, final Object owner) {

        return deepCopy(original);
    }

    /**
     * Hash code.
     *
     * @param x the x
     * @return the int
     */
    /* see superclass */
    @Override
    public int hashCode(final String x) {

        if (x == null) {
            return 0;
        }

        return x.hashCode();
    }

    /**
     * Equals.
     *
     * @param x the x
     * @param y the y
     * @return true, if successful
     */
    /* see superclass */
    @Override
    public boolean equals(final String x, final String y) {

        if (x == null && y == null) {
            return true;
        } else if (x != null) {
            return x.equals(y);
        } else {
            return false;
        }
    }

}
