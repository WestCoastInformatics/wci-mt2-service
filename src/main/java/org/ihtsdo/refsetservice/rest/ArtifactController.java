/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.io.File;
import java.nio.file.Files;

import javax.ws.rs.core.MediaType;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Artifact;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.terminologyservice.ArtifactService;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /artifact endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Artifact Controller", version = "1.0.0", description = "Endpoints for adding, updating, and removing reference set artifacts."), tags = {
		@Tag(name = "artifact", description = "Artifact service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class ArtifactController extends BaseController {

	/** The Constant LOG. */
	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(ArtifactController.class);

	/**
	 * Returns the artifact entry.
	 *
	 * @param id the id
	 * @return the artifact entry
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/artifact/{id}")
	@Operation(summary = "Get artifact.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Artifact.class))) }, parameters = {
					@Parameter(name = "id", description = "Artifact id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)) })
	@RecordMetric
	public @ResponseBody ResponseEntity<Artifact> getArtifact(@PathVariable(value = "id") final String id)
			throws Exception {

		// no auth required
		try {

			final Artifact artifact = ArtifactService.getArtifact(id);
			return new ResponseEntity<>(artifact, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search artifact entries.
	 *
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/artifact", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Find artifacts.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Artifact.class))),
			@ApiResponse(responseCode = "417", description = "Expecation failed") })
	// @ModelAttribute API params documented in SearchParameter
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<Artifact>> findArtifacts(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		// no auth required
		try {

			if (searchParameters != null) {
				searchParameters.setActiveOnly(true);
			}
			final ResultList<Artifact> results = ArtifactService.findArtifacts(searchParameters);

			if (results != null && results.getItems() != null && !results.getItems().isEmpty()) {
				for (final Artifact artifact : results.getItems()) {
					artifact.setDownloadUrl("/artifact/" + artifact.getId() + "/file");
				}
			}

			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Adds the artifact.
	 *
	 * @param artifact  the artifact entry
	 * @param inputFile the input file
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PostMapping(value = "/artifact")
	@Operation(summary = "Add artifact. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Added artifact", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Artifact.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	@Parameters({ @Parameter(name = "artifact", description = "Artifact object", required = true) })
	@RecordMetric
	public ResponseEntity<?> addArtifact(@RequestParam final String artifactStr,
			@RequestParam("file") final MultipartFile inputFile) throws Exception {

		final User authUser = authorizeUser();

		try {

			Artifact artifact = null;
			try {
				artifact = ModelUtility.fromJson(artifactStr, Artifact.class);
			} catch (Exception e) {
				throw new RestException(false, 417, "Expectation Failed ", "Unable to parse artifact = " + artifactStr);
			}

			// TODO: check required values.

			final File file = FileUtility.saveArtifactFile(inputFile,
					artifact.getEntityType() + "-" + artifact.getEntityId(), null);

			artifact.setStoredFileName(file.getName());
			artifact.setFileName(inputFile.getOriginalFilename());

			final String fileType = (FilenameUtils.getExtension(file.getCanonicalFile().toString()));
			if (StringUtils.isNotBlank(fileType)) {
				artifact.setFileType(fileType.toUpperCase());
			}

			final Artifact newArtifact = ArtifactService.addArtifact(authUser, artifact);

			return ResponseEntity.status(HttpStatus.ACCEPTED).body(newArtifact);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Update artifact.
	 *
	 * @param id       the id
	 * @param artifact the artifact
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@SuppressWarnings("rawtypes")
	@PutMapping(value = "/artifact/{id}")
	@Operation(summary = "Update artifact. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Updated artifact"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "417", description = "Expecation failed") }, requestBody = @RequestBody(description = "User to update", required = true, content = {
					@Content(mediaType = "application/json", schema = @Schema(implementation = Artifact.class)) }))
	@Parameters({ @Parameter(name = "id", description = "Artifact id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "artifact", description = "Artifact object", required = true) })
	@RecordMetric
	public ResponseEntity updateArtifact(final @PathVariable String id,
			final @org.springframework.web.bind.annotation.RequestBody String artifactStr) throws Exception {

		final User authUser = authorizeUser();

		try {

			final Artifact existingArtifact = ArtifactService.getArtifact(id);
			if (existingArtifact == null) {
				throw new RestException(false, 404, "Not found", "Unable to find artifact by id = " + id);
			}
			Artifact artifact = null;
			try {
				artifact = ModelUtility.fromJson(artifactStr, Artifact.class);
			} catch (Exception e) {
				throw new RestException(false, 417, "Expectation Failed ", "Unable to parse artifact = " + artifactStr);

			}

			existingArtifact.populateFrom(artifact);
			final Artifact returnArtifact = ArtifactService.updateArtifact(authUser, existingArtifact);

			return ResponseEntity.status(HttpStatus.OK).body(returnArtifact);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Inactivate artifact.
	 *
	 * @param id the id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@SuppressWarnings("rawtypes")
	@DeleteMapping(value = "/artifact/{id}")
	@Operation(summary = "Inactivate artifact. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "204", description = "Successfully inactivated artifact"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({ @Parameter(name = "id", description = "Artifact id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity inactivateArtifact(final @PathVariable String id) throws Exception {

		final User authUser = authorizeUser();
		try {

			final Artifact artifact = ArtifactService.getArtifact(id);
			if (artifact == null) {
				throw new RestException(false, 404, "Not found", "Unable to find artifact by id = " + id);
			}

			artifact.setActive(false);
			ArtifactService.updateArtifact(authUser, artifact);
			return new ResponseEntity<>(HttpStatus.NO_CONTENT);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Download artifact.
	 *
	 * @param id the id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@GetMapping(value = "/artifact/{id}/file")
	@Operation(summary = "Download artifact.", responses = {
			@ApiResponse(responseCode = "200", description = "Retrieved artifact"),
			@ApiResponse(responseCode = "404", description = "Not Found") })
	@Parameters({ @Parameter(name = "id", description = "Artifact id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity<Resource> downloadArtifact(@PathVariable("id") final String id) throws Exception {

		// no auth required
		try {

			final Artifact artifact = ArtifactService.getArtifact(id);
			if (artifact == null) {
				throw new RestException(false, 404, "Not found", "Unable to find artifact by id = " + id);
			}

			final Resource file = FileUtility.getArtifactFile(artifact.getStoredFileName());

			if (file == null) {
				throw new RestException(false, 404, "Not found",
						"Unable to get artifact file = " + artifact.getStoredFileName());
			}

			return ResponseEntity.ok()
					.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
					.header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath()))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + artifact.getFileName() + "\"")
					.contentLength(file.contentLength()).body(file);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

}
