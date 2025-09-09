/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Objects;

import org.ihtsdo.refsetservice.model.enums.FileExportType;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.ihtsdo.refsetservice.util.ModelUtility;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class MapSetExportRequest.
 */
@JsonInclude(Include.NON_EMPTY)
@Schema(description = "Represents a set of parameters for exporting a map set file")
public class MapSetExportRequest {

    /** The branch. */
    @Schema(description = "Branch path. e.g. MAIN/SNOMEDCT-US/2025-03-01/WCITEST")
    private String branch;

    /** The map set code. */
    @Schema(description = "The map set code. e.g. 447562003")
    private String mapSetCode;

    /** The file format type. */
    @Schema(description = "The format of the file", allowableValues = {
        "SNAPSHOT", "DELTA"
    })
    private FileFormatType fileFormatType;

    /** The file export type. */
    @Schema(description = "The format of the file", allowableValues = {
        "RF2", "RF2_WITH_NAMES", "SCTIDS"
    })
    private FileExportType fileExportType;

    /** The file name date. */
    @Schema(description = "The date to append to the file name in YYYYMMDD format. e.g. 20250101")
    private String fileNameDate;

    /** The language id. */
    @Schema(description = "Language code. e.g. en or en-US or 900000000000509007")
    private String languageId;

    /** The start effective time. */
    @Schema(description = "The start effective time in YYYYMMDD format. e.g. 20240101")
    private String startEffectiveTime;

    /** The transient effective time. */
    @Schema(description = "The transient effective time in YYYYMMDD format. e.g. 20240101")
    private String transientEffectiveTime;

    /** The export metadata. */
    private boolean exportMetadata;

    /**
     * Instantiates a new map set export request.
     */
    public MapSetExportRequest() {

        // do nothing
    }

    /**
     * Gets the branch.
     *
     * @return the branch
     */
    public String getBranch() {

        return branch;
    }

    /**
     * Sets the branch.
     *
     * @param branch the new branch
     */
    public void setBranch(final String branch) {

        this.branch = branch;
    }

    /**
     * Gets the mapset id.
     *
     * @return the mapset id
     */
    public String getMapSetCode() {

        return mapSetCode;
    }

    /**
     * Sets the mapset id.
     *
     * @param mapSetCode the new map set code
     */
    public void setMapSetCode(final String mapSetCode) {

        this.mapSetCode = mapSetCode;
    }

    /**
     * Gets the format.
     *
     * @return the format
     */
    public FileFormatType getFileFormatType() {

        return fileFormatType;
    }

    /**
     * Sets the format.
     *
     * @param fileFormatType the new file format type
     */
    public void setFileFormatType(final FileFormatType fileFormatType) {

        this.fileFormatType = fileFormatType;
    }

    /**
     * Gets the export type.
     *
     * @return the export type
     */
    public FileExportType getFileExportType() {

        return fileExportType;
    }

    /**
     * Sets the export type.
     *
     * @param fileExportType the new file export type
     */
    public void setFileExportType(final FileExportType fileExportType) {

        this.fileExportType = fileExportType;
    }

    /**
     * Gets the file name date.
     *
     * @return the file name date
     */
    public String getFileNameDate() {

        return fileNameDate;
    }

    /**
     * Sets the file name date.
     *
     * @param fileNameDate the new file name date
     */
    public void setFileNameDate(final String fileNameDate) {

        this.fileNameDate = fileNameDate;
    }

    /**
     * Gets the language id.
     *
     * @return the language id
     */
    public String getLanguageId() {

        return languageId;
    }

    /**
     * Sets the language id.
     *
     * @param languageId the new language id
     */
    public void setLanguageId(final String languageId) {

        this.languageId = languageId;
    }

    /**
     * Gets the start effective time.
     *
     * @return the start effective time
     */
    public String getStartEffectiveTime() {

        return startEffectiveTime;
    }

    /**
     * Sets the start effective time.
     *
     * @param startEffectiveTime the new start effective time
     */
    public void setStartEffectiveTime(final String startEffectiveTime) {

        this.startEffectiveTime = startEffectiveTime;
    }

    /**
     * Gets the transient effective time.
     *
     * @return the transient effective time
     */
    public String getTransientEffectiveTime() {

        return transientEffectiveTime;
    }

    /**
     * Sets the transient effective time.
     *
     * @param transientEffectiveTime the new transient effective time
     */
    public void setTransientEffectiveTime(final String transientEffectiveTime) {

        this.transientEffectiveTime = transientEffectiveTime;
    }

    /**
     * Checks if is export metadata.
     *
     * @return true, if is export metadata
     */
    public boolean isExportMetadata() {

        return exportMetadata;
    }

    /**
     * Sets the export metadata.
     *
     * @param exportMetadata the new export metadata
     */
    public void setExportMetadata(final boolean exportMetadata) {

        this.exportMetadata = exportMetadata;
    }

    /**
     * Checks if is with names.
     *
     * @return true, if is with names
     */
    @JsonIgnore
    public boolean isWithNames() {

        return getFileExportType() == FileExportType.RF2_WITH_NAMES;
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    /* see superclass */
    @Override
    public int hashCode() {

        return Objects.hash(branch, exportMetadata, fileExportType, fileNameDate, fileFormatType, languageId, mapSetCode, startEffectiveTime,
            transientEffectiveTime);
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    /* see superclass */
    @Override
    public boolean equals(Object obj) {

        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MapSetExportRequest other = (MapSetExportRequest) obj;
        return Objects.equals(branch, other.branch) && exportMetadata == other.exportMetadata && fileExportType == other.fileExportType
            && Objects.equals(fileNameDate, other.fileNameDate) && fileFormatType == other.fileFormatType && Objects.equals(languageId, other.languageId)
            && Objects.equals(mapSetCode, other.mapSetCode) && Objects.equals(startEffectiveTime, other.startEffectiveTime)
            && Objects.equals(transientEffectiveTime, other.transientEffectiveTime);
    }

    /**
     * To string.
     *
     * @return the string
     */
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
