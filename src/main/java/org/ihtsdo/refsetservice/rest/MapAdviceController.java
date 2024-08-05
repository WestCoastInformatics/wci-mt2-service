package org.ihtsdo.refsetservice.rest;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapAdvice;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapAdviceService;
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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Controller for /mapAdvice endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class MapAdviceController extends BaseController {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MapAdviceController.class);

    /**
     * Add the Map Advice.
     *
     * @param mapProject the mapProject
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/mapAdvice", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Add map advice.  This call requires authentication with the correct role.", tags = {
        "map advice"
    }, responses = {
        @ApiResponse(responseCode = "201", description = "Successfully create map map advice"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not Found"), @ApiResponse(responseCode = "409", description = "Conflict"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    }, requestBody = @RequestBody(description = "Map Project to add", required = true, content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = MapAdvice.class))
    }))
    @RecordMetric
    public @ResponseBody ResponseEntity<MapAdvice> createMapAdvice(final MapAdvice mapAdvice) throws Exception {

        if (StringUtils.isBlank(mapAdvice.getName())) {
            final String errorMessage = "Map advice name is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        if (StringUtils.isBlank(mapAdvice.getDetail())) {
            final String errorMessage = "Map advice detail is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapAdvice newMapAdvice = MapAdviceService.createMapAdvice(service, tempUser, mapAdvice);

            service.commit();

            return new ResponseEntity<>(newMapAdvice, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Return the mapAdvice.
     *
     * @param id the id
     * @return the mapAdvice
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapAdvice/{id}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapAdvice", tags = {
        "mapadvice"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information")
    })
    @Parameters({
        @Parameter(name = "id", description = "MapAdvice id, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public ResponseEntity<MapAdvice> getMapAdvice(@PathVariable(value = "id") final String id) throws Exception {

        // no auth required
        try (final TerminologyService service = new TerminologyService()) {

            final MapAdvice mapAdvice = MapAdviceService.getMapAdvice(service, id);
            return new ResponseEntity<>(mapAdvice, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Return the map advices.
     *
     * @return the map advices
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapAdvice/", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get all map advices", tags = {
        "mapadvice"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information")
    })
    @RecordMetric
    public ResponseEntity<ResultList<MapAdvice>> getMapAdvices() throws Exception {
        // no auth required

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<MapAdvice> results = MapAdviceService.getMapAdvices(service);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Update the Map Advice.
     *
     * @param mapProject the mapProject
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapAdvice/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update map advice.  This call requires authentication with the correct role.", tags = {
        "map advice"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Update create map map advice"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    }, requestBody = @RequestBody(description = "Map Project to update", required = true, content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = MapAdvice.class))
    }))
    @RecordMetric
    public @ResponseBody ResponseEntity<MapAdvice> updateMapAdvice(@PathVariable(value = "id") final String id, final MapAdvice mapAdvice) throws Exception {

        if (mapAdvice == null || !StringUtils.equals(id, mapAdvice.getId())) {

            final String errorMessage = "Map advice is null or mapAdvice id does not match id in URL.";
            throw new RestException(false, 404, "Not found", errorMessage);
        }

        if (StringUtils.isBlank(mapAdvice.getName())) {
            final String errorMessage = "Map advice name is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        if (StringUtils.isBlank(mapAdvice.getDetail())) {
            final String errorMessage = "Map advice detail is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        if (StringUtils.isBlank(mapAdvice.getName())) {
            final String errorMessage = "Map advice name is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapAdvice newMapAdvice = MapAdviceService.updateMapAdvice(service, tempUser, mapAdvice);

            service.commit();

            return new ResponseEntity<>(newMapAdvice, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Search MapAdvices.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapAdvice/search", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Find map advices.", tags = {
        "mapadvice"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    // @ModelAttribute API params documented in SearchParameter
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<MapAdvice>> getMapAdvices(@ModelAttribute final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {
        // no auth required

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<MapAdvice> results = MapAdviceService.searchMapAdvices(service, searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

}
