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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.FileExportType;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapProjectService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
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

    /** The local directory to store exported refset files. */
    private static String exportFileDir;

    /** Search teams API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** Static initialization. */
    static {
        exportFileDir = PropertyUtility.getProperty("mapexport.fileDir");
        //TODO - undo once property issue is figured out. 
        new File("/tmp/mapsetExport").mkdirs();
    }

    /**
     * Gets the map set.
     *
     * @param code the code
     * @return the map set
     * @throws Exception the exception
     */
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

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2024-04-15/WCITEST";
            final MapSet mapset = MapSetService.getMapSet(branch, code);
            return new ResponseEntity<>(mapset, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

    /**
     * Search mapsets.
     *
     * @param searchParameters the search parameters
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

            // TODO: determine branch.
            final String branch = "MAIN/SNOMEDCT-NO/2024-04-15/WCITEST";
            final List<MapSet> mapSets = MapSetService.getMapSets(branch);

            return new ResponseEntity<>(mapSets, HttpStatus.OK);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * RF2 Mapping Export mappings from external sources.
     *
     * @author vparekh RF2 Mapping Export mappings from external sources.
     * @param mapSetExportRequest the map set export request
     * @return A ResponseEntity containing the response message.
     * @throws Exception if an error occurs during export.
     */
    @PostMapping(value = "/mapset/export", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Generate export RF2 Map Sets files.", tags = {
        "mapset"
    }, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "MapSetExportRequest schema", required = true,
        content = @io.swagger.v3.oas.annotations.media.Content(
            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = MapSetExportRequest.class))),
        responses = {
            @ApiResponse(responseCode = "200", description = "Successfully exported RF2 mappings"),
            @ApiResponse(responseCode = "417", description = "Failed to export the mappings"),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters")
        })
    @RecordMetric
    public @ResponseBody ResponseEntity<String> exportMapset(@RequestBody final MapSetExportRequest mapSetExportRequest

    ) throws Exception {

        LOG.info("Exporting RF2 Mapset: {}", mapSetExportRequest);

        if (mapSetExportRequest == null) {
            LOG.error("Invalid request parameters: missing all parameters.");
            return new ResponseEntity<>("Invalid request parameters: missing all parameters.", HttpStatus.BAD_REQUEST);
        }

        if (StringUtils.isBlank(mapSetExportRequest.getBranch())) {
            LOG.error("Invalid request parameters: missing branch.");
            return new ResponseEntity<>("Invalid request parameters: missing branch.", HttpStatus.BAD_REQUEST);
        }

        if (StringUtils.isBlank(mapSetExportRequest.getMapSetCode())) {
            LOG.error("Invalid request parameters: missing mapSetCode.");
            return new ResponseEntity<>("Invalid request parameters: missing mapSetCode.", HttpStatus.BAD_REQUEST);
        }

        if (mapSetExportRequest.getFileFormatType() == null) {
            LOG.error("Invalid request parameters: missing format.");
            return new ResponseEntity<>("Invalid request parameters: missing format.", HttpStatus.BAD_REQUEST);
        }

        if (FileFormatType.getValues().stream().noneMatch(e -> e.equals(mapSetExportRequest.getFileFormatType()))) {
            LOG.error("Invalid request parameters: invalid format.");
            return new ResponseEntity<>("Invalid request parameters: invalid format.", HttpStatus.BAD_REQUEST);
        }

        if (mapSetExportRequest.getFileExportType() == null) {
            LOG.error("Invalid request parameters: missing exportType.");
            return new ResponseEntity<>("Invalid request parameters: missing exportType.", HttpStatus.BAD_REQUEST);
        }

        if (FileExportType.getValues().stream().noneMatch(e -> e.equals(mapSetExportRequest.getFileExportType()))) {
            LOG.error("Invalid request parameters: invalid exportType.");
            return new ResponseEntity<>("Invalid request parameters: invalid exportType.", HttpStatus.BAD_REQUEST);
        }

        if (StringUtils.isBlank(mapSetExportRequest.getTransientEffectiveTime())) {
            LOG.error("Invalid request parameters: missing transientEffectiveTime.");
            return new ResponseEntity<>("Invalid request parameters: missing transientEffectiveTime.", HttpStatus.BAD_REQUEST);
        }

        if (mapSetExportRequest.getFileFormatType() == FileFormatType.DELTA && StringUtils.isBlank(mapSetExportRequest.getStartEffectiveTime())) {
            LOG.error("Invalid request parameters: Only SNAPSHOT can use startEffectiveTime.");
            return new ResponseEntity<>("Invalid request parameters: Only DELTA can use startEffectiveTime.", HttpStatus.BAD_REQUEST);
        }

        // TODO: Remove hard-coding of mapProject stuff
        final User user = getUser();
        final String id = "1";

        try (final TerminologyService service = new TerminologyService()) {

            final MapProject mapProject = MapProjectService.getMapProject(service, id, Boolean.FALSE);
            // User user = SecurityService.getUserFromSession(); //No auth

            final String jobId = MapSetService.exportMapSet(user, mapProject, mapSetExportRequest);
            final String responseMessage = "{\"url\": \"job/" + jobId + "\"}";
            return new ResponseEntity<>(responseMessage, HttpStatus.OK);

        } catch (final IllegalArgumentException e) {

            LOG.error("Invalid request parameters: {}", e.getMessage(), e);
            return new ResponseEntity<>("Invalid request parameters: " + e.getMessage(), HttpStatus.BAD_REQUEST);

        } catch (final Exception e) {

            LOG.error("Failed to export mapset: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Download mapset export.
     *
     * @param fileName the file name
     * @return the response entity
     * @throws Exception the exception
     */
    @Operation(summary = "Download export RF2 Map Sets files", tags = {
        "mapset"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully downloaded the file"),
        @ApiResponse(responseCode = "404", description = "File not found"), @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Parameter(name = "fileName", description = "Name of the file to download", required = true)
    @RequestMapping(method = RequestMethod.GET, value = "/mapset/export/download/{fileName}")
    public @ResponseBody ResponseEntity<Resource> downloadMapSetExport(@PathVariable(value = "fileName") final String fileName) throws Exception {

        try {

            LOG.info("downloadMapSetExport: fileName: {}", fileName);

            // no auth required

            final Path filePath = Paths.get(exportFileDir, fileName);
            final Resource file = new UrlResource(filePath.toUri());

            if (!file.exists() || !file.isReadable()) {

                throw new RuntimeException("Could not read the file!");
            }

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }
    
    // temporary method to get user
    private User getUser() {
        final User user = new User();
        user.setName("WCITEST");
        user.setUserName("WCITEST");
        return user;
    }

}
