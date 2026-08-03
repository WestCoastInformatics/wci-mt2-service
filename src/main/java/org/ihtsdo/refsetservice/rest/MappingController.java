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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapNote;
import org.ihtsdo.refsetservice.model.MapNoteImportResult;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.BranchService;
import org.ihtsdo.refsetservice.terminologyservice.MapNoteService;
import org.ihtsdo.refsetservice.terminologyservice.MapProjectService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.terminologyservice.MappingService;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
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
import org.springframework.web.server.ResponseStatusException;

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
     * @param mapSetInternalId the map set internal id
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @param query the query
     * @param limit the limit
     * @param offset the offset
     * @param activeOnly the active only
     * @param sort the sort
     * @param sortAscending the sort ascending
     * @param editing the editing
     * @param searchAfter the search after
     * @param request the request
     * @return the mappings
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetInternalId}/mappings", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetInternalId", description = "Mapset internal id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "filter", description = "Search text: SNOMED terms on the map source, map target code (e.g. ICD-10 R07.4), or a 6–18 digit SNOMED source concept id", required = false),
        @Parameter(name = "showOverriddenEntries", description = "Show underlying entries that have been overridden by this extension", required = false),
        @Parameter(name = "conceptCodes", description = "Comma delimited list of concept codes, e.g. 880057004,880057005", required = false),
        @Parameter(name = "query", description = "The search query", required = false),
        @Parameter(name = "limit", description = "Maximum number of search results", required = false),
        @Parameter(name = "offset", description = "Start index of search results", required = false),
        @Parameter(name = "activeOnly", description = "Only active content", required = false),
        @Parameter(name = "sort", description = "Sort field for search results", required = false),
        @Parameter(name = "sortAscending", description = "Sort ascending (true) or descending (false)", required = false),
        @Parameter(name = "editing", description = "Search is for editing", required = false),
        @Parameter(name = "searchAfter", description = "Search after cursor", required = false)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultListMapping> getMappings(@PathVariable(value = "mapSetInternalId") final String mapSetInternalId,
        @RequestParam(required = false) final String filter, @RequestParam(required = false, defaultValue = "true") boolean showOverriddenEntries,
        @RequestParam(required = false) final String conceptCodes, @RequestParam(required = false) final String query,
        @RequestParam(required = false) final Integer limit, @RequestParam(required = false) final Integer offset,
        @RequestParam(required = false) final Boolean activeOnly, @RequestParam(required = false) final String sort,
        @RequestParam(required = false) final Boolean sortAscending, @RequestParam(required = false) final Boolean editing,
        @RequestParam(required = false) final String searchAfter, final HttpServletRequest request) throws Exception {

        requireSessionUser(request);

        final SearchParameters sp = new SearchParameters();
        sp.setQuery(query);
        sp.setLimit(limit != null && limit > 0 ? limit : 100);
        sp.setOffset(offset);
        sp.setActiveOnly(activeOnly);
        sp.setSort(sort);
        sp.setSortAscending(sortAscending);
        sp.setEditing(Boolean.TRUE.equals(editing));
        sp.setSearchAfter(searchAfter);
        LOG.info("Mappings for a Mapset {}: {}", mapSetInternalId, sp);
        final long controllerStartMs = System.currentTimeMillis();

        try (final TerminologyService service = new TerminologyService()) {
            final List<String> conceptCodesList = parseConceptCodes(conceptCodes);
            final String filterString = (StringUtils.isBlank(filter)) ? StringUtils.EMPTY : StringUtils.trim(filter);

            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            String branch = BranchService.getMapSetBranchPath(mapSet);
            if (StringUtils.isBlank(branch)) {
                branch = MapSetService.resolveBranchFromMapSets(service, mapSetInternalId);
            }
            if (StringUtils.isBlank(branch) || "empty".equals(branch) || "none".equals(branch)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "map_sets.branchPath is required for map set " + mapSetInternalId + ". Database is missing required path data.");
            }

            final ResultListMapping mappings = MappingService.getMappings(branch, mapSet, sp, filterString, showOverriddenEntries, conceptCodesList);

            // Keep the returned order aligned to the incoming `conceptCodes` list (when provided).
            if (conceptCodesList != null && !conceptCodesList.isEmpty()) {
                reorderMappingsByConceptCodes(mappings, conceptCodesList);
            }

            MapNoteService.attachNotes(service, mapSet, mappings);

            LOG.info("getMappings HTTP done mapSet={} {}ms items={} total={}", mapSetInternalId, System.currentTimeMillis() - controllerStartMs,
                mappings.getItems().size(), mappings.getTotal());
            return new ResponseEntity<>(mappings, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Parses the concept codes.
     *
     * @param conceptCodes the concept codes
     * @return the list
     */
    private static List<String> parseConceptCodes(final String conceptCodes) {

        if (StringUtils.isBlank(conceptCodes)) {
            return new ArrayList<>();
        }

        final String[] parts = conceptCodes.split(",");
        final List<String> codes = new ArrayList<>(parts.length);
        for (final String part : parts) {
            final String trimmed = part == null ? StringUtils.EMPTY : StringUtils.trim(part);
            if (StringUtils.isNotBlank(trimmed)) {
                codes.add(trimmed);
            }
        }
        return codes;
    }

    /**
     * Reorder mappings by concept codes.
     *
     * @param mappings the mappings
     * @param conceptCodesList the concept codes list
     */
    private static void reorderMappingsByConceptCodes(final ResultListMapping mappings, final List<String> conceptCodesList) {

        if (mappings == null || mappings.getItems().isEmpty()) {
            return;
        }

        // Preserve input ordering while deduplicating exact matches.
        final Set<String> orderedConceptCodes = new LinkedHashSet<>(conceptCodesList);

        final Map<String, Mapping> mappingByConceptCode = new HashMap<>();
        for (final Mapping mapping : mappings.getItems()) {
            if (mapping != null && StringUtils.isNotBlank(mapping.getCode())) {
                mappingByConceptCode.putIfAbsent(mapping.getCode(), mapping);
            }
        }

        final Set<String> addedConceptCodes = new HashSet<>();
        final List<Mapping> reordered = new ArrayList<>(mappings.getItems().size());

        // Add mappings requested by concept code in the exact order requested.
        for (final String conceptCode : orderedConceptCodes) {
            final Mapping mapping = mappingByConceptCode.get(conceptCode);
            if (mapping != null) {
                reordered.add(mapping);
                addedConceptCodes.add(conceptCode);
            }
        }

        // Append any non-requested mappings (e.g. generated by `filter`) in their original order.
        for (final Mapping mapping : mappings.getItems()) {
            if (mapping == null || StringUtils.isBlank(mapping.getCode())) {
                reordered.add(mapping);
                continue;
            }
            if (addedConceptCodes.contains(mapping.getCode())) {
                continue;
            }
            reordered.add(mapping);
        }

        mappings.setItems(reordered);
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

        try (final TerminologyService service = new TerminologyService()) {

            final org.ihtsdo.refsetservice.model.MapSet mapSet = MapSetService.findMapSetByRefSetCode(service, mapSetCode);
            if (mapSet == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            String branch = BranchService.getMapSetBranchPath(mapSet);
            if (StringUtils.isBlank(branch)) {
                branch = MapSetService.resolveBranchFromMapSets(service, mapSetCode);
            }
            if (StringUtils.isBlank(branch) || "empty".equals(branch) || "none".equals(branch)) {
                throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "map_sets.branchPath is required for map set " + mapSetCode + ". Database is missing required path data.");
            }
            final File exportMapPkg = MappingService.exportMappings(branch, mapSetCode, mappingExportRequest, mapSet);

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

            try (final TerminologyService service = new TerminologyService()) {
                final MapSet mapSet = MapSetService.findMapSetByBranchPath(service, decodedBranch);
                if (mapSet == null || mapSet.getMapProject() == null) {
                    LOG.error("No map set or map project found for branch: {}", decodedBranch);
                    return new ResponseEntity<>("No map set or map project found for branch.", HttpStatus.EXPECTATION_FAILED);
                }
                final MapProject mapProject = MapProjectService.getMapProject(service, mapSet.getMapProject().getId(), false);
                LOG.info("Fetched MapProject with ID: {}", mapSet.getMapProject().getId());

                // Import RF2 mappings
                final List<Mapping> updatedRF2Mappings = MappingService.importMappings(mapProject, decodedBranch, mappingFile, mapSet);
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
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @param searchParameters the search parameters
     * @return the mapping
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapping. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"), @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Resource not found"), @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetInternalId", description = "Mapset internal id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. 880057004", required = true),
        @Parameter(name = "showOverriddenEntries", description = "Show underlying entries that have been overridden by this extension", required = false),
        @Parameter(name = "query", description = "The search query", required = false),
        @Parameter(name = "limit", description = "Maximum number of search results", required = false),
        @Parameter(name = "offset", description = "Start index of search results", required = false),
        @Parameter(name = "activeOnly", description = "Only active content", required = false),
        @Parameter(name = "sort", description = "Sort field for search results", required = false),
        @Parameter(name = "sortAscending", description = "Sort ascending (true) or descending (false)", required = false),
        @Parameter(name = "editing", description = "Search is for editing", required = false),
        @Parameter(name = "searchAfter", description = "Search after cursor", required = false)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> getMapping(@PathVariable final String mapSetInternalId, @PathVariable final String conceptCode,
        @RequestParam(required = false, defaultValue = "true") boolean showOverriddenEntries,
        @Parameter(hidden = true) @ModelAttribute final SearchParameters searchParameters) throws Exception {

        LOG.info("Mapping for Mapset: {}, Source Concept Code: {}, Search params: {}", mapSetInternalId, conceptCode, searchParameters);
        // final User authUser = authorizeUser(request);

        try (final TerminologyService service = new TerminologyService()) {

            final String branch = MapSetService.resolveBranchFromMapSets(service, mapSetInternalId);
            if (StringUtils.isBlank(branch) || "empty".equals(branch) || "none".equals(branch)) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            final Mapping mapping = MappingService.getMapping(branch, conceptCode, showOverriddenEntries, mapSet);
            MapNoteService.attachNotes(service, mapSet, mapping);

            return new ResponseEntity<>(mapping, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Creates the mapping.
     *
     * @param mapSetInternalId the map set internal id
     * @param mapping the mapping
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping(value = "/mapset/{mapSetInternalId}", consumes = MediaType.APPLICATION_JSON)
    @Operation(summary = "Create mapping for the mapSetInternalId. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "201", description = "Successfully created the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetInternalId", description = "Mapset internal id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> createMapping(@PathVariable final String mapSetInternalId, final Mapping mapping) throws Exception {

        LOG.info("Create Mapping mapSetInternalId:{}, mapping:{}", mapSetInternalId, ModelUtility.toJson(mapping));

        try (final TerminologyService service = new TerminologyService()) {
            final MapSet mapSet;
            try {
                mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            } catch (final Exception e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final String branch = BranchService.getMapSetBranchPath(mapSet);
            if (StringUtils.isBlank(branch)) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final List<Mapping> mappings = new ArrayList<>();
            mappings.add(mapping);
            MappingService.createMappings(mapSet.getMapProject(), branch, mapSet.getRefSetCode(), mappings);
            return new ResponseEntity<Mapping>(HttpStatus.CREATED);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Creates the mappings.
     *
     * @param mapSetInternalId the map set internal id
     * @param mappings the mappings
     * @return the response entity
     * @throws Exception the exception
     */
    @PostMapping(value = "/mapset/{mapSetInternalId}/bulk", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Create mapping for the mapSetInternalId. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "201", description = "Successfully created the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetInternalId", description = "Mapset internal id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(name = "conceptCode", description = "Source concept code identifier, e.g. &lt;uuid&gt;", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<Mapping>> createMappings(@PathVariable final String mapSetInternalId, final List<Mapping> mappings)
        throws Exception {

        LOG.info("Create Mapping mapSetInternalId:{}, mapping:{}", mapSetInternalId, ModelUtility.toJson(mappings));

        try (final TerminologyService service = new TerminologyService()) {
            final MapSet mapSet;
            try {
                mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            } catch (final Exception e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final String branch = BranchService.getMapSetBranchPath(mapSet);
            if (StringUtils.isBlank(branch)) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final List<Mapping> createdMappings = MappingService.createMappings(mapSet.getMapProject(), branch, mapSet.getRefSetCode(), mappings);
            return new ResponseEntity<>(createdMappings, HttpStatus.CREATED);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Update mapping.
     *
     * @param mapSetInternalId the map set internal id
     * @param mapping the mapping
     * @param request the request
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapset/{mapSetInternalId}", consumes = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update mapping for the mapSetInternalId. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully updated the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetInternalId", description = "Mapset internal id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(description = "Mapping object to update", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<Mapping> updateMapping(@PathVariable final String mapSetInternalId, @RequestBody final Mapping mapping,
        final HttpServletRequest request) throws Exception {

        LOG.info("Update Mapping mapSetInternalId:{}, mapping:{}", mapSetInternalId, ModelUtility.toJson(mapping));

        try (final TerminologyService service = new TerminologyService()) {
            final MapSet mapSet;
            try {
                mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            } catch (final Exception e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final String branch = BranchService.getMapSetBranchPath(mapSet);
            if (StringUtils.isBlank(branch)) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final List<Mapping> mappings = new ArrayList<>();
            mappings.add(mapping);
            final User user = requireSessionUser(request);
            MappingWorkflowService.canUserEditMappings(user, mapSet, mappings, service);
            MappingService.updateMappings(mapSet.getMapProject(), branch, mapSet.getRefSetCode(), mappings, mapSet);

            return new ResponseEntity<>(mapping, HttpStatus.OK);

        } catch (final Exception e) {

            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Update mappings.
     *
     * @param mapSetInternalId the map set internal id
     * @param mappings the mappings
     * @param request the request
     * @return the response entity
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapset/{mapSetInternalId}/bulk", consumes = MediaType.APPLICATION_JSON,
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update mapping for the mapSetInternalId. This call requires authentication with the correct role.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully updated the mapping"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden"), @ApiResponse(responseCode = "404", description = "Resource not found"),
        @ApiResponse(responseCode = "417", description = "Failed Expectation")
    })
    @Parameters({
        @Parameter(name = "mapSetInternalId", description = "Mapset internal id, e.g. &lt;uuid&gt;", required = true),
        @Parameter(description = "Mapping object to update", required = true)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<List<Mapping>> updateMappings(@PathVariable final String mapSetInternalId, @RequestBody final List<Mapping> mappings,
        final HttpServletRequest request) throws Exception {

        LOG.info("Update Mapping mapSetInternalId:{}, mapping:{}", mapSetInternalId, ModelUtility.toJson(mappings));

        try (final TerminologyService service = new TerminologyService()) {
            final MapSet mapSet;
            try {
                mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            } catch (final Exception e) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final String branch = BranchService.getMapSetBranchPath(mapSet);
            if (StringUtils.isBlank(branch)) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final User user = requireSessionUser(request);
            MappingWorkflowService.canUserEditMappings(user, mapSet, mappings, service);
            final List<Mapping> updatedMappings = MappingService.updateMappings(mapSet.getMapProject(), branch, mapSet.getRefSetCode(), mappings, mapSet);

            return new ResponseEntity<>(updatedMappings, HttpStatus.OK);

        } catch (final Exception e) {

            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Get currently assigned mapping workflows for the authenticated user.
     *
     * @param limit max rows (default 10, max 1000)
     * @param offset start index (default 0)
     * @param sort sort field (default assignedAt)
     * @param sortAscending true for oldest first (default false)
     * @param mapProjectId optional map project filter
     * @param mapSetId optional map set filter
     * @param workflowStatus optional workflow status filter
     * @param request the request
     * @return assigned mapping workflow rows
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mappings/workflow/assigned", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get currently assigned mapping workflows for the current user.", tags = {
        "mapping"
    })
    @Parameters({
        @Parameter(name = "limit", description = "Maximum number of results (default 10, max 1000)", required = false),
        @Parameter(name = "offset", description = "Start index of results (default 0)", required = false),
        @Parameter(name = "sort", description = "Sort field (default assignedAt)", required = false),
        @Parameter(name = "sortAscending", description = "Sort ascending (true) or descending (false, default)", required = false),
        @Parameter(name = "mapProjectId", description = "Optional map project id filter", required = false),
        @Parameter(name = "mapSetId", description = "Optional map set id filter", required = false),
        @Parameter(name = "workflowStatus", description = "Optional workflow status filter", required = false)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<MappingWorkflow>> getAssignedMappingWorkflows(@RequestParam(required = false) final Integer limit,
        @RequestParam(required = false) final Integer offset, @RequestParam(required = false) final String sort,
        @RequestParam(required = false) final Boolean sortAscending, @RequestParam(required = false) final String mapProjectId,
        @RequestParam(required = false) final String mapSetId, @RequestParam(required = false) final String workflowStatus, final HttpServletRequest request)
        throws Exception {

        final User user = requireSessionUser(request);

        MapWorkflowStatus statusFilter = null;
        if (StringUtils.isNotBlank(workflowStatus)) {
            try {
                statusFilter = MapWorkflowStatus.fromString(workflowStatus);
            } catch (final IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workflowStatus: " + workflowStatus);
            }
        }

        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLimit(limit);
        searchParameters.setOffset(offset);
        searchParameters.setSort(sort);
        searchParameters.setSortAscending(sortAscending);

        try (final TerminologyService service = new TerminologyService()) {
            final ResultList<MappingWorkflow> results =
                MappingWorkflowService.findAssignedWorkflows(service, user.getUserName(), mapProjectId, mapSetId, statusFilter, searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Get mapping workflows most recently modified by the authenticated user.
     *
     * <p>Uses {@code MappingWorkflow.modifiedBy}/{@code modified} (workflow-row updates), not Snowstorm
     * content edits.
     *
     * @param limit max rows (default 10, max 100)
     * @param offset start index (default 0)
     * @param sort sort field (default modified)
     * @param sortAscending true for oldest first (default false)
     * @param mapProjectId optional map project filter
     * @param mapSetId optional map set filter
     * @param request the request
     * @return recently modified mapping workflow rows
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mappings/workflow/recentlyModified", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get mapping workflows most recently modified by the current user.", tags = {
        "mapping"
    })
    @Parameters({
        @Parameter(name = "limit", description = "Maximum number of results (default 10, max 100)", required = false),
        @Parameter(name = "offset", description = "Start index of results (default 0)", required = false),
        @Parameter(name = "sort", description = "Sort field (default modified)", required = false),
        @Parameter(name = "sortAscending", description = "Sort ascending (true) or descending (false, default)", required = false),
        @Parameter(name = "mapProjectId", description = "Optional map project id filter", required = false),
        @Parameter(name = "mapSetId", description = "Optional map set id filter", required = false)
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<ResultList<MappingWorkflow>> getRecentlyModifiedMappingWorkflows(
        @RequestParam(required = false) final Integer limit, @RequestParam(required = false) final Integer offset,
        @RequestParam(required = false) final String sort, @RequestParam(required = false) final Boolean sortAscending,
        @RequestParam(required = false) final String mapProjectId, @RequestParam(required = false) final String mapSetId,
        final HttpServletRequest request) throws Exception {

        final User user = requireSessionUser(request);

        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLimit(limit);
        searchParameters.setOffset(offset);
        searchParameters.setSort(sort);
        searchParameters.setSortAscending(sortAscending);

        try (final TerminologyService service = new TerminologyService()) {
            final ResultList<MappingWorkflow> results =
                MappingWorkflowService.findRecentlyModifiedWorkflows(service, user.getUserName(), mapProjectId, mapSetId, searchParameters);
            return new ResponseEntity<>(results, HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Get the per-concept mapping workflow state.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param request the request
     * @return the mapping workflow row
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/workflowStatus",
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get per-concept mapping workflow state.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<MappingWorkflow> getMappingWorkflowStatus(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, final HttpServletRequest request) throws Exception {

        final User user = requireSessionUser(request);

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final MappingWorkflow workflow = MappingWorkflowService.ensureWorkflowForConcept(service, mapSet, conceptCode);
            return new ResponseEntity<>(workflow, HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Change per-concept mapping workflow state.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param action the workflow action
     * @param notes transition notes
     * @param assignToUser target user for REASSIGN
     * @param request the request
     * @return the updated mapping workflow row
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/workflowStatus",
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Change per-concept mapping workflow state.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<MappingWorkflow> setMappingWorkflowStatus(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, @RequestParam final MappingWorkflowAction action,
        @RequestParam(required = false) final String notes, @RequestParam(required = false) final String assignToUser,
        final HttpServletRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final User user = requireSessionUser(request);
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final MapProject mapProject = MappingWorkflowService.loadMapProject(service, mapSet);
            if (mapProject == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final MappingWorkflow workflow = MappingWorkflowService.ensureWorkflowForConcept(service, mapSet, conceptCode);
            if (workflow == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                service, user, action, workflow, mapSet, mapProject, notes, assignToUser);
            service.commit();
            return new ResponseEntity<>(updated, HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Get per-concept mapping workflow history.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param searchParameters optional paging/sorting
     * @param request the request
     * @return the workflow history rows
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/workflowHistory",
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get per-concept mapping workflow history.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<ResultList<MappingWorkflowHistory>> getMappingWorkflowHistory(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, @ModelAttribute final SearchParameters searchParameters, final HttpServletRequest request) throws Exception {

        final User user = requireSessionUser(request);

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            final MappingWorkflow workflow = MappingWorkflowService.ensureWorkflowForConcept(service, mapSet, conceptCode);
            final ResultList<MappingWorkflowHistory> history = MappingWorkflowService.getWorkflowHistory(service, workflow, searchParameters);
            return new ResponseEntity<>(history, HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Import map notes from a pipe-delimited file for a map set.
     * <p>
     * File format (optional header): {@code conceptCode|User name|Date|Map note text}
     * </p>
     * Validates the entire file first. Unknown usernames are reported and nothing is imported.
     *
     * @param mapSetInternalId the map set internal id
     * @param notesFile the notes file
     * @param request the HTTP request
     * @return created notes on success, or validation preview on failure
     * @throws Exception the exception
     */
    @PostMapping(value = "/mapset/{mapSetInternalId}/notes/import", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Import map notes from a pipe-delimited file.", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully imported map notes"),
        @ApiResponse(responseCode = "400", description = "Validation failed; response body is the preview"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "404", description = "Map set not found")
    })
    @RecordMetric
    public @ResponseBody ResponseEntity<?> importMappingNotes(@PathVariable final String mapSetInternalId,
        @RequestParam(name = "notesFile", required = true) final MultipartFile notesFile, final HttpServletRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final User user = requireSessionUser(request);
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final MapNoteImportResult result = MapNoteService.importNotes(service, user, mapSet, notesFile);
            if (!result.isSuccess()) {
                service.rollback();
                return new ResponseEntity<>(result.getPreview(), HttpStatus.BAD_REQUEST);
            }

            service.commit();
            return new ResponseEntity<>(result.getNotes(), HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * List map notes for a source concept on a map set.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param request the HTTP request
     * @return the notes
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/notes",
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "List map notes for a source concept.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<List<MapNote>> getMappingNotes(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, final HttpServletRequest request) throws Exception {

        final User user = requireSessionUser(request);

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy(user.getUserName());
            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
            return new ResponseEntity<>(MapNoteService.getNotes(service, mapSet, conceptCode), HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Create a map note for a source concept on a map set.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param mapNote the note payload (uses note text)
     * @param request the HTTP request
     * @return the created note
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.POST, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/notes",
        consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Create a map note for a source concept.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<MapNote> createMappingNote(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, @RequestBody final MapNote mapNote, final HttpServletRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final User user = requireSessionUser(request);
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final String noteText = mapNote == null ? null : mapNote.getNote();
            final MapNote created = MapNoteService.createNote(service, user, mapSet, conceptCode, noteText);
            service.commit();
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Update a map note for a source concept on a map set.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param noteId the note id
     * @param mapNote the note payload (uses note text)
     * @param request the HTTP request
     * @return the updated note
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.PUT, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/notes/{noteId}",
        consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Update a map note for a source concept.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<MapNote> updateMappingNote(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, @PathVariable final String noteId, @RequestBody final MapNote mapNote,
        final HttpServletRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final User user = requireSessionUser(request);
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            final String noteText = mapNote == null ? null : mapNote.getNote();
            final MapNote updated = MapNoteService.updateNote(service, user, mapSet, conceptCode, noteId, noteText);
            service.commit();
            return new ResponseEntity<>(updated, HttpStatus.OK);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Delete a map note for a source concept on a map set.
     *
     * @param mapSetInternalId the map set internal id
     * @param conceptCode the source concept code
     * @param noteId the note id
     * @param request the HTTP request
     * @return empty response
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.DELETE, value = "/mapset/{mapSetInternalId}/mappings/{conceptCode}/notes/{noteId}",
        produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Delete a map note for a source concept.", tags = {
        "mapset"
    })
    public @ResponseBody ResponseEntity<Void> deleteMappingNote(@PathVariable final String mapSetInternalId,
        @PathVariable final String conceptCode, @PathVariable final String noteId, final HttpServletRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final User user = requireSessionUser(request);
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final MapSet mapSet = MapSetService.getMapSet(service, mapSetInternalId);
            if (mapSet.getMapProject() == null) {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }

            MapNoteService.deleteNote(service, mapSet, conceptCode, noteId);
            service.commit();
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } catch (final Exception e) {
            rethrowHandled(e);
            return null;
        }
    }

    /**
     * Returns the authenticated user from the HTTP session.
     *
     * @param httpServletRequest the http servlet request
     * @return the session user
     * @throws Exception the exception
     * @throws ResponseStatusException when no authenticated user is present
     */
    private User requireSessionUser(final HttpServletRequest httpServletRequest) throws Exception {

        final Object testSessionUser = httpServletRequest.getAttribute(SecurityService.TEST_SESSION_USER_ATTRIBUTE);
        if (testSessionUser instanceof User) {
            final User user = (User) testSessionUser;
            if (!SecurityService.GUEST_USERNAME.equals(user.getUserName())) {
                return user;
            }
        }

        final HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            final Object sessionUser = session.getAttribute(SecurityService.SESSION_USER_OBJECT_KEY);
            if (sessionUser instanceof User) {
                final User user = (User) sessionUser;
                if (!SecurityService.GUEST_USERNAME.equals(user.getUserName())) {
                    return user;
                }
            }
        }

        final User user = SecurityService.getUserFromSession();
        if (user == null || SecurityService.GUEST_USERNAME.equals(user.getUserName())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }

    /**
     * Rethrow {@link ResponseStatusException}; otherwise delegate to {@link #handleException(Exception)}.
     *
     * @param exception the exception
     * @throws Exception the exception
     */
    private void rethrowHandled(final Exception exception) throws Exception {

        final ResponseStatusException responseStatusException = findResponseStatusException(exception);
        if (responseStatusException != null) {
            throw responseStatusException;
        }
        handleException(exception);
    }

    /**
     * Find response status exception.
     *
     * @param exception the exception
     * @return the response status exception
     */
    private ResponseStatusException findResponseStatusException(final Throwable exception) {

        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ResponseStatusException) {
                return (ResponseStatusException) current;
            }
        }
        return null;
    }

}
