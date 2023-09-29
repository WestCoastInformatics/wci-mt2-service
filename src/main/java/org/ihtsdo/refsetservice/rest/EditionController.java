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

import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
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

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /edition endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Edition Controller", version = "1.0.0", description = "Endpoints for retrieving editions."), tags = {
		@Tag(name = "edition", description = "Edition service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class EditionController extends BaseController {

	/** The Constant LOG. */
	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(EditionController.class);

	/**
	 * Return the edition.
	 *
	 * @param id the id
	 * @return the edition
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/edition/{id}", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get edition", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information") })
	@Parameters({ @Parameter(name = "id", description = "Edition id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	public ResponseEntity<Edition> getEdition(@PathVariable(value = "id") final String id) throws Exception {

		// no auth required
		try {
			final Edition edition = EditionService.getEdition(id);
			return new ResponseEntity<>(edition, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Return the editions.
	 *
	 * @return the editions
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/edition/", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Get all editions", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information") })
	@RecordMetric
	public ResponseEntity<ResultList<Edition>> getEditions() throws Exception {
		// no auth required

		try {

			final ResultList<Edition> results = EditionService.getEditions();
			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Search Editions.
	 *
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/edition/search", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Find editions.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	// @ModelAttribute API params documented in SearchParameter
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<Edition>> getEditions(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {
		// no auth required

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		try {

			final ResultList<Edition> results = EditionService.searchEditions(searchParameters);
			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

}
