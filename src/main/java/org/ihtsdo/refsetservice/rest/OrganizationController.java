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

import java.io.File;
import java.nio.file.Files;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.ResultListProject;
import org.ihtsdo.refsetservice.model.ResultListTeam;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.server.ResponseStatusException;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /organization endpoints.
 */
@RestController
@Api(tags = "Organization endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class OrganizationController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(OrganizationController.class);

    /** Search teams API notes. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** The local icon file directory. */
    private static final String ICON_URL_PREFIX = "user/icon/";

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Return the organization.
     *
     * @param id the id
     * @param includeMembers the include members
     * @return the organization
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the organization for the specified identifier", response = Organization.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<Organization> getOrganization(@PathVariable(value = "id") final String id, @QueryParam(value = "includeMembers") final boolean includeMembers) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            logger.info("Get organization {}", id);
            // TODO check permissions, fail if not authorized.
            final User user = SecurityService.getUserFromSession();

            final Organization organization = OrganizationService.getOrganization(service, user, id, includeMembers);
            return new ResponseEntity<>(organization, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }
    }

    /**
     * Search organizations.
     *
     * @param includeMembers the include members
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get organizations search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/organization/search", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Organization>> getOrganizations(@QueryParam(value = "includeMembers") final boolean includeMembers, final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        logger.info("Search organizations: {}", ModelUtility.toJson(searchParameters));
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Organization> results = OrganizationService.searchOrganizations(service, user, searchParameters, includeMembers);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            logger.error("Error searching organizations.  Search criteria: {} ", searchParameters.toString());
            return handleException(e);
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
    @ApiOperation(value = "Add organization", response = Organization.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Organization successfully created"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/organization", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity addOrganization(@RequestBody final Organization organization) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            logger.info("Add organization: {}", organization);
            // TODO check permissions, fail if not authorized.
            final User user = SecurityService.getUserFromSession();

            if (organization == null) {
                throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED, "Missing organization");
            }

            service.setModifiedBy(user.getUserName());

            try {
                organization.validateAdd();
            } catch (final Exception e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
            }

            final Organization org = OrganizationService.createOrganization(service, user, organization);

            return new ResponseEntity<>(org, HttpStatus.CREATED);

        } catch (final Exception e) {

            return handleException(e);
        }
    }

    /**
     * Update organization.
     *
     * @param id the id
     * @param organization the organization
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    @ApiOperation(value = "Update organization", response = Organization.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Organization successfully updated"), @ApiResponse(code = 400, message = "Bad Request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 403, message = "Forbidden"), @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/organization/{id}", consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity updateOrganization(@PathVariable(value = "id") final String id, @RequestBody final Organization organization) throws Exception {

        logger.info("Update organization: {}", organization);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("");
        }

        if (organization == null || !org.apache.commons.lang3.StringUtils.equals(id, organization.getId())) {

            final String errorMessage = "Organization is null or organization id does not match id in URL.";
            logger.error(errorMessage);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorMessage);
        }

        try {
            organization.validateUpdate(null);
        } catch (final Exception e) {

            logger.error("Bad request for organization update.", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            final Organization org = OrganizationService.updateOrganization(service, user, organization);

            return new ResponseEntity<>(org, HttpStatus.OK);

        } catch (final Exception e) {

            logger.error("Error updating organization. Organization: {}", organization);
            return handleException(e);
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
    @ApiOperation(value = "Inactivate organization")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Inactivate specified organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/organization/{id}")
    public ResponseEntity deleteOrganization(@PathVariable("id") final String id) throws Exception {

        logger.info("Inactivate organization: {}", id);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            OrganizationService.inactivateOrganization(service, user, id);

            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Return users for the organization.
     *
     * @param id the id
     * @param includeTeams the include teams
     * @return the organization
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the organization for the specified identifier", response = ResultListUser.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}/users", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListUser> getOrganizationUsers(@PathVariable(value = "id") final String id, @QueryParam(value = "includeTeams") final boolean includeTeams) throws Exception {

        logger.info("Get organization users. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final ResultListUser usersResultList = OrganizationService.getOrganizationUsers(service, id, includeTeams);
            return new ResponseEntity<>(usersResultList, HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Return teams for the organization.
     *
     * @param id Id of the organization
     * @return ResponseEntity<ResultListTeam>
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the organization for the specified identifier", response = ResultListTeam.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}/teams", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListTeam> getOrganizationTeams(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get organization teams. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Team> orgTeams = OrganizationService.getOrganizationTeams(service, id);
            return new ResponseEntity<>(new ResultListTeam(orgTeams), HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Return teams for the organization.
     *
     * @param id Id of the organization
     * @return ResponseEntity<ResultListProject>
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the projects for an organization for the specified identifier", response = ResultListProject.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataTypeClass = String.class, paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}/projects", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListProject> getOrganizationProjects(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get organization teams. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Project> orgProjects = OrganizationService.getOrganizationProjects(service, id);
            return new ResponseEntity<>(new ResultListProject(orgProjects), HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Add the user to the organization.
     *
     * @param organizationId the organization id
     * @param email the email of the user to add
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add user to organization", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "User added to organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/organization/{organizationId}/user")
    public @ResponseBody ResponseEntity<String> addUserToOrganization(@PathVariable final String organizationId, final String email) throws Exception {

        logger.info("Add user: {} to organization: {}.", email, organizationId);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            OrganizationService.addUserToOrganization(service, user, organizationId, email);
            // service.commit();

            return new ResponseEntity<>(HttpStatus.CREATED);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Remove the user from the organization.
     *
     * @param organizationId the organization id
     * @param userId the user id
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Remove user from organization", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "User removed from organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/organization/{organizationId}/user/{userId}")
    public @ResponseBody ResponseEntity<Organization> removeUserFromOrganization(@PathVariable(value = "organizationId") final String organizationId,
        @PathVariable(value = "userId") final String userId) throws Exception {

        logger.info("Add user: {} to organization: {}.", userId, organizationId);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final Organization org = OrganizationService.removeUserFromOrganization(service, user, userId, organizationId);
            // service.commit();

            return new ResponseEntity<>(org, HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Returns the organization icon.
     *
     * @param fileName the file name
     * @return the organization icon
     * @throws Exception the exception
     */
    @RequestMapping(value = "/organization/icon/{fileName}", method = RequestMethod.GET)
    public @ResponseBody ResponseEntity<Resource> getOrganizationIcon(@PathVariable("fileName") final String fileName) throws Exception {

        try {

            logger.info("GET icon for organization {}", fileName);
            final Resource file = FileUtility.getIconFile(fileName);

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(file.getFile().toPath())).contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {

            logger.error("Trying to get organization icon file " + fileName, e);
            return handleException(e);
        }
    }

    /**
     * Edit the organization icon.
     *
     * @param organizationId the organization id
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Edit icon for organization")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Saveed icon for organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/organization/{organizationId}/icon")
    public ResponseEntity<String> editOrganizationIcon(@PathVariable("organizationId") final String organizationId, @RequestParam("file") final MultipartFile inputFile) throws Exception {

        logger.info("Add icon for organization: {}.", organizationId);
        // TODO check permissions, fail if not authorized.
        final User user = SecurityService.getUserFromSession();

        if (user == null) {
            return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final Organization organization = OrganizationService.getOrganization(service, user, organizationId, false);

            String fileToDelete = "";

            if (organization.getIconUri() != null) {
                fileToDelete = organization.getIconUri().replace(ICON_URL_PREFIX, "");
            }

            final File file = FileUtility.saveIconFile(inputFile, organizationId, fileToDelete);

            OrganizationService.updateOrganizationIcon(service, user, organizationId, ICON_URL_PREFIX, file.getName());
            // service.commit();

            return new ResponseEntity<>("\"" + organization.getIconUri() + "\"", HttpStatus.ACCEPTED);

        } catch (final Exception e) {

            logger.error("Trying to edit user icon for organization " + organizationId, e);
            return handleException(e);
        }
    }

    // @SuppressWarnings("rawtypes")
    // @Hidden
    // @PostMapping(value = "/organization/{organizationId}/user/{userId}/temp")
    // public ResponseEntity addOrganizationAdminUser(@PathVariable("organizationId") final String organizationId, @PathVariable("userId") final String userId) throws
    // Exception {
    //
    // logger.info("Add icon for organization: {}.", organizationId);
    // final User authUser = SecurityService.getUserFromSession();
    // if (authUser == null) {
    // return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    // }
    //
    // try {
    //
    // if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
    // // throws not found exception
    // final Organization organization = OrganizationService.getOrganization(organizationId, false);
    //
    // // throws not found exception
    // final User user = UserService.getUser(userId, false);
    //
    // final String groupName = CrowdGroupNameAlgorithm.generateCrowdGroupName(organization.getEdition().getShortName(), "all", "admin");
    // CrowdAPIClient.addMembership(groupName, user.getUserName());
    //
    // } else {
    // logger.info("SKIP CALLING CROWD API");
    // }
    //
    // return new ResponseEntity<>(HttpStatus.CREATED);
    //
    // } catch (final NotFoundException nfe) {
    //
    // return new ResponseEntity<>(nfe.getMessage(), HttpStatus.NOT_FOUND);
    //
    // } catch (final Exception e) {
    //
    // logger.error("Trying to edit user icon for organization " + organizationId, e);
    // handleException(e);
    // return null;
    // }
    //
    // }
    //
    // @SuppressWarnings("rawtypes")
    // @Hidden
    // @DeleteMapping(value = "/organization/{organizationId}/user/{userId}/temp")
    // public ResponseEntity removeOrganizationAdminUser(@PathVariable("organizationId") final String organizationId, @PathVariable("userId") final String userId) throws
    // Exception {
    //
    // logger.info("Add icon for organization: {}.", organizationId);
    // final User authUser = SecurityService.getUserFromSession();
    // if (authUser == null) {
    // return new ResponseEntity<>(HttpStatus.FORBIDDEN);
    // }
    //
    // try {
    //
    // if (PROPERTIES.getProperty("crowd.unit.test.skip") == null || !"true".equalsIgnoreCase(PROPERTIES.getProperty("crowd.unit.test.skip"))) {
    // // throws not found exception
    // final Organization organization = OrganizationService.getOrganization(organizationId, false);
    //
    // // throws not found exception
    // final User user = UserService.getUser(userId, false);
    //
    // final String groupName = CrowdGroupNameAlgorithm.generateCrowdGroupName(organization.getEdition().getShortName(), "all", "admin");
    // CrowdAPIClient.deleteMembership(groupName, user.getUserName());
    //
    // } else {
    // logger.info("SKIP CALLING CROWD API");
    // }
    //
    // return new ResponseEntity<>(HttpStatus.ACCEPTED);
    //
    // } catch (final NotFoundException nfe) {
    //
    // return new ResponseEntity<>(nfe.getMessage(), HttpStatus.NOT_FOUND);
    //
    // } catch (final Exception e) {
    //
    // logger.error("Trying to edit user icon for organization " + organizationId, e);
    // handleException(e);
    // return null;
    // }
    //
    // }

}
