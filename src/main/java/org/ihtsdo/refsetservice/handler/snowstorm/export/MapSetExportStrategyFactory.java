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

import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.enums.FileExportType;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating and selecting the appropriate MapSetExportStrategy.
 */
public class MapSetExportStrategyFactory {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetExportStrategyFactory.class);

    /** The export handler. */
    private final ExportHandler exportHandler;

    /**
     * Instantiates a new map set export strategy factory.
     *
     * @param exportHandler the export handler
     */
    public MapSetExportStrategyFactory(final ExportHandler exportHandler) {

        this.exportHandler = exportHandler;
    }

    /**
     * Gets the appropriate export strategy based on the export request parameters.
     *
     * @param mapSetExportRequest the map set export request
     * @return the appropriate export strategy
     */
    public MapSetExportStrategy getExportStrategy(final MapSetExportRequest mapSetExportRequest) {

        if (mapSetExportRequest == null || mapSetExportRequest.getFileExportType() == null) {
            throw new IllegalArgumentException("MapSetExportRequest and FileExportType cannot be null");
        }
        if (!FileExportType.getValues().contains(mapSetExportRequest.getFileExportType())) {
            throw new IllegalArgumentException("Unsupported file export type: " + mapSetExportRequest.getFileExportType());
        }

        if (mapSetExportRequest.getFileExportType() == FileExportType.SCTIDS) {

            LOG.info("Using SCTID export strategy");
            return new SctIdExportStrategy(exportHandler);

        }

        if (mapSetExportRequest.getFileFormatType() == FileFormatType.DELTA) {
            LOG.info("Using RF2 delta export strategy");
            return new Rf2DeltaExportStrategy(exportHandler);

        }

        LOG.info("Using RF2 snapshot export strategy");
        return new Rf2SnapshotExportStrategy(exportHandler);

    }
}