
package org.ihtsdo.refsetservice.model;

import java.util.Properties;

/**
 * Generically represents something configurable.
 */
public interface Configurable {

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName();

    /**
     * Sets the properties.
     *
     * @param p the properties
     * @throws Exception the exception
     */
    public void setProperties(Properties p) throws Exception;

}
