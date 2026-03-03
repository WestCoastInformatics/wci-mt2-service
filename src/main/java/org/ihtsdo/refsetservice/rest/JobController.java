package org.ihtsdo.refsetservice.rest;

import org.ihtsdo.refsetservice.model.Job;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for managing asynchronous jobs.
 */
@RestController
@RequestMapping("/")
@Tag(name = "jobs", description = "Operations for managing asynchronous jobs")
public class JobController {

    /**
     * Gets the details of a job.
     * @param jobId The job ID
     * @return The job status
     */
    @GetMapping("/job/{jobId}")
    @Operation(summary = "Get job status", description = "Retrieves the current status of an asynchronous job", tags = {
        "jobs"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved job status",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Job.class))),
        @ApiResponse(responseCode = "404", description = "Job not found")
    })
    @Parameter(name = "jobId", description = "The ID of the job to check", required = true)
    public ResponseEntity<Job> getJobStatus(@PathVariable String jobId) {

        try(final TerminologyService service = new TerminologyService()) {
            Job job = JobService.getJob(service, jobId);
            if (job == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(job);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}