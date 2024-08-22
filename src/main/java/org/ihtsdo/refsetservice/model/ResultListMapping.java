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

import java.util.List;

import org.ihtsdo.refsetservice.util.ResultList;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Represents a list of mappings.
 */
@Schema(description = "Represents a list of mappings returned from a find call")
public class ResultListMapping extends ResultList<Mapping> {

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
    }
}
