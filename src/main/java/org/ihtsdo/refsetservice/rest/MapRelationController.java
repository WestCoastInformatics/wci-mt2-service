package org.ihtsdo.refsetservice.rest;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapRelationService;
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
 * Controller for /mapRelation endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class MapRelationController extends BaseController {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MapRelationController.class);

    /**
     * Add the Map Relation.
     *
     * @param mapProject the mapProject
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/mapRelation", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Add map relation.  This call requires authentication with the correct role.", tags = {
        "map relation"
    }, responses = {
        @ApiResponse(responseCode = "201", description = "Successfully create map map relation"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Not Found"), @ApiResponse(responseCode = "409", description = "Conflict"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    }, requestBody = @RequestBody(description = "Map Project to add", required = true, content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = MapRelation.class))
    }))
    @RecordMetric
    public @ResponseBody ResponseEntity<MapRelation> createMapRelation(final MapRelation mapRelation) throws Exception {

        if (StringUtils.isBlank(mapRelation.getTerminologyId())) {
            final String errorMessage = "Map relation terminology id is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        if (StringUtils.isBlank(mapRelation.getName())) {
            final String errorMessage = "Map relation name is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapRelation newMapRelation = MapRelationService.createMapRelation(service, tempUser, mapRelation);

            service.commit();

            return new ResponseEntity<>(newMapRelation, HttpStatus.CREATED);

        } catch (final Exception e) {
            handleException(e);
            
            LOG.error("Failed to create map relation", e);
            
            return null;
        }
    }

    /**
     * Return the mapRelation.
     *
     * @param id the id
     * @return the mapRelation
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapRelation/{id}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapRelation", tags = {
        "mapRelation"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information")
    })
    @Parameters({
        @Parameter(name = "id", description = "MapRelation id, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public ResponseEntity<MapRelation> getMapRelation(@PathVariable(value = "id") final String id) throws Exception {

        // no auth required
        try (final TerminologyService service = new TerminologyService()) {
            final MapRelation mapRelation = MapRelationService.getMapRelation(service, id);
            return new ResponseEntity<>(mapRelation, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Return the map Relations.
     *
     * @return the map Relations
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapRelation/", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get all map Relations", tags = {
        "mapRelation"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information")
    })
    @RecordMetric
    public ResponseEntity<ResultList<MapRelation>> getMapRelations() throws Exception {
        // no auth required

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<MapRelation> results = MapRelationService.getMapRelations(service);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Update the Map Relation.
     *
     * @param mapProject the mapProject
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapRelation/{id}", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update map relation.  This call requires authentication with the correct role.", tags = {
        "map relation"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Update create map map relation"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Not Found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    }, requestBody = @RequestBody(description = "Map Project to update", required = true, content = {
        @Content(mediaType = "application/json", schema = @Schema(implementation = MapRelation.class))
    }))
    @RecordMetric
    public @ResponseBody ResponseEntity<MapRelation> updateMapRelation(@PathVariable(value = "id") final String id, final MapRelation mapRelation)
        throws Exception {

        if (mapRelation == null || !StringUtils.equals(id, mapRelation.getId())) {

            final String errorMessage = "Map relation is null or mapRelation id does not match id in URL.";
            throw new RestException(false, 404, "Not found", errorMessage);
        }

        if (StringUtils.isBlank(mapRelation.getTerminologyId())) {
            final String errorMessage = "Map relation terminology id is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        if (StringUtils.isBlank(mapRelation.getName())) {
            final String errorMessage = "Map relation name is required.";
            throw new RestException(false, 417, "Expectation failed", errorMessage);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User tempUser = new User("testUser", "testPassword", "testRole", "testOrganization", "testEmail", null);

            service.setModifiedBy(tempUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapRelation newMapRelation = MapRelationService.updateMapRelation(service, tempUser, mapRelation);

            service.commit();

            return new ResponseEntity<>(newMapRelation, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Search MapRelations.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapRelation/search", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Find map relations.", tags = {
        "mapRelation"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    // @ModelAttribute API params documented in SearchParameter
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<MapRelation>> getMapRelations(@ModelAttribute final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {
        // no auth required

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<MapRelation> results = MapRelationService.getMapRelations(service, searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

}
