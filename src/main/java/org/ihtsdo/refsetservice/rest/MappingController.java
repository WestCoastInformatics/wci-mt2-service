/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapProjectService;
import org.ihtsdo.refsetservice.terminologyservice.MappingService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The Class MappingConroller.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class MappingController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingController.class);

    /**
     * Gets the mappings.
     *
     * @param mapSetCode the map set code
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @param searchParameters the search parameters
     * @return the mappings
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetCode}/mappings", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. 447562003", required = true),
        @Parameter(name = "filter", description = "Text to search, e.g. Brain", required = false),
        @Parameter(name = "showOverriddenEntries", description = "Show underlying entries that have been overridden by this extension", required = false),
        @Parameter(name = "conceptCodes", description = "Comma delimited list of concept codes, e.g. 880057004,880057005", required = false)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultListMapping> getMappings(@PathVariable(value = "mapSetCode") final String mapSetCode,
        @RequestParam(required = false) final String filter, @RequestParam(required = false, defaultValue = "true") boolean showOverriddenEntries,
        @RequestParam(required = false) final String conceptCodes, @ModelAttribute final SearchParameters searchParameters) throws Exception {

        LOG.info("Mappings for a Mapset {}: {}", mapSetCode, searchParameters);
        // final User authUser = authorizeUser(request);

        try {
            final SearchParameters sp = (searchParameters != null) ? searchParameters : new SearchParameters();
            if (sp.getLimit() == null || sp.getLimit() == 0) {
                sp.setLimit(100);
            }
            final List<String> conceptCodesList = (StringUtils.isBlank(conceptCodes)) ? new ArrayList<>() : List.of(conceptCodes.split(","));
            final String filterString = (StringUtils.isBlank(filter)) ? StringUtils.EMPTY : StringUtils.trim(filter);

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";

            final ResultListMapping mappings = MappingService.getMappings(branch, mapSetCode, sp, filterString, showOverriddenEntries, conceptCodesList);

            return new ResponseEntity<>(mappings, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Export mappings for a map set.
     *
     * @author vparekh export the mappings.
     * @param mapSetCode the map set code
     * @param mappingExportRequest the mapping export request
     * @return zip file
     * @throws Exception the exception
     */
    @PostMapping(value = "/mapset/{mapSetCode}/export", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Export Mapset Rows", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. 447562003", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Resource> exportMappings(@PathVariable(value = "mapSetCode") final String mapSetCode,
        @RequestBody final MappingExportRequest mappingExportRequest) throws Exception {

        // final User authUser = authorizeUser(request);

        if (mappingExportRequest == null) {
            throw new RuntimeException("MappingExportRequest is required.");
        }

        if (mappingExportRequest.getColumnNames() == null || mappingExportRequest.getColumnNames().isEmpty()) {
            throw new RuntimeException("One or more column names are required.");
        }

        if (mappingExportRequest.getConceptCodes() == null || mappingExportRequest.getConceptCodes().isEmpty()) {
        	throw new RuntimeException("One or more concept selections are required.");
        }

        if (mappingExportRequest.getConceptCodes() != null && mappingExportRequest.getConceptCodes().size() > 10000) {
        	throw new RuntimeException("Maximum concept limit of 10,000 exceeded.");
        }

        try {

            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";
            final File exportMapPkg = MappingService.exportMappings(branch, mapSetCode, mappingExportRequest);

            final Resource file = new UrlResource(exportMapPkg.toURI());
            if (!file.exists() || !file.isReadable()) {
                throw new RuntimeException("Could not read the file!");
            }

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(exportMapPkg.toPath()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Import mappings.
     *
     * @author vparekh import mappings from external sources.
     * @param branch the branch
     * @param mappingFile the mappingFile
     * @return the response entity
     */
    @PostMapping(value = "/mapset/{branch:.+}/mappings/import", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Import mappings from RF2 file.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully imported RF2 mappings"),
        @ApiResponse(responseCode = "417", description = "Failed to import the mappings"),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    @RecordMetric
    public ResponseEntity<String> importMappings(@PathVariable(name = "branch", required = true) String branch,
        @RequestParam(name = "mappingFile", required = true) MultipartFile mappingFile) {

        LOG.info("RF2 Map Import file: {}", mappingFile);
        LOG.info("RF2 Map Import branch : {}", branch);
        final String decodedBranch = URLDecoder.decode(branch, StandardCharsets.UTF_8);

        try {
            // Validate the mapping file
            if (mappingFile == null || mappingFile.isEmpty()) {
                LOG.error("Mapping file is missing or empty.");
                return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
            }

            // TODO: Remove hard-coding of mapProject stuff
            final String id = "1";
            MapProject mapProject = null;

            try (final TerminologyService service = new TerminologyService()) {
                mapProject = MapProjectService.getMapProject(service, id, false);
                LOG.info("Fetched MapProject with ID: {}", id);

                // Import RF2 mappings
                final List<Mapping> updatedRF2Mappings = MappingService.importMappings(mapProject, decodedBranch, mappingFile);
                if (updatedRF2Mappings == null || updatedRF2Mappings.isEmpty())
                    LOG.info("Mapping import wasn't successful for branch: {}", decodedBranch);
                else
                    LOG.info("Mapping import was successful for branch: {}", decodedBranch);

                return new ResponseEntity<>(HttpStatus.OK);

            } catch (Exception e) {
                LOG.error("Error fetching map project: {}", e.getMessage());
                return new ResponseEntity<>("Failed to fetch map project.", HttpStatus.EXPECTATION_FAILED);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
        }
    }

    /**
     * Gets the mapping.
     *
     * @param mapSetCode the map set code
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @param searchParameters the search parameters
     * @return the mapping
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetCode}/mappings/{conceptCode}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapping. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. 447562003", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. 880057004", required = true),
        @Parameter(name = "showOverriddenEntries", description = "Show underlying entries that have been overridden by this extension", required = false)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> getMapping(@PathVariable final String mapSetCode, @PathVariable final String conceptCode,
        @RequestParam(required = false, defaultValue = "true") boolean showOverriddenEntries, @ModelAttribute final SearchParameters searchParameters)
        throws Exception {

        LOG.info("Mapping for Mapset: {}, Source Concept Code: {}, Search params: {}", mapSetCode, conceptCode, searchParameters);
        // final User authUser = authorizeUser(request);

        try {

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";
            final Mapping mapping = MappingService.getMapping(branch, mapSetCode, conceptCode, showOverriddenEntries);

            return new ResponseEntity<>(mapping, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Creates the mapping.
     *
     * @param mapSetCode the map set code
     * @param mapping the mapping
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping(value = "/mapset/{mapSetCode}", consumes = MediaType.APPLICATION_JSON)
    @Operation(summary = "Create mapping for the mapSetCode. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "201", description = "Successfully created the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> createMapping(@PathVariable final String mapSetCode, final Mapping mapping) throws Exception {

        LOG.info("Create Mapping mapSetCode:{}, mapping:{}", mapSetCode, ModelUtility.toJson(mapping));

        // TODO: Remove hard-coding of mapProject stuff
        final String id = "1";
        final Boolean includeMembers = Boolean.FALSE;
        MapProject mapProject = null;

        try (final TerminologyService service = new TerminologyService()) {
            mapProject = MapProjectService.getMapProject(service, id, includeMembers);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

        try {

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";
            final List<Mapping> mappings = new ArrayList<>();
            mappings.add(mapping);
            MappingService.createMappings(mapProject, branch, mapSetCode, mappings);
            return new ResponseEntity<Mapping>(HttpStatus.CREATED);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Creates the mappings.
     *
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping(value = "/mapset/{mapSetCode}/bulk", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Create mapping for the mapSetCode. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "201", description = "Successfully created the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<Mapping>> createMappings(@PathVariable final String mapSetCode, final List<Mapping> mappings) throws Exception {

        LOG.info("Create Mapping mapSetCode:{}, mapping:{}", mapSetCode, ModelUtility.toJson(mappings));

        // TODO: Remove hard-coding of mapProject stuff
        final String id = "1";
        final Boolean includeMembers = Boolean.FALSE;
        MapProject mapProject = null;

        try (final TerminologyService service = new TerminologyService()) {
            mapProject = MapProjectService.getMapProject(service, id, includeMembers);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

        try {

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";
            final List<Mapping> createdMappings = MappingService.createMappings(mapProject, branch, mapSetCode, mappings);
            return new ResponseEntity<>(createdMappings, HttpStatus.CREATED);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Update mapping.
     *
     * @param mapSetCode the map set code
     * @param mapping the mapping
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapset/{mapSetCode}", consumes = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update mapping for the mapSetCode. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully updated the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. &lt;uuid&gt;", required = true),
        @Parameter(description = "Mapping object to update", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> updateMapping(@PathVariable final String mapSetCode, @RequestBody final Mapping mapping) throws Exception {

        LOG.info("Update Mapping mapSetCode:{}, mapping:{}", mapSetCode, ModelUtility.toJson(mapping));

        // TODO: Remove hard-coding of mapProject stuff
        final String id = "1";
        final Boolean includeMembers = Boolean.FALSE;
        MapProject mapProject = null;

        try (final TerminologyService service = new TerminologyService()) {
            mapProject = MapProjectService.getMapProject(service, id, includeMembers);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

        try {

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";
            final List<Mapping> mappings = new ArrayList<>();
            mappings.add(mapping);
            MappingService.updateMappings(mapProject, branch, mapSetCode, mappings);

            return new ResponseEntity<>(HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Update mappings.
     *
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapset/{mapSetCode}/bulk", consumes = MediaType.APPLICATION_JSON,
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update mapping for the mapSetCode. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully updated the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetCode", description = "Mapset code identifier, e.g. &lt;uuid&gt;", required = true),
        @Parameter(description = "Mapping object to update", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<Mapping>> updateMappings(@PathVariable final String mapSetCode, @RequestBody final List<Mapping> mappings)
        throws Exception {

        LOG.info("Update Mapping mapSetCode:{}, mapping:{}", mapSetCode, ModelUtility.toJson(mappings));

        // TODO: Remove hard-coding of mapProject stuff
        final String id = "1";
        final Boolean includeMembers = Boolean.FALSE;
        MapProject mapProject = null;

        try (final TerminologyService service = new TerminologyService()) {
            mapProject = MapProjectService.getMapProject(service, id, includeMembers);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

        try {

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2025-12-15/WCITEST";
            final List<Mapping> updatedMappings = MappingService.updateMappings(mapProject, branch, mapSetCode, mappings);

            return new ResponseEntity<>(updatedMappings, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

}
