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

import java.util.concurrent.CompletableFuture;

import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMapping;
import org.ihtsdo.refsetservice.model.Job;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.terminologyservice.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatcher class for handling map set export requests.
 */
public class MapSetExportDispatcher {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetExportDispatcher.class);

    /** The strategy factory. */
    private final MapSetExportStrategyFactory strategyFactory;

    /**
     * Instantiates a new map set export dispatcher.
     *
     * @param exportHandler the export handler
     * @param jobService the job service
     */
    public MapSetExportDispatcher(final ExportHandler exportHandler) {

        this.strategyFactory = new MapSetExportStrategyFactory(exportHandler);
    }

    /**
     * Export map set using the appropriate strategy.
     *
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @return the job ID for tracking the export progress
     * @throws Exception the exception
     */
    public String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest) throws Exception {

        LOG.info("Exporting map set with request: {}", mapSetExportRequest);

        // Get the map set
        final MapSet mapSet = SnowstormMapping.getMapSet(mapSetExportRequest.getBranch(), mapSetExportRequest.getMapSetCode());

        if (mapSet == null) {
            throw new MapSetExportException("Map Set Code: " + mapSetExportRequest.getMapSetCode() + " does not exist");
        }

        // Set branch path if not already set
        if (mapSet.getBranchPath() == null || mapSet.getBranchPath().isEmpty()) {
            mapSet.setBranchPath("MAIN/SNOMEDCT-NO/2025-12-15"); // Default branch path
        }

        // Create job first
        final Job job = JobService.createExportJob(user, mapSet, mapProject, mapSetExportRequest);

        // Start export in background
        CompletableFuture.runAsync(() -> {
            try {
                job.setStatus(Job.Status.PROCESSING);
                JobService.updateJob(user, job);

                // Get the appropriate strategy
                final MapSetExportStrategy exportStrategy = strategyFactory.getExportStrategy(mapSetExportRequest);

                // Execute the export strategy
                final String result = exportStrategy.export(mapSet, mapProject, mapSetExportRequest);

                // Update job with success
                job.setStatus(Job.Status.COMPLETED);
                job.setResult(result);
                JobService.updateJob(user, job);

                LOG.info("Export completed successfully. Download URI: {}", result);

            } catch (Exception e) {
                LOG.error("Export failed", e);
                // Update job with failure
                job.setStatus(Job.Status.FAILED);
                job.setErrorMessage(e.getMessage());
                try {
                    JobService.updateJob(user, job);
                } catch (Exception ex) {
                    LOG.error("Failed to update job status for job {} with status {}", job.getId(), Job.Status.FAILED, ex);
                }
            }
        });
        return job.getId();

    }
}