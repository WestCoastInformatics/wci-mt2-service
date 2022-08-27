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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
import org.ihtsdo.refsetservice.sync.SyncAgentUtilities;
import org.ihtsdo.refsetservice.sync.SyncCodeSystemAgent;
import org.ihtsdo.refsetservice.sync.SyncDataInitializer;
import org.ihtsdo.refsetservice.sync.SyncRefsetAgent;
import org.ihtsdo.refsetservice.terminologyservice.DiscussionService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.EmailUtility;
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

/**
 * Controller for /concept endpoints.
 */
@RestController
@Api(tags = "Refset endpoints")
@SuppressWarnings("javadoc")
public class RefsetController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetController.class);

    /** The local directory to store exported refset files. */
    private static String EXPORT_FILE_DIR;

    /** The local directory to store exported refset files. */
    private static final String API_NOTES = "Use cases for search range from use of paging parameters, additional filters, searches properties, and so on.";

    private static final String SHARE_REFSET_EMAIL_SUBJECT = "SNOMED INternational Refset Tool - Shared Refset";

    /** Static initialization. */
    static {

        EXPORT_FILE_DIR = PropertyUtility.getProperty("export.fileDir") + "/";
    }

    /**
     * Returns the refset.
     *
     * @param refsetId the refset ID
     * @param versionDate the version date or IN DEVELOPMENT
     * @return the refset
     * @throws Exception the exception
     */

    @ApiOperation(value = "Get the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetId", value = "The ID of the refset to return.", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/versionDate/{versionDate}", produces = "application/json")
    public @ResponseBody ResponseEntity<Refset> getRefset(@PathVariable(value = "refsetId") final String refsetId, @PathVariable(value = "versionDate") final String versionDate) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            logger.debug("getRefset: refsetId: " + refsetId + " ; versionDate: " + versionDate);

            User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(service, user, refsetId, versionDate);
            RefsetService.getRefsetDescriptions(refset);

            logger.debug("getRefset: Including discussion count");
            DiscussionService.attachRefsetDiscussionCount(service, user, refset);

            if (RefsetMemberService.refsetsBeingUpdated.contains(refset.getId())) {

                refset.setLocked(true);
            }

            if (RefsetService.refsetsToShowUpgradeWarning.contains(refset.getId())) {

                refset.setUpgradeWarning(true);
                RefsetService.refsetsToShowUpgradeWarning.remove(refset.getId());
            }

            logger.debug("getRefset: refset: " + ModelUtility.toJson(refset));

            return new ResponseEntity<>(refset, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Returns the refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the refset
     * @throws Exception the exception
     */

    @ApiOperation(value = "Returns if the refset is locked", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to check.", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/isLocked", produces = "application/json")
    public @ResponseBody ResponseEntity<String> isRefsetLocked(@PathVariable(value = "refsetInternalId") final String refsetInternalId, HttpServletRequest request) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            final boolean isLocked = RefsetMemberService.refsetsBeingUpdated.contains(refsetInternalId);
            String returnString = isLocked + "";
            logger.debug("isRefsetLocked: refsetInternalId: " + refsetInternalId + " ; Locked: " + isLocked);
            logger.debug("isRefsetLocked: refsetsUpdatedMembers: " + RefsetMemberService.refsetsUpdatedMembers);
            logger.debug("isRefsetLocked: does update map contain this refset: " + RefsetMemberService.refsetsUpdatedMembers.containsKey(refsetInternalId));

            if (!isLocked && RefsetMemberService.refsetsUpdatedMembers.containsKey(refsetInternalId)) {

                returnString = ModelUtility.toJson(RefsetMemberService.refsetsUpdatedMembers.get(refsetInternalId));
                RefsetMemberService.refsetsUpdatedMembers.remove(refsetInternalId);
            }

            return new ResponseEntity<>(returnString, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * update active status.
     *
     * @param active the active status
     * @param refsetId The refset ID
     * @return the updated refset
     * @throws Exception the exception
     */
    @PutMapping("/refset/{refsetInternalId}/changeStatus")
    public ResponseEntity<Refset> updateActive(final @RequestBody boolean active, final @PathVariable String refsetInternalId) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            // logger.debug("updateActive: active: " + active + " ; refsetId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();
            final Refset refset = service.findSingle("refsetId:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);
            refset.setActive(active);
            service.setModifiedBy("restApi");
            service.update(refset);

            // logger.debug("updateActive: refset: " + ModelUtility.toJson(refset));

            return new ResponseEntity<>(refset, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Add new refset members.
     *
     * @param active the active status
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a comma separated list of concepts to add
     * @param ecl an ECL query to identify concepts to add
     * @param conceptFile a file containing concept IDs to add
     * @param fileType the type of file uploaded (list or rf2)
     * @return the new internal refset ID
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/members")
    public @ResponseBody ResponseEntity<String> addRefsetMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @RequestBody(required = false) final String conceptIds,
        @RequestParam(required = false) final String ecl, @RequestParam(required = false) final MultipartFile conceptFile, @RequestParam(required = false) final String fileType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            List<String> conceptIdList = new ArrayList<>();
            String error = "";
            List<String> unaddedConcepts;
            final User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            logger.debug("addRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType);

            // create the list of concepts based on what was passed in
            if (conceptIds != null && !conceptIds.equals("")) {

                conceptIdList = new ArrayList<String>(Arrays.asList(conceptIds.split(",")));

            } else if (ecl != null && !ecl.equals("")) {

                final String branchPath = RefsetService.getBranchPath(service, refsetInternalId);
                conceptIdList = RefsetMemberService.getConceptIdsFromEcl(branchPath, ecl);
            } else {

                conceptIdList = RefsetService.getConceptIdsFromFile(conceptFile, fileType);
            }

            logger.debug("addRefsetMembers: conceptIdList: " + conceptIdList);

            // add the list of concepts as members to the refset
            unaddedConcepts = RefsetMemberService.addRefsetMembers(service, user, refsetInternalId, conceptIdList);

            // see if there are any concepts that were unable to be added and craft the error message
            if (unaddedConcepts.size() > 0) {

                error = "Unable to add concepts ";

                for (final String unaddedConcept : unaddedConcepts) {

                    error += unaddedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            // service.commit();
            logger.debug("addRefsetMembers: Finished with " + unaddedConcepts.size() + " invaild concepts");

            if (error.equals("")) {

                return new ResponseEntity<>("{\"status\": \"All concepts added.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
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
    @PostMapping("/refset/{refsetInternalId}/removeMembers")
    public @ResponseBody ResponseEntity<String> removeRefsetMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @RequestBody(required = false) final String conceptIds,
        @RequestParam(required = false) final String ecl, @RequestParam(required = false) final MultipartFile conceptFile, @RequestParam(required = false) final String fileType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            String conceptsToRemove = null;
            User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            logger.debug("removeRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType);

            String error = "";

            // If concepts were passed in use those
            if (conceptIds != null && !conceptIds.equals("")) {

                conceptsToRemove = conceptIds;

            } else if (ecl != null && !ecl.equals("")) {

                final String branchPath = RefsetService.getBranchPath(service, refsetInternalId);
                conceptsToRemove = String.join(",", RefsetMemberService.getConceptIdsFromEcl(branchPath, ecl));
            } else {

                conceptsToRemove = String.join(",", RefsetService.getConceptIdsFromFile(conceptFile, fileType));
            }

            logger.debug("removeRefsetMembers: conceptIds: " + conceptIds);

            // add the list of concepts as members to the refset
            final List<String> unremovedConcepts = RefsetMemberService.removeRefsetMembers(service, user, refsetInternalId, conceptsToRemove);
            // service.commit();

            // see if there are any concepts that were unable to be added and craft the error message
            if (unremovedConcepts.size() > 0) {

                error = "Unable to remove concepts ";

                for (final String unremovedConcept : unremovedConcepts) {

                    error += unremovedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                return new ResponseEntity<>("{\"status\": \"All concepts removed.\"}", HttpStatus.OK);
            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
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
     * @param definitionType is the exception an inclusion or exclusion
     * @return the status or error message
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/definitionExceptions")
    public @ResponseBody ResponseEntity<String> addRefsetDefinitionExceptions(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = false) final String conceptIds, @RequestParam(required = false) final String ecl, @RequestParam(required = false) final MultipartFile conceptFile,
        @RequestParam(required = false) final String fileType, @RequestParam(required = false) final String definitionExceptionType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            List<String> conceptIdList = new ArrayList<>();
            final User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            logger.debug("addRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType
                + " ; definitionExceptionType: " + definitionExceptionType);

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

            final String status = RefsetService.addDefinitionException(service, user, refsetInternalId, inclusionEcl, definitionExceptionType);
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

            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
        }

    }

    /**
     * Remove an intensional refset definition exception.
     *
     * @param refsetInternalId the internal refset ID
     * @param @param definitionExceptionId the exception ID
     * @return the status or error message
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/removeDefinitionException/{definitionExceptionId}")
    public @ResponseBody ResponseEntity<String> removeRefsetDefinitionExceptions(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @PathVariable(value = "definitionExceptionId") final String definitionExceptionId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            final User user = SecurityService.getUserFromSession();
            logger.debug("removeRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; definitionExceptionId: " + definitionExceptionId);

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.removeDefinitionException(service, user, refsetInternalId, definitionExceptionId);
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

            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
        }

    }

    /**
     * Create a new refset.
     *
     * @param refsetParameters The paramaters for the new refset
     * @return the new internal refset ID
     * @throws Exception the exception
     */
    @PostMapping("/refset")
    public @ResponseBody ResponseEntity<String> createRefset(final @RequestBody Refset refsetParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        String newRefsetInternalId = null;

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("createRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));

            User user = SecurityService.getUserFromSession();

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

            RefsetMemberService.refsetsBeingUpdated.remove(newRefsetInternalId);
        }

    }

    /**
     * Modify an existing refset that is in edit mode.
     *
     * @param refsetInternalId the internal refset ID
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @PutMapping("/refset/{refsetInternalId}")
    public @ResponseBody ResponseEntity<String> modifyRefset(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final @RequestBody Refset refsetParameters,
        final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("modifyRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.modifyRefset(service, user, refsetInternalId, refsetParameters);
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

            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
        }

    }

    /**
     * Get Workflow history for a refset.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the workflow history
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get Workflow history search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "terminology", value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "ncit"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/workflowHistory", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<WorkflowHistory>> getWorkflowHistory(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            // logger.debug("getWorkflowHistory refsetInternalId: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters));

            User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            ResultList<WorkflowHistory> results = WorkflowService.getWorkflowHistory(service, refset, searchParameters);

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Modify an existing refset that is in edit mode.
     *
     * @param refsetInternalId the internal refset ID
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/workflowStatus")
    public @ResponseBody ResponseEntity<Refset> setWorkflowStatus(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @RequestParam final String action,
        @RequestParam(required = false) final String notes) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("setWorkflowStatus: refsetInternalId: " + refsetInternalId + " ; action: " + action + " ; notes: " + notes);

            User user = SecurityService.getUserFromSession();
            Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            final String currentStatus = refset.getWorkflowStatus();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            // if the status is Published then create a new version of the refset that is ready to be edited
            if (currentStatus == null || currentStatus.equals(WorkflowService.PUBLISHED)) {

                final List<String> editionVersions = RefsetService.getBranchVersions(refset.getEditionBranch());
                final String versionDate = DateUtility.formatDate(refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null);

                final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, user, refset.getId(), true);
                refset = RefsetService.getRefset(service, user, newRefsetInternalId);

                if (action.equals(WorkflowService.EDIT) && editionVersions.indexOf(versionDate) > 0) {

                    RefsetService.refsetsToShowUpgradeWarning.add(newRefsetInternalId);
                    refset.setUpgradeWarning(true);
                }

                return new ResponseEntity<>(refset, HttpStatus.OK);
            }

            refset = WorkflowService.setWorkflowStatusByAction(service, user, action, refset, notes);
            // service.commit();

            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(refset.getWorkflowStatus())) {

                logger.debug("setWorkflowStatus: updated refset: " + ModelUtility.toJson(refset));
                return new ResponseEntity<>(refset, HttpStatus.OK);
            } else {

                logger.debug("setWorkflowStatus: did not update workflow status.");
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
    @PutMapping("/refset/{refsetInternalId}/workflowNote")
    public @ResponseBody ResponseEntity<ResultList<WorkflowHistory>> updateWorkflowNote(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = true) final String notes) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // logger.debug("updateWorkflowNote: refsetInternalId: " + refsetInternalId + " ;notes: " + notes);
            User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
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
     * Complete the publication of all Ready for Publication refsetsin a code system.
     *
     * @param versionDate the publication date of the refset in YYYY/mm/dd format
     * @param codeSystem a code system to limit the refset to
     * @return the status of the operation
     * @throws Exception the exception
     */
    @PutMapping("/admin/completeAllRefsetPublications")
    public @ResponseBody ResponseEntity<String> completeAllRefsetPublications(@RequestParam(required = true) final String versionDate, @RequestParam(required = true) final String codeSystem)
        throws Exception {

        final User user = SecurityService.getUserFromSession();

        if (StringUtility.isEmpty(codeSystem)) {

            throw new Exception("A Code System must be specified.");
        }

        try (TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            logger.debug("completeAllRefsetPublications: versionDate: " + versionDate + " ; editionShortName (codeSystem): " + codeSystem);

            final List<String> refsetsNotUpdated = WorkflowService.completeAllRefsetPublications(service, versionDate, codeSystem);
            String error = "";

            service.commit();

            // see if there are any refsets that were unable to be updated and craft the error message
            if (refsetsNotUpdated.size() > 0) {

                error = "Unable to complete publication for refsets in code system " + codeSystem + ": ";

                for (final String unremovedConcept : refsetsNotUpdated) {

                    error += unremovedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                String message = "All refset publications completed in code system " + codeSystem;
                return new ResponseEntity<>("{\"status\": \"" + message + ".\"}", HttpStatus.OK);

            } else {

                return new ResponseEntity<>("{\"error\": \"" + error + "\"}", HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Set refsets that failed publication back to 'Ready For Edit' status.
     *
     * @param refsetIds a comma separated list of refset IDs
     * @param notes the reason why the refsets failed
     * @return the status of the operation
     * @throws Exception the exception
     */
    @PutMapping("/admin/failRefsetPublications")
    public @ResponseBody ResponseEntity<String> failRefsetPublications(@RequestParam(required = true) final String refsetIds, @RequestParam(required = true) final String notes) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            logger.debug("failRefsetPublications: refset IDs: " + refsetIds + " ; notes: " + notes);

            final List<String> refsetsNotUpdated = WorkflowService.setBatchWorkflowStatusByAction(service, user, refsetIds, WorkflowService.FAILS_RVF, notes);
            String error = "";

            // see if there are any refsets that were unable to be updated and craft the error message
            if (refsetsNotUpdated.size() > 0) {

                error = "Unable to update refsets: ";

                for (final String unremovedConcept : refsetsNotUpdated) {

                    error += unremovedConcept + ", ";
                }

                error = StringUtils.removeEnd(error, ", ");
            }

            if (error.equals("")) {

                return new ResponseEntity<>("{\"status\": \"All refsets updated.\"}", HttpStatus.OK);
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
    @PostMapping("/refset/{refsetInternalId}/newVersion")
    public @ResponseBody ResponseEntity<String> createNewRefsetVersion(@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        // checkBinding(bindingResult);

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("createNewRefsetVersion: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();

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
     * Inactivate a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    @DeleteMapping("/refset/{refsetInternalId}")
    public @ResponseBody ResponseEntity<String> inactiveRefset(final @PathVariable String refsetInternalId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // logger.debug("inactiveRefset: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.inactivateRefset(service, user, refsetInternalId);
            // service.commit();

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Convert intensional refset to extensional
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/convert", produces = "application/json")
    public @ResponseBody ResponseEntity<String> convertToExtensional(final @PathVariable String refsetInternalId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // logger.debug("deleteRefsetEditVersion: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());

            final String status = RefsetService.convertToExtensional(service, user, refsetInternalId);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Delete the edit version of a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    @DeleteMapping("/refset/{refsetInternalId}/editVersion")
    public @ResponseBody ResponseEntity<String> deleteRefsetEditVersion(final @PathVariable String refsetInternalId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // logger.debug("deleteRefsetEditVersion: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            // service.setTransactionPerOperation(false);
            // service.beginTransaction();

            final String status = RefsetService.deleteInDevelopmentVersion(service, user, refsetInternalId, true);
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
     * @param showInDevelopment flag on whether to include IN_DEVELOPMENT refsets
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "terminology", value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "ncit"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/search", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<Refset>> searchRefsets(final SearchParameters searchParameters, final boolean searchConcepts, final boolean showInDevelopment,
        @RequestParam(required = false) final Boolean countComments, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        User user = SecurityService.getUserFromSession();

        try (TerminologyService service = new TerminologyService()) {

            logger.debug("searchRefsets searchParameters: " + ModelUtility.toJson(searchParameters) + "; searchConcepts: " + searchConcepts + " ; showInDevelopment: " + showInDevelopment
                + " ; countComments: " + countComments);

            ResultList<Refset> results = RefsetService.searchRefsets(user, service, searchParameters, searchConcepts, true, false);

            if (countComments != null && countComments) {

                logger.debug("searchRefsets: Including discussion count");
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
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search the taxonomy for refset members", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "ncit"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {
        "/refset/{refsetInternalId}/taxonomySearch", "/refset/{refsetInternalId}/conceptSearch"
    }, produces = "application/json")
    public @ResponseBody ResponseEntity<ConceptResultList> searchConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final SearchParameters searchParameters,
        final BindingResult bindingResult, HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        User user = SecurityService.getUserFromSession();
        boolean searchRefsetMembers = false;
        final String uri = request.getRequestURI();

        if (uri.contains("taxonomySearch")) {

            searchRefsetMembers = true;
        }

        try (final TerminologyService service = new TerminologyService()) {

            ConceptResultList results = new ConceptResultList();
            String query = searchParameters.getQuery();

            logger.debug("taxonomySearch: searchConcepts: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters) + " ; searchRefsetMembers: " + searchRefsetMembers);

            if (query != null && !query.equals("")) {

                results = RefsetMemberService.prepareConceptSearch(service, user, refsetInternalId, searchParameters, searchRefsetMembers);
            }

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Refset Members.
     *
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param displayType Should results be a list or hierarchical taxonomy
     * @param taxonomyParameters the taxonomy parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "terminology", value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "ncit"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = true, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = true, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "displayType", value = "Should results be a list or taxonomy", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "list"),
        @ApiImplicitParam(name = "startingConceptId", value = "For taxonomy calls the starting concept ID (exclusive - get the children of this concept not the concept itself)", required = false,
            dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "depth", value = "For taxonomy calls the depth - how many levels of children or parents to retrieve", required = false, dataTypeClass = Integer.class,
            paramType = "query", defaultValue = "1"),
        @ApiImplicitParam(name = "returnChildren", value = "For taxonomy calls should children be returned. If false then parents will be returned", required = false, dataType = "boolean",
            paramType = "query", defaultValue = "true"),
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/members", produces = "application/json")
    public @ResponseBody ResponseEntity<ConceptResultList> getMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final SearchParameters searchParameters,
        final String displayType, final TaxonomyParameters taxonomyParameters, @RequestParam(required = false) final Boolean countComments, final BindingResult bindingResult) throws Exception {

        checkBinding(bindingResult);

        final long start = System.currentTimeMillis();
        ConceptResultList results = new ConceptResultList();
        User user = SecurityService.getUserFromSession();

        logger.debug("getMembers: refsetInternalId: " + refsetInternalId + " ; searchParameters: + " + searchParameters + " ; taxonomyParameters: " + taxonomyParameters + " ; displayType: "
            + displayType + " ; countComments: " + countComments);

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetMemberService.getRefset(user, service, refsetInternalId);

            results = RefsetMemberService.getRefsetMembers(service, user, refsetInternalId, searchParameters, displayType, taxonomyParameters);

            if (countComments != null && countComments) {

                logger.debug("getMembers: Including discussion count");
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
    @ApiOperation(value = "Cache the ancestors of the refset members for the specified refset ID", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully populated the refset's ancestor cache"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetId", value = "The ID of the refset for which ancestors are to be identified.", required = true, dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/ancestors/{refsetId}/versionDate/{versionDate}", produces = "application/json")
    public @ResponseBody ResponseEntity<String> cacheMemberAncestors(@PathVariable(value = "refsetId") final String refsetId, @PathVariable(value = "versionDate") final String versionDate)
        throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            User user = SecurityService.getUserFromSession();
            logger.debug("cacheMemberAncestors: refsetId: " + refsetId + " ; versionDate: " + versionDate);

            String returnJson = "{\"success\": \"<RESULT>\"}";

            final boolean success = RefsetMemberService.cacheMemberAncestors(service, user, refsetId, versionDate);

            if (success) {

                returnJson = returnJson.replace("<RESULT>", "true");
            } else {

                returnJson = returnJson.replace("<RESULT>", "false");
            }

            // logger.debug("cacheMemberAncestors results: " + returnJson);

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
    @ApiOperation(value = "Export the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "exportType", value = "The RF2 type SNAPSHOT or DELTA.", required = true, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "languageId", value = "For formats with names which language to display the name in.", required = false, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "format", value = "The type of export: 'rf2', 'rf2_with_names', ' or 'sctids'.", required = true, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "fileNameDate", value = "Format: yyyymmdd. Date to be embedded in the RF2 file names.", required = true, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "startEffectiveTime", value = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.",
            required = false, dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "transientEffectiveTime", value = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.", required = false,
            dataTypeClass = String.class, paramType = "query"),
        @ApiImplicitParam(name = "exportMetadata", value = "e.g.  true or false", required = true, dataType = "boolean", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/{refsetInternalId}", produces = "application/json")
    public @ResponseBody ResponseEntity<String> exportRefset(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final String format, final String exportType,
        final String languageId, final String fileNameDate, String startEffectiveTime, final String transientEffectiveTime, final boolean exportMetadata) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            User user = SecurityService.getUserFromSession();
            logger.debug("exportRefset: refsetInternalId: " + refsetInternalId + " ; format: " + format + " ; type: " + exportType + " ; fileNameDate: " + fileNameDate + " ; startEffectiveTime: "
                + startEffectiveTime + " ; transientEffectiveTime: " + transientEffectiveTime + " ; exportMetadata: " + exportMetadata);

            String url = null;

            if (format.equals("rf2") || format.equals("rf2_with_names")) {

                boolean withNames = false;

                if (format.equals("rf2_with_names")) {

                    withNames = true;
                }

                String uri = "";

                if (exportType.contentEquals("SNAPSHOT")) {

                    uri = RefsetMemberService.exportRefsetRf2(service, refsetInternalId, exportType, languageId, fileNameDate, startEffectiveTime, transientEffectiveTime, exportMetadata, withNames);
                } else {

                    uri = RefsetMemberService.exportRefsetRf2Delta(service, user, refsetInternalId, exportType, languageId, fileNameDate, startEffectiveTime, transientEffectiveTime, exportMetadata,
                        withNames);
                }

                logger.debug("results: " + uri);
                url = "{\"url\": \"" + uri + "\"}";

            } else if (format.equals("sctids")) {

                String uri = RefsetMemberService.exportRefsetSctidList(service, refsetInternalId, exportMetadata);
                url = "{\"url\": \"" + uri + "\"}";
            }

            return new ResponseEntity<>(url, HttpStatus.OK);

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
    @ApiOperation(value = "Download the specified refset export file", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "fileName", value = "The name of the file to download.", required = true, dataTypeClass = String.class, paramType = "path")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/download/{fileName}", produces = "application/json")
    public @ResponseBody ResponseEntity<Resource> downloadExport(@PathVariable(value = "fileName") final String fileName) throws Exception {

        try {

            logger.debug("downloadExport: fileName: " + fileName);

            User user = SecurityService.getUserFromSession();
            Path filePath = Paths.get(EXPORT_FILE_DIR + fileName);
            Resource file = new UrlResource(filePath.toUri());

            if (!file.exists() || !file.isReadable()) {

                throw new RuntimeException("Could not read the file!");
            }

            return ResponseEntity.ok().header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION).header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFilename() + "\"").contentLength(file.contentLength()).body(file);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Gets member history.
     *
     * @param conceptId the member id
     * @param refsetInternalId the refset internal id
     * @return the member history
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the concept for the specified ID", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "memberId", value = "The ID of the member to return.", required = true, dataTypeClass = String.class, paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<Map<String, String>>> getMemberHistory(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @PathVariable(value = "conceptId") final String conceptId) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            logger.debug("getMemberHistory: memberId: " + conceptId + "; refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {

                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                RefsetService.setRefsetPermissions(user, refset);
                final List<Map<String, String>> versions = RefsetService.getSortedRefsetVersionList(refset, service, true);

                final List<Map<String, String>> memberHistory = RefsetMemberService.getMemberHistory(service, conceptId, versions);

                logger.debug("getMemberHistory: member: " + ModelUtility.toJson(memberHistory));

                ResultList<Map<String, String>> results = new ResultList<>(memberHistory);
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
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataTypeClass = String.class, paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{conceptId}", produces = "application/json")
    public @ResponseBody ResponseEntity<Concept> getConceptDetails(@PathVariable(value = "conceptId") final String conceptId, final String refsetInternalId) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            // logger.debug("getConceptDetails: conceptId: " + conceptId + "; refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {

                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                final Concept concept = RefsetMemberService.getConceptDetails(conceptId, refset);

                // logger.debug("getConceptDetails: concept: " + ModelUtility.toJson(concept));

                return new ResponseEntity<>(concept, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Syncs RTT data into the database but only if the database is empty.
     *
     * @param quickSync Should the sync be run adding a refset version for each branch version, which is faster than checking each refset for publication. Default is false
     * @param perVersionCreation If true, create a refset for every version created. If false, only when changes are observed.
     * @return the status of the sync
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/rtt", produces = "application/json")
    public @ResponseBody ResponseEntity<String> syncRttData(@RequestParam(required = false) final Boolean perVersionCreation, @RequestParam(required = false) final Boolean forProduction)
        throws Exception {

        return syncSnowstorm(perVersionCreation, forProduction);
    }

    /**
     * Sync against snowstorm still relying upon latest RTT data files to sync. Compares against all of a given refets's versions on snowstorm, so no need for a quickSync
     * option
     * 
     * TODO: Determine if can do a nightly update of data files programmatically
     *
     * @param forProduction Should the sync add projects, teams, and other testing data, which it should NOT do for Production. Default is true
     * @param perVersionCreation If true, create a refset for every version created. If false, only when changes are observed.
     * @return the status of the sync
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/snowstorm", produces = "application/json")
    public @ResponseBody ResponseEntity<String> syncSnowstorm(@RequestParam(required = false) final Boolean perVersionCreation, @RequestParam(required = false) final Boolean forProduction)
        throws Exception {

        try {

            boolean refsetPerVersionSync = false;
            boolean runForProduction = false;

            if (perVersionCreation != null && perVersionCreation.booleanValue()) {

                logger.info("!!!!! syncSnowstorm RUNNING QUICK SYNC - WILL HAVE MORE THAN ONLY PUBLISHED REFSET VERSIONS");
                refsetPerVersionSync = true;
            }

            if (forProduction != null && forProduction.booleanValue()) {

                logger.info("!!!!! syncSnowstorm RUNNING SYNC ON PRODUCTION - SHOULDN'T CONTAIN TESTING PROJECTS, TEAMS, AND REFSETS");
                runForProduction = true;
            }

            SyncAgent agent = new SyncCodeSystemAgent(refsetPerVersionSync, runForProduction);

            try (TerminologyService service = new TerminologyService()) {

                String message = "";

                logger.info("Starting Syncing of Code System, Branches, and Refsets from Snowstorm");

                // Only identify branches on filtered code systems and on runShortSync value
                agent.syncSnowstorm();

                SyncAgentUtilities syncUtilities = new SyncAgentUtilities();
                syncUtilities.parseRttData();

                // Find all refsets from filtered branches
                agent = new SyncRefsetAgent(refsetPerVersionSync, runForProduction);
                agent.syncSnowstorm();

                // Update imported refsets with RTT-based metadata (as defined in parseRttData())
                if (!runForProduction) {

                    SyncDataInitializer initializer = new SyncDataInitializer();
                    initializer.initialize(agent.getDeveleperTestingEdition(), agent.getAllDatabaseEditions(), agent.getAllDatabaseRefsets(), agent.getDefaultEditionProjects());
                }

                logger.info("Completed Syncing with Snowstorm");

                return new ResponseEntity<>(message + "RT2 synced with Snowstorm successfully", HttpStatus.OK);
            } finally {

                logger.info(agent.printStatistics());
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Creates a new testing refset containing initial feedback. The method will identify the last refset created for this purpose (based on numbering). It will create a new
     * one, with the same initial feedback content, but with an incremented number appended to the name and refsetId
     *
     * @return the status of the creation
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/feedback", produces = "application/json")
    public @ResponseBody ResponseEntity<String> createNewFeedbackTestingRefset() throws Exception {

        try {

            String status = "Feedback testing refset created successfully";
            logger.info("Create new refset, initialized with feedback, for testing purposes");
            SyncDataInitializer initializer = new SyncDataInitializer();
            Refset refset = initializer.createTestingFeedbackRefset();

            logger.info("New Feedback testing refset created succesffully with internal/SctiId pair: " + refset.getId() + "/" + refset.getRefsetId());

            return new ResponseEntity<>(status, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Creates a new testing refset for testing intensional functionality. The method will identify the last refset created for this purpose (based on numbering). It will
     * create a new one, similarly as intensionsal, but with an incremented number appended to the name and refsetId
     *
     * @return the status of the creation
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/admin/sync/intensional", produces = "application/json")
    public @ResponseBody ResponseEntity<String> createNewIntensionalTestingRefset() throws Exception {

        try {

            String status = "Intensional testing refset created successfully";
            logger.info("Create new intensional refset for testing purposes");
            SyncDataInitializer initializer = new SyncDataInitializer();
            Refset refset = initializer.createTestingIntensionalRefset();

            logger.info("New Feedback testing refset created succesffully with internal/SctiId pair: " + refset.getId() + "/" + refset.getRefsetId());

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
    @ApiOperation(value = "Gets the version statuses", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versionStatuses", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getVersionStatuses() throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final List<TypeKeyValue> versionStatuses = new ArrayList<>();

                for (VersionStatus value : VersionStatus.values()) {

                    TypeKeyValue typeKeyValue = new TypeKeyValue("status", value.getLable(), value.getLable());
                    versionStatuses.add(typeKeyValue);
                }

                ResultList<TypeKeyValue> results = new ResultList<>(versionStatuses);
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
    @ApiOperation(value = "Gets the versions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versions", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getVersions() throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                ResultList<Refset> refsets = new ResultList<Refset>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("_exists_:versionDate AND latestPublishedVersion: true");

                refsets = service.find(query, pfs, Refset.class, null);

                ResultList<TypeKeyValue> results = new ResultList<>();
                List<TypeKeyValue> resultItems = new ArrayList<>();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

                for (Refset refset : refsets.getItems()) {

                    String version = sdf.format(refset.getVersionDate());
                    TypeKeyValue entry = new TypeKeyValue("version", version, version);

                    if (!resultItems.contains(entry)) {

                        resultItems.add(entry);
                    }

                }

                resultItems.sort(new Comparator<TypeKeyValue>() {

                    @Override
                    public int compare(TypeKeyValue o1, TypeKeyValue o2) {

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
    @ApiOperation(value = "Gets the editions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/editions", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getEditions(@QueryParam(value = "onlyUsersTeams") final boolean onlyEditionsWithoutOrganizations) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

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

                logger.debug("getEditions results: " + ModelUtility.toJson(results));
                List<Edition> editionList = results.getItems();
                editionList.sort(new Comparator<Edition>() {

                    @Override
                    public int compare(Edition o1, Edition o2) {

                        return o1.getName().compareTo(o2.getName());
                    }
                });
                List<TypeKeyValue> entryList = new ArrayList<>();
                ResultList<TypeKeyValue> entryResults = new ResultList<>();

                for (Edition edition : editionList) {

                    // if this flag is set skip any edition already tied to an organization
                    if (onlyEditionsWithoutOrganizations) {

                        boolean hasOrganization = false;

                        for (Organization organization : organizationList) {

                            if (edition.getOrganization().getId().equals(organization.getId())) {

                                hasOrganization = true;
                            }

                        }

                        if (hasOrganization) {

                            continue;
                        }

                    }

                    TypeKeyValue tkv = new TypeKeyValue("edition", edition.getName(), edition.getName());
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
    @ApiOperation(value = "Gets the list of Refset Concepts that can be used as parents to a refset or as the underlying concept for a new refset.", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/general/refsetConcepts", produces = "application/json")
    public @ResponseBody ResponseEntity<ConceptResultList> getRefsetConcepts(final String branch, final boolean areParentConcepts) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // logger.debug("getRefsetConcepts: branch: " + branch + "; areParentConcepts: " + areParentConcepts);

            User user = SecurityService.getUserFromSession();
            ConceptResultList results = RefsetService.getRefsetConcepts(service, branch, areParentConcepts);

            // logger.debug("getRefsetConcepts: results: " + ModelUtility.toJson(results));

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
    @ApiOperation(value = "Gets the branch versions", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/general/branchVersions", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<String>> getBranchVersions(final String branch) throws Exception {

        try {

            // logger.debug("getBranchVersions - branch: " + branch);

            User user = SecurityService.getUserFromSession();

            final ResultList<String> results = new ResultList<>();
            results.setItems(RefsetService.getBranchVersions(branch));

            // logger.debug("getBranchVersions - results: " + results);

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
    @ApiOperation(value = "Gets the organizations", response = ResultList.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/organizations", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<TypeKeyValue>> getOrganizations() throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Organization> results = new ResultList<Organization>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();
                query.setQuery("active:true");

                results = service.find(query, pfs, Organization.class, null);

                results.setTimeTaken(System.currentTimeMillis() - start);
                results.setTotalKnown(true);

                // logger.debug("results: " + ModelUtility.toJson(results));
                List<Organization> organizationList = results.getItems();
                organizationList.sort(new Comparator<Organization>() {

                    @Override
                    public int compare(Organization o1, Organization o2) {

                        return o1.getName().compareTo(o2.getName());
                    }
                });
                List<TypeKeyValue> entryList = new ArrayList<>();
                ResultList<TypeKeyValue> entryResults = new ResultList<>();

                for (Organization organization : organizationList) {

                    TypeKeyValue tkv = new TypeKeyValue("organization", organization.getName(), organization.getName());
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
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/ancestorCache", produces = "application/json")
    public @ResponseBody ResponseEntity<String> getRefsetAncestorCache(@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            logger.debug("getRefsetAncestorCache: refsetInternalId: " + refsetInternalId);

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
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}/ancestorConcepts", produces = "application/json")
    public @ResponseBody ResponseEntity<Concept> getMemberAncestorConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @PathVariable(value = "conceptId") final String conceptId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("getMemberAncestorConcepts: refsetInternalId: " + refsetInternalId + " ; conceptId: " + conceptId);
            final User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
            final Concept concept = new Concept();
            concept.setCode(conceptId);

            RefsetMemberService.getConceptAncestors(refset, Arrays.asList(concept));

            return new ResponseEntity<>(concept, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Compile and store the data to upgrade a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @param upgradeBranch the branch to upgrade to
     * @return The operation status
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/compileUpgradeData", produces = "application/json")
    public @ResponseBody ResponseEntity<String> compileUpgradeData(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @RequestParam(required = false) final String upgradeBranch)
        throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            String status = "";

            logger.debug("compileUpgradeData: refsetInternalId: " + refsetInternalId + "; upgradeBranch: " + upgradeBranch);

            // add the list of concepts as members to the refset
            status = RefsetMemberService.compileUpgradeData(service, user, refsetInternalId, upgradeBranch);

            logger.debug("compileUpgradeData: Finished with status " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
        }

    }

    /**
     * Get the stored the data to upgrade a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @param upgradeBranch the branch to upgrade to
     * @return The upgrade data
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/upgradeData", produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<UpgradeInactiveConcept>> getUpgradeData(@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("getUpgradeData: refsetInternalId: " + refsetInternalId);

            // add the list of concepts as members to the refset
            final ResultList<UpgradeInactiveConcept> results = RefsetMemberService.getUpgradeData(service, user, refsetInternalId);

            logger.debug("getUpgradeData: results " + results);

            return new ResponseEntity<>(results, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Make a change to an upgrade concept.
     *
     * @param active the active status
     * @param refsetInternalId the internal refset ID
     * @param inactiveConceptId the concept ID of the inactive concept to be upgraded
     * @param replacementConceptId the concept ID of the replacement concept to be updated
     * @param manualReplacementConcept the manual upgrade replacement concept that to be added
     * @param changed a string identifying what has been changed
     * @return the status
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/modifyUpgradeConcept")
    public @ResponseBody ResponseEntity<String> modifyUpgradeConcept(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = true) final String inactiveConceptId, @RequestParam(required = false) final String replacementConceptId, @RequestParam(required = true) final String changed,
        @RequestBody(required = false) final UpgradeReplacementConcept manualReplacementConcept) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully";

            logger.debug("modifyUpgradeConcept: refsetInternalId: " + refsetInternalId + "; changed: " + changed + "; inactiveConceptId: " + inactiveConceptId + "; replacementConceptId: "
                + replacementConceptId + "; manualReplacementConcept: " + manualReplacementConcept);

            status = RefsetMemberService.modifyUpgradeConcept(service, user, refsetInternalId, inactiveConceptId, replacementConceptId, manualReplacementConcept, changed);

            logger.debug("modifyUpgradeConcept: Finished with status: " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Remove all inactive Upgrade concepts at once.
     *
     * @param active the active status
     * @param refsetInternalId the internal refset ID
     * @return the status
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/removeAllUpgradeInactiveConcepts")
    public @ResponseBody ResponseEntity<String> removeAllUpgradeInactiveConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully";

            logger.debug("removeAllUpgradeInactiveConcepts: refsetInternalId: " + refsetInternalId);

            status = RefsetMemberService.removeAllUpgradeInactiveConcepts(service, user, refsetInternalId);

            logger.debug("removeAllUpgradeInactiveConcepts: Finished with status: " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    /**
     * Add all replacement Upgrade concepts as members at once.
     *
     * @param active the active status
     * @param refsetInternalId the internal refset ID
     * @return the status
     * @throws Exception the exception
     */
    @PostMapping("/refset/{refsetInternalId}/addAllUpgradeReplacementConcepts")
    public @ResponseBody ResponseEntity<String> addAllUpgradeReplacementConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully";

            logger.debug("addAllUpgradeReplacementConcepts: refsetInternalId: " + refsetInternalId);

            status = RefsetMemberService.addAllUpgradeReplacementConcepts(service, user, refsetInternalId);

            logger.debug("addAllUpgradeReplacementConcepts: Finished with status: " + status);

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
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search the taxonomy for refset members", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "ncit"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {
        "/refset/{refsetInternalId}/replacementConceptSearch"
    }, produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<UpgradeReplacementConcept>> replacementConceptSearch(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final SearchParameters searchParameters, final BindingResult bindingResult, HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetMemberService.getRefset(user, service, refsetInternalId);
            ResultList<UpgradeReplacementConcept> results = new ResultList<>();
            String query = searchParameters.getQuery();

            logger.debug("replacementConceptSearch: refsetInternalId: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters));

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
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Search the taxonomy for refset members", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataTypeClass = String.class, paramType = "query", defaultValue = "ncit"),
        @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataTypeClass = String.class, paramType = "query", defaultValue = ""),
        @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0"),
        @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataTypeClass = Integer.class, paramType = "query", defaultValue = "0")
        // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {
        "/refset/dropdownSearch"
    }, produces = "application/json")
    public @ResponseBody ResponseEntity<ResultList<Refset>> searchRefsetsForDropdowns(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            ResultList<Refset> results = new ResultList<>();
            String query = searchParameters.getQuery();

            logger.debug("refsetDropdownSearch: searchParameters: " + ModelUtility.toJson(searchParameters));

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
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{activeRefsetInternalId}/compileComparisonData", produces = "application/json")
    public @ResponseBody ResponseEntity<String> compileComparisonData(@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId,
        @RequestParam(required = true) final String comparisonRefsetInternalId) throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            String status = "";
            RefsetMemberService.refsetsBeingUpdated.add(activeRefsetInternalId);

            logger.debug("compileComparisonData: activeRefsetInternalId: " + activeRefsetInternalId + "; comparisonRefsetInternalId: " + comparisonRefsetInternalId);

            // add the list of concepts as members to the refset
            status = RefsetMemberService.compileComparisonData(service, user, activeRefsetInternalId, comparisonRefsetInternalId);

            logger.debug("compileComparisonData: Finished with status " + status);

            return new ResponseEntity<>("{\"status\": \"" + status + "\"}", HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

        finally {

            RefsetMemberService.refsetsBeingUpdated.remove(activeRefsetInternalId);
        }

    }

    /**
     * Get the data to compare two refsets.
     *
     * @param activeRefsetInternalId the internal ID of the active refset
     * @return The comparison data
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{activeRefsetInternalId}/comparisonData", produces = "application/json")
    public @ResponseBody ResponseEntity<RefsetMemberComparison> getComparisonData(@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId, final HttpServletRequest request)
        throws Exception {

        final User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("getComparisonData: activeRefsetInternalId: " + activeRefsetInternalId);

            // add the list of concepts as members to the refset
            final String uuid = (String) request.getSession().getAttribute("refsetMemberComparison_" + activeRefsetInternalId);
            final RefsetMemberComparison results = (RefsetMemberComparison) SecurityService.getFromInMemoryStorage(uuid);
            SecurityService.removeFromInMemoryStorage(uuid);
            request.getSession().removeAttribute("refsetMemberComparison_" + activeRefsetInternalId);

            if (results == null) {

                throw new Exception("There were no comparison results to retrieve for this refset.");
            }

            logger.debug("getComparisonData: results " + results);

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
    @ApiOperation(value = "Request access to the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested information"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to request access to.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "comments", value = "Any comments related to the request.", required = true, dataTypeClass = String.class, paramType = "query"),

    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/requestAccess", produces = "application/json")
    public @ResponseBody ResponseEntity<Boolean> requestRefsetAccess(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final String comments) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            User user = SecurityService.getUserFromSession();
            logger.debug("requestRefsetAccess: refsetInternalId: " + refsetInternalId + " ; comments: " + comments);
            String url = null;

            return new ResponseEntity<>(true, HttpStatus.OK);

        } catch (final Exception e) {

            return handleException(e);
        }

    }

    @ApiOperation(value = "Share a refset via email", response = Refset.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully shared the requested refset"), @ApiResponse(code = 400, message = "Invalid email address recipient entered"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @PostMapping(value = "/refset/{refsetInternalId}/share", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public @ResponseBody ResponseEntity<String> shareRefset(@PathVariable final String refsetInternalId, @RequestBody(required = true) final SendCommunicationEmailInfo emailInfo) throws Exception {

        try {

            logger
                .debug("getRefset: refsetId: " + refsetInternalId + " and emailInfo.recipient: " + emailInfo.getRecipient() + " and emailInfo.additionalMessage: " + emailInfo.getAdditionalMessage());

            try (TerminologyService service = new TerminologyService()) {

                User user = SecurityService.getUserFromSession();
                final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

                StringBuffer emailBody = new StringBuffer();

                // Title
                emailBody.append("Hello, " + emailInfo.getRecipient() + "!" + System.getProperty("line.separator"));
                emailBody.append(System.getProperty("line.separator"));

                // Main announcement
                emailBody.append(user.getName() + " would like to share " + refset.getName() + " with you: " + 
                emailBody.append(refset.getExternalUrl() + System.getProperty("line.separator"));
                emailBody.append(System.getProperty("line.separator"));

                // Additonal Info from Sender
                if (emailInfo.getAdditionalMessage() != null && !emailInfo.getAdditionalMessage().isBlank()) {

                    emailBody.append(user.getName() + " has included the additional message:" + System.getProperty("line.separator"));
                    emailBody.append(emailInfo.getAdditionalMessage() + System.getProperty("line.separator"));
                    emailBody.append(System.getProperty("line.separator"));
                }

                // Warning
                emailBody.append("If this email was recieved in error, you can safely ingnore it." + System.getProperty("line.separator"));
                emailBody.append(System.getProperty("line.separator"));

                // Signature
                emailBody.append("Thank you," + System.getProperty("line.separator"));
                emailBody.append("The SNOMED CT Referencve Set Tool Team");

                EmailUtility.sendEmail(SHARE_REFSET_EMAIL_SUBJECT, user.getEmail(), new HashSet<>(Arrays.asList(emailInfo.getRecipient())), emailBody.toString());

                AuditEntryHelper.sendCommunicationEmailEntry(refset, "Share email", user.getUserName(), emailInfo.getRecipient());

                String returnString = "Shared refset";

                return new ResponseEntity<>(returnString, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }

    }

}
