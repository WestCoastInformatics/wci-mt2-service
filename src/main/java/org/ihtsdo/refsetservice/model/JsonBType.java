
package org.ihtsdo.refsetservice.model;

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import javax.xml.bind.DatatypeConverter;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.SerializationException;
import org.hibernate.usertype.UserType;

/**
 * Custom data type for postgres.
 */
public class JsonBType implements UserType {

    /* see superclass */
    @Override
    public void nullSafeSet(final PreparedStatement st, final Object value, final int index,
        final SharedSessionContractImplementor session) throws HibernateException, SQLException {

        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value, Types.OTHER);
        }
    }

    /* see superclass */
    @Override
    public Object deepCopy(final Object originalValue) throws HibernateException {
        if (originalValue == null) {
            return null;
        }
        return new String(originalValue.toString());
    }

    /* see superclass */
    @Override
    public Object nullSafeGet(final ResultSet rs, final String[] names,
        final SharedSessionContractImplementor session, final Object owner)
        throws HibernateException, SQLException {
        if (rs.getObject(names[0]) instanceof String) {
            final String s = rs.getObject(names[0]).toString();
            try {
                final byte[] bytes = DatatypeConverter.parseHexBinary(s.substring(14));
                return new String(bytes, "UTF-8");
            } catch (final UnsupportedEncodingException e) {
                return s;
            }
        }

        return null;

    }

    /* see superclass */
    @Override
    public Serializable disassemble(final Object value) throws HibernateException {
        final Object copy = deepCopy(value);

        if (copy instanceof Serializable) {
            return (Serializable) copy;
        }

        throw new SerializationException(String.format(
                "Cannot serialize '%s', %s is not Serializable.", value, value.getClass()), null);
    }

    /* see superclass */
    @Override
    public Object assemble(final Serializable cached, final Object owner)
        throws HibernateException {
        return deepCopy(cached);
    }

    /* see superclass */
    @Override
    public Object replace(final Object original, final Object target, final Object owner)
        throws HibernateException {
        return deepCopy(original);
    }

    /* see superclass */
    @Override
    public boolean isMutable() {
        return true;
    }

    /* see superclass */
    @Override
    public int hashCode(final Object x) throws HibernateException {
        if (x == null) {
            return 0;
        }

        return x.hashCode();
    }

    /* see superclass */
    @Override
    public boolean equals(final Object x, final Object y) throws HibernateException {
        if (x == null && y == null) {
            return true;
        } else if (x != null) {
            return x.equals(y);
        } else {
            return false;
        }
    }

    /* see superclass */
    @Override
    public Class<?> returnedClass() {
        return String.class;
    }

    /* see superclass */
    @Override
    public int[] sqlTypes() {
        return new int[] {
                Types.JAVA_OBJECT
        };
    }

}
