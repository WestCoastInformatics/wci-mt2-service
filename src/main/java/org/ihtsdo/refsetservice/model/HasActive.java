
package org.ihtsdo.refsetservice.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generically represents something that is active or inactive.
 */
public interface HasActive {

    /**
     * Indicates whether or not active is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    @Schema(description = "Indicates whether or not the component is active")
    public Boolean getActive();

    /**
     * Sets the active.
     *
     * @param active the active
     */
    public void setActive(final Boolean active);
}
