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
import java.util.Arrays;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListProject;
import org.ihtsdo.refsetservice.model.ResultListTeam;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.SendCommunicationEmailInfo;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
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
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /organization endpoints.
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Organization Controller", version = "1.0.0", description = "Endpoints for creating, retrieving, updating, and deleting organizations."), tags = {
		@Tag(name = "organization", description = "Organization service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class OrganizationController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(OrganizationController.class);

	/** Search teams API notes. */
	private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

	/** The local icon file directory. */
	private static final String ICON_URL_PREFIX = "user/icon/";

	/**
	 * Return the organization.
	 *
	 * @param id             the id
	 * @param includeMembers the include members
	 * @return the organization
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization identifier, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "includeMembers", description = "Include organization's members (users)", required = false) })
	@RecordMetric
	@RequestMapping(value = "/organization/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
	public ResponseEntity<Organization> getOrganization(@PathVariable(value = "id") final String id,
			@RequestParam(value = "includeMembers", defaultValue = "false") final boolean includeMembers)
			throws Exception {

		LOG.info("Get organization {}", id);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final Organization organization = OrganizationService.getOrganization(service, authUser, id,
					includeMembers);
			return new ResponseEntity<>(organization, HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Search organizations.
	 *
	 * @param includeMembers   the include members
	 * @param searchParameters the search parameters
	 * @param bindingResult    the binding result
	 * @return the string
	 * @throws Exception the exception
	 */
	@Operation(summary = "Find organizations. This call requires authentication with the correct role.", description = API_NOTES, responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation") })
	// @ModelAttribute API params documented in SearchParameter
	@RecordMetric
	@RequestMapping(method = RequestMethod.GET, value = "/organization/search", produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<ResultList<Organization>> getOrganizations(
			@RequestParam(value = "includeMembers") final boolean includeMembers,
			@ModelAttribute final SearchParameters searchParameters, final BindingResult bindingResult)
			throws Exception {

		LOG.info("Search organizations: {}", ModelUtility.toJson(searchParameters));
		final User authUser = authorizeUser();

		// Check to make sure parameters were properly bound to variables.
		checkBinding(bindingResult);

		try (final TerminologyService service = new TerminologyService()) {

			final ResultList<Organization> results = OrganizationService.searchOrganizations(service, authUser,
					searchParameters, includeMembers);
			return new ResponseEntity<>(results, HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Add the organization.
	 *
	 * @param organization the organization
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Add organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Organization successfully created"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Resource not found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "organization", description = "Organization object", required = true) })
	@RecordMetric
	@PostMapping(value = "/organization", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public ResponseEntity addOrganization(@RequestBody final Organization organization) throws Exception {

		LOG.info("Add organization: {}", organization);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			if (organization == null) {
				throw new RestException(false, 417, "Expectation failed", "Missing organization");
			}

			service.setModifiedBy(authUser.getUserName());

			try {
				organization.validateAdd();
			} catch (final Exception e) {
				throw new RestException(false, 417, "Expectation failed", e.getMessage());
			}

			final Organization org = OrganizationService.createOrganization(service, authUser, organization);

			return new ResponseEntity<>(org, HttpStatus.CREATED);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update organization.
	 *
	 * @param id           the id
	 * @param organization the organization
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Update organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Organization successfully updated"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "organization", description = "Organization object", required = true) })
	@RecordMetric
	@PutMapping(value = "/organization/{id}", consumes = MediaType.APPLICATION_JSON)
	public ResponseEntity updateOrganization(@PathVariable(value = "id") final String id,
			@RequestBody final Organization organization) throws Exception {

		LOG.info("Update organization: {}", organization);
		final User authUser = authorizeUser();

		if (organization == null || !org.apache.commons.lang3.StringUtils.equals(id, organization.getId())) {

			final String errorMessage = "Organization is null or organization id does not match id in URL.";
			throw new RestException(false, 404, "Not found", errorMessage);
		}

		try {
			organization.validateUpdate(null);
		} catch (final Exception e) {
			throw new RestException(false, 417, "Expectation failed", "Validation of organization failed");
		}

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			final Organization org = OrganizationService.updateOrganization(service, authUser, organization);

			return new ResponseEntity<>(org, HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Logical delete (inactivate) the organization.
	 *
	 * @param id the id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@SuppressWarnings("rawtypes")
	@Operation(summary = "Inactivate organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Successfully inactivated organization"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@DeleteMapping(value = "/organization/{id}")
	public ResponseEntity deleteOrganization(@PathVariable("id") final String id) throws Exception {

		LOG.info("Inactivate organization: {}", id);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			OrganizationService.updateOrganizationStatus(service, authUser, id, false);

			return new ResponseEntity<>(HttpStatus.ACCEPTED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Return users for the organization.
	 *
	 * @param id           the id
	 * @param includeTeams the include teams
	 * @return the organization
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get user(s) for the organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad request"),
			@ApiResponse(responseCode = "404", description = "Resource not found") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "includeTeams", description = "Include organization user's teams", required = false, example = "false") })
	@RecordMetric
	@RequestMapping(value = "/organization/{id}/users", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
	public ResponseEntity<ResultListUser> getOrganizationUsers(@PathVariable(value = "id") final String id,
			@RequestParam(value = "includeTeams") final boolean includeTeams) throws Exception {

		LOG.info("Get organization users. Id: {}", id);
		authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {
			final User authUser = authorizeUser();

			final Organization organization = OrganizationService.getOrganization(service, authUser, id, true);

			final ResultListUser usersResultList = OrganizationService.getOrganizationUsers(service, organization,
					includeTeams);
			return new ResponseEntity<>(usersResultList, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Return teams for the organization.
	 *
	 * @param id Id of the organization
	 * @return ResponseEntity<ResultListTeam>
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get team(s) for the organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@RequestMapping(value = "/organization/{id}/teams", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
	public ResponseEntity<ResultListTeam> getOrganizationTeams(@PathVariable(value = "id") final String id)
			throws Exception {

		LOG.info("Get organization teams. Id: {}", id);
		authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final ResultList<Team> orgTeams = OrganizationService.getActiveOrganizationTeams(service, id);
			return new ResponseEntity<>(new ResultListTeam(orgTeams), HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Return teams for the organization.
	 *
	 * @param id Id of the organization
	 * @return ResponseEntity<ResultListProject>
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get projects(s) the organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@RequestMapping(value = "/organization/{id}/projects", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
	public ResponseEntity<ResultListProject> getOrganizationProjects(@PathVariable(value = "id") final String id)
			throws Exception {

		LOG.info("Get organization teams. Id: {}", id);
		authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final ResultList<Project> orgProjects = OrganizationService.getOrganizationProjects(service, id);
			return new ResponseEntity<>(new ResultListProject(orgProjects), HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Add the user(s) to the organization by semi-colon delimited email
	 * address(es).
	 *
	 * @param id     the organization id
	 * @param emails the emails of the user(s) to add
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Add user to organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "User added to organization"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@PostMapping(value = "/organization/{id}/user")
	public @ResponseBody ResponseEntity<String> addUserToOrganization(@PathVariable final String id,
			final String emails) throws Exception {

		LOG.info("Add user(s): {} to organization: {}.", emails, id);
		final User authUser = authorizeUser();

		if (StringUtils.isBlank(emails)) {
			throw new RestException(false, 417, "Expectation failed", "Unexpected blank emails");
		}

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());

			if (emails.contains(";")) {
				for (final String email : Arrays.asList(emails.split(";"))) {
					OrganizationService.addUserToOrganization(service, authUser, id, email);
				}
			} else {
				OrganizationService.addUserToOrganization(service, authUser, id, emails);
			}

			return new ResponseEntity<>(HttpStatus.CREATED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Remove the user from the organization.
	 *
	 * @param id     the organization id
	 * @param userId the user id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Delete user from organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Successfully removed user from organization"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "409", description = "Conflict"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "userId", description = "User id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@DeleteMapping(value = "/organization/{id}/user/{userId}")
	public @ResponseBody ResponseEntity<Organization> removeUserFromOrganization(
			@PathVariable(value = "id") final String id, @PathVariable(value = "userId") final String userId)
			throws Exception {

		LOG.info("Add user: {} to organization: {}.", userId, id);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			// service.setTransactionPerOperation(false);
			// service.beginTransaction();

			final Organization org = OrganizationService.removeUserFromOrganization(service, authUser, userId, id);
			// service.commit();

			return new ResponseEntity<>(org, HttpStatus.ACCEPTED);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Returns the organization icon.
	 *
	 * @param fileName the file name
	 * @return the organization icon
	 * @throws Exception the exception
	 */
	@Operation(summary = "Get organization icon.", responses = {
			@ApiResponse(responseCode = "200", description = "Retrieved organization icon"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "fileName", description = "fileName, e.g. &lt;uuid&gt;", required = true) })
	@RequestMapping(value = "/organization/icon/{fileName}", method = RequestMethod.GET)
	// no auth required
	public @ResponseBody ResponseEntity<Resource> getOrganizationIcon(@PathVariable("fileName") final String fileName)
			throws Exception {

		try {

			LOG.info("GET icon for organization {}", fileName);
			final Resource file = FileUtility.getIconFile(fileName);

			return ResponseEntity.ok()
					.header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
					.header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath()))
					.contentLength(file.contentLength()).body(file);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Edit the organization icon.
	 *
	 * @param id        the organization id
	 * @param inputFile the input file
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Update icon for organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "202", description = "Updated icon for organization"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "409", description = "Conflict"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "file", description = "Icon file", required = true) })
	@RecordMetric
	@PostMapping(value = "/organization/{id}/icon")
	public ResponseEntity<String> editOrganizationIcon(@PathVariable("id") final String id,
			@RequestParam("file") final MultipartFile inputFile) throws Exception {

		LOG.info("Update icon for organization: {}.", id);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());

			final Organization organization = OrganizationService.getOrganization(service, authUser, id, false);

			String fileToDelete = "";

			if (organization.getIconUri() != null) {
				fileToDelete = organization.getIconUri().replace(ICON_URL_PREFIX, "");
			}

			final File file = FileUtility.saveIconFile(inputFile, id, fileToDelete);

			OrganizationService.updateOrganizationIcon(service, authUser, id, ICON_URL_PREFIX, file.getName());

			return new ResponseEntity<>("\"" + organization.getIconUri() + "\"", HttpStatus.ACCEPTED);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Delete organization icon.
	 *
	 * @param id the organization id
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Delete organization icon. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully removed organization icon"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "415", description = "Unsupported Media Type"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true) })
	@RecordMetric
	@DeleteMapping(value = "/organization/{id}/icon", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<Organization> deleteOrganizationIcon(
			@PathVariable(value = "id") final String id) throws Exception {

		LOG.info("Delete organization icon: {}", id);
		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			service.setTransactionPerOperation(false);
			service.beginTransaction();

			final Organization organization = OrganizationService.getOrganization(service, authUser, id, false);
			if (organization == null || !org.apache.commons.lang3.StringUtils.equals(id, organization.getId())) {
				throw new RestException(false, 417, "Expectation failed",
						"Organization is null or organization id does not match id in URL.");
			}

			organization.setIconUri(null);
			final Organization original = OrganizationService.updateOrganization(service, authUser, organization);

			service.commit();

			return new ResponseEntity<>(original, HttpStatus.OK);

		} catch (final NotFoundException nfe) {
			throw new RestException(false, 404, "Not found", "Unable to find organization for id = " + id);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}
	}

	/**
	 * Invite user to organization.
	 *
	 * @param id        the organization id
	 * @param emailInfo the email info
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Request member/non-member to join organization. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Organization icon deleted"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "404", description = "Not Found"),
			@ApiResponse(responseCode = "409", description = "Conflict"),
			@ApiResponse(responseCode = "415", description = "Unsupported Media Type"),
			@ApiResponse(responseCode = "417", description = "Failed Expectation"),
			@ApiResponse(responseCode = "500", description = "Internal server error") })
	@Parameters({ @Parameter(name = "id", description = "Organization id, e.g. &lt;uuid&gt;", required = true),
			@Parameter(name = "emailInfo", description = "Email information", required = true) })
	@RecordMetric
	@PostMapping(value = "/organization/{id}/invite", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
	public @ResponseBody ResponseEntity<String> inviteUserToRefset(@PathVariable final String id,
			@RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

		final User authUser = authorizeUser();

		try {

			LOG.info("inviteUserToOrganization: id: " + id + " and emailInfo.recipient: " + emailInfo.getRecipient()
					+ " and emailInfo.additionalMessage: " + emailInfo.getAdditionalMessage());

			OrganizationService.inviteUserToOrganization(authUser, id, emailInfo.getRecipient(),
					emailInfo.getAdditionalMessage());

			final String returnMessage = "{\"message\": \"Refset invite was Successful\"}";

			return new ResponseEntity<>(returnMessage, HttpStatus.OK);

		} catch (final Exception e) {
			handleException(e);
			return null;
		}

	}

}
