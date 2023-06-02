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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetMemberComparison;
import org.ihtsdo.refsetservice.model.SendCommunicationEmailInfo;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.SyncAgent;
import org.ihtsdo.refsetservice.sync.util.SyncTestingInitializer;
import org.ihtsdo.refsetservice.terminologyservice.DiscussionService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Hidden;
import springfox.documentation.annotations.ApiIgnore;

/**
 * Controller for /concept endpoints.
 */
@RestController
@Api(tags = "refset", description = "Endpoints for refsets and refset member managment")
public class RefsetController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(RefsetController.class);

    /** The local directory to store exported refset files. */
    private static String exportFileDir;

    /** The local directory to store exported refset files. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    /** Static initialization. */
    static {

        exportFileDir = PropertyUtility.getProperty("export.fileDir") + "/";
    }

    /**
     * Returns the refset.
     *
     * @param refsetId the refset ID
     * @param versionDate the version date or IN DEVELOPMENT
     * @return the refset
     * @throws Exception the exception
     */
    @ApiOperation(
        value = "Get the refset for the specified ID and version date. To see certain results this call requires authentication with the correct role.",
        response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetId", value = "The ID of the refset to return.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "versionDate", value = "The date of the refset version (YYYY-MM-DD) or IN DEVELOPMENT.", required = true,
            dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/versionDate/{versionDate}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Refset> getRefset(@PathVariable(value = "refsetId") final String refsetId,
        @PathVariable(value = "versionDate") final String versionDate) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("getRefset: refsetId: " + refsetId + " ; versionDate: " + versionDate);

            // no auth required
            final User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(service, user, refsetId, versionDate);

            // if (user.getUserName().equals(SecurityService.GUEST_USERNAME) && (refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT) ||
            // refset.isPrivateRefset())) {
            // return new ResponseEntity<>(new Refset(), HttpStatus.OK);
            //
            // } else if ((refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT) || refset.isPrivateRefset()) &&
            // !user.doesUserHavePermission(User.ROLE_VIEWER, refset.getProject())) {
            // throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User does not have permission to view this refset.");
            // }

            RefsetService.getRefsetDescriptions(refset);

            LOG.debug("getRefset: Including discussion count");
            DiscussionService.attachRefsetDiscussionCount(service, user, refset);

            if (RefsetMemberService.REFSETS_BEING_UPDATED.contains(refset.getId())) {

                refset.setLocked(true);
            }

            if (RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.contains(refset.getId())) {

                refset.setUpgradeWarning(true);
                RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.remove(refset.getId());
            }

            refset.getProject().getEdition().setModuleNames(ProjectService.getModuleNames(refset.getProject()));

            LOG.debug("getRefset: refset: " + ModelUtility.toJson(refset));

            return new ResponseEntity<>(refset, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Returns the refset member count.
     *
     * @param refsetInternalId the internal refset ID
     * @param request the request
     * @return the refset member count
     * @throws Exception the exception
     */
    @ApiOperation(value = "Returns the refset member count. To see certain results this call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to check.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/memberCount", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> getRefsetMemberCount(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final HttpServletRequest request) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("getRefsetMemberCount: refsetInternalId: " + refsetInternalId);

            // no auth required
            final User user = SecurityService.getUserFromSession();
            final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

            if (refset == null) {
                throw new Exception("Unable to retrieve reference set " + refsetInternalId);
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            RefsetService.setRefsetMemberCount(service, refset, false);

            LOG.debug("getRefsetMemberCount: refset: " + refset.getRefsetId() + " ; member count: " + refset.getMemberCount());

            return new ResponseEntity<>(refset.getMemberCount() + "", HttpStatus.OK);

        } catch (final Exception e) {
            return handleException(e);
        }
    }

    /**
     * Returns the refset.
     *
     * @param refsetInternalId the internal refset ID
     * @param request the request
     * @return the refset
     * @throws Exception the exception
     */
    @ApiOperation(value = "Returns if the refset is locked and the status of any member changes", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. Payload may include member update statuses"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to check.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/isLocked", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> isRefsetLocked(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final HttpServletRequest request) throws Exception {

        try {

            // no auth required
            SecurityService.getUserFromSession();
            final boolean isLocked = RefsetMemberService.REFSETS_BEING_UPDATED.contains(refsetInternalId);
            String returnString = isLocked + "";
            // LOG.debug("isRefsetLocked: refsetInternalId: " + refsetInternalId + " ;
            // Locked: " + isLocked);
            // LOG.debug("isRefsetLocked: refsetsUpdatedMembers: " +
            // RefsetMemberService.REFSETS_UPDATED_MEMBERS);
            // LOG.debug("isRefsetLocked: does update map contain this refset: " +
            // RefsetMemberService.REFSETS_UPDATED_MEMBERS.containsKey(refsetInternalId));

            if (!isLocked && RefsetMemberService.REFSETS_UPDATED_MEMBERS.containsKey(refsetInternalId)) {

                returnString = ModelUtility.toJson(RefsetMemberService.REFSETS_UPDATED_MEMBERS.get(refsetInternalId));
                RefsetMemberService.REFSETS_UPDATED_MEMBERS.remove(refsetInternalId);
            }

            return new ResponseEntity<>(returnString, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Add new refset members.
     *
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a comma separated list of concepts to add
     * @param ecl an ECL query to identify concepts to add
     * @param conceptFile a file containing concept IDs to add
     * @param fileType the type of file uploaded (list or rf2)
     * @return the new internal refset ID
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add new refset members. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully added the members. payload contains the status of the operation. Long running background process, "
                + "call /refset/{refsetInternalId}/isLocked to get full status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "conceptIds", value = "A comma separated list of concept IDs to add", required = false, dataTypeClass = String.class,
            paramType = "body"),
        @ApiImplicitParam(name = "ecl", value = "An ECL query to identify concepts to add", required = false, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "conceptFile", value = "A file containing concept IDs to add", required = false, dataTypeClass = MultipartFile.class,
            paramType = "query"),
        @ApiImplicitParam(name = "fileType", value = "The type of file uploaded (list or rf2)", required = false, dataTypeClass = String.class,
            paramType = "query"),
    })
    @PostMapping("/refset/{refsetInternalId}/members")
    public @ResponseBody ResponseEntity<String> addRefsetMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl,
        @RequestParam(required = false) final MultipartFile conceptFile, @RequestParam(required = false) final String fileType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
            List<String> conceptIdList = new ArrayList<>();
            String error = "";
            List<String> unaddedConcepts;
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            LOG.debug("addRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType);

            String type = "an individual concept";

            // create the list of concepts based on what was passed in
            if (conceptIds != null && !conceptIds.equals("")) {

                conceptIdList = new ArrayList<String>(Arrays.asList(conceptIds.split(",")));

                if (conceptIdList.size() > 1) {

                    type = "concepts by list";
                }

            } else if (ecl != null && !ecl.equals("")) {

                type = "by changing ECL definition";
                conceptIdList = RefsetMemberService.getConceptIdsFromEcl(refset.getBranchPath(), ecl);
            } else {

                type = "by file";

                conceptIdList = RefsetService.getConceptIdsFromFile(conceptFile, fileType);
            }

            LOG.debug("addRefsetMembers: conceptIdList: " + conceptIdList);

            // add the list of concepts as members to the refset
            unaddedConcepts = RefsetMemberService.addRefsetMembers(service, user, refset, conceptIdList);

            // see if there are any concepts that were unable to be added and craft the
            // error message
            if (unaddedConcepts.size() > 0) {

                error = "Unable to add concepts ";

                for (final String unaddedConcept : unaddedConcepts) {

                    error += unaddedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            // service.commit();
            LOG.debug("addRefsetMembers: Finished with " + unaddedConcepts.size() + " invaild concepts");

            if (error.equals("")) {

                AuditEntryHelper.addMembersEntry(refset, type, conceptIds);

                return new ResponseEntity<>("{\"status\": \"All concepts added.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Remove or inactivate refset membership for a group of concepts.
     *
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a comma separated list of concepts to remove
     * @param ecl an ECL query to identify concepts to remove
     * @param conceptFile a file containing concept IDs to remove
     * @param fileType the type of file uploaded (list or rf2)
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiOperation(value = "Remove or inactivate refset membership for a group of concepts. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully removed the members. payload contains the status of the operation. Long running background process, "
                + "call /refset/{refsetInternalId}/isLocked to get full status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "conceptIds", value = "A comma separated list of concept IDs to remove", required = false, dataTypeClass = String.class,
            paramType = "body"),
        @ApiImplicitParam(name = "ecl", value = "An ECL query to identify concepts to remove", required = false, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "conceptFile", value = "A file containing concept IDs to remove", required = false, dataTypeClass = MultipartFile.class,
            paramType = "query"),
        @ApiImplicitParam(name = "fileType", value = "The type of file uploaded (list or rf2)", required = false, dataTypeClass = String.class,
            paramType = "query"),
    })
    @PostMapping("/refset/{refsetInternalId}/removeMembers")
    public @ResponseBody ResponseEntity<String> removeRefsetMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl,
        @RequestParam(required = false) final MultipartFile conceptFile, @RequestParam(required = false) final String fileType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
            String conceptsToRemove = null;
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            LOG.debug(
                "removeRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType);

            String error = "";
            String type = "an individual concept";

            // If concepts were passed in use those
            if (conceptIds != null && !conceptIds.equals("")) {

                conceptsToRemove = conceptIds;

                if (Arrays.asList(conceptsToRemove.split(",")).size() > 1) {

                    type = "concepts by list";
                }

            } else if (ecl != null && !ecl.equals("")) {

                type = "by changing ECL definition";
                conceptsToRemove = String.join(",", RefsetMemberService.getConceptIdsFromEcl(refset.getBranchPath(), ecl + " AND ^" + refset.getRefsetId()));
            } else {

                type = "by file";

                conceptsToRemove = String.join(",", RefsetService.getConceptIdsFromFile(conceptFile, fileType));
            }

            LOG.debug("removeRefsetMembers: conceptIds: " + conceptIds);

            // add the list of concepts as members to the refset
            final List<String> unremovedConcepts = RefsetMemberService.removeRefsetMembers(service, user, refset, conceptsToRemove);
            // service.commit();

            // see if there are any concepts that were unable to be added and craft the
            // error message
            if (unremovedConcepts.size() > 0) {

                error = "Unable to remove concepts ";

                for (final String unremovedConcept : unremovedConcepts) {

                    error += unremovedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                AuditEntryHelper.removeMembersEntry(refset, type, conceptsToRemove);

                return new ResponseEntity<>("{\"status\": \"All concepts removed.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Add new intensional refset definition exceptions.
     *
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a comma separated list of concepts to add
     * @param ecl an ECL query to identify concepts to add
     * @param conceptFile a file containing concept IDs to add
     * @param fileType the type of file uploaded (list or rf2)
     * @param definitionExceptionType the definition exception type
     * @return the status or error message
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add new intensional refset definition exceptions. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully added the exceptions. payload contains the status of the operation. Long running background process, "
                + "call /refset/{refsetInternalId}/isLocked to get full status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "conceptIds", value = "A comma separated list of concept IDs to add", required = false, dataTypeClass = String.class,
            paramType = "body"),
        @ApiImplicitParam(name = "ecl", value = "An ECL query to identify concepts to add", required = false, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "conceptFile", value = "A file containing concept IDs to add", required = false, dataTypeClass = MultipartFile.class,
            paramType = "query"),
        @ApiImplicitParam(name = "fileType", value = "The type of file uploaded (list or rf2)", required = false, dataTypeClass = String.class,
            paramType = "query"),
    })
    @PostMapping("/refset/{refsetInternalId}/definitionExceptions")
    public @ResponseBody ResponseEntity<String> addRefsetDefinitionExceptions(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl,
        @RequestParam(required = false) final MultipartFile conceptFile, @RequestParam(required = false) final String fileType,
        @RequestParam(required = false) final String definitionExceptionType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
            List<String> conceptIdList = new ArrayList<>();
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            LOG.debug("addRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: "
                + fileType + " ; definitionExceptionType: " + definitionExceptionType);

            String inclusionEcl = ecl;

            if (ecl == null || ecl.equals("")) {

                // create the list of concepts based on what was passed in
                if (conceptIds != null && !conceptIds.equals("")) {

                    conceptIdList = Arrays.asList(conceptIds.split(","));

                } else if (ecl == null || ecl.equals("")) {

                    conceptIdList = RefsetService.getConceptIdsFromFile(conceptFile, fileType);
                }

                inclusionEcl = RefsetMemberService.conceptListToEclStatement(conceptIdList);
            }

            final String status = RefsetService.addDefinitionException(service, user, refset, inclusionEcl, definitionExceptionType);
            // service.commit();

            if (!status.startsWith("Error")) {

                return new ResponseEntity<>("{\"status\": \"Definition exception added.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Remove an intensional refset definition exception.
     *
     * @param refsetInternalId the internal refset ID
     * @param definitionExceptionId the exception ID
     * @return the status or error message
     * @throws Exception the exception
     */
    @ApiOperation(value = "Remove an intensional refset definition exception. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully removed the exception. payload contains the status of the operation. Long running background process, "
                + "call /refset/{refsetInternalId}/isLocked to get full status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "definitionExceptionId", value = "The internal definition ID", required = true, dataTypeClass = String.class,
            paramType = "path"),
    })
    @PostMapping("/refset/{refsetInternalId}/removeDefinitionException/{definitionExceptionId}")
    public @ResponseBody ResponseEntity<String> removeRefsetDefinitionExceptions(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @PathVariable(value = "definitionExceptionId") final String definitionExceptionId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
            final User user = SecurityService.getUserFromSession();
            LOG.debug("removeRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; definitionExceptionId: " + definitionExceptionId);

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.removeDefinitionException(service, user, refset, definitionExceptionId);
            // service.commit();

            if (!status.startsWith("Error")) {

                return new ResponseEntity<>("{\"status\": \"Definition exception removed.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Create a new refset.
     *
     * @param refsetParameters The paramaters for the new refset
     * @param bindingResult the binding result
     * @return the new internal refset ID
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add a new refset. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully added the refset. payload contains the new refset ID."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetParameters", value = "The required fields of the refset to add.", required = true, dataTypeClass = Refset.class,
            paramType = "body")
    })
    @PostMapping("/refset")
    public @ResponseBody ResponseEntity<String> createRefset(final @RequestBody Refset refsetParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        String newRefsetInternalId = null;

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("createRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));

            final User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            String status = "";
            final Object returned = RefsetService.createRefset(service, user, refsetParameters);

            if (returned instanceof String) {

                status = (String) returned;
            } else {

                final Refset refset = (Refset) returned;
                newRefsetInternalId = refset.getId();
                status = refset.getRefsetId();
            }

            // service.commit();

            if (status.startsWith("Error")) {

                return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
            }

            return new ResponseEntity<>("{\"refsetId\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(newRefsetInternalId);
        }

    }

    /**
     * Modify an existing refset that is in edit mode.
     *
     * @param refsetInternalId the internal refset ID
     * @param refsetParameters the refset parameters
     * @param bindingResult the binding result
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @ApiOperation(value = "Modify an existing refset that is in edit mode. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully modified the refset."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "refsetParameters", value = "The refset to modify.", required = true, dataTypeClass = Refset.class, paramType = "body")
    })
    @PutMapping("/refset/{refsetInternalId}")
    public @ResponseBody ResponseEntity<String> modifyRefset(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final @RequestBody Refset refsetParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("modifyRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));
            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final String status = RefsetService.modifyRefset(service, user, refset, refsetParameters);
            service.commit();

            if (!status.startsWith("Error")) {
                return new ResponseEntity<>("{\"refsetInternalId\": \"" + refsetInternalId + "\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Recalculate the definition of an intensional refset, updating the members.
     *
     * @param refsetInternalId the internal refset ID
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @ApiOperation(value = "Recalculate the definition of an intensional refset, updating the members. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully modified the refset. Long running background process, call /refset/{refsetInternalId}/isLocked to get full status"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @PutMapping("/refset/{refsetInternalId}/recalculateDefinition")
    public @ResponseBody ResponseEntity<String> recalculateRefsetDefinition(@PathVariable(value = "refsetInternalId") final String refsetInternalId)
        throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("recalculateRefsetDefinition: refsetInternalId: " + ModelUtility.toJson(refsetInternalId));
            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.modifyRefsetDefinition(user, service, refset, refset.getDefinitionClauses());
            // service.commit();

            if (!status.startsWith("Error")) {
                return new ResponseEntity<>("{\"refsetInternalId\": \"" + refsetInternalId + "\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Get Workflow history for a refset.
     *
     * @param refsetInternalId the refset internal id
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the workflow history
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get Workflow history search results. This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/workflowHistory", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<WorkflowHistory>> getWorkflowHistory(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            // LOG.debug("getWorkflowHistory refsetInternalId: " + refsetInternalId + " ;
            // searchParameters: " + ModelUtility.toJson(searchParameters));

            final User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            final ResultList<WorkflowHistory> results = WorkflowService.getWorkflowHistory(service, refset, searchParameters);

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Change the workflow status of a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @param action the action triggering the status change
     * @param notes Notes about the status change
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @ApiOperation(value = "Change the workflow status of a refset. This call requires authentication with the correct role.", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully changed the refset status. The payload contains the updated refset"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "action", value = "The action triggering the status change", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "notes", value = "Notes about the status change", required = false, dataTypeClass = String.class, paramType = "query"),
    })
    @PostMapping("/refset/{refsetInternalId}/workflowStatus")
    public @ResponseBody ResponseEntity<Refset> setWorkflowStatus(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam final String action, @RequestParam(required = false) final String notes) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("setWorkflowStatus: refsetInternalId: " + refsetInternalId + " ; action: " + action + " ; notes: " + notes);

            final User user = SecurityService.getUserFromSession();
            Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            final String currentStatus = refset.getWorkflowStatus();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            if (action.equals(WorkflowService.FINISH_EDIT)) {

                AuditEntryHelper.addEditingCycleEntry(refset, true);
            } else if (action.equals(WorkflowService.CANCEL_EDIT)) {

                AuditEntryHelper.addEditingCycleEntry(refset, false);
            } else if (action.equals(WorkflowService.CANCEL_UPGRADE)) {
                RefsetMemberService.REFSETS_UPDATED_MEMBERS.remove(refsetInternalId);
            }

            // if the status is Published then create a new version of the refset that is
            // ready to be edited
            if (currentStatus == null || currentStatus.equals(WorkflowService.PUBLISHED)) {

                final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, user, refset.getId(), true);
                refset = RefsetService.getRefset(service, user, newRefsetInternalId);

                if (action.equals(WorkflowService.EDIT) && !refset.isBasedOnLatestVersion()) {

                    RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.add(newRefsetInternalId);
                    refset.setUpgradeWarning(true);
                }

                return new ResponseEntity<>(refset, HttpStatus.OK);
            }

            refset = WorkflowService.setWorkflowStatusByAction(service, user, action, refset, notes);
            // service.commit();

            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(refset.getWorkflowStatus())) {

                LOG.debug("setWorkflowStatus: updated refset: " + ModelUtility.toJson(refset));
                return new ResponseEntity<>(refset, HttpStatus.OK);
            } else {

                LOG.debug("setWorkflowStatus: did not update workflow status.");
                return null;
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Modify an existing refset that is in edit mode.
     *
     * @param refsetInternalId the internal refset ID
     * @param notes the notes
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @ApiOperation(value = "Modify a refset workflow status note. This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully changed the status note. The payload contains the full updated workflow history"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "notes", value = "The updated note", required = true, dataTypeClass = String.class, paramType = "body"),
    })
    @PutMapping("/refset/{refsetInternalId}/workflowNote")
    public @ResponseBody ResponseEntity<ResultList<WorkflowHistory>> updateWorkflowNote(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = true) final String notes) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // LOG.debug("updateWorkflowNote: refsetInternalId: " + refsetInternalId + "
            // ;notes: " + notes);
            final User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            // not used final String currentStatus = refset.getWorkflowStatus();

            WorkflowService.updateWorkflowNote(service, user, refset, notes);
            // service.commit();

            final ResultList<WorkflowHistory> results = WorkflowService.getWorkflowHistory(service, refset, new SearchParameters());

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Start the publication of all Ready for Publication refsets in a code system by promoting them to the REFSETS branch. This call requires authentication
     * with the correct role. ** IMPORTANT ** Once this step is taken it will be very hard to reverse
     *
     * @param codeSystem a code system to limit the publication to
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiIgnore
    @ApiOperation(
        value = "Start the publication of all Ready for Publication refsets in a code system by promoting them to the REFSETS branch. "
            + "** IMPORTANT ** Once this step is taken it will be very hard to reverse. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully began the publication process. The payload contains the status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "codeSystem", value = "A code system to limit the publication to", required = true, dataTypeClass = String.class,
            paramType = "query"),
    })
    @PutMapping("/admin/startAllRefsetPublications")
    public @ResponseBody ResponseEntity<String> startAllRefsetPublications(@RequestParam(required = true) final String codeSystem) throws Exception {

        final User user = SecurityService.getUserFromSession();

        if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
            return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
        }

        if (StringUtility.isEmpty(codeSystem)) {

            throw new Exception("A Code System must be specified.");
        }

        try (final TerminologyService service = new TerminologyService()) {

            final Edition edition = service.findSingle("shortName:" + codeSystem, Edition.class, null);

            if (edition == null) {
                return new ResponseEntity<>("The code system '" + codeSystem + "' could not be found", HttpStatus.EXPECTATION_FAILED);
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            LOG.debug("startAllRefsetPublications: editionShortName (codeSystem): " + codeSystem);

            final List<String> refsetsNotUpdated = WorkflowService.startAllRefsetPublications(service, codeSystem);
            String error = "";

            // see if there are any refsets that were unable to be updated and craft the
            // error message
            if (refsetsNotUpdated.size() > 0) {

                error = "Unable to promote refsets in code system " + codeSystem + ": ";

                for (final String unremovedConcept : refsetsNotUpdated) {

                    error += unremovedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                final String message = "All reference sets promoted in code system " + codeSystem;
                return new ResponseEntity<>("{\"status\": \"" + message + ".\"}", HttpStatus.OK);

            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Complete the publication of all Ready for Publication refsets in a code system. This call requires authentication with the correct role.
     *
     * @param versionDate the publication date of the refset in yyyy-MM-dd format
     * @param codeSystem a code system to limit the refset to
     * @param publishType if value is 'localset' this will publish (non-snomed versioning) only local sets. If not supplied or any other value this will
     *            published everything other than local sets.
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiIgnore
    @ApiOperation(
        value = "Complete the publication of all Ready for Publication refsets in a code system. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully published the refsets. The payload contains the status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "versionDate", value = "the publication date of the refsets (YYYY-MM-DD)", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "codeSystem", value = "A code system to limit the publication to", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "publishType",
            value = "If value is 'localset' this will publish (non-snomed versioning) only local sets. "
                + "If not supplied or any other value this will published everything other than local sets.",
            required = false, dataTypeClass = String.class, paramType = "query"),
    })
    @PutMapping("/admin/completeAllRefsetPublications")
    public @ResponseBody ResponseEntity<String> completeAllRefsetPublications(@RequestParam(required = true) final String versionDate,
        @RequestParam(required = true) final String codeSystem, @RequestParam(required = false) final String publishType) throws Exception {

        final User user = SecurityService.getUserFromSession();
        String typeToPublish = "regular";

        if (StringUtility.isEmpty(codeSystem)) {
            return new ResponseEntity<>("A Code System must be specified.", HttpStatus.EXPECTATION_FAILED);
        }

        try {
            new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE).parse(versionDate);

        } catch (final Exception e) {
            return new ResponseEntity<>("The version date must be specified in this format: " + DateUtility.DATE_FORMAT_REVERSE, HttpStatus.EXPECTATION_FAILED);
        }

        String branchPath = "MAIN/";

        if (!codeSystem.equals("SNOMEDCT")) {
            branchPath += codeSystem + "/";
        }

        branchPath += versionDate;

        if (!WorkflowService.doesBranchExist(branchPath)) {
            return new ResponseEntity<>("The version branch '" + branchPath
                + "' does not exist. This must be created and populated with the reference sets to be versioned outside of this tool "
                + "before this publication completion process can be run.", HttpStatus.EXPECTATION_FAILED);
        }

        try (final TerminologyService service = new TerminologyService()) {

            final Edition edition = service.findSingle("shortName:" + codeSystem, Edition.class, null);

            if (edition == null) {
                return new ResponseEntity<>("The code system '" + codeSystem + "' could not be found", HttpStatus.EXPECTATION_FAILED);
            }

            if (!StringUtility.isEmpty(publishType) && publishType.equals("localset")) {

                typeToPublish = "localset";

                if (!user.checkPermission(User.ROLE_ADMIN, edition, null)) {
                    return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
                }
            } else {

                if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
                    return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
                }
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            LOG.debug("completeAllRefsetPublications: versionDate: " + versionDate + " ; editionShortName (codeSystem): " + codeSystem + " ; typeToPublish: "
                + typeToPublish);

            final List<String> refsetsNotUpdated = WorkflowService.completeAllRefsetPublications(service, versionDate, codeSystem, typeToPublish);
            String error = "";
            String messageType = "";

            if (typeToPublish.equals("localset")) {
                messageType = "local ";
            }

            service.commit();

            // see if there are any refsets that were unable to be updated and craft the
            // error message
            if (refsetsNotUpdated.size() > 0) {

                error = "Unable to complete publication for " + messageType + "reference sets in code system " + codeSystem + ": ";

                for (final String refsetNotUpdated : refsetsNotUpdated) {

                    error += refsetNotUpdated + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                final String message = "All " + messageType + "reference set publications completed in code system " + codeSystem;
                return new ResponseEntity<>("{\"status\": \"" + message + ".\"}", HttpStatus.OK);

            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Publish a Ready for Publication local refset in a code system. This call requires authentication with the correct role.
     *
     * @param refsetInternalId the internal refset ID
     * @param versionDate the publication date of the refset in yyyy-MM-dd format
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiIgnore
    @ApiOperation(value = "Publish a Ready for Publication local refset in a code system. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully published the refset. The payload contains the status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "versionDate", value = "the publication date of the refset (YYYY-MM-DD)", required = true, dataTypeClass = String.class,
            paramType = "query")
    })
    @PutMapping("/admin/refset/{refsetInternalId}/publishLocalset")
    public @ResponseBody ResponseEntity<String> publishLocalsetRefset(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = true) final String versionDate) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

            if (!user.checkPermission(User.ROLE_ADMIN, refset.getEdition(), refset.getProject().getCrowdProjectId())) {
                return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
            }

            LOG.debug("publishLocalsetRefset: refsetInternalId: " + refsetInternalId + " ; versionDate: " + versionDate);

            final List<String> refsetsNotUpdated = WorkflowService.completeRefsetPublication(service, refset, versionDate);
            String error = "";

            // see if there are any refsets that were unable to be updated and craft the
            // error message
            if (refsetsNotUpdated.size() > 0) {
                error = "Unable to complete publication for local reference set " + refset.getRefsetId();
            }

            if (error.equals("")) {

                final Refset newVersionRefset = RefsetService.getLatestRefsetVersion(service, refset.getRefsetId());

                // final String message = "Publication completed for local reference set " + refset.getRefsetId();
                return new ResponseEntity<>(ModelUtility.toJson(newVersionRefset), HttpStatus.OK);

            } else {

                return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Set refsets that failed publication back to 'Ready For Edit' status. This call requires authentication with the correct role.
     *
     * @param refsetIds a comma separated list of refset IDs
     * @param notes the reason why the refsets failed
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiIgnore
    @ApiOperation(value = "Set refsets that failed publication back to 'Ready For Edit' status. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully changed the refset statuses. The payload contains the status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetIds", value = "A comma separated list of refset IDs", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "notes", value = "The reason why the refsets failed", required = true, dataTypeClass = String.class, paramType = "query")
    })
    @PutMapping("/admin/failRefsetPublications")
    public @ResponseBody ResponseEntity<String> failRefsetPublications(@RequestParam(required = true) final String refsetIds,
        @RequestParam(required = true) final String notes) throws Exception {

        final User user = SecurityService.getUserFromSession();

        if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
            return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
        }

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            LOG.debug("failRefsetPublications: refset IDs: " + refsetIds + " ; notes: " + notes);

            final List<String> refsetsNotUpdated = WorkflowService.setBatchWorkflowStatusByAction(service, user, refsetIds, WorkflowService.FAILS_RVF, notes);
            String error = "";

            // see if there are any refsets that were unable to be updated and craft the
            // error message
            if (refsetsNotUpdated.size() > 0) {

                error = "Unable to update reference sets: ";

                for (final String unremovedConcept : refsetsNotUpdated) {

                    error += unremovedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                return new ResponseEntity<>("{\"status\": \"All reference sets updated.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Create a new version of an existing refset in edit mode.
     *
     * @param refsetInternalId the internal refset ID
     * @return the new refset internal ID or errors
     * @throws Exception the exception
     */
    @ApiOperation(value = "Create a new In Development version of an existing refset in edit mode. This call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully created the new refset version. payload contains the internal ID of the new version."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @PostMapping("/refset/{refsetInternalId}/newVersion")
    public @ResponseBody ResponseEntity<String> createNewRefsetVersion(@PathVariable(value = "refsetInternalId") final String refsetInternalId)
        throws Exception {

        // Check to make sure parameters were properly bound to variables.
        // checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("createNewRefsetVersion: refsetInternalId: " + refsetInternalId);
            final User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, user, refsetInternalId, true);
            // service.commit();

            if (newRefsetInternalId.startsWith("Error")) {

                return new ResponseEntity<>("{\"error\": \"" + newRefsetInternalId + "\"}", HttpStatus.OK);
            }

            return new ResponseEntity<>("{\"refsetInternalId\": \"" + newRefsetInternalId + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Change a refset status.
     *
     * @param refsetInternalId the internal refset ID
     * @param active is the refset active
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiOperation(value = "Change a refset status. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully changed the refset status. payload contains the new status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "active", value = "Is the refset active", required = true, dataTypeClass = boolean.class, paramType = "query")
    })
    @PutMapping("/refset/{refsetInternalId}/refsetStatus")
    public @ResponseBody ResponseEntity<String> updateRefsetStatus(final @PathVariable String refsetInternalId, final boolean active) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // LOG.debug("updateRefsetStatus: refsetInternalId: " + refsetInternalId + "
            // ; active: " + active);
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());

            final String status = RefsetService.updatedRefsetStatus(service, user, refset, active);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Convert intensional refset to extensional.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiOperation(value = "Convert intensional refset to extensional. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully converted the refset. Long running background process, call /refset/{refsetInternalId}/isLocked to get full status. "
                + "Payload contains the status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
    })
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/convert", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> convertToExtensional(final @PathVariable String refsetInternalId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalId);
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refsetInternalId, new HashMap<>());

            // LOG.debug("deleteRefsetEditVersion: refsetInternalId: " +
            // refsetInternalId);
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());

            final String status = RefsetService.convertToExtensional(service, user, refset);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalId);
        }

    }

    /**
     * Delete the edit version of a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    @ApiOperation(value = "Delete the edit version of a refset. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully deleted the refset edit version. payload contains the status."),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
    })
    @DeleteMapping("/refset/{refsetInternalId}/editVersion")
    public @ResponseBody ResponseEntity<String> deleteRefsetEditVersion(final @PathVariable String refsetInternalId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // LOG.debug("deleteRefsetEditVersion: refsetInternalId: " +
            // refsetInternalId);
            final User user = SecurityService.getUserFromSession();

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserPerformInDevelopmentActionsOnRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.deleteInDevelopmentVersion(service, user, refset, true);
            // service.commit();
            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Search Directory.
     *
     * @param searchParameters the search parameters
     * @param searchConcepts the search concepts
     * @param showInDevelopment flag on whether to include IN_DEVELOPMENT refsets
     * @param countComments the count comments
     * @param showOnlyPermitted flag on whether to only show refsets user has specific permission to and not general public refsets
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results. To see certain results this call requires authentication with the correct role.",
        response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class,
            paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "showInDevelopment", value = " A flag on whether to include IN_DEVELOPMENT refsets", required = false,
            dataTypeClass = boolean.class, paramType = "query"),
        @ApiImplicitParam(name = "showOnlyPermitted",
            value = " A flag on whether to only show refsets user has specific permission to and not general public refsets", required = false,
            dataTypeClass = boolean.class, paramType = "query")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/search", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Refset>> searchRefsets(final SearchParameters searchParameters, final boolean searchConcepts,
        @RequestParam(required = false) final Boolean showInDevelopment, @RequestParam(required = false) final Boolean countComments,
        @RequestParam(required = false) final Boolean showOnlyPermitted, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        // no auth required
        final User user = SecurityService.getUserFromSession();
        final boolean includeInDevelopment = !(showInDevelopment != null && !showInDevelopment);
        final boolean onlyShowPermitted = !(showOnlyPermitted != null && showOnlyPermitted);

        // if (showInDevelopment != null && showInDevelopment == false) {
        // includeInDevelopment = false;
        // }
        //
        // if (showOnlyPermitted != null && showOnlyPermitted == true) {
        // onlyShowPermitted = true;
        // }

        try (final TerminologyService service = new TerminologyService()) {

            RefsetService.getInDevelopmentBranchPaths(service);

            LOG.debug("searchRefsets searchParameters: " + ModelUtility.toJson(searchParameters) + "; searchConcepts: " + searchConcepts
                + " ; showInDevelopment: " + includeInDevelopment + " ; countComments: " + countComments);

            final ResultList<Refset> results =
                RefsetService.searchRefsets(user, service, searchParameters, searchConcepts, true, false, includeInDevelopment, onlyShowPermitted);

            if (countComments != null && countComments) {

                LOG.debug("searchRefsets: Including discussion count");
                DiscussionService.attachRefsetDiscussionCounts(service, user, results.getItems());
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Search Taxonomy for members.
     *
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @param request the request
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search the taxonomy for refset members. To see certain results this call requires authentication with the correct role.",
        response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {
        "/refset/{refsetInternalId}/taxonomySearch", "/refset/{refsetInternalId}/conceptSearch"
    }, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ConceptResultList> searchConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final SearchParameters searchParameters, final BindingResult bindingResult, final HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        // no auth required
        final User user = SecurityService.getUserFromSession();
        boolean searchRefsetMembers = false;
        final String uri = request.getRequestURI();

        if (uri.contains("taxonomySearch")) {

            searchRefsetMembers = true;
        }

        try (final TerminologyService service = new TerminologyService()) {

            ConceptResultList results = new ConceptResultList();
            final String query = searchParameters.getQuery();

            LOG.debug("taxonomySearch: searchConcepts: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters)
                + " ; searchRefsetMembers: " + searchRefsetMembers);

            if (query != null && !query.equals("")) {

                results = RefsetMemberService.prepareConceptSearch(service, user, refsetInternalId, searchParameters, searchRefsetMembers);
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Search for refset members.
     *
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param displayType Should results be a list or hierarchical taxonomy
     * @param taxonomyParameters the taxonomy parameters
     * @param countComments the count comments
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search for refset members. To see certain results this call requires authentication with the correct role.",
        response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class,
            paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "displayType", value = "Should results be a list or taxonomy", required = true, dataTypeClass = String.class,
            paramType = "query", defaultValue = "list"),
        @ApiImplicitParam(name = "startingConceptId",
            value = "For taxonomy calls the starting concept ID (exclusive - get the children of this concept not the concept itself)", required = false,
            dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "depth", value = "For taxonomy calls the depth - how many levels of children or parents to retrieve", required = false,
            dataTypeClass = Integer.class, paramType = "query"),
        @ApiImplicitParam(name = "returnChildren", value = "For taxonomy calls should children be returned. If false then parents will be returned",
            required = false, dataType = "boolean", paramType = "query", defaultValue = "true")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/members", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ConceptResultList> getMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final SearchParameters searchParameters, final String displayType, final TaxonomyParameters taxonomyParameters,
        @RequestParam(required = false) final Boolean countComments, final BindingResult bindingResult) throws Exception {

        checkBinding(bindingResult);

        // no auth required
        final long start = System.currentTimeMillis();
        ConceptResultList results = new ConceptResultList();
        final User user = SecurityService.getUserFromSession();

        LOG.debug("getMembers: refsetInternalId: " + refsetInternalId + " ; searchParameters: + " + searchParameters + " ; taxonomyParameters: "
            + taxonomyParameters + " ; displayType: " + displayType + " ; countComments: " + countComments);

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetMemberService.getRefset(user, service, refsetInternalId);

            results = RefsetMemberService.getRefsetMembers(service, user, refsetInternalId, searchParameters, displayType, taxonomyParameters);

            if (countComments != null && countComments) {

                LOG.debug("getMembers: Including discussion count");
                DiscussionService.attachMemberDiscussionCounts(service, user, refset, results.getItems());
            }

            results.setTimeTaken(System.currentTimeMillis() - start);
            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Cache all ancestors for all members of a refset.
     *
     * @param refsetId the refset ID
     * @param versionDate the version date or IN DEVELOPMENT
     * @return the success/failure
     * @throws Exception the exception
     */
    @ApiOperation(value = "Cache the ancestors of the refset members for the specified refset ID. To see certain results this call requires "
        + "authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully populated the refset's ancestor cache. Payload contains the status"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetId", value = "The ID of the refset for which ancestors are to be identified.", required = true,
            dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "versionDate", value = "The date of the refset version (YYYY-MM-DD) or IN DEVELOPMENT.", required = true,
            dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/ancestors/{refsetId}/versionDate/{versionDate}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> cacheMemberAncestors(@PathVariable(value = "refsetId") final String refsetId,
        @PathVariable(value = "versionDate") final String versionDate) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // no auth required
            final User user = SecurityService.getUserFromSession();
            LOG.debug("cacheMemberAncestors: refsetId: " + refsetId + " ; versionDate: " + versionDate);

            String returnJson = "{\"success\": \"<RESULT>\"}";

            final boolean success = RefsetMemberService.cacheMemberAncestors(service, user, refsetId, versionDate);

            if (success) {

                returnJson = returnJson.replace("<RESULT>", "true");
            } else {

                returnJson = returnJson.replace("<RESULT>", "false");
            }

            // LOG.debug("cacheMemberAncestors results: " + returnJson);

            return new ResponseEntity<>(returnJson, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Export refset.
     *
     * @param refsetInternalId the internal refset id
     * @param format the format
     * @param exportType the export type
     * @param languageId the language to display names in
     * @param fileNameDate the file name date
     * @param startEffectiveTime the start effective time
     * @param transientEffectiveTime the transient effective time
     * @param exportMetadata the export metadata
     * @return the uri
     * @throws Exception the exception
     */
    @ApiOperation(value = "Export the refset for the specified ID. Payload contains the URL to download the export file. "
        + "To see certain results this call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. Payload contains the URL to download the export file"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "exportType", value = "The RF2 type SNAPSHOT or DELTA", required = false, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "languageId", value = "For formats with names which language to display the name in.", required = false,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "format", value = "The type of export: 'rf2', 'rf2_with_names', 'sctids' or 'freeset'.", required = true,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "fileNameDate", value = "Format: yyyymmdd. Date to be embedded in the RF2 file names.", required = true,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "startEffectiveTime",
            value = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.",
            required = false, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "transientEffectiveTime",
            value = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.", required = false,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "exportMetadata", value = "e.g.  true or false", required = true, dataType = "boolean", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/{refsetInternalId}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> exportRefset(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final String format,
        final String exportType, final String languageId, final String fileNameDate, final String startEffectiveTime, final String transientEffectiveTime,
        final boolean exportMetadata) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // no auth required
            final User user = SecurityService.getUserFromSession();
            LOG.debug("exportRefset: refsetInternalId: " + refsetInternalId + " ; format: " + format + " ; type: " + exportType + " ; fileNameDate: "
                + fileNameDate + " ; startEffectiveTime: " + startEffectiveTime + " ; transientEffectiveTime: " + transientEffectiveTime + " ; exportMetadata: "
                + exportMetadata);

            String responseMessage = null;

            if ("rf2".equalsIgnoreCase(format) || "rf2_with_names".equalsIgnoreCase(format)) {

                final boolean withNames = ("rf2_with_names".equalsIgnoreCase(format));

                String downloadUri = "";

                if (exportType.contentEquals("SNAPSHOT")) {

                    downloadUri = RefsetMemberService.exportRefsetRf2(service, refsetInternalId, exportType, languageId, fileNameDate, startEffectiveTime,
                        transientEffectiveTime, exportMetadata, withNames);

                } else {

                    downloadUri = RefsetMemberService.exportRefsetRf2Delta(service, user, refsetInternalId, exportType, languageId, fileNameDate,
                        startEffectiveTime, transientEffectiveTime, exportMetadata, withNames);
                }

                LOG.debug("results: " + downloadUri);
                responseMessage = "{\"url\": \"" + downloadUri + "\"}";

            } else if (format.equals("sctids")) {

                final String downloadUri = RefsetMemberService.exportRefsetSctidList(service, refsetInternalId, exportMetadata);
                responseMessage = "{\"url\": \"" + downloadUri + "\"}";

            } else if ("freeset".equals(format)) {

                final String downloadUri = RefsetMemberService.exportFreeset(service, refsetInternalId, languageId);
                responseMessage = "{\"url\": \"" + downloadUri + "\"}";
            }

            return new ResponseEntity<>(responseMessage, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Export all published refsets for a project.
     *
     * @param projectId the project id
     * @param format the format
     * @param languageId the language to display names in
     * @param fileNameDate the file name date
     * @param exportMetadata the export metadata
     * @return the uri
     * @throws Exception the exception
     */
    @ApiOperation(value = "Export the all latest version of all published refsets for the project. Payload contains the URL to download the export file. "
        + "To see certain results this call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. Payload contains the URL to download the export file"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "projectId", value = "The id of the project to export refsets.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "languageId", value = "For formats with names which language to display the name in.", required = false,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "fileNameDate", value = "Format: yyyymmdd. Date to be embedded in the RF2 file names.", required = true,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "format", value = "The type of export: 'rf2', 'rf2_with_names'", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "exportMetadata", value = "e.g.  true or false", required = true, dataType = "boolean", paramType = "query")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/project/{projectId}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> exportAllRefsetsForProject(@PathVariable(value = "projectId") final String projectId, final String format,
        final String languageId, final String fileNameDate, final boolean exportMetadata) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // no auth required
            final User user = SecurityService.getUserFromSession();
            LOG.debug("exportAllRefsetsForProject: projectId: " + projectId + " ; fileNameDate: " + fileNameDate);
            final boolean withNames = ("rf2_with_names".equalsIgnoreCase(format));

            final String downloadUri =
                RefsetMemberService.exportAllRefsetsRf2ForProject(service, user, projectId, "snapshot", languageId, fileNameDate, exportMetadata, withNames);
            final String responseMessage = "{\"url\": \"" + downloadUri + "\"}";

            return new ResponseEntity<>(responseMessage, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Download an exported refset.
     *
     * @param fileName the file name
     * @return the file
     * @throws Exception the exception
     */
    @ApiOperation(value = "Download the specified refset export file", response = Resource.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "fileName", value = "The name of the file to download.", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/download/{fileName}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Resource> downloadExport(@PathVariable(value = "fileName") final String fileName) throws Exception {

        try {

            LOG.debug("downloadExport: fileName: " + fileName);

            // no auth required
            SecurityService.getUserFromSession();
            final Path filePath = Paths.get(exportFileDir + fileName);
            final Resource file = new UrlResource(filePath.toUri());

            if (!file.exists() || !file.isReadable()) {

                throw new RuntimeException("Could not read the file!");
            }

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets member history.
     *
     * @param refsetInternalId the refset internal id
     * @param conceptId the member id
     * @return the member history
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the member history for the specified ID. To see certain results this call requires authentication with the correct role.",
        response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "conceptId", value = "The ID of the member to return.", required = true, dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Map<String, String>>> getMemberHistory(
        @PathVariable(value = "refsetInternalId") final String refsetInternalId, @PathVariable(value = "conceptId") final String conceptId) throws Exception {

        try {

            // no auth required
            final User user = SecurityService.getUserFromSession();
            LOG.debug("getMemberHistory: memberId: " + conceptId + "; refsetInternalId: " + refsetInternalId);

            try (final TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {

                    throw new Exception("Unable to retrieve reference set " + refsetInternalId);
                }

                RefsetService.setRefsetPermissions(user, refset);
                final List<Map<String, String>> versions = RefsetService.getSortedRefsetVersionList(refset, service, true);

                final List<Map<String, String>> memberHistory = RefsetMemberService.getMemberHistory(service, conceptId, versions);

                LOG.debug("getMemberHistory: member: " + ModelUtility.toJson(memberHistory));

                final ResultList<Map<String, String>> results = new ResultList<>(memberHistory);
                results.setTotalKnown(true);

                return new ResponseEntity<>(results, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the concept details.
     *
     * @param conceptId the concept id
     * @param refsetInternalId the refset internal id
     * @return the concept details
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the concept for the specified ID", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "conceptId", value = "The ID of the concept to return.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataTypeClass = String.class,
            paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{conceptId}", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Concept> getConceptDetails(@PathVariable(value = "conceptId") final String conceptId, final String refsetInternalId)
        throws Exception {

        try {

            // no auth required
            SecurityService.getUserFromSession();
            // LOG.debug("getConceptDetails: conceptId: " + conceptId + ";
            // refsetInternalId: " + refsetInternalId);

            try (final TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {

                    throw new Exception("Unable to retrieve reference set " + refsetInternalId);
                }

                final Concept concept = RefsetMemberService.getConceptDetails(conceptId, refset);

                // LOG.debug("getConceptDetails: concept: " + ModelUtility.toJson(concept));

                return new ResponseEntity<>(concept, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Syncs RTT data into the database but only if the database is empty. This call requires authentication with the correct role.
     *
     * @param perVersionCreation If true, create a refset for every version created. If false, only when changes are observed.
     * @param forProduction the for production
     * @param ignoreCoreRefsets the ignore core refsets
     * @return the status of the sync
     * @throws Exception the exception
     */
    @ApiIgnore
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/rtt", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> syncRttData(@RequestParam(required = false) final Boolean perVersionCreation,
        @RequestParam(required = false) final Boolean forProduction, @RequestParam(required = false) final Boolean ignoreCoreRefsets) throws Exception {

        return syncSnowstorm(perVersionCreation, forProduction, ignoreCoreRefsets);
    }

    /**
     * Sync against snowstorm still relying upon latest RTT data files to sync. Compares against all of a given refets's versions on snowstorm, so no need for a
     * quickSync option. This call requires authentication with the correct role.
     * 
     * TODO: Determine if can do a nightly update of data files programmatically
     *
     * @param perVersionCreation If true, create a refset for every version created. If false, only when changes are observed.
     * @param forProduction Should the sync add projects, teams, and other testing data, which it should NOT do for Production. Default is true
     * @param ignoreCoreRefsets the ignore core refsets
     * @return the status of the sync
     * @throws Exception the exception
     */
    @ApiIgnore
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/snowstorm", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> syncSnowstorm(@RequestParam(required = false) final Boolean perVersionCreation,
        @RequestParam(required = false) final Boolean forProduction, @RequestParam(required = false) final Boolean ignoreCoreRefsets) throws Exception {

        final User user = SecurityService.getUserFromSession();

        if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
            return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
        }

        final String message = "";

        try {

            boolean refsetPerVersionSync = false;
            boolean runForProduction = false;
            boolean isIgnoreCoreRefsets = false;

            if (perVersionCreation != null && perVersionCreation.booleanValue()) {

                LOG.info("!!!!! syncSnowstorm RUNNING QUICK SYNC - WILL HAVE MORE THAN ONLY PUBLISHED REFSET VERSIONS");
                refsetPerVersionSync = true;
            }

            if (forProduction != null && forProduction.booleanValue()) {

                LOG.info("!!!!! syncSnowstorm RUNNING SYNC ON PRODUCTION - SHOULDN'T CONTAIN TESTING PROJECTS, TEAMS, AND REFSETS");
                runForProduction = true;
            }

            if (ignoreCoreRefsets != null && ignoreCoreRefsets.booleanValue()) {

                LOG.info("!!!!! syncSnowstorm IGNORING SNOMED INTERNATIONAL Refsets");
                isIgnoreCoreRefsets = true;
            }

            try (final TerminologyService service = new TerminologyService()) {

                SyncAgent.sync(service, refsetPerVersionSync, runForProduction, isIgnoreCoreRefsets);

                return new ResponseEntity<>(message + "RT2 synced with Snowstorm successfully", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Creates a new testing refset containing initial feedback. The method will identify the last refset created for this purpose (based on numbering). It will
     * create a new one, with the same initial feedback content, but with an incremented number appended to the name and refsetId. This call requires
     * authentication with the correct role.
     *
     * @return the status of the creation
     * @throws Exception the exception
     */
    @ApiIgnore
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/feedback", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> createNewFeedbackTestingRefset() throws Exception {

        try {

            final User user = SecurityService.getUserFromSession();

            if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
                return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
            }

            final String status = "Feedback testing refset created successfully";
            LOG.info("Create new refset, initialized with feedback, for testing purposes");
            final SyncTestingInitializer initializer = new SyncTestingInitializer();
            final Refset refset = initializer.createTestingFeedbackRefset();

            LOG.info("New Feedback testing refset created succesffully with internal/SctiId pair: " + refset.getId() + "/" + refset.getRefsetId());

            return new ResponseEntity<>(status, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Creates a new testing refset for testing intensional functionality. The method will identify the last refset created for this purpose (based on
     * numbering). It will create a new one, similarly as intensionsal, but with an incremented number appended to the name and refsetId. This call requires
     * authentication with the correct role.
     *
     * @return the status of the creation
     * @throws Exception the exception
     */
    @ApiIgnore
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/intensional", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> createNewIntensionalTestingRefset() throws Exception {

        try {

            final User user = SecurityService.getUserFromSession();

            if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
                return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
            }

            final String status = "Intensional testing refset created successfully";
            LOG.info("Create new intensional refset for testing purposes");
            final SyncTestingInitializer initializer = new SyncTestingInitializer();
            final Refset refset = initializer.createTestingIntensionalRefset();

            LOG.info("New Feedback testing refset created succesffully with internal/SctiId pair: " + refset.getId() + "/" + refset.getRefsetId());

            return new ResponseEntity<>(status, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the version statuses.
     *
     * @return the version statuses
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets a list of possible version statuses", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versionStatuses", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getVersionStatuses() throws Exception {

        try {

            // no auth required
            SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final List<TypeKeyValue> versionStatuses = new ArrayList<>();

                for (final VersionStatus value : VersionStatus.values()) {

                    final TypeKeyValue typeKeyValue = new TypeKeyValue("status", value.getLable(), value.getLable());
                    versionStatuses.add(typeKeyValue);
                }

                final ResultList<TypeKeyValue> results = new ResultList<>(versionStatuses);
                results.setTotalKnown(true);

                return new ResponseEntity<>(results, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the versions.
     *
     * @return the versions
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets a list of all current refset versions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versions", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getVersions() throws Exception {

        try {

            // no auth required
            SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                ResultList<Refset> refsets = new ResultList<Refset>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("_exists_:versionDate AND latestPublishedVersion: true");

                refsets = service.find(query, pfs, Refset.class, null);

                final ResultList<TypeKeyValue> results = new ResultList<>();
                final List<TypeKeyValue> resultItems = new ArrayList<>();
                final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

                for (final Refset refset : refsets.getItems()) {

                    final String version = sdf.format(refset.getVersionDate());
                    final TypeKeyValue entry = new TypeKeyValue("version", version, version);

                    if (!resultItems.contains(entry)) {

                        resultItems.add(entry);
                    }

                }

                resultItems.sort(new Comparator<TypeKeyValue>() {

                    @Override
                    public int compare(final TypeKeyValue o1, final TypeKeyValue o2) {

                        return o2.getValue().compareTo(o1.getValue());
                    }

                });
                results.setItems(resultItems);
                results.setTotal(resultItems.size());

                return new ResponseEntity<>(results, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the editions.
     *
     * @param onlyEditionsWithoutOrganizations should the results be limited to editions that do not have an organization tied to them
     * @return the editions
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets a list of all the editions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "onlyEditionsWithoutOrganizations",
            value = "Should the results be limited to editions that do not have an organization tied to them.", required = false, dataTypeClass = String.class,
            paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/editions", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getEditions(
        @QueryParam(value = "onlyUsersTeams") final boolean onlyEditionsWithoutOrganizations) throws Exception {

        try {

            // no auth required
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Edition> results = new ResultList<Edition>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                List<Organization> organizationList = new ArrayList<>();

                if (onlyEditionsWithoutOrganizations) {

                    organizationList = OrganizationService.searchOrganizations(service, user, new SearchParameters(), false).getItems();
                }

                results = service.find(query, pfs, Edition.class, null);

                results.setTimeTaken(System.currentTimeMillis() - start);
                results.setTotalKnown(true);

                LOG.debug("getEditions results: " + ModelUtility.toJson(results));
                final List<Edition> editionList = results.getItems();
                editionList.sort(new Comparator<Edition>() {

                    @Override
                    public int compare(final Edition o1, final Edition o2) {

                        return o1.getName().compareTo(o2.getName());
                    }
                });
                final List<TypeKeyValue> entryList = new ArrayList<>();
                final ResultList<TypeKeyValue> entryResults = new ResultList<>();

                for (final Edition edition : editionList) {

                    // if this flag is set skip any edition already tied to an organization
                    if (onlyEditionsWithoutOrganizations) {

                        boolean hasOrganization = false;

                        for (final Organization organization : organizationList) {

                            if (edition.getOrganization().getId().equals(organization.getId())) {

                                hasOrganization = true;
                            }

                        }

                        if (hasOrganization) {

                            continue;
                        }

                    }

                    final TypeKeyValue tkv = new TypeKeyValue("edition", edition.getName(), edition.getName());
                    tkv.setId(edition.getId());
                    entryList.add(tkv);
                }

                entryResults.setItems(entryList);
                entryResults.setTotalKnown(true);

                return new ResponseEntity<>(entryResults, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the list of Refset Concepts that can be used as parents to a refset or as the underlying concept for a new refset.
     *
     * @param branch the branch to retrieve the concepts from
     * @param areParentConcepts Do these concepts represent parent concepts for a new refset, or will they be the underlying concepts for a the refset itself
     * @return the editions
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets the list of Refset Concepts that can be used as parents to a refset or as the underlying concept for a new refset.",
        response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "branch", value = "The branch to retrieve the concepts from.", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "areParentConcepts",
            value = "Do these concepts represent parent concepts for a new refset, or will they be the underlying concepts for a the refset itself.",
            required = true, dataTypeClass = String.class, paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/general/refsetConcepts", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ConceptResultList> getRefsetConcepts(final String branch, final boolean areParentConcepts) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // LOG.debug("getRefsetConcepts: branch: " + branch + "; areParentConcepts: "
            // + areParentConcepts);

            // no auth required
            SecurityService.getUserFromSession();
            final ConceptResultList results = RefsetService.getRefsetConcepts(service, branch, areParentConcepts);

            // LOG.debug("getRefsetConcepts: results: " + ModelUtility.toJson(results));

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the list of branch versions.
     *
     * @param branch the branch to get versions from
     * @return list of edition versions
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets the list of branch versions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "branch", value = "The branch to retrieve the concepts from.", required = true, dataTypeClass = String.class,
            paramType = "query")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/general/branchVersions", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<String>> getBranchVersions(final String branch) throws Exception {

        try {

            // LOG.debug("getBranchVersions - branch: " + branch);

            // no auth required
            SecurityService.getUserFromSession();

            final ResultList<String> results = new ResultList<>();
            results.setItems(RefsetService.getBranchVersions(branch));

            // LOG.debug("getBranchVersions - results: " + results);

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets the Organizations.
     *
     * @return the organizations
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets a list of the organizations", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/organizations", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getOrganizations() throws Exception {

        try {

            // no auth required
            SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Organization> results = new ResultList<Organization>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("active:true");

                results = service.find(query, pfs, Organization.class, null);

                results.setTimeTaken(System.currentTimeMillis() - start);
                results.setTotalKnown(true);

                // LOG.debug("results: " + ModelUtility.toJson(results));
                final List<Organization> organizationList = results.getItems();
                organizationList.sort(new Comparator<Organization>() {

                    @Override
                    public int compare(final Organization o1, final Organization o2) {

                        return o1.getName().compareTo(o2.getName());
                    }
                });
                final List<TypeKeyValue> entryList = new ArrayList<>();
                final ResultList<TypeKeyValue> entryResults = new ResultList<>();

                for (final Organization organization : organizationList) {

                    final TypeKeyValue tkv = new TypeKeyValue("organization", organization.getName(), organization.getName());
                    tkv.setId(organization.getId());
                    entryList.add(tkv);
                }

                entryResults.setItems(entryList);
                entryResults.setTotalKnown(true);

                return new ResponseEntity<>(entryResults, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Returns the contents of the ancestor cache for a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the ancestor cache
     * @throws Exception the exception
     */
    @ApiOperation(
        value = "Returns the contents of the ancestor cache for a refset. To see certain results this call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200,
            message = "Successfully retrieved the requested information. Payload contains a set of concept IDs that are ancestors "
                + "to the members of this refset."),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/ancestorCache", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> getRefsetAncestorCache(@PathVariable(value = "refsetInternalId") final String refsetInternalId)
        throws Exception {

        // no auth required
        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("getRefsetAncestorCache: refsetInternalId: " + refsetInternalId);

            final Refset refset = RefsetMemberService.getRefset(SecurityService.getUserFromSession(), service, refsetInternalId);
            final Map<String, Set<String>> ancestorsCache = RefsetMemberService.getCacheForMemberAncestors(RefsetMemberService.getBranchPath(refset));

            if (ancestorsCache.containsKey(refsetInternalId)) {

                return new ResponseEntity<>(ModelUtility.toJson(ancestorsCache.get(refsetInternalId)), HttpStatus.OK);
            } else {

                return new ResponseEntity<>("Not Cached", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Returns the ancestor path concepts for a refset member.
     *
     * @param refsetInternalId the internal refset ID
     * @param conceptId the ID of the member concept
     * @return the concept with the ancestor path filled in
     * @throws Exception the exception
     */
    @ApiOperation(
        value = "Returns the ancestor path concepts for a refset member. To see certain results this call requires authentication with the correct role.",
        response = Concept.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "conceptId", value = "The ID of the member concept.", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}/ancestorConcepts", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Concept> getMemberAncestorConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @PathVariable(value = "conceptId") final String conceptId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("getMemberAncestorConcepts: refsetInternalId: " + refsetInternalId + " ; conceptId: " + conceptId);

            // no auth required
            final User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

            final Concept concept = RefsetMemberService.getConceptAncestors(refset, conceptId);

            return new ResponseEntity<>(concept, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Compile and store the data to upgrade a list of refsets.
     *
     * @param refsetInternalIds a list of comma separated internal refset IDs to upgrade
     * @return The operation status
     * @throws Exception the exception
     */
    @ApiOperation(value = "Compile and store the data to upgrade a list of refsets. Long running background process, call /refset/{refsetInternalId}/isLocked "
        + "to get full status. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. Payload contains the status of the operation"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "A list of comma separated internal refset IDs to upgrade.", required = true,
            dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalIds}/compileUpgradeData", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> compileUpgradeData(@PathVariable(value = "refsetInternalIds") final String refsetInternalIds) throws Exception {

        final User user = SecurityService.getUserFromSession();

        final Thread t = new Thread(new Runnable() {

            @Override
            public void run() {

                try (final TerminologyService service = new TerminologyService()) {

                    service.setModifiedBy(user.getUserName());
                    service.setModifiedFlag(true);

                    String status = "";

                    final String[] refsetInternalIdArray = refsetInternalIds.split(",");
                    boolean isBatch = false;

                    if (refsetInternalIdArray.length > 1) {

                        isBatch = true;
                        RefsetMemberService.REFSETS_BEING_UPDATED.add(refsetInternalIds);
                        LOG.debug("compileUpgradeData: Batch upgrade started with refsetInternalIds: " + refsetInternalIds);
                    }

                    for (final String internalId : refsetInternalIdArray) {

                        RefsetMemberService.REFSETS_BEING_UPDATED.add(internalId);
                        LOG.debug("compileUpgradeData: individual refsetInternalId: " + internalId);

                        try {

                            // add the list of concepts as members to the refset
                            status = RefsetMemberService.compileUpgradeData(service, user, internalId);

                        } finally {
                            RefsetMemberService.REFSETS_BEING_UPDATED.remove(internalId);
                        }

                        LOG.debug("compileUpgradeData: individual refsetInternalId " + internalId + " finished with status " + status);
                    }

                    if (isBatch) {
                        LOG.debug("compileUpgradeData: Batch upgrade finished");
                    }

                } catch (final Exception e) {
                    try {
                        handleException(e);
                    } catch (final Exception e1) {
                        // n/a - in thread
                    }
                }

                finally {

                    RefsetMemberService.REFSETS_BEING_UPDATED.remove(refsetInternalIds);
                }

            }
        });
        t.start();
        return new ResponseEntity<>("{\"status\": \"started\"}", HttpStatus.OK);

    }

    /**
     * Get the stored the data to upgrade a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return The upgrade data
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the stored the data to upgrade a refset. This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/upgradeData", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<UpgradeInactiveConcept>> getUpgradeData(
        @PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("getUpgradeData: refsetInternalId: " + refsetInternalId);

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

            // add the list of concepts as members to the refset
            final ResultList<UpgradeInactiveConcept> results = RefsetMemberService.getUpgradeData(service, user, refset);

            LOG.debug("getUpgradeData: results " + results);

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Make a change to an upgrade concept.
     *
     * @param refsetInternalId the internal refset ID
     * @param inactiveConceptId the concept ID of the inactive concept to be upgraded
     * @param replacementConceptId the concept ID of the replacement concept to be updated
     * @param changed a string identifying what has been changed
     * @param manualReplacementConcept the manual upgrade replacement concept that to be added
     * @return the status
     * @throws Exception the exception
     */
    @ApiOperation(value = "Make a change to an upgrade concept. This call requires authentication with the correct role.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "inactiveConceptId", value = "The concept ID of the inactive concept to be upgraded.", required = true,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "replacementConceptId", value = "The concept ID of the replacement concept to be updated.", required = false,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "changed", value = "A string identifying what has been changed.", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "manualReplacementConcept", value = "The manual upgrade replacement concept to be added.", required = false,
            dataTypeClass = UpgradeReplacementConcept.class, paramType = "body"),
    })
    @RecordMetric
    @PostMapping("/refset/{refsetInternalId}/modifyUpgradeConcept")
    public @ResponseBody ResponseEntity<String> modifyUpgradeConcept(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = true) final String inactiveConceptId, @RequestParam(required = false) final String replacementConceptId,
        @RequestParam(required = true) final String changed, @RequestBody(required = false) final UpgradeReplacementConcept manualReplacementConcept)
        throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully";

            LOG.debug("modifyUpgradeConcept: refsetInternalId: " + refsetInternalId + "; changed: " + changed + "; inactiveConceptId: " + inactiveConceptId
                + "; replacementConceptId: " + replacementConceptId + "; manualReplacementConcept: " + manualReplacementConcept);

            status =
                RefsetMemberService.modifyUpgradeConcept(service, user, refset, inactiveConceptId, replacementConceptId, manualReplacementConcept, changed);

            LOG.debug("modifyUpgradeConcept: Finished with status: " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Remove all inactive Upgrade concepts at once.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status
     * @throws Exception the exception
     */
    @ApiOperation(value = "Remove all inactive Upgrade concepts at once. Long running background process, call /refset/{refsetInternalId}/isLocked "
        + "to get full status. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. The payload contains the status of the operation"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @PostMapping("/refset/{refsetInternalId}/removeAllUpgradeInactiveConcepts")
    public @ResponseBody ResponseEntity<String> removeAllUpgradeInactiveConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId)
        throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully";

            LOG.debug("removeAllUpgradeInactiveConcepts: refsetInternalId: " + refsetInternalId);

            status = RefsetMemberService.removeAllUpgradeInactiveConcepts(service, user, refset);

            LOG.debug("removeAllUpgradeInactiveConcepts: Finished with status: " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Add all replacement Upgrade concepts as members at once.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add all replacement Upgrade concepts as members at once. Long running background process, call /refset/{refsetInternalId}/isLocked "
        + "to get full status. This call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. The payload contains the status of the operation"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @PostMapping("/refset/{refsetInternalId}/addAllUpgradeReplacementConcepts")
    public @ResponseBody ResponseEntity<String> addAllUpgradeReplacementConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId)
        throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully";

            LOG.debug("addAllUpgradeReplacementConcepts: refsetInternalId: " + refsetInternalId);

            status = RefsetMemberService.addAllUpgradeReplacementConcepts(service, user, refset);

            LOG.debug("addAllUpgradeReplacementConcepts: Finished with status: " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Search for members replacement concepts for upgrade.
     *
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @param request the request
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search for members replacement concepts for upgrade. This call requires authentication with the correct role.",
        response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {
        "/refset/{refsetInternalId}/replacementConceptSearch"
    }, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<UpgradeReplacementConcept>> replacementConceptSearch(
        @PathVariable(value = "refsetInternalId") final String refsetInternalId, final SearchParameters searchParameters, final BindingResult bindingResult,
        final HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetMemberService.getRefset(user, service, refsetInternalId);
            ResultList<UpgradeReplacementConcept> results = new ResultList<>();
            final String query = searchParameters.getQuery();

            LOG.debug("replacementConceptSearch: refsetInternalId: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters));

            if (query != null && !query.equals("")) {

                results = RefsetMemberService.replacementConceptSearch(user, service, refset, searchParameters);
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Search for refsets for dropdown menus.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search for refsets for dropdown menus. To see certain results this call requires authentication with the correct role.",
        response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    // @ModelAttribute API params documented in SearchParameter
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataTypeClass = String.class, paramType = "query")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {
        "/refset/dropdownSearch"
    }, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<ResultList<Refset>> searchRefsetsForDropdowns(final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        // no auth required
        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            ResultList<Refset> results = new ResultList<>();
            final String query = searchParameters.getQuery();

            LOG.debug("refsetDropdownSearch: searchParameters: " + ModelUtility.toJson(searchParameters));

            if (query != null && !query.equals("")) {

                results = RefsetService.refsetDropdownSearch(user, service, searchParameters, true, true);
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Compile the data to compare two refsets.
     *
     * @param activeRefsetInternalId the internal refset ID of the active refset
     * @param comparisonRefsetInternalId the internal refset ID of the comparison refset
     * @return The operation status
     * @throws Exception the exception
     */
    @ApiOperation(
        value = "Compile the data to compare two refsets. Long running background process, call /refset/{refsetInternalId}/isLocked to get full status. "
            + "To see certain results this call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information. Payload contains the status of the operation"),
        @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 401, message = "Unauthorized"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "activeRefsetInternalId", value = "The internal ID of the active refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "comparisonRefsetInternalId", value = "The internal ID of the comparison refset.", required = true,
            dataTypeClass = String.class, paramType = "query")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{activeRefsetInternalId}/compileComparisonData", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> compileComparisonData(@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId,
        @RequestParam(required = true) final String comparisonRefsetInternalId) throws Exception {

        // no auth required
        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            String status = "";
            RefsetMemberService.REFSETS_BEING_UPDATED.add(activeRefsetInternalId);

            LOG.debug(
                "compileComparisonData: activeRefsetInternalId: " + activeRefsetInternalId + "; comparisonRefsetInternalId: " + comparisonRefsetInternalId);

            // add the list of concepts as members to the refset
            status = RefsetMemberService.compileComparisonData(service, user, activeRefsetInternalId, comparisonRefsetInternalId);

            LOG.debug("compileComparisonData: Finished with status " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(activeRefsetInternalId);
        }

    }

    /**
     * Get the data to compare two refsets.
     *
     * @param activeRefsetInternalId the internal ID of the active refset
     * @param request the request
     * @return The comparison data
     * @throws Exception the exception
     */
    @ApiOperation(value = "Compile the data to compare two refsets. To see certain results this call requires authentication with the correct role.",
        response = RefsetMemberComparison.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 401, message = "Unauthorized"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "activeRefsetInternalId", value = "The internal ID of the active refset.", required = true, dataTypeClass = String.class,
            paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{activeRefsetInternalId}/comparisonData", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<RefsetMemberComparison> getComparisonData(
        @PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId, final HttpServletRequest request) throws Exception {

        // no auth required
        SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("getComparisonData: activeRefsetInternalId: " + activeRefsetInternalId);

            // add the list of concepts as members to the refset
            final String uuid = (String) request.getSession().getAttribute("refsetMemberComparison_" + activeRefsetInternalId);
            final RefsetMemberComparison results = (RefsetMemberComparison) SecurityService.getFromInMemoryStorage(uuid);
            SecurityService.removeFromInMemoryStorage(uuid);
            request.getSession().removeAttribute("refsetMemberComparison_" + activeRefsetInternalId);

            if (results == null) {

                throw new Exception("There were no comparison results to retrieve for this reference set.");
            }

            LOG.debug("getComparisonData: results " + results);

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Request access to the refset for the specified ID.
     *
     * @param refsetInternalId the internal refset id
     * @param comments Any comments related to the request
     * @return was the operation successful
     * @throws Exception the exception
     */
    @ApiOperation(value = "Request access to the refset for the specified ID", response = Boolean.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to request access to.", required = true,
            dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "comments", value = "Any comments related to the request.", required = true, dataTypeClass = String.class,
            paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/requestAccess", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<Boolean> requestRefsetAccess(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final String comments) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // no auth required
            SecurityService.getUserFromSession();
            LOG.debug("requestRefsetAccess: refsetInternalId: " + refsetInternalId + " ; comments: " + comments);

            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Share refset.
     *
     * @param refsetInternalId the refset internal id
     * @param emailInfo the email info
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Share a refset via email. To see certain results this call requires authentication with the correct role.", response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully shared the requested refset. The payload contains the status of the operation"),
        @ApiResponse(code = 400, message = "Invalid email address recipient entered"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "emailInfo", value = "The information about the email address to send to.", required = true, dataTypeClass = String.class,
            paramType = "body")
    })
    @RecordMetric
    @PostMapping(value = "/refset/{refsetInternalId}/share", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> shareRefset(@PathVariable final String refsetInternalId,
        @RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

        try {

            // no auth required
            final User user = SecurityService.getUserFromSession();

            LOG.debug("shareRefset: refsetId: " + refsetInternalId + " and emailInfo.recipient: " + emailInfo.getRecipient()
                + " and emailInfo.additionalMessage: " + emailInfo.getAdditionalMessage());

            RefsetService.shareRefset(user, refsetInternalId, emailInfo.getRecipient(), emailInfo.getAdditionalMessage());

            final String returnMessage = "{\"message\": \"Share Reference Set was Successful\"}";

            return new ResponseEntity<>(returnMessage, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Request project access.
     *
     * @param refsetInternalId the refset internal id
     * @param emailInfo the email info
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Request project access from administrators. To see certain results this call requires authentication with the correct role.",
        response = String.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully requested access to the refset's ecnlosing project"),
        @ApiResponse(code = 400, message = "Invalid email address recipient entered"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "emailInfo", value = "The information about the email address to send to.", required = true, dataTypeClass = String.class,
            paramType = "body")
    })
    @RecordMetric
    @PostMapping(value = "/refset/{refsetInternalId}/request", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> requestProjectAccess(@PathVariable final String refsetInternalId,
        @RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

        try {

            // no auth required
            final User user = SecurityService.getUserFromSession();

            LOG.debug("requestProjectAccess: refsetInternalId: " + refsetInternalId + " and emailInfo.recipient: " + emailInfo.getRecipient()
                + " and emailInfo.additionalMessage: " + emailInfo.getAdditionalMessage());

            RefsetService.requestProjectAccess(user, refsetInternalId, emailInfo.getRecipient(), emailInfo.getAdditionalMessage());

            final String returnMessage = "{\"message\": \"Reference set access (via project access) was requested was Successful\"}";

            return new ResponseEntity<>(returnMessage, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Copy refset.
     *
     * @param refsetInternalId the refset internal id
     * @param name the name
     * @param projectId the project id
     * @param localSet the local set
     * @param privateRefset the private refset
     * @param comboSet the combo set
     * @param narrative the narrative
     * @param tags the tags
     * @param parentConceptId the parent concept id
     * @param newRefsetConceptId the new refset concept id
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Create a new refset that is a copy of an existing one. This call requires authentication with the correct role.",
        response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully copied refset specified"), @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to copy.", required = true, dataTypeClass = String.class,
            paramType = "path"),
        @ApiImplicitParam(name = "name", value = "The information about the email address to send to.", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "projectId", value = "The project ID for the new refset.", required = true, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "localSet", value = "Will the new refset be a local set.", required = true, dataTypeClass = Boolean.class,
            paramType = "query"),
        @ApiImplicitParam(name = "privateRefset", value = "Will the new refset be a local set.", required = true, dataTypeClass = Boolean.class,
            paramType = "query"),
        @ApiImplicitParam(name = "comboSet", value = "Will the new refset be a local set.", required = true, dataTypeClass = Boolean.class,
            paramType = "query"),
        @ApiImplicitParam(name = "narrative", value = "The new refset's narrative.", required = true, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "tags", value = "The new refset's tags.", required = true, dataTypeClass = Set.class, paramType = "query"),
        @ApiImplicitParam(name = "parentConceptId", value = "The new refset's parent concept ID.", required = true, dataTypeClass = String.class,
            paramType = "query"),
        @ApiImplicitParam(name = "newRefsetConceptId", value = "The new refset's concept ID", required = true, dataTypeClass = String.class,
            paramType = "query")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/copy", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> copyRefset(@PathVariable(required = true) final String refsetInternalId, @RequestParam final String name,
        @RequestParam final String projectId, @RequestParam final Boolean localSet, @RequestParam final Boolean privateRefset,
        @RequestParam final Boolean comboSet, @RequestParam final String narrative, @RequestParam final Set<String> tags,
        @RequestParam final String parentConceptId, @RequestParam final String newRefsetConceptId) throws Exception {

        // TODO: Add support for providing a zip RF2 or a refset file to clone off of.
        // Questions to be answered first: Always use a) latest version for refsetId
        // provided or b) Version if provided RF2 file instead and c) Can't supply both
        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("Copy Refset: refsetId: " + refsetInternalId + " projectId: " + projectId + " with new name: " + name);

            final User user = SecurityService.getUserFromSession();
            service.setModifiedBy(user.getUserName());

            String status = "";
            final Object returned = RefsetService.copyRefset(service, user, refsetInternalId, name, projectId, localSet, privateRefset, comboSet, narrative,
                tags, parentConceptId, newRefsetConceptId);

            if (returned instanceof String) {

                status = (String) returned;
            } else {

                final Refset refset = (Refset) returned;
                status = refset.getRefsetId();
            }

            if (status.startsWith("Error")) {

                return new ResponseEntity<>("{\"error\": \"" + status + "\"}", HttpStatus.OK);
            }

            return new ResponseEntity<>("{\"refsetId\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Reset refset.
     *
     * @param refsetId the refset id
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiIgnore
    @ApiOperation(value = "Reset a refset to contain the contents of Snowstorm. Note only works if refset has not been upgraded during edit cycle. "
        + "This call requires authentication with the correct role.", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully reset the refset"), @ApiResponse(code = 403, message = "May not reset refset on a production system"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/reset", produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> resetRefset(@PathVariable final String refsetId) throws Exception {

        final User user = SecurityService.getUserFromSession();

        if (!user.checkPermission(User.ROLE_ADMIN, null, null)) {
            return new ResponseEntity<>("This user does not have permission to perform this action", HttpStatus.UNAUTHORIZED);
        }

        try (final TerminologyService service = new TerminologyService()) {

            LOG.debug("resetRefset: refsetId: " + refsetId);

            if (!SyncAgent.getIsProductionSystem()) {

                service.setModifiedBy(user.getUserName());

                final String result = RefsetService.resetRefset(service, user, refsetId);

                final String returnMessage = "{\"message\": \"Reset Successful " + result + "\"}";

                return new ResponseEntity<>(returnMessage, HttpStatus.OK);
            }

            final String returnMessage = "{\"message\": \"It is prohibited to be reseting reference sets on this production system\"}";
            return new ResponseEntity<>(returnMessage, HttpStatus.FORBIDDEN);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Invite user to refset.
     *
     * @param refsetInternalId the refset internal id
     * @param emailInfo the email info
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiIgnore
    @ApiOperation(value = "Request member/non-member to join organization. This call requires authentication with the correct role.")
    @RecordMetric
    @PostMapping(value = "/refset/{refsetInternalId}/invite", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    @Hidden
    public @ResponseBody ResponseEntity<String> inviteUserToRefset(@PathVariable final String refsetInternalId,
        @RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

        try {

            final User authUser = SecurityService.getUserFromSession();

            LOG.debug("inviteUserToRefset: refsetInternalId: " + refsetInternalId + " and emailInfo.recipient: " + emailInfo.getRecipient()
                + " and emailInfo.additionalMessage: " + emailInfo.getAdditionalMessage());

            RefsetService.inviteUserToOrganization(authUser, refsetInternalId, emailInfo.getRecipient(), emailInfo.getAdditionalMessage());

            final String returnMessage = "{\"message\": \"Reference set invite was Successful\"}";

            return new ResponseEntity<>(returnMessage, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

}
