/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Artifact;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.terminologyservice.ArtifactService;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
@Api(tags = "Artifact endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class ArtifactController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(ArtifactController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns the artifact entry.
     *
     * @param id the id
     * @return the artifact entry
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/artifact/{id}")
    public @ResponseBody ResponseEntity<Artifact> getArtifact(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get artifact entry: {}", id);

        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setQuery("id: " + id);
        ResultList<Artifact> result = ArtifactService.searchArtifact(searchParameters);

        return new ResponseEntity<>(result.getItems().get(0), HttpStatus.OK);

    }

    /**
     * Adds the artifact.
     *
     * @param artifact the artifact entry
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add artifact")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Saveed icon for organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/artifact")
    public ResponseEntity addArtifact(@RequestBody final Artifact artifact, @RequestParam("file") final MultipartFile inputFile) throws Exception {

        logger.info("Add artifact: " + artifact);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }

        try {

            final File file = FileUtility.saveArtifactFile(inputFile, "artifact", null);
            artifact.setFileName(file.getCanonicalFile().toString());

            final Artifact newArtifact = ArtifactService.addArtifact(authUser, artifact);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(newArtifact);

        } catch (final Exception e) {

            logger.error("Trying to add artifact " + artifact, e);
            handleException(e);
            return null;
        }
    }

    /**
     * Download the artifact.
     *
     * @param artifact the artifact entry
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Download artifact")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Saveed icon for organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/artifact/{id}")
    public ResponseEntity addArtifact(@PathVariable("id") final String id) throws Exception {

        logger.info("Download artifact: " + id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }

        try {

            final Artifact artifact = ArtifactService.getAudit(id);
            final Resource file = FileUtility.getArtifactFile(artifact.getFileName());

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath())).contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {

            logger.error("Trying to download artifact for id:" + id + ".", e);
            handleException(e);
            return null;
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
    @ApiOperation(value = "Get artifact entry search results", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/artifact", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Artifact>> searchArtifactEntries(@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        logger.info("Search artifact entry search parameters: {}", ModelUtility.toJson(searchParameters));

        try {

            final ResultList<Artifact> results = ArtifactService.searchArtifact(searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {
            logger.error("Error searching artifactImpls.  Search criteria: {} ", searchParameters.toString());
            handleException(e);
            return null;
        }
    }
}
