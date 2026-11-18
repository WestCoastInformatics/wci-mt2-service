/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Job;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.BoundedExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bulk mapping save that returns on this request when it finishes before the gateway timeout, and otherwise continues in the background.
 *
 * <p>
 * The response body is the updated mapping list when the save finishes in time. When it does not, the body is {@code {"url":"job/{id}"}} and
 * {@code GET /job/{id}} reports {@code PROCESSING}, {@code COMPLETED}, or {@code FAILED}. That is the same shape map-set export uses.
 */
public final class MappingBulkSave {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingBulkSave.class);

    /**
     * How long the HTTP request waits before handing the save off. Shorter than {@link RefsetMemberService#TIMEOUT_MILLISECOND_THRESHOLD} so the gateway can
     * still return this response.
     */
    static final long SYNC_WAIT_MS = RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD - 15_000L;

    /** Map sets with a bulk save that has not finished. */
    private static final Set<String> IN_PROGRESS = ConcurrentHashMap.newKeySet();

    /**
     * Instantiates a new mapping bulk save.
     */
    private MappingBulkSave() {

        // n/a
    }

    /**
     * Saves the mappings, returning either the updated list or a job URL.
     *
     * <p>
     * Permission checks stay on the caller. This method reloads the map set on the worker thread so the request session can close.
     *
     * @param user the acting user
     * @param mapSetInternalId the map set internal id
     * @param mappings the mappings to save
     * @return the updated mappings, or a map whose {@code url} is {@code job/{id}}
     * @throws Exception the exception
     */
    public static Object save(final User user, final String mapSetInternalId, final List<Mapping> mappings) throws Exception {

        if (!reserve(mapSetInternalId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A mapping save is already in progress for this map set.");
        }

        final User actingUser = copyUser(user);
        final Job job;
        try {
            job = JobService.createMappingBulkSaveJob(actingUser, mapSetInternalId, mappings == null ? 0 : mappings.size());
        } catch (final Exception e) {
            release(mapSetInternalId);
            throw e;
        }

        final CompletableFuture<List<Mapping>> future =
            CompletableFuture.supplyAsync(() -> runSave(actingUser, mapSetInternalId, mappings, job), BoundedExecutors.background());
        return awaitResult(future, job.getId(), SYNC_WAIT_MS);
    }

    /**
     * Reserves a map set so a second bulk save cannot start while the first is still writing.
     *
     * @param mapSetInternalId the map set internal id
     * @return true when this call holds the reservation
     */
    static boolean reserve(final String mapSetInternalId) {

        return IN_PROGRESS.add(mapSetInternalId);
    }

    /**
     * Releases a map set reserved by {@link #reserve(String)}.
     *
     * @param mapSetInternalId the map set internal id
     */
    static void release(final String mapSetInternalId) {

        IN_PROGRESS.remove(mapSetInternalId);
    }

    /**
     * Waits for the save. On timeout the future keeps running and the body tells the caller which job to poll.
     *
     * @param future the save
     * @param jobId the job id
     * @param waitMs how long to wait on this request
     * @return the updated mappings, or a map whose {@code url} is {@code job/{id}}
     * @throws Exception the exception
     */
    static Object awaitResult(final CompletableFuture<List<Mapping>> future, final String jobId, final long waitMs) throws Exception {

        try {
            return future.get(Math.max(0L, waitMs), TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            LOG.info("Bulk mapping save continuing in background job={}", jobId);
            final Map<String, String> body = new LinkedHashMap<>();
            body.put("url", "job/" + jobId);
            return body;
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (final ExecutionException e) {
            throw unwrap(e);
        }
    }

    /**
     * Runs the save and records the job status. Always releases the map-set reservation.
     *
     * @param user the acting user
     * @param mapSetInternalId the map set internal id
     * @param mappings the mappings
     * @param job the job
     * @return the updated mappings
     */
    private static List<Mapping> runSave(final User user, final String mapSetInternalId, final List<Mapping> mappings, final Job job) {

        try {
            mark(user, job, Job.Status.PROCESSING, null, null);
            try (final TerminologyService service = new TerminologyService()) {
                final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
                if (mapSet.getMapProject() == null) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map set not found.");
                }
                final String branch = BranchService.getMapSetBranchPath(mapSet);
                if (StringUtils.isBlank(branch)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map set branch not found.");
                }
                final List<Mapping> updated =
                    MappingService.updateMappings(mapSet.getMapProject(), branch, mapSet.getRefSetCode(), mappings, mapSet, user);
                mark(user, job, Job.Status.COMPLETED, updated.size() + " mappings saved", null);
                return updated;
            }
        } catch (final Exception e) {
            LOG.error("Bulk mapping save failed mapSet={} job={}", mapSetInternalId, job.getId(), e);
            mark(user, job, Job.Status.FAILED, null, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new CompletionException(e);
        } finally {
            release(mapSetInternalId);
        }
    }

    /**
     * Copies the fields the background thread reads. The request user stays on the request session.
     *
     * @param user the request user
     * @return a detached user
     */
    private static User copyUser(final User user) {

        final User copy = new User();
        copy.setUserName(user.getUserName());
        copy.setName(user.getName());
        return copy;
    }

    /**
     * Updates job status. A status-write failure does not hide a successful save or replace the original error.
     *
     * @param user the acting user
     * @param job the job
     * @param status the status
     * @param result the result, or null
     * @param errorMessage the error message, or null
     */
    private static void mark(final User user, final Job job, final Job.Status status, final String result, final String errorMessage) {

        try {
            job.setStatus(status);
            if (result != null) {
                job.setResult(result);
            }
            if (errorMessage != null) {
                job.setErrorMessage(StringUtils.abbreviate(errorMessage, 3900));
            }
            JobService.updateJob(user, job);
        } catch (final Exception e) {
            LOG.error("Failed to update mapping save job {} to {}", job.getId(), status, e);
        }
    }

    /**
     * Returns the failure from the worker, unwrapping the future's wrappers.
     *
     * @param failure the failure from {@code Future.get}
     * @return the exception to throw on the request thread
     */
    private static Exception unwrap(final Throwable failure) {

        Throwable current = failure;
        while ((current instanceof ExecutionException || current instanceof CompletionException) && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof Exception) {
            return (Exception) current;
        }
        return new Exception(current);
    }
}
