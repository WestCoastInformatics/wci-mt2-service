/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents application metadata.
 */
@Schema(description = "Container for overall application metadata.")
@JsonInclude(Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationMetadata {

    /** The module. */
    public List<ModuleInfo> module;

    /**
     * Instantiates an empty {@link ApplicationMetadata}.
     */
    public ApplicationMetadata() {

        // n/a
    }

    /**
     * Instantiates a {@link ApplicationMetadata} from the specified parameters.
     *
     * @param other the other
     */
    public ApplicationMetadata(final ApplicationMetadata other) {

        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final ApplicationMetadata other) {

        // super.populateFrom(other);
        module = new ArrayList<>(other.getModule());

    }

    /**
     * Returns the module.
     *
     * @return the module
     */
    @Schema(description = "List of all available module")
    public List<ModuleInfo> getModule() {

        if (module == null) {
            module = new ArrayList<>();
        }
        return module;
    }

    /**
     * Sets the module.
     *
     * @param module the new module
     */
    public void setModule(final List<ModuleInfo> module) {

        this.module = module;
    }

}
