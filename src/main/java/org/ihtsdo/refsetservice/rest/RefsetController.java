
package org.ihtsdo.refsetservice.rest;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.SearchParameters;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 * Controller for /concept endpoints.
 */
@RestController
@Api(tags = "Refset endpoints")
@SuppressWarnings("javadoc")
public class RefsetController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetController.class);

    /**
     * Returns the refset.
     *
     * @param refsetId the code
     * @return the concept
     * @throws Exception the exception
     */

    @ApiOperation(value = "Get the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetId", value = "The ID of the refset to return.",
                    required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}",
            produces = "application/json")
    public @ResponseBody Refset getRefset(@PathVariable(value = "refsetId") final String refsetId)
        throws Exception {

        try {

            logger.info("*********** getRefset: refsetId: " + refsetId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "refsetId:" + QueryParserBase.escape(refsetId) + "", Refset.class, null);

                logger.info("*********** getRefset: refset: " + ModelUtility.toJson(refset));

                return refset;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * update active status.
     *
     * @param active the active status
     * @param refsetId The refset ID
     * @return the updated refset
     * @throws Exception the exception
     */
    @PutMapping("/refset/{refsetId}")
    Refset updateActive(final @RequestBody boolean active, final @PathVariable String refsetId)
        throws Exception {

        try {

            logger.info("*********** updateActive: active: " + active + " ; refsetId: " + refsetId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "refsetId:" + QueryParserBase.escape(refsetId) + "", Refset.class, null);
                refset.setActive(active);
                service.setModifiedBy("restApi");
                service.update(refset);

                logger.info("*********** updateActive: refset: " + ModelUtility.toJson(refset));

                return refset;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Search.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology",
                    value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true,
                    dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/search",
            produces = "application/json")
    public @ResponseBody ResultList<Refset> search(final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = "
                        + error.getField() + ", " + error.getCode();
                logger.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.join("\n ", errorMessages));
        }

        try (TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            ResultList<Refset> results = new ResultList<Refset>();

            logger.debug("******** searchParameters: " + ModelUtility.toJson(searchParameters));

            final PfsParameter pfs = new PfsParameter();

            if (searchParameters.getOffset() != null) {
                pfs.setOffset(searchParameters.getOffset());
            }

            if (searchParameters.getLimit() != null) {
                pfs.setLimit(searchParameters.getLimit());
            }

            if (searchParameters.getSortAscending() != null) {
                pfs.setAscending(searchParameters.getSortAscending());
            }

            if (searchParameters.getSort() != null) {
                pfs.setSort(searchParameters.getSort());
            }

            // ResultList<Refset> test = service.find("id:
            // 78659156-b6d6-4935-bcdf-c4692bcee10d", pfs, Refset.class, null);
            // logger.debug("******** test: " + ModelUtility.toJson(test));

            results = service.find(searchParameters.getQuery(), pfs, Refset.class, null);

            for (Refset refset : results.getItems()) {

                refset.setDownloadable(true);
                refset.setFeedbackVisible(false);
            }
            results.setTimeTaken(System.currentTimeMillis() - start);
            logger.debug("******** results: " + ModelUtility.toJson(results));
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Refset Members.
     *
     * @param refsetId the refset ID
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology",
                    value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true,
                    dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/members",
            produces = "application/json")
    public @ResponseBody ConceptResultList members(
        @PathVariable(value = "refsetId") final String refsetId,
        final SearchParameters searchParameters, final BindingResult bindingResult)
        throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = "
                        + error.getField() + ", " + error.getCode();
                logger.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.join("\n ", errorMessages));
        }

        final long start = System.currentTimeMillis();
        ConceptResultList results = new ConceptResultList();

        try {

            results = RefsetMemberService.getRefsetMembers(refsetId, searchParameters);
            logger.debug("******** results: " + ModelUtility.toJson(results));
            results.setTimeTaken(System.currentTimeMillis() - start);
            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

}
