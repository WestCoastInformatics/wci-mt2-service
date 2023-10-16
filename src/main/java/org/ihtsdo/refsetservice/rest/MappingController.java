package org.ihtsdo.refsetservice.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.terminologyservice.MappingService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
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

    @RequestMapping(method = RequestMethod.GET, value = "/mapping", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "id", description = "Mappings identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<Mapping>> getMappings(@ModelAttribute final SearchParameters searchParameters) throws Exception {

        LOG.info("Mappings for a Mapset {}", ModelUtility.toJson(searchParameters));
        // final User authUser = authorizeUser(request);

        try {

            final List<Mapping> mappings = MappingService.getMappings(null);

            return new ResponseEntity<>(mappings, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    @PostMapping(value = "/mapping", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "id", description = "Mappings identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<Mapping>> getMappings(@ModelAttribute final MapSet mapSet, @ModelAttribute final SearchParameters searchParameters)
        throws Exception {

        LOG.info("Mappings for a Mapset {}", ModelUtility.toJson(searchParameters));
        // final User authUser = authorizeUser(request);

        try {

            final List<Mapping> mappings = MappingService.getMappings(mapSet);

            return new ResponseEntity<>(mappings, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

}
