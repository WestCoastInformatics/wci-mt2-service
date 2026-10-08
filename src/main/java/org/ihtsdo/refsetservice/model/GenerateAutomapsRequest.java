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
 * Request body for generating unsaved automap suggestions.
 */
@JsonInclude(Include.NON_EMPTY)
@Schema(description = "Generate automap suggestions for source concept codes. Results are not saved.")
public class GenerateAutomapsRequest {

    /** Source concept codes to map. */
    private List<String> conceptCodes = new ArrayList<>();

    /** Automap entity type, such as condition. When empty, the configured default is used. */
    private String entityType;

    /** Minimum automap confidence, from 0 to 1. When null, the configured default is used. */
    private Double minConfidence;

    /**
     * Instantiates an empty {@link GenerateAutomapsRequest}.
     */
    public GenerateAutomapsRequest() {

        // n/a
    }

    /**
     * Returns the concept codes.
     *
     * @return the concept codes
     */
    @Schema(description = "Source concept codes to map.", required = true)
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
     * Returns the entity type.
     *
     * @return the entity type
     */
    @Schema(description = "Automap entity type. Defaults to condition.", example = "condition")
    public String getEntityType() {

        return entityType;
    }

    /**
     * Sets the entity type.
     *
     * @param entityType the entity type
     */
    public void setEntityType(final String entityType) {

        this.entityType = entityType;
    }

    /**
     * Returns the minimum confidence.
     *
     * @return the minimum confidence
     */
    @Schema(description = "Minimum automap confidence from 0 to 1. Defaults to 0.8.", example = "0.8")
    public Double getMinConfidence() {

        return minConfidence;
    }

    /**
     * Sets the minimum confidence.
     *
     * @param minConfidence the minimum confidence
     */
    public void setMinConfidence(final Double minConfidence) {

        this.minConfidence = minConfidence;
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
        final GenerateAutomapsRequest other = (GenerateAutomapsRequest) obj;
        return Objects.equals(conceptCodes, other.conceptCodes) && Objects.equals(entityType, other.entityType)
            && Objects.equals(minConfidence, other.minConfidence);
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(conceptCodes, entityType, minConfidence);
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
