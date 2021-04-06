
package org.ihtsdo.refsetservice.rest;

import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.SearchParameters;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.TerminologyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ModelAttribute;
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
@Api(tags = "Concept endpoints")
public class ConceptController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(ConceptController.class);

    /** The term utils. */
    @Autowired
    private TerminologyUtils termUtils;

    /**
     * Returns the concept.
     *
     * @param terminology the terminology
     * @param code the code
     * @return the concept
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the concept for the specified terminology and code",
            response = Concept.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology", value = "Terminology, e.g. 'ncit'",
                    required = true, dataType = "string", paramType = "path",
                    defaultValue = "ncit"),
            @ApiImplicitParam(name = "code",
                    value = "Code in the specified terminology, e.g. 'C3224'", required = true,
                    dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "include",
                    value = "Indicator of how much data to return. Comma-separated list of any of "
                            + "the following values: minimal, summary, full, associations, "
                            + "children, definitions, disjointWith, inverseAssociations, "
                            + "inverseRoles, maps, parents, properties, roles, synonyms. "
                            + "<a href='https://github.com/NCIEVS/evsrestapi-client-SDK/"
                            + "blob/master/doc/INCLUDE.md'>See here for detailed information</a>.",
                    required = false, dataType = "string", paramType = "query",
                    defaultValue = "summary")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{terminology}/{code}",
            produces = "application/json")
    public @ResponseBody Concept getConcept(@PathVariable(value = "terminology")
    final String terminology, @PathVariable(value = "code")
    final String code) throws Exception {
        try {

            logger.info("*********** getConcept: terminology: " + terminology + " ; code: " + code);

            try (TerminologyService service = new TerminologyService()) {

                final Concept concept = service.findSingle("terminology:" + terminology
                        + " AND code:" + QueryParserBase.escape(code) + "", Concept.class, null);

                logger.info(
                        "*********** getConcept: serviceConcept: " + ModelUtility.toJson(concept));

                return concept;
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
     * @param code The code
     * @return the updated concept
     * @throws Exception the exception
     */
    @PutMapping("/concept/{code}")
    Concept updateActive(final @RequestBody boolean active, final @PathVariable String code)
        throws Exception {

        try {

            logger.info("*********** getConcept: active: " + active + " ; code: " + code);

            try (TerminologyService service = new TerminologyService()) {

                final Concept concept = service.findSingle(
                        "code:" + QueryParserBase.escape(code) + "", Concept.class, null);
                concept.setActive(active);
                service.setModifiedBy("restApi");
                service.update(concept);

                logger.info(
                        "*********** getConcept: serviceConcept: " + ModelUtility.toJson(concept));

                return concept;
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
    @ApiOperation(value = "Get concept search results", response = ConceptResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging parameters, additional filters, searches properties, roles, and associations, and so on.  To further explore the range of search options, take a look at the <a href='https://github.com/NCIEVS/evsrestapi-client-SDK' target='_blank'>Github client SDK library created for the NCI EVS Rest API</a>.")
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
    @RequestMapping(method = RequestMethod.GET, value = "/concept/search",
            produces = "application/json")
    public @ResponseBody ConceptResultList search(@ModelAttribute
    final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

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

        try {
            final long start = System.currentTimeMillis();
            final ConceptResultList results = new ConceptResultList();

            // TBD
            results.setTimeTaken(System.currentTimeMillis() - start);
            return results;
        } catch (final ResponseStatusException rse) {
            throw rse;
        } catch (final Exception e) {
            handleException(e);
            return null;
        }

        /**
         * Returns the concept.
         *
         * @param terminology the terminology
         * @param code the code
         * @return the concept
         * @throws Exception the exception
         */
    }
}
