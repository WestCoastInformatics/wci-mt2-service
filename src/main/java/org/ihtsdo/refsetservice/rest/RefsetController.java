
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
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetMemberComparison;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcecpt;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcecpt;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.HistoricDataMigrator;
import org.ihtsdo.refsetservice.util.IndexUtility;
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
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "refsetId", value = "The ID of the refset to return.", required = true, dataType = "string", paramType = "path")})
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetId}/versionDate/{versionDate}", produces = "application/json")
    public @ResponseBody Refset getRefset(
        @PathVariable(value = "refsetId") final String refsetId, 
        @PathVariable(value = "versionDate") final String versionDate, 
        HttpServletRequest request
    ) throws Exception {

        try {

            logger.debug("getRefset: refsetId: " + refsetId + " ; versionDate: " + versionDate);
            
            User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(user, refsetId, versionDate);
            RefsetService.getRefsetDescriptions(refset);
            
            if (RefsetMemberService.refsetsBeingUpdated.contains(refset.getId())) {
                refset.setLocked(true);
            }
            
            return refset;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to check.", required = true, dataType = "string", paramType = "path")})
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/isLocked", produces = "application/json")
    public @ResponseBody String isRefsetLocked(@PathVariable(value = "refsetInternalId") final String refsetInternalId, HttpServletRequest request) throws Exception {
 
        try {

            User user = SecurityService.getUserFromSession();
            final boolean isLocked = RefsetMemberService.refsetsBeingUpdated.contains(refsetInternalId);
            String returnString = isLocked + "";
            logger.debug("isRefsetLocked: refsetInternalId: " + refsetInternalId + " ; Locked: " + isLocked);
            logger.debug("********** isRefsetLocked: refsetsUpdatedMembers: " + RefsetMemberService.refsetsUpdatedMembers);
            logger.debug("********** isRefsetLocked: does update map contain this refset: " + RefsetMemberService.refsetsUpdatedMembers.containsKey(refsetInternalId));
            
            if (!isLocked && RefsetMemberService.refsetsUpdatedMembers.containsKey(refsetInternalId)) {
                
                returnString = ModelUtility.toJson(RefsetMemberService.refsetsUpdatedMembers.get(refsetInternalId));
                RefsetMemberService.refsetsUpdatedMembers.remove(refsetInternalId);
            }
            
            return returnString;

        } catch (final Exception e) {

            handleException(e);
            return false + "";
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
    public Refset updateActive(final @RequestBody boolean active, final @PathVariable String refsetInternalId)
        throws Exception {

        try {

            //logger.debug("updateActive: active: " + active + " ; refsetId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "refsetId:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);
                refset.setActive(active);
                service.setModifiedBy("restApi");
                service.update(refset);

                //logger.debug("updateActive: refset: " + ModelUtility.toJson(refset));

                return refset;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String addRefsetMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = false) final String conceptIds, @RequestParam(required = false) final String ecl, 
        @RequestParam(required = false) final MultipartFile conceptFile,
        @RequestParam(required = false) final String fileType)
        throws Exception {
        
        try {
            
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            List<String> conceptIdList = new ArrayList<>();
            String error = "";
            List<String> unaddedConcepts;
            final User user = SecurityService.getUserFromSession(); 
            
            logger.debug("addRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType);
              
            // create the list of concepts based on what was passed in
            if (conceptIds != null && !conceptIds.equals("")) {
                conceptIdList = new ArrayList<String>(Arrays.asList(conceptIds.split(",")));
                
            } else if (ecl != null && !ecl.equals("")) {
                
                final String branchPath = RefsetService.getBranchPath(refsetInternalId);
                conceptIdList = RefsetMemberService.getConceptIdsFromEcl(branchPath, ecl);
            } else {
                conceptIdList = RefsetService.getConceptIdsFromFile(conceptFile, fileType);
            }
         
            logger.debug("addRefsetMembers: conceptIdList: " + conceptIdList);
            
            // add the list of concepts as members to the refset
            unaddedConcepts = RefsetMemberService.addRefsetMembers(user, refsetInternalId, conceptIdList);
            
            // see if there are any concepts that were unable to be added and craft the error message
            if (unaddedConcepts.size() > 0) {
                
                error = "Unable to add concepts ";
                
                for (final String unaddedConcept : unaddedConcepts) {
                    error += unaddedConcept + ", ";
                }
                
                error = StringUtils.removeEnd(error, ", ");
            }
            
            logger.debug("addRefsetMembers: Finished with " + unaddedConcepts.size() + " invaild concepts");
            
            if (error.equals("")) {
                return "{\"status\": \"All concepts added.\"}";
            } else {
                return "{\"error\": \"" + error + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String removeRefsetMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = false) final String conceptIds, @RequestParam(required = false) final String ecl, 
        @RequestParam(required = false) final MultipartFile conceptFile,
        @RequestParam(required = false) final String fileType)
        throws Exception {
        
        try {
            
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            String conceptsToRemove = null;
            User user = SecurityService.getUserFromSession();

            logger.debug("removeRefsetMembers: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType);
            
            String error = "";
            
            // If concepts were passed in use those
            if (conceptIds != null && !conceptIds.equals("")) {
                conceptsToRemove = conceptIds;
                
            } else if (ecl != null && !ecl.equals("")) {
                
                final String branchPath = RefsetService.getBranchPath(refsetInternalId);
                conceptsToRemove = String.join(",", RefsetMemberService.getConceptIdsFromEcl(branchPath, ecl));
            } else {
                conceptsToRemove = String.join(",", RefsetService.getConceptIdsFromFile(conceptFile, fileType));
            }
         
            logger.debug("removeRefsetMembers: conceptIds: " + conceptIds);
            
            // add the list of concepts as members to the refset
            final List<String> unremovedConcepts = RefsetMemberService.removeRefsetMembers(user, refsetInternalId, conceptsToRemove);
            
            // see if there are any concepts that were unable to be added and craft the error message
            if (unremovedConcepts.size() > 0) {
                
                error = "Unable to remove concepts ";
                
                for (final String unremovedConcept : unremovedConcepts) {
                    error += unremovedConcept + ", ";
                }
                
                error = StringUtils.removeEnd(error, ", ");
            }
            
            if (error.equals("")) {
                return "{\"status\": \"All concepts removed.\"}";
            } else {
                return "{\"error\": \"" + error + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String addRefsetDefinitionExceptions(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = false) final String conceptIds, @RequestParam(required = false) final String ecl, 
        @RequestParam(required = false) final MultipartFile conceptFile,
        @RequestParam(required = false) final String fileType,
        @RequestParam(required = false) final String definitionExceptionType)
        throws Exception {
        
        try {
            
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            List<String> conceptIdList = new ArrayList<>();
            final User user = SecurityService.getUserFromSession(); 
            
            logger.debug("addRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; conceptIds: " + conceptIds + "; ecl: " + ecl + "; fileType: " + fileType + " ; definitionExceptionType: " + definitionExceptionType);
               
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
            
            final String status = RefsetService.addDefinitionException(user, refsetInternalId, inclusionEcl, definitionExceptionType);
            
            
            if (!status.startsWith("Error")) {
                return "{\"status\": \"Definition exception added.\"}";
            } else {
                return "{\"error\": \"" + status + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String removeRefsetDefinitionExceptions(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @PathVariable(value = "definitionExceptionId") final String definitionExceptionId)
        throws Exception {
        
        try {
            
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            final User user = SecurityService.getUserFromSession(); 
            logger.debug("removeRefsetDefinitionExceptions: refsetInternalId: " + refsetInternalId + "; definitionExceptionId: " + definitionExceptionId);
               
            final String status = RefsetService.removeDefinitionException(user, refsetInternalId, definitionExceptionId);
            
            if (!status.startsWith("Error")) {
                return "{\"status\": \"Definition exception removed.\"}";
            } else {
                return "{\"error\": \"" + status + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String createRefset(final @RequestBody Refset refsetParameters,
        final BindingResult bindingResult)
        throws Exception {
        
        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);
        
        String newRefsetInternalId = null;

        try {

            logger.debug("createRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));
            
            User user = SecurityService.getUserFromSession();
            String status = "";
            final Object returned = RefsetService.createRefset(user, refsetParameters);
            
            if (returned instanceof String) {
                status = (String)returned;
            } else {
                
                final Refset refset = (Refset)returned;
                newRefsetInternalId = refset.getId();
                status = refset.getRefsetId();
            }
            
            if (status.startsWith("Error")) {
                return "{\"error\": \"" + status + "\"}";
            }

            return "{\"refsetId\": \"" + status + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String modifyRefset(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final @RequestBody Refset refsetParameters,
        final BindingResult bindingResult)
        throws Exception {
        
        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            logger.debug("modifyRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
            User user = SecurityService.getUserFromSession();
            final String status = RefsetService.modifyRefset(user, refsetInternalId, refsetParameters);
            
            if (!status.startsWith("Error")) {
                return "{\"refsetInternalId\": \"" + refsetInternalId + "\"}";
            } else {
                return "{\"error\": \"" + status + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology", value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true, dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/workflowHistory", produces = "application/json")
    public @ResponseBody ResultList<WorkflowHistory> getWorkflowHistory(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            //logger.debug("getWorkflowHistory refsetInternalId: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters));
            
            User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(user, refsetInternalId);
            ResultList<WorkflowHistory> results = WorkflowService.getWorkflowHistory(refset, searchParameters);
           
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody Refset setWorkflowStatus(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam final String action, @RequestParam(required = false) final String notes)
        throws Exception {
        
        try {

            logger.debug("setWorkflowStatus: refsetInternalId: " + refsetInternalId + " ; action: " + action + " ; notes: " + notes);
            
            User user = SecurityService.getUserFromSession();
            Refset refset = RefsetService.getRefset(user, refsetInternalId);
            final String currentStatus = refset.getWorkflowStatus();
            
            // if the status is Published then create a new version of the refset that is ready to be edited
            if (currentStatus == null || currentStatus.equals(WorkflowService.PUBLISHED)) {
                
                try (final TerminologyService service = new TerminologyService()) {

                    final List<String> editionVersions = RefsetService.getBranchVersions(refset.getEditionBranch());
                    final String versionDate = DateUtility.formatDate(refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null);
                    
                    final String newRefsetInternalId = RefsetService.createNewRefsetVersion(user, refset.getId(), true);
                    refset = RefsetService.getRefset(service, user, newRefsetInternalId);
                    
                    if (editionVersions.indexOf(versionDate) > 1) {
                        refset.setUpgradeWarning(true);
                    }
                }
                
                return refset;
            }

            refset = WorkflowService.setWorkflowStatusByAction(user, action, refset, notes);
            
            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(refset.getWorkflowStatus())) {
                
                logger.debug("setWorkflowStatus: updated refset: " + ModelUtility.toJson(refset));
                return refset;
            } else {
                
                logger.debug("setWorkflowStatus: did not update workflow status.");
                return null;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }
    
    /**
     * Modify an existing refset that is in edit mode.
     *
     * @param refsetInternalId the internal refset ID
     * @return the refset internal ID or errors
     * @throws Exception the exception
     */
    @PutMapping("/refset/{refsetInternalId}/workflowNote")
    public @ResponseBody String updateWorkflowNote(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestBody(required = true) final String notes)
        throws Exception {
        
        try {

            //logger.debug("updateWorkflowNote: refsetInternalId: " + refsetInternalId + " ;notes: " + notes);
            User user = SecurityService.getUserFromSession();
            
            Refset refset = RefsetService.getRefset(user, refsetInternalId);
            final String currentStatus = refset.getWorkflowStatus();
            
            WorkflowService.updateWorkflowNote(user, refset, notes);
            
            return "true";

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String completeAllRefsetPublications(@RequestParam(required = true) final String versionDate,
        @RequestParam(required = true) final String codeSystem) throws Exception {
        
        final User user = SecurityService.getUserFromSession();
        
        if (StringUtility.isEmpty(codeSystem)) {
            throw new Exception ("A Code System must be specified.");
        }
        
        try (TerminologyService service = new TerminologyService()){
            
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            logger.debug("completeAllRefsetPublications: versionDate: " + versionDate + " ; editionShortName (codeSystem): " + codeSystem);
            
            final List<String> refsetsNotUpdated = WorkflowService.completeAllRefsetPublications(service, versionDate, codeSystem);
            String error = "";
            
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
                
                return "{\"status\": \"" + message + ".\"}";
                
            } else {
                return "{\"error\": \"" + error + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String failRefsetPublications(@RequestParam(required = true) final String refsetIds, @RequestParam(required = true) final String notes) throws Exception {
        
        final User user = SecurityService.getUserFromSession();
        
        try (TerminologyService service = new TerminologyService()){
            
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
                return "{\"status\": \"All refsets updated.\"}";
            } else {
                return "{\"error\": \"" + error + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String createNewRefsetVersion(@PathVariable(value = "refsetInternalId") final String refsetInternalId)
        throws Exception {
        
        // Check to make sure parameters were properly bound to variables.
        //checkBinding(bindingResult);

        try {

            logger.debug("createNewRefsetVersion: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();
            
            final String newRefsetInternalId = RefsetService.createNewRefsetVersion(user, refsetInternalId, true);
            
            if (newRefsetInternalId.startsWith("Error")) {
                return "{\"error\": \"" + newRefsetInternalId + "\"}";
            }
            
            return "{\"refsetInternalId\": \"" + newRefsetInternalId + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String inactiveRefset(final @PathVariable String refsetInternalId)
        throws Exception {
        
        try {

            //logger.debug("inactiveRefset: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();
            
            final String status = RefsetService.inactivateRefset(user, refsetInternalId);

            return "{\"status\": \"" + status + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String deleteRefsetEditVersion(final @PathVariable String refsetInternalId)
        throws Exception {
        
        try {

            //logger.debug("deleteRefsetEditVersion: refsetInternalId: " + refsetInternalId);
            User user = SecurityService.getUserFromSession();
            
            final String status = RefsetService.deleteInDevelopmentVersion(user, refsetInternalId, true);

            return "{\"status\": \"" + status + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }
    
    /**
     * Returns a specific project.
     *
     * @param projectId the project ID
     * @return the project
     * @throws Exception the exception
     */

    @ApiOperation(value = "Get the project for the specified ID", response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "projectId", value = "The ID of the project to return.", required = true, dataType = "string", paramType = "path")})
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/{projectId}", produces = "application/json")
    public @ResponseBody Project getProject(@PathVariable(value = "projectId") final String projectId) throws Exception {

        try {

            //logger.debug("getProject: projectId: " + projectId);
            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final Project project = RefsetService.getProject(projectId);

                //logger.debug("getProject: project: " + ModelUtility.toJson(project));

                return project;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }
    
    /**
     * Search Projects.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get project search results", response = ResultList.class, notes = API_NOTES)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/search", produces = "application/json")
    public @ResponseBody ResultList<Project> getProjects(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            logger.debug("getProjects searchParameters: " + ModelUtility.toJson(searchParameters));
            
            User user = SecurityService.getUserFromSession();
            ResultList<Project> results = RefsetService.searchProjects(user, searchParameters);

            //logger.debug("getProjects results: " + ModelUtility.toJson(results));
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology", value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true, dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/search", produces = "application/json")
    public @ResponseBody ResultList<Refset> searchDirectory(final SearchParameters searchParameters, final boolean searchConcepts, final boolean showInDevelopment,
        final BindingResult bindingResult, HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);
        
        User user = SecurityService.getUserFromSession();

        try (TerminologyService service = new TerminologyService()) {

            logger.debug("searchDirectory searchParameters: " + ModelUtility.toJson(searchParameters) + "; searchConcepts: " + searchConcepts + " ; showInDevelopment: " + showInDevelopment);
            
            ResultList<Refset> results = RefsetService.searchRefsets(user, service, searchParameters, searchConcepts, true, false);
           
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {"/refset/{refsetInternalId}/taxonomySearch", "/refset/{refsetInternalId}/conceptSearch"}, produces = "application/json")
    public @ResponseBody ConceptResultList searchConcepts(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final SearchParameters searchParameters, final BindingResult bindingResult, HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);
        
        User user = SecurityService.getUserFromSession();
        boolean searchRefsetMembers = false;
        final String uri = request.getRequestURI();
        
        if (uri.contains("taxonomySearch")) {
            searchRefsetMembers = true;
        }

        try {

            ConceptResultList results = new ConceptResultList();
            String query = searchParameters.getQuery();

            logger.debug("taxonomySearch: searchConcepts: " + refsetInternalId + " ; searchParameters: "
                    + ModelUtility.toJson(searchParameters) + " ; searchRefsetMembers: " + searchRefsetMembers);

            if (query != null && !query.equals("")) {

                results = RefsetMemberService.prepareConceptSearch(user, refsetInternalId, searchParameters, searchRefsetMembers);
            }

            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology", value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true, dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = true, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = true, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "displayType", value = "Should results be a list or taxonomy", required = true, dataType = "string", paramType = "query", defaultValue = "list"),
            @ApiImplicitParam(name = "startingConceptId", value = "For taxonomy calls the starting concept ID (exclusive - get the children of this concept not the concept itself)",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "depth", value = "For taxonomy calls the depth - how many levels of children or parents to retrieve",
                    required = false, dataType = "int", paramType = "query", defaultValue = "1"),
            @ApiImplicitParam(name = "returnChildren", value = "For taxonomy calls should children be returned. If false then parents will be returned",
                    required = false, dataType = "boolean", paramType = "query", defaultValue = "true"),
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/members", produces = "application/json")
    public @ResponseBody ConceptResultList getMembers(@PathVariable(value = "refsetInternalId") final String refsetInternalId, final SearchParameters searchParameters, 
            final String displayType, final TaxonomyParameters taxonomyParameters, final BindingResult bindingResult) throws Exception {

        checkBinding(bindingResult);

        final long start = System.currentTimeMillis();
        ConceptResultList results = new ConceptResultList();
        User user = SecurityService.getUserFromSession();

        logger.debug("getMembers: refsetInternalId: " + refsetInternalId + " ; searchParameters: + " + searchParameters + " ; taxonomyParameters: " + taxonomyParameters + " ; displayType: " + displayType);

        try {

            results = RefsetMemberService.getRefsetMembers(user, refsetInternalId, searchParameters, displayType, taxonomyParameters);
            
            results.setTimeTaken(System.currentTimeMillis() - start);
            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully populated the refset's ancestor cache"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetId",
                    value = "The ID of the refset for which ancestors are to be identified.", required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/ancestors/{refsetId}/versionDate/{versionDate}", produces = "application/json")
    public @ResponseBody String cacheMemberAncestors(
        @PathVariable(value = "refsetId") final String refsetId, 
        @PathVariable(value = "versionDate") final String versionDate
    ) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            logger.debug("cacheMemberAncestors: refsetId: " + refsetId + " ; versionDate: " + versionDate);

            String returnJson = "{\"success\": \"<RESULT>\"}";

            final boolean success = RefsetMemberService.cacheMemberAncestors(user, refsetId, versionDate);

            if (success) {
                returnJson = returnJson.replace("<RESULT>", "true");
            } else {
                returnJson = returnJson.replace("<RESULT>", "false");
            }

            //logger.debug("cacheMemberAncestors results: " + returnJson);

            return returnJson;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset to return.", required = true,
                    dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "exportType", value = "The RF2 type SNAPSHOT or DELTA.",
                    required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "languageId",
                    value = "For formats with names which language to display the name in.",
                    required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "format",
                    value = "The type of export: 'rf2', 'rf2_with_names', ' or 'sctids'.",
                    required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "fileNameDate",
                    value = "Format: yyyymmdd. Date to be embedded in the RF2 file names.",
                    required = true, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "startEffectiveTime",
                    value = "Format: yyyymmdd. Can be used to produce a delta after content is versioned by filtering a SNAPSHOT export by effectiveTime.",
                    required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "transientEffectiveTime",
                    value = "Format: yyyymmdd. Add a transient effectiveTime to rows of content which are not yet versioned.",
                    required = false, dataType = "string", paramType = "query"),
            @ApiImplicitParam(name = "exportMetadata", value = "e.g.  true or false",
                    required = true, dataType = "boolean", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/{refsetInternalId}", produces = "application/json")
    public @ResponseBody String exportRefset(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final String format, final String exportType,
        final String languageId, final String fileNameDate, String startEffectiveTime,
        final String transientEffectiveTime, final boolean exportMetadata) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            logger.debug("exportRefset: refsetInternalId: " + refsetInternalId
                    + " ; format: " + format + " ; type: " + exportType + " ; fileNameDate: "
                    + fileNameDate + " ; startEffectiveTime: " + startEffectiveTime
                    + " ; transientEffectiveTime: " + transientEffectiveTime + " ; exportMetadata: "
                    + exportMetadata);

            try (TerminologyService service = new TerminologyService()) {

                try {

                    String url = null;

                    if (format.equals("rf2") || format.equals("rf2_with_names")) {

                        boolean withNames = false;

                        if (format.equals("rf2_with_names")) {
                            withNames = true;
                        }

                        String uri = "";

                        if (exportType.contentEquals("SNAPSHOT")) {
                            uri = RefsetMemberService.exportRefsetRf2(refsetInternalId, exportType,
                                    languageId, fileNameDate, startEffectiveTime,
                                    transientEffectiveTime, exportMetadata, withNames);
                        } else {
                            uri = RefsetMemberService.exportRefsetRf2Delta(user, refsetInternalId,
                                    exportType, languageId, fileNameDate, startEffectiveTime,
                                    transientEffectiveTime, exportMetadata, withNames);
                        }
                        logger.debug("results: " + uri);
                        url = "{\"url\": \"" + uri + "\"}";

                    } else if (format.equals("sctids")) {

                        String uri = RefsetMemberService.exportRefsetSctidList(refsetInternalId,
                                exportMetadata);
                        url = "{\"url\": \"" + uri + "\"}";
                    } 

                    return url;

                } catch (final Exception e) {

                    handleException(e);
                    return null;
                }
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({@ApiImplicitParam(name = "fileName", value = "The name of the file to download.", required = true, dataType = "string", paramType = "path")})
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/export/download/{fileName}", produces = "application/json")
    public @ResponseBody ResponseEntity<Resource> downloadExport(@PathVariable(value = "fileName")
    final String fileName) throws Exception {

        try {

            logger.debug("downloadExport: fileName: " + fileName);

            User user = SecurityService.getUserFromSession();
            Path filePath = Paths.get(EXPORT_FILE_DIR + fileName);
            Resource file = new UrlResource(filePath.toUri());

            if (!file.exists() || !file.isReadable()) {
                throw new RuntimeException("Could not read the file!");
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                            HttpHeaders.CONTENT_DISPOSITION)
                    .header(HttpHeaders.CONTENT_TYPE, Files.probeContentType(filePath))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + file.getFilename() + "\"")
                    .contentLength(file.contentLength()).body(file);

            // ContentDisposition contentDisposition =
            // ContentDisposition.builder("inline").filename(fileName).build();
            //
            // File file = new File(EXPORT_FILE_DIR + fileName);
            // HttpHeaders headers = new HttpHeaders();
            // headers.add("Cache-Control", "no-cache, no-store,
            // must-revalidate");
            // headers.add("Pragma", "no-cache");
            // headers.add("Expires", "0");
            // headers.add("Content-Length", file.length() + "");
            // headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
            // HttpHeaders.CONTENT_DISPOSITION);
            // headers.add("Content-disposition", "attachment; filename=\"" +
            // fileName + "\"");
            // headers.add("Content-Type", "application/octet-stream");
            // //headers.setContentDisposition(contentDisposition);
            // Path path = Paths.get(file.getAbsolutePath());
            // ByteArrayResource resource = new
            // ByteArrayResource(Files.readAllBytes(path));
            //
            // return
            // ResponseEntity.ok().headers(headers).contentLength(file.length())
            // .contentType(MediaType.parseMediaType("application/octet-stream"))
            // .body(resource);

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "memberId", value = "The ID of the member to return.", required = true, dataType = "string", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/member/{conceptId}", produces = "application/json")
    public @ResponseBody ResultList<Map<String, String>> getMemberHistory(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @PathVariable(value = "conceptId")
        final String conceptId) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            logger.debug("getMemberHistory: memberId: " + conceptId + "; refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {
                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                RefsetService.setRefsetPermissions(user, refset);
                final List<Map<String, String>> versions = RefsetService.getSortedRefsetVersionList(refset, service, true);

                final List<Map<String, String>> memberHistory =
                        RefsetMemberService.getMemberHistory(conceptId, versions);

                logger.debug("getMemberHistory: member: " + ModelUtility.toJson(memberHistory));

                ResultList<Map<String, String>> results = new ResultList<>(memberHistory);
                results.setTotalKnown(true);

                return results;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "conceptId", value = "The ID of the concept to return.", required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "refsetInternalId", value = "The internal ID of the refset to return.", required = true, dataType = "string", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{conceptId}", produces = "application/json")
    public @ResponseBody Concept getConceptDetails(@PathVariable(value = "conceptId")
    final String conceptId, final String refsetInternalId) throws Exception {

        try {

            User user = SecurityService.getUserFromSession();
            //logger.debug("getConceptDetails: conceptId: " + conceptId + "; refsetInternalId: " + refsetInternalId);
            
            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {
                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                final Concept concept = RefsetMemberService.getConceptDetails(conceptId, refset);

                //logger.debug("getConceptDetails: concept: " + ModelUtility.toJson(concept));

                return concept;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Migrates RTT data into the database but only if the database is empty.
     *
     * @return the status of the migration
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/admin/migration/rtt", produces = "application/json")
    public @ResponseBody String migrateRttData() throws Exception {

        try {

            try (TerminologyService service = new TerminologyService()) {

                final ResultList<String> editions = service.findIds("", null, Edition.class, null);
                String message = "";

                if (editions.size() > 2) {
                    return "Database not empty, migration cancelled";
                }

                logger.info("migrateRttData Starting RTT data migration");

                HistoricDataMigrator migrator = new HistoricDataMigrator();
                migrator.migrate();

                logger.info("migrateRttData Finished RTT data migration");

                return message + "RTT data migration completed successfully";
            }

        } catch (final Exception e) {

            handleException(e);
            return "Errors occurred, check with the system administrator";
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versionStatuses", produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getVersionStatuses() throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final List<TypeKeyValue> versionStatuses = new ArrayList<>();

                for (VersionStatus value : VersionStatus.values()) {
                    TypeKeyValue typeKeyValue =
                            new TypeKeyValue("status", value.getLable(), value.getLable());
                    versionStatuses.add(typeKeyValue);
                }

                ResultList<TypeKeyValue> results = new ResultList<>(versionStatuses);
                results.setTotalKnown(true);

                return results;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versions",
            produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getVersions() throws Exception {

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

                return results;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Gets the editions.
     *
     * @return the editions
     * @throws Exception the exception
     */
    @ApiOperation(value = "Gets the editions", response = ResultList.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/editions", produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getEditions() throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Edition> results = new ResultList<Edition>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();

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
                    TypeKeyValue tkv =
                            new TypeKeyValue("edition", edition.getName(), edition.getName());
                    tkv.setId(edition.getId());
                    entryList.add(tkv);
                }
                entryResults.setItems(entryList);
                entryResults.setTotalKnown(true);

                return entryResults;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/general/refsetConcepts", produces = "application/json")
    public @ResponseBody ConceptResultList getRefsetConcepts(final String branch, final boolean areParentConcepts) throws Exception {

        try {

            //logger.debug("getRefsetConcepts: branch: " + branch + "; areParentConcepts: " + areParentConcepts);

            User user = SecurityService.getUserFromSession();
            ConceptResultList results = RefsetService.getRefsetConcepts(branch, areParentConcepts);
            
            //logger.debug("getRefsetConcepts: results: " + ModelUtility.toJson(results));

            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/general/branchVersions", produces = "application/json")
    public @ResponseBody ResultList<String> getBranchVersions(final String branch) throws Exception {

        try {

            //logger.debug("getBranchVersions - branch: " + branch);

            User user = SecurityService.getUserFromSession();
            
            final ResultList<String> results = new ResultList<>();
            results.setItems(RefsetService.getBranchVersions(branch));
            
            //logger.debug("getBranchVersions - results: " + results);
            
            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/organizations", produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getOrganizations() throws Exception {

        try {

            User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Organization> results = new ResultList<Organization>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();

                results = service.find(query, pfs, Organization.class, null);

                results.setTimeTaken(System.currentTimeMillis() - start);
                results.setTotalKnown(true);

                //logger.debug("results: " + ModelUtility.toJson(results));
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
                    TypeKeyValue tkv = new TypeKeyValue("organization", organization.getName(),
                            organization.getName());
                    tkv.setId(organization.getId());
                    entryList.add(tkv);
                }
                entryResults.setItems(entryList);
                entryResults.setTotalKnown(true);

                return entryResults;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String getRefsetAncestorCache(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            logger.debug("getRefsetAncestorCache: refsetInternalId: " + refsetInternalId);

            final Refset refset = RefsetMemberService.getRefset(SecurityService.getUserFromSession(), service, refsetInternalId);
            final Map<String, Set<String>> ancestorsCache = RefsetMemberService.getCacheForMemberAncestors(RefsetMemberService.getBranchPath(refset));
            
            if (ancestorsCache.containsKey(refsetInternalId)) {
                return ModelUtility.toJson(ancestorsCache.get(refsetInternalId));
            } else {
                return "Not Cached";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody Concept getMemberAncestorConcepts(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @PathVariable(value = "conceptId") final String conceptId) throws Exception {

        try {

            logger.debug("getMemberAncestorConcepts: refsetInternalId: " + refsetInternalId + " ; conceptId: " + conceptId);
            final User user = SecurityService.getUserFromSession();
            final Refset refset = RefsetService.getRefset(user, refsetInternalId);
            final Concept concept = new Concept();
            concept.setCode(conceptId);
            
            RefsetMemberService.getConceptAncestors(refset, Arrays.asList(concept));
            
            return concept;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String compileUpgradeData(@PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = false) final String upgradeBranch) throws Exception {
        
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
            
            return "{\"status\": \"" + status + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody ResultList<UpgradeInactiveConcecpt> getUpgradeData(@PathVariable(value = "refsetInternalId") final String refsetInternalId) throws Exception {
        
        final User user = SecurityService.getUserFromSession();
        
        try (final TerminologyService service = new TerminologyService()) {
            
            logger.debug("getUpgradeData: refsetInternalId: " + refsetInternalId);
              
            // add the list of concepts as members to the refset
            final ResultList<UpgradeInactiveConcecpt> results = RefsetMemberService.getUpgradeData(service, user, refsetInternalId);
            
            logger.debug("getUpgradeData: results " + results);
            
            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String modifyUpgradeConcept(@PathVariable(value = "refsetInternalId") final String refsetInternalId, @RequestParam(required = true) final String inactiveConceptId,
        @RequestParam(required = false) final String replacementConceptId, @RequestParam(required = true) final String changed, 
        @RequestBody(required = false) final UpgradeReplacementConcecpt manualReplacementConcept) throws Exception {
        
        final User user = SecurityService.getUserFromSession();
        
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            String status = "All changes made successfully"; 
            
            logger.debug("modifyUpgradeConcept: refsetInternalId: " + refsetInternalId + "; changed: " + changed + "; inactiveConceptId: " + inactiveConceptId 
                + "; replacementConceptId: " + replacementConceptId + "; manualReplacementConcept: " + manualReplacementConcept);

            status = RefsetMemberService.modifyUpgradeConcept(service, user, refsetInternalId, inactiveConceptId, replacementConceptId, manualReplacementConcept, changed);
            
            logger.debug("modifyUpgradeConcept: Finished with status: " + status);

            return "{\"status\": \"" + status + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {"/refset/{refsetInternalId}/replacementConceptSearch"}, produces = "application/json")
    public @ResponseBody ResultList<UpgradeReplacementConcecpt> replacementConceptSearch(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final SearchParameters searchParameters, final BindingResult bindingResult, HttpServletRequest request) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);
        
        User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = RefsetMemberService.getRefset(user, service, refsetInternalId);
            ResultList<UpgradeReplacementConcecpt> results = new ResultList<>();
            String query = searchParameters.getQuery();

            logger.debug("replacementConceptSearch: refsetInternalId: " + refsetInternalId + " ; searchParameters: " + ModelUtility.toJson(searchParameters));

            if (query != null && !query.equals("")) {
                results = RefsetMemberService.replacementConceptSearch(user, service, refset, searchParameters);
            }

            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID", required = true, dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query", value = "The term, phrase, or code to be searched, e.g. 'melanoma'", required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return", required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result", required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = {"/refset/dropdownSearch"}, produces = "application/json")
    public @ResponseBody ResultList<Refset> refsetDropdownSearch(final SearchParameters searchParameters, final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);
        
        User user = SecurityService.getUserFromSession();

        try (final TerminologyService service = new TerminologyService()) {

            ResultList<Refset> results = new ResultList<>();
            String query = searchParameters.getQuery();

            logger.debug("refsetDropdownSearch: searchParameters: " + ModelUtility.toJson(searchParameters));

            if (query != null && !query.equals("")) {
                results = RefsetService.refsetDropdownSearch(user, service, searchParameters, false, true);
            }

            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
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
    public @ResponseBody String compileComparisonData(@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId,
        @RequestParam(required = true) final String comparisonRefsetInternalId) throws Exception {
        
        final User user = SecurityService.getUserFromSession();
        
        try (final TerminologyService service = new TerminologyService()) {
       
            String status = "";
            
            logger.debug("compileUpgradeData: activeRefsetInternalId: " + activeRefsetInternalId + "; comparisonRefsetInternalId: " + comparisonRefsetInternalId);
            
            // add the list of concepts as members to the refset
            status = RefsetMemberService.compileComparisonData(service, user, activeRefsetInternalId, comparisonRefsetInternalId);
            
            logger.debug("compileUpgradeData: Finished with status " + status);
            
            return "{\"status\": \"" + status + "\"}";
    
        } catch (final Exception e) {
    
            handleException(e);
            return null;
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
    public @ResponseBody RefsetMemberComparison getComparisonData(@PathVariable(value = "activeRefsetInternalId") final String activeRefsetInternalId, final HttpServletRequest request) throws Exception {
        
        final User user = SecurityService.getUserFromSession();
        
        try (final TerminologyService service = new TerminologyService()) {
            
            logger.debug("getComparisonData: activeRefsetInternalId: " + activeRefsetInternalId);
            
            // add the list of concepts as members to the refset
            final RefsetMemberComparison results = ModelUtility.fromJson((String)request.getSession().getAttribute("refsetMemberComparison_" + activeRefsetInternalId), RefsetMemberComparison.class);
            
            logger.debug("getComparisonData: results " + results);
            
            return results;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }
}
