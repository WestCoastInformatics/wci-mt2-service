
package org.ihtsdo.refsetservice.model;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Generically represents something that has an id.
 */
public interface HasId {

    /**
     * Returns the id.
     *
     * @return the id
     */
    @Schema(description = "unique identifier", requiredMode = RequiredMode.REQUIRED, format = "uuid")
    public String getId();

    /**
     * Sets the id.
     *
     * @param id the id
     */
    public void setId(final String id);
}
