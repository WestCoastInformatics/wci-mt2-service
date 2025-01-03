package org.ihtsdo.refsetservice.rest;

import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapProjectService;
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
import org.springframework.web.bind.annotation.PostMapping;
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

	/** The request. */
	@SuppressWarnings("unused")
	@Autowired
	private HttpServletRequest request;

	/** Search teams API notes. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

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
	 * @param includeMembers   the include members
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
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
	@RecordMetric
	public @ResponseBody ResponseEntity<List<MapSet>> getMapSets(
			@ModelAttribute final SearchParameters searchParameters) throws Exception {

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
	 * @param branch                 The branch (e.g.,
	 *                               MAIN/SNOMEDCT-NO/2024-04-15/WCITEST).
	 * @param MapsetId               The RF2 MapsetId (e.g., "447562003" for the
	 *                               ICD10NO map).
	 * @param refsetInternalId       The internal refset ID.
	 * @param format                 The export format (e.g., rf2, rf2_with_names,
	 *                               sctids).
	 * @param exportType             The export type (e.g., SNAPSHOT, DELTA).
	 * @param fileNameDate           The file name date (e.g., 20241226).
	 * @param languageId             The language ID (optional, e.g., EN).
	 * @param startEffectiveTime     The start effective time (optional).
	 * @param transientEffectiveTime The transient effective time (optional).
	 * @param exportMetadata         Whether to export metadata (optional).
	 * @return A ResponseEntity containing the response message.
	 * @throws Exception if an error occurs during export.
	 */
	@RecordMetric
	@PostMapping(value = "/export/", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Export RF2 Mapsets.", tags = { "mapset" }, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully exported RF2 mappings"),
			@ApiResponse(responseCode = "417", description = "Failed to export the mappings"),
			@ApiResponse(responseCode = "400", description = "Invalid request parameters") })
	@Parameters({
			@Parameter(name = "branch", description = "Branch, e.g., MAIN/SNOMEDCT-NO/2024-04-15/WCITEST", required = true),
			@Parameter(name = "mapsetId", description = "RF2 MapsetId, e.g., '447562003' for the ICD10NO map", required = true),
			@Parameter(name = "format", description = "Format, e.g., rf2, rf2_with_names, sctids", required = true),
			@Parameter(name = "exportType", description = "Export type, e.g., SNAPSHOT, DELTA", required = false),
			@Parameter(name = "fileNameDate", description = "File name date, e.g., 20250102", required = true),
			@Parameter(name = "languageId", description = "Language ID, e.g., EN", required = false),
			@Parameter(name = "startEffectiveTime", description = "Start effective time", required = false),
			@Parameter(name = "transientEffectiveTime", description = "Transient effective time", required = true),
			@Parameter(name = "exportMetadata", description = "Whether to export metadata", required = false) })
	public @ResponseBody ResponseEntity<String> exportMapset(
			@RequestParam(name = "branch", required = true) String branch,
			@RequestParam(name = "mapsetId", required = true) String mapsetId,
			@RequestParam(name = "format", required = true) String format,
			@RequestParam(name = "exportType", required = true) String exportType,
			@RequestParam(name = "fileNameDate", required = true) String fileNameDate,
			@RequestParam(name = "languageId", required = false) String languageId,
			@RequestParam(name = "startEffectiveTime", required = false) String startEffectiveTime,
			@RequestParam(name = "transientEffectiveTime", required = true) String transientEffectiveTime,
			@RequestParam(name = "exportMetadata", required = false, defaultValue = "false") boolean exportMetadata)
			throws Exception {
		LOG.info(
				"Exporting RF2 Mapset: MapsetId={}, Branch={}, Format={}, ExportType={}, FileNameDate={}, StartEffectiveTime={}, TransientEffectiveTime={}, ExportMetadata={}",
				mapsetId, branch, format, exportType, fileNameDate, startEffectiveTime, transientEffectiveTime,
				exportMetadata);

		// TODO: Remove hard-coding of mapProject stuff
		final String id = "1";
		final Boolean includeMembers = Boolean.FALSE;
		MapProject mapProject = null;

		try (final TerminologyService service = new TerminologyService()) {
			mapProject = MapProjectService.getMapProject(service, id, includeMembers);
			// User user = SecurityService.getUserFromSession(); //No auth

			String responseMessage = null;
			String downloadUri;

			if ("rf2".equalsIgnoreCase(format) || "rf2_with_names".equalsIgnoreCase(format)) {
				boolean withNames = "rf2_with_names".equalsIgnoreCase(format);
				if ("SNAPSHOT".equalsIgnoreCase(exportType)) {
					downloadUri = MapSetService.exportMapsetRf2(service, mapProject, branch, mapsetId, exportType,
							languageId, fileNameDate, null, transientEffectiveTime, exportMetadata, withNames); // SNAPSHOT																											
																												// startEffectiveTime
																												// not needed
																											
					responseMessage = "{\"url\": \"" + downloadUri + "\"}";
					LOG.info("SNAPSHOT Export completed successfully. Download URI: {}", downloadUri);
				} else {
					downloadUri = MapSetService.exportMapsetRf2(service, mapProject, branch, mapsetId, exportType,
							languageId, fileNameDate, startEffectiveTime, transientEffectiveTime, exportMetadata,
							withNames); // DELTA
					LOG.info("DELTA Export completed successfully. Download URI: {}", downloadUri);
				}
				
			} else if ("sctids".equalsIgnoreCase(format)) {
				downloadUri = MapSetService.exportMapsetSctidList(service, mapProject, branch, mapsetId, exportType,
						languageId, fileNameDate, startEffectiveTime, transientEffectiveTime, exportMetadata);
				responseMessage = "{\"url\": \"" + downloadUri + "\"}";
				LOG.info("SCTIDs Export completed successfully. Download URI: {}", downloadUri);
			} else {

				throw new IllegalArgumentException("Invalid format specified: " + format);
			}

			return new ResponseEntity<>(responseMessage, HttpStatus.OK);

		} catch (IllegalArgumentException e) {
			LOG.error("Invalid request parameters: {}", e.getMessage(), e);
			return new ResponseEntity<>("Invalid request parameters: " + e.getMessage(), HttpStatus.BAD_REQUEST);
		} catch (Exception e) {
			LOG.error("Failed to export mapset: {}", e.getMessage(), e);
			throw e;
		}
	}

}
