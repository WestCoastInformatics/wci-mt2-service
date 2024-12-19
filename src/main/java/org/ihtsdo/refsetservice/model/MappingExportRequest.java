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
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class MappingExportRequest.
 */
@JsonInclude(Include.NON_EMPTY)
@Schema(description = "Represents a set of search parameters for finding data.")
public class MappingExportRequest {

    /** The concept codes. */
    private List<String> conceptCodes;

    /** The column names. */
    private List<String> columnNames;

    /**
     * Instantiates a new mapping export request.
     */
    public MappingExportRequest() {

    }

    /**
     * Gets the concept codes.
     *
     * @return the concept codes
     */
    @Schema(description = "The array of concept codes to export.")
    public List<String> getConceptCodes() {

        return conceptCodes;
    }

    /**
     * Sets the concept codes.
     *
     * @param conceptCodes the new concept codes
     */
    public void setConceptCodes(final List<String> conceptCodes) {

        this.conceptCodes = conceptCodes;
    }

    /**
     * Gets the column names.
     *
     * @return the column names
     */
    @Schema(description = "The array of column names to include in the export.")
    public List<String> getColumnNames() {

        return columnNames;
    }

    /**
     * Sets the column names.
     *
     * @param columnNames the new column names
     */
    public void setColumnNames(final List<String> columnNames) {

        this.columnNames = columnNames;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        return Objects.hash(conceptCodes, columnNames);
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MappingExportRequest other = (MappingExportRequest) obj;
        return Objects.equals(conceptCodes, other.conceptCodes) && Objects.equals(columnNames, other.columnNames);
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {

        return "MappingExportRequest [conceptCodes=" + conceptCodes + ", columnNames=" + columnNames + "]";
    }

}
