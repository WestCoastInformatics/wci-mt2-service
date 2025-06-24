/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm.export;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for map set export strategies that provides common functionality.
 */
public abstract class AbstractMapSetExportStrategy implements MapSetExportStrategy {

    /** The Constant LOG. */
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractMapSetExportStrategy.class);

    /** The Constant FILE_DOWNLOAD_URL. */
    protected static final String FILE_DOWNLOAD_URL = "/mapset/export/download/";

    /** The export handler. */
    protected ExportHandler exportHandler;

    /**
     * Instantiates a new abstract map set export strategy.
     *
     * @param exportHandler the export handler
     */
    protected AbstractMapSetExportStrategy(final ExportHandler exportHandler) {

        this.exportHandler = exportHandler;
    }

    /**
     * Generates the AWS versioned path for the export.
     *
     * @param mapSet the map set
     * @param mapSetExportRequest the map set export request
     * @param projectDir the project directory
     * @return the AWS versioned path
     * @throws IOException if there is an error generating the path
     */
    protected String generateAwsVersionedPath(final MapSet mapSet, final MapSetExportRequest mapSetExportRequest, final String projectDir) throws IOException {

        try {
            return exportHandler.generateAwsMt2BaseVersionPath(mapSet, mapSetExportRequest, projectDir);
        } catch (final Exception e) {
            throw new IOException("Failed to generate AWS versioned path", e);
        }
    }

    /**
     * Generates the Snowstorm file name.
     *
     * @param mapProject the map project
     * @param mapSet the map set
     * @param fileFormatType the file format type
     * @param transientEffectiveTime the transient effective time
     * @param startEffectiveTime the start effective time
     * @return the Snowstorm file name
     * @throws IOException if there is an error generating the file name
     */
    protected String generateSnowstormFileName(final MapProject mapProject, final MapSet mapSet, final FileFormatType fileFormatType,
        final String transientEffectiveTime, final String startEffectiveTime) throws IOException {

        try {
            return exportHandler.generateMt2SnowVersionFileName(mapProject, mapSet, fileFormatType, transientEffectiveTime, startEffectiveTime);
        } catch (final Exception e) {
            throw new IOException("Failed to generate Snowstorm file name", e);
        }
    }

    /**
     * Generates the MT2 version file name.
     *
     * @param mapProject the map project
     * @param mapSet the map set
     * @param request the export request
     * @return the MT2 version file name
     * @throws IOException if there is an error generating the file name
     */
    protected String generateMt2VersionFileName(final MapProject mapProject, final MapSet mapSet, final MapSetExportRequest request) throws IOException {

        try {
            return exportHandler.generateMt2VersionFileName(mapProject, mapSet, request);
        } catch (final Exception e) {
            throw new IOException("Failed to generate MT2 version file name", e);
        }
    }

    /**
     * Generates the export request parameters.
     *
     * @param mapSet the map set
     * @param mapSetExportRequest the map set export request
     * @param moduleIds the module ids
     * @return the export request parameters
     */
    protected String generateExportRequestParameters(final MapSet mapSet, final MapSetExportRequest mapSetExportRequest, final Set<String> moduleIds) {

        return new ExportRequestBuilder().withRefsetId(mapSet.getRefSetCode()).withBranchPath(mapSetExportRequest.getBranch())
            .withConceptsAndRelationshipsOnly(false).withFilenameEffectiveDate(mapSetExportRequest.getFileNameDate()).withLegacyZipNaming(false)
            .withType(mapSetExportRequest.getFileFormatType()).withUnpromotedChangesOnly(false).withModuleIds(moduleIds)
            .withTransientEffectiveTime(mapSetExportRequest.getTransientEffectiveTime()).withStartEffectiveTime(mapSetExportRequest.getStartEffectiveTime())
            .build();
    }

    /**
     * Validates the map set exists.
     *
     * @param mapSet the map set to validate
     * @param mapSetExportRequest the map set export request
     * @throws MapSetExportException if the map set is null
     */
    protected void validateMapSet(final MapSet mapSet, final MapSetExportRequest mapSetExportRequest) throws MapSetExportException {

        if (mapSet == null) {
            throw new MapSetExportException("Map Set Code: " + mapSetExportRequest.getMapSetCode() + " does not exist");
        }
    }

    /**
     * Get refset as of date.
     *
     * @param mapSet the map set
     * @return the string
     */
    protected String getRefsetAsOfDate(final MapSet mapSet) {

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return mapSet.getModified() != null ? simpleDateFormat.format(mapSet.getModified()) : simpleDateFormat.format(new Date());
    }
}