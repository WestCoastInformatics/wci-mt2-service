/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import org.ihtsdo.refsetservice.model.Job;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Service for managing asynchronous jobs.
 */
@Service
public class JobService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(JobService.class);

    /**
     * Instantiates a new job service.
     */
    private JobService() {

        // default constructor
    }

    /**
     * Creates a new export job.
     *
     * @param user the user
     * @param mapSet the map set
     * @param mapProject the map project
     * @param request the request
     * @return the job
     * @throws Exception the exception
     */
    public static Job createExportJob(final User user, final MapSet mapSet, final MapProject mapProject, final MapSetExportRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getName());
            service.beginTransaction();

            final Job job = new Job();
            job.setJobType(determineJobType(request));
            job.setResourceId(mapSet.getId());
            job.setStatus(Job.Status.PENDING);

            try {
                final ExportJobParameters params = new ExportJobParameters(mapSet, mapProject, request);
                job.setParameters(ThreadLocalMapper.get().writeValueAsString(params));

            } catch (JsonProcessingException e) {
                LOG.error("Failed to serialize export parameters", e);
                job.setErrorMessage("Failed to serialize export parameters: " + e.getMessage());
            }

            final Job createdJob = service.add(job);
            service.commit();

            return createdJob;
        }

    }

    /**
     * Gets a job by ID.
     *
     * @param service the terminology service
     * @param jobId the job ID
     * @return the job
     * @throws Exception the exception
     */
    public static Job getJob(final TerminologyService service, final String jobId) throws Exception {

        return service.get(jobId, Job.class);
    }

    /**
     * Updates a job.
     *
     * @param user the user
     * @param job the job
     * @throws Exception the exception
     */
    public static void updateJob(final User user, final Job job) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getName());
            service.beginTransaction();
            service.update(job);
            service.commit();
        }

    }

    /**
     * Determines the job type based on the export request.
     *
     * @param request the request
     * @return the job type
     */
    private static Job.Type determineJobType(final MapSetExportRequest request) {

        switch (request.getFileFormatType()) {
            case SNAPSHOT:
                return Job.Type.RF2_SNAPSHOT_EXPORT;
            case DELTA:
                return Job.Type.RF2_DELTA_EXPORT;
            default:
                return Job.Type.EXPORT;
        }
    }

    /**
     * Class to hold export job parameters.
     */
    private static class ExportJobParameters {

        /** The map set id. */
        private final String mapSetId;

        /** The map project id. */
        private final String mapProjectId;

        /** The request. */
        private final MapSetExportRequest request;

        /**
         * Instantiates a new export job parameters.
         *
         * @param mapSet the map set
         * @param mapProject the map project
         * @param request the request
         */
        public ExportJobParameters(final MapSet mapSet, final MapProject mapProject, final MapSetExportRequest request) {

            this.mapSetId = mapSet.getId();
            this.mapProjectId = mapProject.getId();
            this.request = request;
        }

        /**
         * Gets the map set id.
         *
         * @return the map set id
         */
        // Getters for serialization
        public String getMapSetId() {

            return mapSetId;
        }

        /**
         * Gets the map project id.
         *
         * @return the map project id
         */
        public String getMapProjectId() {

            return mapProjectId;
        }

        /**
         * Gets the request.
         *
         * @return the request
         */
        public MapSetExportRequest getRequest() {

            return request;
        }
    }
}