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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.AuthContext;
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
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

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

    /** The request. */
    @Autowired
    HttpServletRequest request;

    /**
     * Return the organization.
     *
     * @param id the id
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
    public ResponseEntity<Organization> getOrganization(@PathVariable(value = "id") final String id) throws Exception {

        try {
            logger.info("Get organization {}", id);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {
                final Organization organization = service.get(id, Organization.class);
                return new ResponseEntity<>(organization, HttpStatus.OK);
            }
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
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final Organization org = (Organization) organization;

                service.setModifiedBy(user.getId());
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
     * @param organizationJsonStr the organization json str
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
    @RequestMapping(value = "/organization/{id}", method = RequestMethod.PATCH, consumes = MediaType.APPLICATION_JSON)
    public ResponseEntity<Organization> updateOrganization(@PathVariable(value = "id") final String id, @RequestBody final Organization organization) throws Exception {

        try {
            logger.info("Update organization: {}", organization);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final Organization original = service.get(id, Organization.class);

                if (original == null) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find organization for id " + id + ".");
                }

                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                original.patchFrom(organization);
                service.update(original);
                service.commit();

                return new ResponseEntity<>(original, HttpStatus.OK);
            }
        } catch (final Exception e) {
            logger.error("Error updating organization. Id: {}", id);
            handleException(e);
            return null;
        }
    }

    /**
     * Logical delete (inactivate) the organization.
     * 
     * @param id
     * @return
     * @throws Exception
     */
    @ApiOperation(value = "Inactivate organization")
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Inactivate specified organization"), @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 403, message = "Forbidden"),
        @ApiResponse(code = 404, message = "Not Found"), @ApiResponse(code = 417, message = "Failed Expectation"), @ApiResponse(code = 500, message = "Internal server error")
    })
    @RecordMetric
    @DeleteMapping(value = "/organization/{id}")
    public ResponseEntity<Void> deleteOrganization(@PathVariable("id") final String id) throws Exception {

        try {
            logger.info("Inactivate organization: {}", id);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                // Find the object
                final Organization organization = service.get(id, Organization.class);

                if (organization == null) {
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Unable to find organization for id:" + id);
                }

                organization.setActive(false);
                service.update(organization);
                service.commit();
                // Return the status
                return new ResponseEntity<>(HttpStatus.ACCEPTED);
            }
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
    @RequestMapping(value = "/organization/{id}/users/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListUser> getOrganizationUsers(@PathVariable(value = "id") final String id) throws Exception {

        try {
            logger.info("Get organization users. Id: {}", id);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("organizationId:" + id);

                final ResultList<User> orgUsers = service.find(query, pfs, User.class, null);
                return new ResponseEntity<>(new ResultListUser(orgUsers), HttpStatus.OK);
            }
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
    @RequestMapping(value = "/organization/{id}/teams/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListTeam> getOrganizationTeams(@PathVariable(value = "id") final String id) throws Exception {

        try {
            logger.info("Get organization teams. Id: {}", id);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("organizationId:" + id);

                final ResultList<Team> orgTeams = service.find(query, pfs, Team.class, null);
                return new ResponseEntity<>(new ResultListTeam(orgTeams), HttpStatus.OK);
            }
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
     * @throws Exception
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
    @RequestMapping(value = "/organization/{id}/projects/", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    public ResponseEntity<ResultListProject> getOrganizationProjects(@PathVariable(value = "id") final String id) throws Exception {

        try {
            logger.info("Get organization teams. Id: {}", id);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("organizationId:" + id);

                final ResultList<Project> orgProjects = service.find(query, pfs, Project.class, null);
                return new ResponseEntity<>(new ResultListProject(orgProjects), HttpStatus.OK);
            }
        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }

}