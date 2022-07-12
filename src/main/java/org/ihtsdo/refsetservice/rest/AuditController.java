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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.AuditService;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /audit endpoints.
 */
@RestController
@Api(tags = "Audit endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class AuditController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(AuditController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns the auditEntryImpl.
     *
     * @param id the id of the auditEntryImpl
     * @return the auditEntryImpl
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/audit/{id}")
    public @ResponseBody ResponseEntity<AuditEntry> getAuditEntry(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get audit entry: {}", id);

        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setQuery("id: " + id);
        ResultList<AuditEntry> result = AuditService.searchAuditEntry(searchParameters);

        return ResponseEntity.status(HttpStatus.OK).body(result.getItems().get(0));

    }

    /**
     * Search audit entries.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get audit entries search results", response = ResultList.class)
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
    @RequestMapping(method = RequestMethod.GET, value = "/audit", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<AuditEntry>> searchAuditEntries(@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        final User authUser = SecurityService.getUserFromSession();
        if (authUser == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        logger.info("Search audit entry search parameters: {}", ModelUtility.toJson(searchParameters));

        try {

            final ResultList<AuditEntry> results = AuditService.searchAuditEntry(searchParameters);
            return ResponseEntity.status(HttpStatus.OK).body(results);

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {
            logger.error("Error searching audit entries.  Search criteria: {} ", searchParameters.toString(), e);
            handleException(e);
            return null;
        }
    }
}
