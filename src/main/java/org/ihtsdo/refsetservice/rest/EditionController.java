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

import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /edition endpoints.
 */
@RestController
@Api(tags = "editions", description = "Endpoints for retrieving editions.")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class EditionController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(EditionController.class);

    /**
     * Return the edition.
     *
     * @param id the id
     * @return the edition
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @ApiOperation(value = "Get edition", response = Edition.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Resource not found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Edition id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(value = "/edition/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    // no auth required
    public ResponseEntity<Edition> getEdition(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get edition for id: {}", id);

        try {
            final Edition edition = EditionService.getEdition(id);
            return new ResponseEntity<>(edition, HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Return the editions.
     *
     * @return the editions
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @ApiOperation(value = "Get all editions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @RequestMapping(value = "/edition/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    // no auth required
    public ResponseEntity<ResultList<Edition>> getEditions() throws Exception {

        logger.info("Get all editions");

        try {

            final ResultList<Edition> results = EditionService.getEditions();
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Search Editions.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    @ApiOperation(value = "Find editions.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The value to be searched'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "10"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/edition/search", produces = MediaType.APPLICATION_JSON)
    // no auth required
    public @ResponseBody ResponseEntity<ResultList<Edition>> getEditions(@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        logger.info("getEditions searchParameters: " + ModelUtility.toJson(searchParameters));

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            final ResultList<Edition> results = EditionService.searchEditions(searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }
    }

}
