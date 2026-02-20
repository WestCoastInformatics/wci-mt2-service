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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.MapSetWorkflowHistory;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.FileExportType;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapProjectService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetWorkflowService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
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

	/** Search teams API notes. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

	/** The request. */
	@Autowired
	private HttpServletRequest request;

	/** The local directory to store exported refset files. */
	@Value("${mapexport.fileDir}")
	private String exportFileDir;

	/**
	 * Gets the map set.
	 *
	 * @param code the code
	 * @return the map set
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/mapset/{code}", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get map set. This call requires authentication with the correct role.", tags = {
			"mapset" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({ @Parameter(name = "code", description = "MapSet identifier, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity<MapSet> getMapSet(@PathVariable(value = "code") final String code) throws Exception {

		LOG.info("Get mapset {}", code);
		// final User authUser = authorizeUser(request);

		try (final TerminologyService service = new TerminologyService()) {

			final String branch = MapSetService.resolveBranchFromMapSets(service, code);
			if (branch == null) {
				LOG.error("No branch found in map_sets for map set code: {}", code);
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
			final MapSet mapset = MapSetService.getMapSet(branch, code);
			if (mapset != null) {
				mergeMapSetWithDbTracking(service, mapset);
			}
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
			"mapset" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found"),
					@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	@Parameters({
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
	public @ResponseBody ResponseEntity<List<MapSet>> getMapSets(
			@Parameter(hidden = true)
			@ModelAttribute final SearchParameters searchParameters) throws Exception {

		// #region agent log
		try {
			Files.write(Paths.get("debug-ea312a.log"), java.util.Collections.singletonList(
				"{\"sessionId\":\"ea312a\",\"runId\":\"swagger\",\"hypothesisId\":\"H1\",\"location\":\"MapSetController.getMapSets\",\"message\":\"getMapSets entered\",\"data\":{\"reached\":true},\"timestamp\":" + System.currentTimeMillis() + "}"),
				java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (Exception e) { /* ignore */ }
		// #endregion
		LOG.info("Search mapsets: {}", searchParameters);
		// final User authUser = authorizeUser(request);

		try (final TerminologyService service = new TerminologyService()) {

			final String branch = MapSetService.resolveBranchFromMapSets(service, null);
			if (branch == null) {
				LOG.error("No branch found in map_sets table");
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
			final List<MapSet> mapSets = MapSetService.getMapSets(branch);
			for (final MapSet mapSet : mapSets) {
				mergeMapSetWithDbTracking(service, mapSet);
			}
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
			"mapset" }, requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "MapSetExportRequest schema", required = true, content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = MapSetExportRequest.class))), responses = {
					@ApiResponse(responseCode = "200", description = "Successfully exported RF2 mappings"),
					@ApiResponse(responseCode = "417", description = "Failed to export the mappings"),
					@ApiResponse(responseCode = "400", description = "Invalid request parameters") })
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
			return new ResponseEntity<>("Invalid request parameters: missing transientEffectiveTime.",
					HttpStatus.BAD_REQUEST);
		}

//		if (mapSetExportRequest.getFileFormatType() == FileFormatType.DELTA
//				&& StringUtils.isBlank(mapSetExportRequest.getStartEffectiveTime())) {
//			LOG.error("Invalid request parameters: Only SNAPSHOT can use startEffectiveTime.");
//			return new ResponseEntity<>("Invalid request parameters: Only DELTA can use startEffectiveTime.",
//					HttpStatus.BAD_REQUEST);
//		}

		// startEffectiveTime is, strangely, only usable with SNAPSHOT exports, but creates a Delta.
		// If we leave it blank, it creates the exports we want correctly.
		if (!StringUtils.isBlank(mapSetExportRequest.getStartEffectiveTime())) {
			mapSetExportRequest.setStartEffectiveTime(null);
		}

		final User user = getUser();

		try (final TerminologyService service = new TerminologyService()) {

			final MapSet mapSet = MapSetService.findMapSetByRefSetCode(service, mapSetExportRequest.getMapSetCode());
			if (mapSet == null || mapSet.getMapProject() == null) {
				return new ResponseEntity<>("Map set or map project not found for mapSetCode.", HttpStatus.BAD_REQUEST);
			}
			final MapProject mapProject = MapProjectService.getMapProject(service, mapSet.getMapProject().getId(), Boolean.FALSE);

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
	@Operation(summary = "Download export RF2 Map Sets files", tags = { "mapset" }, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully downloaded the file"),
			@ApiResponse(responseCode = "404", description = "File not found"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameter(name = "fileName", description = "Name of the file to download", required = true)
	@RequestMapping(method = RequestMethod.GET, value = "/mapset/export/download/{fileName}")
	public @ResponseBody ResponseEntity<Resource> downloadMapSetExport(
			@PathVariable(value = "fileName") final String fileName) throws Exception {

		try {

			LOG.info("downloadMapSetExport: path: {}, fileName: {}", exportFileDir, fileName);

			// no auth required

			final Path filePath = Paths.get(exportFileDir, fileName);
			final Resource file = new UrlResource(filePath.toUri());

			if (!file.exists() || !file.isReadable()) {

				throw new RuntimeException("Could not read the file!");
			}

			return ResponseEntity.ok()
					.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
					.header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"")
					.contentLength(file.contentLength()).body(file);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}

	}

	/**
	 * Get Workflow history for a map set.
	 *
	 * @param mapSetInternalId the map set internal id
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the workflow history
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/mapset/{mapSetInternalId}/workflowHistory", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get Workflow history for a map set.", tags = {
			"mapset" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found"),
					@ApiResponse(responseCode = "417", description = "Expectation failed"),
	})
	@Parameters({
			@Parameter(name = "mapSetInternalId", description = "The internal ID or refSetCode of the map set.", required = true),
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
	public @ResponseBody ResponseEntity<ResultList<MapSetWorkflowHistory>> getWorkflowHistory(
			@PathVariable(value = "mapSetInternalId") final String mapSetInternalId,
			@Parameter(hidden = true) @ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

		checkBinding(bindingResult);

		final User authUser = authorizeUser(request);
		try (final TerminologyService service = new TerminologyService()) {

			final MapSet mapSet = MapSetService.getMapSetForWorkflow(service, mapSetInternalId);
			final ResultList<MapSetWorkflowHistory> results = MapSetWorkflowService.getWorkflowHistory(service, mapSet,
					searchParameters);

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Change the workflow status of a map set.
	 *
	 * @param mapSetInternalId the internal map set ID or refSetCode
	 * @param action           the action triggering the status change
	 * @param notes            Notes about the status change
	 * @return the updated map set or errors
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.POST, value = "/mapset/{mapSetInternalId}/workflowStatus")
	@Operation(summary = "Change the workflow status of a map set.", tags = {
			"mapset" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully changed the map set status. The payload contains the updated map set"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found")
	})
	@Parameters({
			@Parameter(name = "mapSetInternalId", description = "The internal ID or refSetCode of the map set.", required = true),
			@Parameter(name = "action", description = "The action triggering the status change", required = true),
			@Parameter(name = "notes", description = "Notes about the status change", required = false),
	})
	public @ResponseBody ResponseEntity<MapSet> setWorkflowStatus(
			@PathVariable(value = "mapSetInternalId") final String mapSetInternalId,
			@RequestParam final WorkflowAction action, @RequestParam(required = false) final String notes)
			throws Exception {

		authorizeUser(request);

		try (final TerminologyService service = new TerminologyService()) {

			LOG.info("setWorkflowStatus: mapSetInternalId: {}; action: {}; notes: {}", mapSetInternalId, action, notes);

			final User user = SecurityService.getUserFromSession();

			service.setModifiedBy(user.getUserName());
			service.setModifiedFlag(true);
			service.setTransactionPerOperation(false);
			service.beginTransaction();

			final MapSet mapSet = MapSetService.setWorkflowStatus(service, user, mapSetInternalId, action, notes);

			if (mapSet != null) {
				service.commit();

				LOG.info("setWorkflowStatus: updated map set: {}", ModelUtility.toJson(mapSet));
				return new ResponseEntity<>(mapSet, HttpStatus.OK);
			}

			return null;

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Modify an existing map set workflow note.
	 *
	 * @param mapSetInternalId the internal map set ID or refSetCode
	 * @param notes            the notes
	 * @return the full updated workflow history
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.PUT, value = "/mapset/{mapSetInternalId}/workflowNote")
	@Operation(summary = "Modify a map set workflow status note.", tags = {
			"mapset" }, responses = {
					@ApiResponse(responseCode = "200", description = "Successfully changed the status note. The payload contains the full updated workflow history"),
					@ApiResponse(responseCode = "400", description = "Bad request"),
					@ApiResponse(responseCode = "401", description = "Unauthorized"),
					@ApiResponse(responseCode = "403", description = "Forbidden"),
					@ApiResponse(responseCode = "404", description = "Resource not found")
	})
	@Parameters({
			@Parameter(name = "mapSetInternalId", description = "The internal ID or refSetCode of the map set.", required = true)
	})
	public @ResponseBody ResponseEntity<ResultList<MapSetWorkflowHistory>> updateWorkflowNote(
			@PathVariable(value = "mapSetInternalId") final String mapSetInternalId,
			@org.springframework.web.bind.annotation.RequestBody(required = true) final String notes) throws Exception {

		final User authUser = authorizeUser(request);
		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());

			final MapSet mapSet = MapSetService.getMapSetForWorkflow(service, mapSetInternalId);
			MapSetWorkflowService.updateWorkflowNote(service, authUser, mapSet, notes);

			final ResultList<MapSetWorkflowHistory> results =
					MapSetWorkflowService.getWorkflowHistory(service, mapSet, new SearchParameters());

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Merges branch/version from map_sets table onto MapSet from Snowstorm.
	 */
	private void mergeMapSetWithDbTracking(final TerminologyService service, final MapSet mapSet) throws Exception {

		final MapSet dbMapSet = MapSetService.findMapSetByRefSetCode(service, mapSet.getRefSetCode());
		if (dbMapSet != null) {
			BeanUtils.copyProperties(dbMapSet, mapSet);
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
