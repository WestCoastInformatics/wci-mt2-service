/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.AuditService;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.ModelUtility;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /audit endpoints.
 */
@RestController
@Api(tags = "audit", description = "Endpoints for searching and retrieving audit entries")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class AuditController extends BaseController {

	/** Logger. */
	private static Logger logger = LoggerFactory.getLogger(AuditController.class);

	/**
	 * Returns the auditEntryImpl.
	 *
	 * @param id the id of the auditEntryImpl
	 * @return the auditEntryImpl
	 * @throws Exception the exception
	 */
    @RequestMapping(method = RequestMethod.GET, value = "/audit/{id}")
    @ApiOperation(value = "Get audit entry.", response = AuditEntry.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Audit entry id, e.g. &lt;uuid&gt;", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
	public @ResponseBody ResponseEntity<AuditEntry> getAuditEntry(@PathVariable(value = "id") final String id)
			throws Exception {

		logger.info("Get audit entry: {}", id);
		// no auth required

		final SearchParameters searchParameters = new SearchParameters();
		searchParameters.setQuery("id: " + id);
		ResultList<AuditEntry> result = AuditService.findAuditEntries(searchParameters);

		return ResponseEntity.status(HttpStatus.OK).body(result.getItems().get(0));

	}

	/**
	 * Search audit entries.
	 *
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
    @SuppressWarnings("unchecked")
    @RequestMapping(method = RequestMethod.GET, value = "/audit", produces = MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Find audit entries. This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The value to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    })
    @RecordMetric
	public @ResponseBody ResponseEntity<ResultList<AuditEntry>> searchAuditEntries(
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {

		final User authUser = SecurityService.getUserFromSession();
		if (authUser == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		logger.info("Search audit entry search parameters: {}", ModelUtility.toJson(searchParameters));

		try {

			final ResultList<AuditEntry> results = AuditService.findAuditEntries(searchParameters);
			return ResponseEntity.status(HttpStatus.OK).body(results);

		} catch (final Exception e) {
			logger.error("Error searching audit entries.  Search criteria: {} ", searchParameters.toString(), e);
			return handleException(e);
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
    @SuppressWarnings("unchecked")
    @ApiOperation(value = "Find audit entries for entity. This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "entityType", value = "The entity type, e.g. 'REFSET'", required = true, dataTypeClass = String.class, paramType = "path", defaultValue = ""),
        @ApiImplicitParam(name = "entityId", value = "The entity id, e.g. '89f97217-ceb1-47b2-8066-cbcdde20884e'", required = true, dataTypeClass = String.class, paramType = "path",
            defaultValue = ""),
        @ApiImplicitParam(name = "expand", value = "Will expand the result to include related entries.  e.g include project and teams for an organization ", required = false,
            dataTypeClass = Boolean.class, paramType = "path", defaultValue = "false"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/audit/{entityType}/{entityId}", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<AuditEntry>> searchAuditEntriesForEntity(
			@PathVariable final String entityType, @PathVariable final String entityId,
			@QueryParam(value = "expand") final Boolean expand, @ModelAttribute final SearchParameters searchParameters,
			final BindingResult bindingResult) throws Exception {

		final User authUser = SecurityService.getUserFromSession();
		if (authUser == null) {
			return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
		}

		if (StringUtils.isBlank(entityType)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		if (StringUtils.isBlank(entityId)) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		try {

			logger.info("Search audit entry search parameters: {} : {} : {}", entityType, entityId,
					ModelUtility.toJson(searchParameters));

			try (final TerminologyService service = new TerminologyService()) {
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
						logger.info("Audit Entry: User {} does not have permissions on organization {}.",
								authUser.getUserName(), entityId);
						return ResponseEntity.status(HttpStatus.OK).body(null);
					}

					final ResultList<Team> orgTeams = OrganizationService.getOrganizationTeams(service, entityId);
					final ResultList<Project> orgProjects = OrganizationService.getOrganizationProjects(service,
							entityId);

					final StringBuilder additionalQuery = new StringBuilder();
					if (orgTeams != null && !orgTeams.getItems().isEmpty()) {
						for (final Team team : orgTeams.getItems()) {
							additionalQuery.append(" OR (entityType:TEAM AND entityId:").append(team.getId())
									.append(")");
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
							+ (StringUtils.isNotEmpty(searchParameters.getQuery())
									? " AND " + searchParameters.getQuery()
									: "");
					searchParameters.setQuery(query);

				} else {

					final String query = "entityType:" + entityType + " AND entityId:" + entityId
							+ (StringUtils.isNotEmpty(searchParameters.getQuery())
									? " AND " + searchParameters.getQuery()
									: "");
					searchParameters.setQuery(query);

				}

				final ResultList<AuditEntry> results = AuditService.findAuditEntries(searchParameters);
				return ResponseEntity.status(HttpStatus.OK).body(results);
			}

		} catch (final Exception e) {
			logger.error("Error searching audit entries.  Search criteria: {} ", searchParameters.toString(), e);
			return handleException(e);
		}
	}
}
