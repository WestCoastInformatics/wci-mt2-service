/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.terminologyservice.ConceptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Controller for /concept endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class ConceptController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ConceptController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /** Search mapProjects API note. */
    @SuppressWarnings("unused")
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /**
     * Returns a specific mapProject.
     *
     * @param terminology the concept terminology
     * @param version the version
     * @param code the code
     * @param branch the branch
     * @return the concept
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{terminology}/{version}/{code}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get concept.  This call requires authentication with the correct role.", tags = {
        "concept"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "terminology", description = "Concept terminology", required = true),
        @Parameter(name = "version", description = "Concept terminology version", required = true),
        @Parameter(name = "code", description = "Concept code, e.g. 4579201", required = true), @Parameter(name = "branch",
            description = "The snowstorm branch to search in (for SNOMED concept searches)", required = false, example = "MAIN/SNOMEDCT-NO/2024-04-15/WCITEST")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Concept> getConcept(@PathVariable(value = "terminology") final String terminology,
        @PathVariable(value = "version") final String version, @PathVariable(value = "code") final String code,
        @RequestParam(required = false) final String branch) throws Exception {

        LOG.info("Concept: code: " + code + ", terminology: " + terminology + ", version: " + version + ", branch: " + (branch == null ? "" : branch));
        // final User authUser = authorizeUser(request);

        try {
            final Concept concept = ConceptService.getConcept(branch, terminology, version, code);
            return new ResponseEntity<>(concept, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

}
