
package org.ihtsdo.refsetservice.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Generically represents something that has an id.
 */
public interface HasId {

    /**
     * Returns the id.
     *
     * @return the id
     */
    @Schema(description = "unique identifier", required = true, format = "uuid")
    public String getId();

    /**
     * Sets the id.
     *
     * @param id the id
     */
    public void setId(final String id);
}
