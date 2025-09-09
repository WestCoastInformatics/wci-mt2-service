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

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.ConceptRef;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.ResultListConceptRef;
import org.ihtsdo.refsetservice.terminologyservice.ConceptService;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

/**
 * Controller for /concept endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class ConceptController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ConceptController.class);

    /** Search mapProjects API note. */
    @SuppressWarnings("unused")
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    
    @RequestMapping(value = "/concept/{terminology}/{version}/autocomplete", method = RequestMethod.GET)
    @Operation(summary = "Suggest autocompletions for text while searching", description = "Finds top ten strings matching input query.", tags = {
        "term"
    })
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of top ten matching concepts."),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "417", description = "Expectation failed"), @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Parameters({
        @Parameter(name = "terminology",
            description = "Comma-separated list of terminology ids or abbreviations (or null for all terminologies)."
                + " e.g. \"uuid1,uuid2\", \"SNOMEDCT,RXNORM\", or \"ICD10CM\".",
            required = false),
        @Parameter(name = "version", description = "Terminology version (required)", required = true),
        @Parameter(name = "query", description = "Search text", required = true),
        @Parameter(name = "limit", description = "Limit of results to return (hard limit of 1000 regardless of value)", required = false,
            schema = @Schema(implementation = Integer.class), example = "10"),
    })
    public ResponseEntity<ResultListConceptRef> autocomplete(@PathVariable(value = "terminology", required = false) final String terminology,
        @PathVariable(value = "version", required = false) final String version,
        @RequestParam(name = "query", required = true) final String query,
        @RequestParam(name = "limit", required = false, defaultValue = "10") final Integer limit) throws Exception {

        try {

            if (StringUtils.isBlank(query) || limit == null || limit <= 0) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setQuery(query);
            searchParameters.setLimit(Math.min(limit, 1000));

            final ResultListConceptRef resultListConceptRef = ConceptService.autoComplete(terminology, version, searchParameters);
            return new ResponseEntity<>(resultListConceptRef, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

    /**
     * Gets the concept.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
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
        @Parameter(name = "terminology", description = "Concept terminology", required = true, example = "SNOMEDCT-US or ICD10NO"),
        @Parameter(name = "version", description = "Version of the terminology", required = true, example = "2025-03-01 or 2026"),
        @Parameter(name = "code", description = "Concept code", required = true, example = "4579201")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Concept> getConcept(@PathVariable(value = "terminology") final String terminology,
        @PathVariable(value = "version") final String version,
        @PathVariable(value = "code") final String code) throws Exception {

        LOG.info("Concept: terminology: {}, version: {}, code: {}", terminology, version, code);
        // final User authUser = authorizeUser(request);

        try {

            final Concept concept = ConceptService.getConcept(terminology, version, code);
            return new ResponseEntity<>(concept, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

    /**
     * Find concept.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{terminology}/{version}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get concept.  This call requires authentication with the correct role.", tags = {
        "concept"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "400", description = "Bad Request"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "terminology", description = "Concept terminology", required = true, example = "SNOMEDCT-US or ICD10NO"),
        @Parameter(name = "version", description = "Concept terminology version", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultListConcept> findConcept(@PathVariable(value = "terminology") final String terminology,
        @PathVariable(value = "version") final String version, @ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
        throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        LOG.info("Concept: terminology: {}, version: {}, searchParameters:{}", terminology, version, (searchParameters == null ? "" : searchParameters));

        // final User authUser = authorizeUser(request);

        try {

            if (searchParameters == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            if (searchParameters.getOffset() == null) {
                searchParameters.setOffset(0);
            }

            if (searchParameters.getLimit() == null || searchParameters.getLimit() == 0) {
                searchParameters.setLimit(20);
            }

            final ResultListConcept resultListConcept = ConceptService.findConcepts(terminology, version, searchParameters);
            return new ResponseEntity<>(resultListConcept, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

}
