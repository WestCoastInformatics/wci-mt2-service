package org.ihtsdo.refsetservice.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.util.ModelUtility;
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
public class MapSetController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetController.class);

    /** The request. */
    @SuppressWarnings("unused")
    @Autowired
    private HttpServletRequest request;

    /** Search teams API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{code}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "code", description = "MapSet identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public ResponseEntity<MapSet> getMapSet(@PathVariable(value = "code") final String code) throws Exception {

        LOG.info("Get mapset {}", code);
        // final User authUser = authorizeUser(request);

        try {

            final MapSet mapset = MapSetService.getMapSet(code);
            return new ResponseEntity<>(mapset, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

    /**
     * Search mapsets.
     *
     * @param includeMembers the include members
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Find mapset. This call requires authentication with the correct role.", description = API_NOTES, tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<MapSet>> getMapSets(@ModelAttribute final SearchParameters searchParameters) throws Exception {

        LOG.info("Search mapsets: {}", ModelUtility.toJson(searchParameters));
        // final User authUser = authorizeUser(request);

        try {

            final List<MapSet> mapSets = MapSetService.getMapSets();

            return new ResponseEntity<>(mapSets, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

}
