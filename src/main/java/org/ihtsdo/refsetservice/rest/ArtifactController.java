/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.io.File;
import java.nio.file.Files;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Artifact;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.terminologyservice.ArtifactService;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /artifact endpoints.
 */
@RestController
@Api(tags = "artifacts", description = "Endpoints for adding, updating, and removing reference set artifacts.")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class ArtifactController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ArtifactController.class);

    /**
     * Returns the artifact entry.
     *
     * @param id the id
     * @return the artifact entry
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @RequestMapping(method = RequestMethod.GET, value = "/artifact/{id}")
    @ApiOperation(value = "Get artifact.", response = Artifact.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Artifact id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Artifact> getArtifact(@PathVariable(value = "id") final String id) throws Exception {

        LOG.info("Get artifact entry: {}", id);

        // no auth required

        try {

            final Artifact artifact = ArtifactService.getArtifact(id);

            return new ResponseEntity<>(artifact, HttpStatus.OK);

        } catch (final Exception e) {
            LOG.error("Error getting artifactd  {}.", id);
            return handleException(e);
        }

    }

    /**
     * Search artifact entries.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @RequestMapping(method = RequestMethod.GET, value = "/artifact", produces = MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Find artifacts.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    // @ModelAttribute API params documented in SearchParameter
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<Artifact>> findArtifacts(@ModelAttribute final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        // no auth required

        LOG.info("Search artifact entry search parameters: {}", ModelUtility.toJson(searchParameters));

        try {

            if (searchParameters != null) {
                searchParameters.setActiveOnly(true);
            }
            final ResultList<Artifact> results = ArtifactService.findArtifacts(searchParameters);

            if (results != null && results.getItems() != null && !results.getItems().isEmpty()) {
                for (final Artifact artifact : results.getItems()) {
                    artifact.setDownloadUrl("/artifact/" + artifact.getId() + "/file");
                }
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            LOG.error("Error searching artifacts.  Search criteria: {} ", searchParameters == null ? null : searchParameters.toString());
            return handleException(e);
        }
    }

    /**
     * Adds the artifact.
     *
     * @param artifact the artifact entry
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping(value = "/artifact")
    @ApiOperation(value = "Add artifact. This call requires authentication with the correct role.", response = Artifact.class)
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Added artifact"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "artifact", value = "Artifact object", required = true, dataTypeClass = Artifact.class, paramType = "body")
    })
    @RecordMetric
    public ResponseEntity<?> addArtifact(@RequestParam final String artifact, @RequestParam("file") final MultipartFile inputFile) throws Exception {

        LOG.info("Add artifact: " + artifact);
        final User authUser = authorizeUser();

        try {

            final Artifact artifactEntry = ModelUtility.fromJson(artifact, Artifact.class);

            // TODO: check required values.

            final File file = FileUtility.saveArtifactFile(inputFile, artifactEntry.getEntityType() + "-" + artifactEntry.getEntityId(), null);

            artifactEntry.setStoredFileName(file.getName());
            artifactEntry.setFileName(inputFile.getOriginalFilename());

            final String fileType = (FilenameUtils.getExtension(file.getCanonicalFile().toString()));
            if (StringUtils.isNotBlank(fileType)) {
                artifactEntry.setFileType(fileType.toUpperCase());
            }

            final Artifact newArtifact = ArtifactService.addArtifact(authUser, artifactEntry);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(newArtifact);

        } catch (final Exception e) {

            LOG.error("Trying to add artifact " + artifact, e);
            return handleException(e);
        }
    }

    /**
     * Update artifact.
     *
     * @param id the id
     * @param artifact the artifact
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @PutMapping(value = "/artifact/{id}")
    @ApiOperation(value = "Update artifact. This call requires authentication with the correct role.", response = Artifact.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Updated artifact"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Artifact id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "artifact", value = "Artifact object", required = true, dataTypeClass = Artifact.class, paramType = "body")
    })
    @RecordMetric
    public ResponseEntity updateArtifact(final @PathVariable String id, final @RequestBody String artifact) throws Exception {

        LOG.info("Update artifact: " + artifact);
        final User authUser = authorizeUser();

        try {

            final Artifact existingArtifact = ArtifactService.getArtifact(id);
            final Artifact artifactEntry = ModelUtility.fromJson(artifact, Artifact.class);

            existingArtifact.populateFrom(artifactEntry);
            final Artifact returnArtifact = ArtifactService.updateArtifact(authUser, existingArtifact);

            return ResponseEntity.status(HttpStatus.OK).body(returnArtifact);

        } catch (final Exception e) {

            LOG.error("Trying to add artifact " + artifact, e);
            return handleException(e);
        }
    }

    /**
     * Inactivate artifact.
     *
     * @param id the id
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @DeleteMapping(value = "/artifact/{id}")
    @ApiOperation(value = "Inactivate artifact. This call requires authentication with the correct role.", response = Void.class)
    @ApiResponses(value = {
        @ApiResponse(code = 204, message = "Successfully inactivated artifact"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Artifact id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    public ResponseEntity inactivateArtifact(final @PathVariable String id) throws Exception {

        LOG.info("Inactivate artifact: " + id);

        final User authUser = authorizeUser();

        try {
            final Artifact artifact = ArtifactService.getArtifact(id);
            artifact.setActive(false);
            ArtifactService.updateArtifact(authUser, artifact);
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);

        } catch (final Exception e) {

            LOG.error("Trying to Inactivate artifact " + id, e);
            return handleException(e);
        }
    }

    /**
     * Download artifact.
     *
     * @param id the id
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @GetMapping(value = "/artifact/{id}/file")
    @ApiOperation(value = "Download artifact.", response = Resource.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Retrieved artifact"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Artifact id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    public ResponseEntity<Resource> downloadArtifact(@PathVariable("id") final String id) throws Exception {

        LOG.info("Download artifact: " + id);

        // no auth required

        try {

            final Artifact artifact = ArtifactService.getArtifact(id);
            if (artifact == null) {
                LOG.info("Artifact: " + id + " not found.");
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final Resource file = FileUtility.getArtifactFile(artifact.getStoredFileName());

            if (file == null) {
                LOG.error("Artifact: file " + artifact.getStoredFileName() + " not found.");
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.getFileName() + "\"").contentLength(file.contentLength())
                .body(file);

        } catch (final Exception e) {

            LOG.error("Trying to download artifact for id:" + id + ".", e);
            return handleException(e);
        }
    }

}
