package org.ihtsdo.refsetservice.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
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
 * Controller for /edition endpoints.
 */
@RestController
@Api(tags = "Edition endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class EditionController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(EditionController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Return the edition.
     *
     * @param id the id
     * @return the edition
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the edition for the specified identifier", response = Edition.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Edition identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/edition/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<Edition> getEdition(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get edition for id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        try {
            final Edition edition = EditionService.getEdition(id);
            return new ResponseEntity<>(edition, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Return the edition.
     *
     * @return the edition
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the edition for the specified identifier", response = Edition.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(value = "/edition/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultList<Edition>> getEditions() throws Exception {

        logger.info("Get all editions ");
        final User user = SecurityService.getUserFromSession();

        try {

            final ResultList<Edition> results = EditionService.getEditions();
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
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
    @ApiOperation(value = "Get editions search results", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/edition/search", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<Edition>> getEditions(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        logger.debug("getEditions searchParameters: " + ModelUtility.toJson(searchParameters));
        final User user = SecurityService.getUserFromSession();

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            final ResultList<Edition> results = EditionService.searchEditions(searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

}
