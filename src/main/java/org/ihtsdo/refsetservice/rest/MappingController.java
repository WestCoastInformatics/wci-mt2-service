package org.ihtsdo.refsetservice.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.terminologyservice.MappingService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The Class MapSetConroller.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class MappingController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingController.class);

    /** The request. */
    @SuppressWarnings("unused")
    @Autowired
    private HttpServletRequest request;

    /** Search teams API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetCode}/mappings", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<Mapping>> getMappings(@PathVariable final String mapSetCode,
        @ModelAttribute final SearchParameters searchParameters) throws Exception {

        LOG.info("Mappings for a Mapset " + mapSetCode, ModelUtility.toJson(searchParameters));
        // final User authUser = authorizeUser(request);

        try {
            final SearchParameters sp = (searchParameters != null) ? searchParameters : new SearchParameters();
            if (sp.getLimit() == null || sp.getLimit() == 0) {
                sp.setLimit(100);
            }

            final ResultList<Mapping> mappings = MappingService.getMappings(mapSetCode, sp);

            return new ResponseEntity<>(mappings, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetCode}/mappings/{conceptCode}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapping. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> getMapping(@PathVariable final String mapSetCode, @PathVariable final String conceptCode,
        @ModelAttribute final SearchParameters searchParameters) throws Exception {

        LOG.info("Mapping for Mapset " + mapSetCode + ", Source Concept " + conceptCode, ModelUtility.toJson(searchParameters));
        // final User authUser = authorizeUser(request);

        try {

            final Mapping mapping = MappingService.getMapping(mapSetCode, conceptCode);

            return new ResponseEntity<>(mapping, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }    
    
}
