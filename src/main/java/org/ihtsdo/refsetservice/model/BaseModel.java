package org.ihtsdo.refsetservice.model;

import javax.persistence.MappedSuperclass;

import org.ihtsdo.refsetservice.util.ModelUtility;

/**
 * Base model for all classes.
 */
@MappedSuperclass
public abstract class BaseModel {

    /**
     * Instantiates an empty {@link BaseModel}.
     */
    protected BaseModel() {

        // n/a
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
