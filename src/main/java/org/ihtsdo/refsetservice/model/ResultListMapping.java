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

import org.ihtsdo.refsetservice.util.ResultList;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of mappings.
 */
@Schema(description = "Represents a list of mappings returned from a find call")
public class ResultListMapping extends ResultList<Mapping> {

    /** Concept IDs that were requested but are not valid (e.g. not found). Only set when conceptCodes were supplied. */
    @Schema(description = "Concept IDs that were requested but could not be resolved (e.g. not found). Present only when conceptCodes were supplied and some were invalid.")
    private List<String> invalidConceptIds;

    /**
     * Instantiates an empty {@link ResultListMapping}.
     */
    public ResultListMapping() {

        // n/a
    }

    /**
     * Instantiates a new mapping result list.
     *
     * @param mappings the mappings
     */
    public ResultListMapping(final List<Mapping> mappings) {

        super(mappings);
    }

    /**
     * Instantiates a new mapping result list.
     *
     * @param other the other
     */
    public ResultListMapping(final ResultListMapping other) {

        super.populateFrom(other);
        if (other.invalidConceptIds != null) {
            this.invalidConceptIds = new ArrayList<>(other.invalidConceptIds);
        }
    }

    /**
     * Gets the list of concept IDs that were requested but could not be resolved.
     *
     * @return the invalid concept IDs, or null if none
     */
    public List<String> getInvalidConceptIds() {

        return invalidConceptIds;
    }

    /**
     * Sets the list of concept IDs that were requested but could not be resolved.
     *
     * @param invalidConceptIds the invalid concept IDs
     */
    public void setInvalidConceptIds(final List<String> invalidConceptIds) {

        this.invalidConceptIds = invalidConceptIds;
    }
}
