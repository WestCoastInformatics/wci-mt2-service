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

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.AuditService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /audit endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Audit Controller", version = "1.0.0", description = "Endpoints for searching and retrieving audit entries."), tags = {
		@Tag(name = "audit", description = "Audit service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class AuditController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(AuditController.class);

	/**
	 * Returns the auditEntryImpl.
	 *
	 * @param id the id of the auditEntryImpl
	 * @return the auditEntryImpl
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/audit/{id}")
	@Operation(summary = "Get audit entry.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"), })
	@Parameters({
			@Parameter(name = "id", description = "Audit entry id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)) })
	@RecordMetric
	public @ResponseBody ResponseEntity<AuditEntry> getAuditEntry(@PathVariable(value = "id") final String id)
			throws Exception {

		// no auth required
		try {

			final SearchParameters searchParameters = new SearchParameters();
			searchParameters.setQuery("id: " + id);
			final ResultList<AuditEntry> result = AuditService.findAuditEntries(searchParameters);
			if (result.size() == 0) {
				return ResponseEntity.status(HttpStatus.OK).body(null);
			}
			return ResponseEntity.status(HttpStatus.OK).body(result.getItems().get(0));

		} catch (Exception e) {
			handleException(e);
			return null;
		}

	}

	/**
	 * Search audit entries.
	 *
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/audit", produces = MediaType.APPLICATION_JSON)
	@Operation(summary = "Find audit entries. This call requires authentication with the correct role.", responses =

	{ @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	@Parameters({
			@Parameter(name = "query", description = "The value to be searched, e.g. 'melanoma'", required = false),
			@Parameter(name = "limit", description = "The max number of results to return", required = false, example = "10"),
			@Parameter(name = "offset", description = "The offset for the first result", required = false, example = "0") })
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<AuditEntry>> searchAuditEntries(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);
		authorizeUser();

		try {

			final ResultList<AuditEntry> results = AuditService.findAuditEntries(searchParameters);
			return ResponseEntity.status(HttpStatus.OK).body(results);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Search audit entries.
	 *
	 * @param entityType       the entity type
	 * @param entityId         the entity id
	 * @param expand           the expand
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Find audit entries for entity. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "417", description = "Expectation failed"), })
	@Parameters({ @Parameter(name = "entityType", description = "The entity type, e.g. 'REFSET'", required = true),
			@Parameter(name = "entityId", description = "The entity id, e.g. '89f97217-ceb1-47b2-8066-cbcdde20884e'", required = true),
			@Parameter(name = "expand", description = "Will expand the result to include related entries.  e.g include project and teams for an organization ", required = false, example = "false"),
			@Parameter(name = "query", description = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false),
			@Parameter(name = "limit", description = "The max number of results to return", required = false, example = "10"),
			@Parameter(name = "offset", description = "The offset for the first result", required = false, example = "0") })
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/audit/{entityType}/{entityId}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<AuditEntry>> searchAuditEntriesForEntity(
			@PathVariable final String entityType, @PathVariable final String entityId,
			@RequestParam(value = "expand") final Boolean expand,
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {

		final User authUser = authorizeUser();

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		try (final TerminologyService service = new TerminologyService()) {

			if (StringUtils.isBlank(entityType)) {
				throw new RestException(false, 417, "Expectation failed", "Unexpected blank entity type");
			}

			if (StringUtils.isBlank(entityId)) {
				throw new RestException(false, 417, "Expectation failed", "Unexpected blank entity id");
			}

			// is the user a member of the project? is yes return history, if not return
			// null?? or error??
			if ("REFSET".equalsIgnoreCase(entityType)) {
				// check user's permission
				final Refset refset = RefsetService.getRefset(service, authUser, entityId);

				if (refset == null || refset.getRoles() == null || refset.getRoles().isEmpty()) {
					// user has no permissions
					return ResponseEntity.status(HttpStatus.OK).body(null);
				}
			}

			if ("ORGANIZATION".equalsIgnoreCase(entityType) && expand != null && expand) {

				final Organization organization = OrganizationService.getOrganization(service, authUser, entityId,
						false);
				if (organization == null || organization.getRoles() == null || organization.getRoles().isEmpty()) {
					// user has no permissions
					LOG.info("Audit Entry: User {} does not have permissions on organization {}.",
							authUser.getUserName(), entityId);
					return ResponseEntity.status(HttpStatus.OK).body(null);
				}

				final ResultList<Team> orgTeams = OrganizationService.getActiveOrganizationTeams(service, entityId);
				final ResultList<Project> orgProjects = OrganizationService.getOrganizationProjects(service, entityId);

				final StringBuilder additionalQuery = new StringBuilder();
				if (orgTeams != null && !orgTeams.getItems().isEmpty()) {
					for (final Team team : orgTeams.getItems()) {
						additionalQuery.append(" OR (entityType:TEAM AND entityId:").append(team.getId()).append(")");
					}
				}
				if (orgProjects != null && !orgProjects.getItems().isEmpty()) {
					for (final Project project : orgProjects.getItems()) {
						additionalQuery.append(" OR (entityType:PROJECT AND entityId:").append(project.getId())
								.append(")");
					}
				}

				final String query = "(entityType:" + entityType + " AND entityId:" + entityId + ") "
						+ (StringUtils.isNotEmpty(additionalQuery.toString()) ? additionalQuery.toString() : "")
						+ (StringUtils.isNotEmpty(searchParameters.getQuery()) ? " AND " + searchParameters.getQuery()
								: "");
				searchParameters.setQuery(query);

			} else {

				final String query = "entityType:" + entityType + " AND entityId:" + entityId
						+ (StringUtils.isNotEmpty(searchParameters.getQuery()) ? " AND " + searchParameters.getQuery()
								: "");
				searchParameters.setQuery(query);

			}

			final ResultList<AuditEntry> results = AuditService.findAuditEntries(searchParameters);
			return ResponseEntity.status(HttpStatus.OK).body(results);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}
}
