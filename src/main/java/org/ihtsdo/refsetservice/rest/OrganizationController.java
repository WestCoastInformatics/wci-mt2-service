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

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListProject;
import org.ihtsdo.refsetservice.model.ResultListTeam;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
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

    /** The request. */
    @Autowired
    HttpServletRequest request;

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
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataType = "string", paramType = "path") // ,
    })

    @RecordMetric
    @RequestMapping(value = "/organization/{id}", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<Organization> getOrganization(@PathVariable(value = "id") final String id, @QueryParam(value = "includeMembers") final boolean includeMembers) throws Exception {

        try {
            logger.info("Get organization {}", id);
            // TODO check permissions, fail if not authorized.
            final User authUser = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {
                final Organization organization = service.findSingle("id: " + id + " AND active:true", Organization.class, null);

                if (organization == null) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find organization for id " + id + ".");
                }
                if (includeMembers) {
                    organization.getMembers();
                } else {
                    if (organization.getMembers() != null && !organization.getMembers().isEmpty()) {
                        organization.getMembers().clear();
                    }
                }

                return new ResponseEntity<>(organization, HttpStatus.OK);
            }
        } catch (final Exception e) {
            handleException(e);
            return null;
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
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
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

        try {

            final ResultList<Organization> results = RefsetService.searchOrganizations(user, searchParameters);
            if (includeMembers) {
                for (Organization organization : results.getItems()) {
                    organization.getMembers();
                }

            } else {
                for (Organization organization : results.getItems()) {
                    if (organization.getMembers() != null && !organization.getMembers().isEmpty()) {
                        organization.getMembers().clear();
                    }
                }

            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {
            logger.error("Error searching organizations.  Search criteria: {} ", searchParameters.toString());
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
    @ApiOperation(value = "Add organization", response = Organization.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Organization successfully created"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/organization", consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity<Organization> addOrganization(@RequestBody final Organization organization) throws Exception {

        try {
            logger.info("Add organization: {}", organization);
            // TODO check permissions, fail if not authorized.
            final User authUser = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final Organization org = (Organization) organization;

                service.setModifiedBy(authUser.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                service.add(org);
                service.commit();

                return new ResponseEntity<>(org, HttpStatus.CREATED);
            }
        } catch (final Exception e) {
            handleException(e);
            return null;
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
    @ApiOperation(value = "Update organization", response = Organization.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Organization successfully updated"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/organization/{id}", consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity<Organization> updateOrganization(@PathVariable(value = "id") final String id, @RequestBody final Organization organization) throws Exception {

        logger.info("Update organization: {}", organization);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Organization original = service.get(id, Organization.class);

            if (original == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find organization for id " + id + ".");
            }

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            original.patchFrom(organization);
            service.update(original);
            service.commit();

            return new ResponseEntity<>(original, HttpStatus.OK);

        } catch (final Exception e) {
            logger.error("Error updating organization. Id: {}", id);
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
    @ApiOperation(value = "Inactivate organization")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Inactivate specified organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/organization/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable("id") final String id) throws Exception {

        logger.info("Inactivate organization: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Find the object
            final Organization organization = service.get(id, Organization.class);

            if (organization == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find organization for id:" + id);
            }

            // inactivate teams, projects. remove users?

            organization.setActive(false);
            service.update(organization);
            service.commit();
            // Return the status
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Error inactivating organization.  Id: {}", id);
            handleException(e);
            return null;
        }
    }

    /**
     * Return users for the organization.
     *
     * @param id the id
     * @return the organization
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the organization for the specified identifier", response = ResultListUser.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataType = "string", paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}/users", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListUser> getOrganizationUsers(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get organization users. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {
            final Organization organization = service.findSingle("id: " + id + " AND active:true", Organization.class, null);

            if (organization == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find organization for id " + id + ".");
            }

            ResultListUser users = new ResultListUser();
            users.getItems().addAll(organization.getMembers());
            users.setTotal(users.getItems().size());

            return new ResponseEntity<>(users, HttpStatus.OK);

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
    @ApiOperation(value = "Get the organization for the specified identifier", response = ResultListTeam.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataType = "string", paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}/teams", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListTeam> getOrganizationTeams(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get organization teams. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            final QueryParameter query = new QueryParameter();
            query.setQuery("organizationId:" + id + " AND active:true");

            final ResultList<Team> orgTeams = service.find(query, pfs, Team.class, null);
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
    @ApiOperation(value = "Get the projects for an organization for the specified identifier", response = ResultListProject.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "Organization identifier, e.g. '43ca2010-5db8-414e-b62b-dd3ea1354b54'", required = true, dataType = "string", paramType = "path") // ,
    })
    @RecordMetric
    @RequestMapping(value = "/organization/{id}/projects", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListProject> getOrganizationProjects(@PathVariable(value = "id") final String id) throws Exception {

        logger.info("Get organization teams. Id: {}", id);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            final QueryParameter query = new QueryParameter();
            query.setQuery("organizationId:" + id + " AND active:true");

            final ResultList<Project> orgProjects = service.find(query, pfs, Project.class, null);
            return new ResponseEntity<>(new ResultListProject(orgProjects), HttpStatus.OK);

        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

    /**
     * Add the user to the organization.
     *
     * @param organizationId the organization id
     * @param userId the user id
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
    @PostMapping(value = "/organization/{organizationId}/user/{userId}")
    public @ResponseBody ResponseEntity<Organization> addUserToOrganization(@PathVariable(value = "organizationId") final String organizationId, @PathVariable(value = "userId") final String userId)
        throws Exception {

        logger.info("Add user: {} to organization: {}.", userId, organizationId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // Find the user
            final User originalUser = service.get(userId, User.class);
            if (originalUser == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find user for " + userId + ".");
            }

            final Organization originalOrganization = service.get(organizationId, Organization.class);
            if (originalOrganization == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find organization for " + organizationId + ".");
            }

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            originalOrganization.getMembers().add(originalUser);

            // Update
            service.update(originalOrganization);
            service.commit();

            return new ResponseEntity<>(originalOrganization, HttpStatus.CREATED);

        } catch (final Exception e) {
            logger.error("Error adding user: {} to organization: {}.", userId, organizationId, e);
            handleException(e);
            return null;
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
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // Find the user
            final User originalUser = service.get(userId, User.class);
            final Organization originalOrganization = service.get(organizationId, Organization.class);

            if (originalUser == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find user for " + userId + ".");
            }
            if (originalOrganization == null) {
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find organization for " + organizationId + ".");
            }

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            originalOrganization.getMembers().remove(originalUser);

            // Update
            service.update(originalOrganization);
            service.commit();

            return new ResponseEntity<>(originalOrganization, HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Error adding user: {} to organization: {}.", userId, organizationId, e);
            handleException(e);
            return null;
        }
    }

    
    /**
     * Adds the organization icon.
     *
     * @param organizationId the organization id
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add icon for organization")
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Saveed icon for organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PostMapping(value = "/organization/{organizationId}/icon")
    public ResponseEntity<Void> addOrganizationIcon(@PathVariable("organizationId") final String organizationId, @RequestParam("file") MultipartFile inputFile) throws Exception {

        logger.info("Add icon for organization: {}.", organizationId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // find organization record, return 404 if not found
            final Organization organization = service.get(organizationId, Organization.class);
            if (organization == null) {
                throw new RestException(false, 404, "Not found", "Unable to find organization for " + organizationId);
            }

            // ensure file exists
            if (inputFile == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file is null");
            }

            // check for file
            final String fileName = inputFile.getOriginalFilename();
            if (fileName == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file has null filename");
            }

            // check file size
            final int maxFileSize = Integer.valueOf(PropertyUtility.getProperty("refset.icon.file.maxsize"));
            if (inputFile.getSize() > maxFileSize) {
                throw new RestException(false, 413, "Failed expectation", "File size must be less than 2 MB");
            }

            final String extension = FileUtility.getFileExtension(StringUtils.cleanPath(fileName)).toLowerCase();
            final List<String> fileTypes = Arrays.asList(PropertyUtility.getProperty("refset.icon.file.types").split(";"));
            
            if (!fileTypes.contains(extension)) {
                throw new RestException(false, 417, "Failed expectation", "Format must be one of " + org.apache.commons.lang3.StringUtils.join(fileTypes, " ") + ".");
            }

            final String storageDirectory = PropertyUtility.getProperty("refset.organization.icon.file.dir");
            final String uri = storageDirectory + organizationId + "." + extension;

            logger.debug("Add organization icon uploadUri = " + uri);

            try (InputStream is = inputFile.getInputStream()) {
                S3ConnectionWrapper.uploadToS3(uri, is);
            }

            final String iconUri = PropertyUtility.getProperty("refset.organization.icon.url.prefix") + organizationId + "." + extension;

            organization.setIconUri(iconUri);

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();
            service.update(organization);
            service.commit();

            // Return the object
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Trying to add organization icon for organiation " + organizationId, e);
            handleException(e);
            return null;
        }

    }

    /**
     * Update organization icon.
     *
     * @param organizationId the organization id
     * @param inputFile the input file
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Update icon for organization", response = User.class)
    @ApiResponses(value = {
        @ApiResponse(code = 202, message = "Updated icon for organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 409, message = "Conflict"), @ApiResponse(code = 417, message = "Failed Expectation"),
        @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @PutMapping(value = "/organization/{organizationId}/icon")
    public ResponseEntity<Void> updateOrganizationIcon(@PathVariable("organizationId") final String organizationId, @RequestParam("file") MultipartFile inputFile) throws Exception {

        logger.info("Add icon for organization: {}.", organizationId);
        // TODO check permissions, fail if not authorized.
        final User authUser = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            // find organization record, return 404 if not found
            final Organization organization = service.get(organizationId, Organization.class);
            if (organization == null) {
                throw new RestException(false, 404, "Not found", "Unable to find organization for " + organizationId);
            }

            // ensure file exists
            if (inputFile == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file is null");
            }

            // check for file
            final String fileName = inputFile.getOriginalFilename();
            if (fileName == null) {
                throw new RestException(false, 417, "Failed expectation", "Uploaded file has null filename");
            }

            // check file size
            final int maxFileSize = Integer.valueOf(PropertyUtility.getProperty("refset.icon.file.maxsize"));
            if (inputFile.getSize() > maxFileSize) {
                throw new RestException(false, 413, "Failed expectation", "File size must be less than 2 MB");
            }

            final String extension = FileUtility.getFileExtension(StringUtils.cleanPath(fileName)).toLowerCase();
            final List<String> fileTypes = Arrays.asList(PropertyUtility.getProperty("refset.icon.file.types").split(";"));
            
            if (!fileTypes.contains(extension)) {
                throw new RestException(false, 417, "Failed expectation", "Format must be one of " + org.apache.commons.lang3.StringUtils.join(fileTypes, " ") + ".");
            }

            final String storageDirectory = PropertyUtility.getProperty("refset.organization.icon.file.dir");
            final String uri = storageDirectory + organizationId + "." + extension;

            logger.debug("Adding organization icon upload URI = " + uri);

            try (InputStream is = inputFile.getInputStream()) {
                S3ConnectionWrapper.uploadToS3(uri, is);
            }

            final String iconUri = PropertyUtility.getProperty("refset.organization.icon.url.prefix") + organizationId + "." + extension;

            organization.setIconUri(iconUri);

            service.setModifiedBy(authUser.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();
            service.update(organization);
            service.commit();

            // Return the object
            return new ResponseEntity<>(HttpStatus.ACCEPTED);

        } catch (final Exception e) {
            logger.error("Trying to add organization icon for organiation " + organizationId, e);
            handleException(e);
            return null;
        }

    }

}