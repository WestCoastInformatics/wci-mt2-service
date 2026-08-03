/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
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
import java.util.Objects;

import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request body for bulk per-concept mapping workflow actions.
 */
@JsonInclude(Include.NON_EMPTY)
@Schema(description = "Bulk mapping workflow status request.")
public class MappingWorkflowBulkRequest {

    /** Source concept codes to update. */
    private List<String> conceptCodes = new ArrayList<>();

    /**
     * Instantiates an empty {@link MappingWorkflowBulkRequest}.
     */
    public MappingWorkflowBulkRequest() {

        // n/a
    }

    /**
     * Returns the concept codes.
     *
     * @return the concept codes
     */
    @Schema(description = "Source concept codes to apply the workflow action to.", required = true)
    public List<String> getConceptCodes() {

        return conceptCodes;
    }

    /**
     * Sets the concept codes.
     *
     * @param conceptCodes the concept codes
     */
    public void setConceptCodes(final List<String> conceptCodes) {

        this.conceptCodes = conceptCodes == null ? new ArrayList<>() : conceptCodes;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        final MappingWorkflowBulkRequest other = (MappingWorkflowBulkRequest) obj;
        return Objects.equals(conceptCodes, other.conceptCodes);
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(conceptCodes);
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {

        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }
}
