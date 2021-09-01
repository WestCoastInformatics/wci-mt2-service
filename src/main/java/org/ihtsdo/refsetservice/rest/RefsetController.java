
package org.ihtsdo.refsetservice.rest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.HistoricDataMigrator;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.RefsetUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
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

    /** Static initialization. */
    static {
        EXPORT_FILE_DIR = PropertyUtility.getProperty("export.fileDir") + "/";
    }

    /**
     * Returns the refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the concept
     * @throws Exception the exception
     */

    @ApiOperation(value = "Get the refset for the specified ID", response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset to return.", required = true,
                    dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}",
            produces = "application/json")
    public @ResponseBody Refset getRefset(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId) throws Exception {

        try {

            logger.info("*********** getRefset: refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {
                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                refset.setDownloadable(true);
                refset.setFeedbackVisible(true);
                refset.setVersionList(
                        RefsetUtility.getSortedRefsetVersionList(refset.getRefsetId(), service));

                logger.info("*********** getRefset: refset: " + ModelUtility.toJson(refset));

                return refset;
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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

            logger.info("*********** updateActive: active: " + active + " ; refsetId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "refsetId:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);
                refset.setActive(active);
                service.setModifiedBy("restApi");
                service.update(refset);

                logger.info("*********** updateActive: refset: " + ModelUtility.toJson(refset));

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
            
            List<String> conceptIdList = new ArrayList<>();
            String error = "";
            
            logger.debug("*********** addRefsetMembers: refsetInternalId: " + refsetInternalId);
            
            // create the list of concepts based on what was passed in
            if (conceptIds != null && !conceptIds.equals("")) {
                conceptIdList = Arrays.asList(conceptIds.split(","));
                
            } else if (ecl != null && !ecl.equals("")) {
                
                final String branchPath = RefsetService.getBranchPath(refsetInternalId);
                conceptIdList = RefsetMemberService.getConceptIdsFromEcl(branchPath, ecl);
            } else {
                conceptIdList = RefsetUtility.getConceptIdsFromFile(conceptFile, fileType);
            }
         
            logger.debug("*********** addRefsetMembers: conceptIds: " + conceptIdList);
            
            // add the list of concepts as members to the refset
            final List<String> unaddedConcepts = RefsetMemberService.addRefsetMembers(refsetInternalId, conceptIdList);
            
            // see if there are any concepts that were unable to be added and craft the error message
            if (unaddedConcepts.size() > 0) {
                
                error = "Unable to add concepts ";
                
                for (final String unaddedConcept : unaddedConcepts) {
                    error += unaddedConcept + ", ";
                }
                
                error = StringUtils.removeEnd(error, ", ");
            }
            
            if (error.equals("")) {
                return "{\"status\": \"All concepts added.\"}";
            } else {
                return "{\"error\": \"" + error + "\"}";
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
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
            
            String conceptsToRemove = null;

            logger.debug("*********** removeRefsetMembers: refsetInternalId: " + refsetInternalId);
            
            String error = "";
            
            // If concepts were passed in use those
            if (conceptIds != null && !conceptIds.equals("")) {
                conceptsToRemove = conceptIds;
                
            } else if (ecl != null && !ecl.equals("")) {
                
                final String branchPath = RefsetService.getBranchPath(refsetInternalId);
                conceptsToRemove = String.join(",", RefsetMemberService.getConceptIdsFromEcl(branchPath, ecl));
            } else {
                conceptsToRemove = String.join(",", RefsetUtility.getConceptIdsFromFile(conceptFile, fileType));
            }
         
            logger.debug("*********** removeRefsetMembers: conceptIds: " + conceptIds);
            
            // add the list of concepts as members to the refset
            final List<String> unremovedConcepts = RefsetMemberService.removeRefsetMembers(refsetInternalId, conceptsToRemove);
            
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
    }
    
    /**
     * Create a new refset.
     *
     * @param active the active status
     * @param refsetId The refset ID
     * @return the new internal refset ID
     * @throws Exception the exception
     */
    @PostMapping("/refset")
    public @ResponseBody String createRefset(final @RequestBody Refset refsetParameters,
        final BindingResult bindingResult)
        throws Exception {
        
        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            logger.info("*********** createRefset: refsetParameters: " + ModelUtility.toJson(refsetParameters));
            
            final String refsetInternalId = RefsetService.createRefset(refsetParameters);
            
            if (refsetInternalId.startsWith("Concept Id")) {
                return "{\"error\": \"" + refsetInternalId + "\"}";
            }

            return "{\"refsetInternalId\": \"" + refsetInternalId + "\"}";

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }
    
    /**
     * Delete or inactivate a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */ 
    @DeleteMapping("/refset/{refsetInternalId}")
    public @ResponseBody String deleteRefset(final @PathVariable String refsetInternalId)
        throws Exception {
        
        try {

            logger.info("*********** deleteRefset: refsetInternalId: " + refsetInternalId);
            
            final String status = RefsetService.deleteRefset(refsetInternalId);

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
    @ApiImplicitParams({
            @ApiImplicitParam(name = "projectId",
                    value = "The ID of the project to return.", required = true,
                    dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/{projectId}",
            produces = "application/json")
    public @ResponseBody Project getProject(@PathVariable(value = "projectId")
    final String projectId) throws Exception {

        try {

            logger.debug("*********** getProject: projectId: " + projectId);

            try (TerminologyService service = new TerminologyService()) {

                final Project project = RefsetService.getProject(projectId);

                logger.debug("*********** getProject: project: " + ModelUtility.toJson(project));

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
    @ApiOperation(value = "Get project search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/project/search",
            produces = "application/json")
    public @ResponseBody ResultList<Project> getProjects(final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            logger.debug("******** getProjects searchParameters: "
                    + ModelUtility.toJson(searchParameters));
            
            ResultList<Project> results = RefsetService.searchProjects(searchParameters);

            logger.debug("******** getProjects results: " + ModelUtility.toJson(results));
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Search.
     *
     * @param searchParameters the search parameters
     * @param bindingResult the binding result
     * @return the string
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get refset search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology",
                    value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true,
                    dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/search",
            produces = "application/json")
    public @ResponseBody ResultList<Refset> searchDirectory(final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try (TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            ResultList<Refset> results = new ResultList<Refset>();
            String query = searchParameters.getQuery();

            logger.debug("******** searchDirectory searchParameters: "
                    + ModelUtility.toJson(searchParameters));

            final PfsParameter pfs = new PfsParameter();

            if (searchParameters.getOffset() != null) {
                pfs.setOffset(searchParameters.getOffset());
            }

            if (searchParameters.getLimit() != null) {
                pfs.setLimit(searchParameters.getLimit());
            }

            if (searchParameters.getSortAscending() != null) {
                pfs.setAscending(searchParameters.getSortAscending());
            }

            if (searchParameters.getSort() != null) {
                pfs.setSort(searchParameters.getSort());
            }

            if (query != null && !query.equals("")) {

                final List<String> directoryColumns = Arrays.asList("id", "refsetId", "name", "editionName",
                        "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
                String[] queryParts = query.split(" AND ");
                String filterQuery = "";
                String termQuery = "";

                for (final String queryPart : queryParts) {

                    String[] keyValue = queryPart.split(":");

                    if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {
                        filterQuery += queryPart + " AND ";
                    } else {
                        termQuery += queryPart + "* AND ";
                    }
                }

                // if the term query isn't empty then search members and build the full term query string
                if (!termQuery.equals("")) {
                    
                    termQuery = StringUtils.removeEnd(termQuery, " AND ");
                    
                    String memberRefsetQuery =
                            RefsetMemberService.searchDirectoryMembers(searchParameters);
                    
                    if (!memberRefsetQuery.equals("")) {
                        termQuery = "((" + termQuery + ") OR " + memberRefsetQuery + ")";
                    } else {
                        termQuery = "(" + termQuery + ")";
                    }
                }
                
                // if the filter query isn't empty then prepare the query with wildcards
                if (!filterQuery.equals("")) {
                    
                    filterQuery = "(" + StringUtils.removeEnd(filterQuery, " AND ") + ")";
                    filterQuery = IndexUtility.addWildcardsToQuery(filterQuery, Refset.class);
                    
                    // if the term query isn't empty then append an 'AND' to the filter query
                    if (!termQuery.equals("")) {
                        filterQuery += " AND ";
                    }
                }
                
                query = filterQuery + termQuery;
            }

            if (query != null && !query.equals("")) {
                query += " AND latestVersion: true";
            } else {
                query = "latestVersion: true";
            }

            results = service.find(query, pfs, Refset.class, null);

            for (Refset refset : results.getItems()) {

                refset.setDownloadable(true);
                refset.setFeedbackVisible(false);
                refset.setVersionList(
                        RefsetUtility.getSortedRefsetVersionList(refset.getRefsetId(), service));
            }

            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            logger.debug("******** searchDirectory results: " + ModelUtility.toJson(results));
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
    @ApiOperation(value = "Search the taxonomy for refset members", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId", value = "the internal refset ID",
                    required = true, dataType = "string", paramType = "query",
                    defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = false, dataType = "int", paramType = "query", defaultValue = "0")
            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/taxonomySearch",
            produces = "application/json")
    public @ResponseBody ConceptResultList searchTaxonomy(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final SearchParameters searchParameters,
        final BindingResult bindingResult) throws Exception {

        // Check to make sure parameters were properly bound to variables.
        checkBinding(bindingResult);

        try {

            ConceptResultList results = new ConceptResultList();
            String query = searchParameters.getQuery();

            logger.info("*********** taxonomySearch: refsetInternalId: " + refsetInternalId);
            logger.debug("******** taxonomySearch: searchParameters: "
                    + ModelUtility.toJson(searchParameters));

            if (query != null && !query.equals("")) {

                results = RefsetMemberService.searchTaxonomyMembers(refsetInternalId,
                        searchParameters);
            }

            logger.debug("******** taxonomySearch: results: " + ModelUtility.toJson(results));
            return results;

        } catch (final ResponseStatusException rse) {
            throw rse;

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
    @ApiOperation(value = "Get refset search results", response = ResultList.class,
            notes = "Use cases for search range from very simple term searches, use of paging "
                    + "parameters, additional filters, searches properties, and so on.")
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved the requested information"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "terminology",
                    value = "Terminologies to search, e.g. 'SNOMEDCT_US'", required = true,
                    dataType = "string", paramType = "query", defaultValue = "ncit"),
            @ApiImplicitParam(name = "query",
                    value = "The term, phrase, or code to be searched, e.g. 'melanoma'",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "limit", value = "The max number of results to return",
                    required = true, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "offset", value = "The offset for the first result",
                    required = true, dataType = "int", paramType = "query", defaultValue = "0"),
            @ApiImplicitParam(name = "displayType", value = "Should results be a list or taxonomy",
                    required = true, dataType = "string", paramType = "query",
                    defaultValue = "list"),
            @ApiImplicitParam(name = "startingConceptId",
                    value = "For taxonomy calls the starting concept ID (exclusive - get the children of this concept not the concept itself)",
                    required = false, dataType = "string", paramType = "query", defaultValue = ""),
            @ApiImplicitParam(name = "depth",
                    value = "For taxonomy calls the depth - how many levels of children or parents to retrieve",
                    required = false, dataType = "int", paramType = "query", defaultValue = "1"),
            @ApiImplicitParam(name = "returnChildren",
                    value = "For taxonomy calls should children be returned. If false then parents will be returned",
                    required = false, dataType = "boolean", paramType = "query",
                    defaultValue = "true"),

            // TODO: activeOnly, sort, sortAscending
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/members",
            produces = "application/json")
    public @ResponseBody ConceptResultList getMembers(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final SearchParameters searchParameters,
        final String displayType, final TaxonomyParameters taxonomyParameters,
        final BindingResult bindingResult) throws Exception {

        // Check whether or not parameter binding was successful
        if (bindingResult.hasErrors()) {

            final List<FieldError> errors = bindingResult.getFieldErrors();
            final List<String> errorMessages = new ArrayList<>();

            for (final FieldError error : errors) {

                final String errorMessage = "ERROR " + bindingResult.getObjectName() + " = "
                        + error.getField() + ", " + error.getCode();
                logger.error(errorMessage);
                errorMessages.add(errorMessage);
            }

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    String.join("\n ", errorMessages));
        }

        final long start = System.currentTimeMillis();
        ConceptResultList results = new ConceptResultList();

        logger.info("*********** getMembers: refsetInternalId: " + refsetInternalId);

        try {

            results = RefsetMemberService.getRefsetMembers(refsetInternalId, searchParameters,
                    displayType, taxonomyParameters);
            logger.debug("******** getMembers results: " + ModelUtility.toJson(results));
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
     * @param refsetInternalId the internal refset id
     * @return the success/failure
     * @throws Exception the exception
     */
    @ApiOperation(value = "Cache the ancestors of the refset members for the specified refset ID",
            response = Refset.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200,
                    message = "Successfully populated the refset's ancestor cache"),
            @ApiResponse(code = 400, message = "Bad request"),
            @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset for which ancestors are to be identified.",
                    required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/ancestors/{refsetInternalId}",
            produces = "application/json")
    public @ResponseBody String cacheMemberAncestors(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId) throws Exception {

        try {

            logger.info("*********** cacheMemberAncestors: refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                try {

                    String returnJson = "{\"success\": \"<RESULT>\"}";

                    final boolean success =
                            RefsetMemberService.cacheMemberAncestors(refsetInternalId);

                    if (success) {
                        returnJson = returnJson.replace("<RESULT>", "true");
                    } else {
                        returnJson = returnJson.replace("<RESULT>", "false");
                    }

                    logger.debug("******** cacheMemberAncestors results: " + returnJson);

                    return returnJson;

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
    @RequestMapping(method = RequestMethod.GET, value = "/export/{refsetInternalId}",
            produces = "application/json")
    public @ResponseBody String exportRefset(@PathVariable(value = "refsetInternalId")
    final String refsetInternalId, final String format, final String exportType,
        final String languageId, final String fileNameDate, String startEffectiveTime,
        final String transientEffectiveTime, final boolean exportMetadata) throws Exception {

        try {

            logger.info("*********** exportRefset: refsetInternalId: " + refsetInternalId
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
                            uri = RefsetMemberService.exportDeltaRefsetRf2(refsetInternalId,
                                    exportType, languageId, fileNameDate, startEffectiveTime,
                                    transientEffectiveTime, exportMetadata, withNames);
                        }
                        logger.debug("******** results: " + uri);
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
    @ApiImplicitParams({
            @ApiImplicitParam(name = "fileName", value = "The name of the file to download.",
                    required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @CrossOrigin(origins = "http://localhost:4200")
    @RequestMapping(method = RequestMethod.GET, value = "/export/download/{fileName}",
            produces = "application/json")
    public @ResponseBody ResponseEntity<Resource> downloadExport(@PathVariable(value = "fileName")
    final String fileName) throws Exception {

        try {

            logger.info("****** downloadExport: fileName: " + fileName);

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
     * Gets the concept details.
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
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset to return.", required = true,
                    dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "memberId", value = "The ID of the member to return.",
                    required = true, dataType = "string", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET,
            value = "/refset/{refsetInternalId}/member/{conceptId}", produces = "application/json")
    public @ResponseBody ResultList<Map<String, String>> getMemberHistory(
        @PathVariable(value = "refsetInternalId")
        final String refsetInternalId, @PathVariable(value = "conceptId")
        final String conceptId) throws Exception {

        try {

            logger.info("*********** getMemberHistory: memberId: " + conceptId
                    + "; refsetInternalId: " + refsetInternalId);

            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {
                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                final List<Map<String, String>> versions =
                        RefsetUtility.getSortedRefsetVersionList(refset.getRefsetId(), service);

                final List<Map<String, String>> memberHistory =
                        RefsetMemberService.getMemberHistory(conceptId, versions);

                logger.info("*********** getMemberHistory: member: "
                        + ModelUtility.toJson(memberHistory));

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
            @ApiImplicitParam(name = "conceptId", value = "The ID of the concept to return.",
                    required = true, dataType = "string", paramType = "path"),
            @ApiImplicitParam(name = "refsetInternalId",
                    value = "The internal ID of the refset to return.", required = true,
                    dataType = "string", paramType = "query"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/concept/{conceptId}",
            produces = "application/json")
    public @ResponseBody Concept getConceptDetails(@PathVariable(value = "conceptId")
    final String conceptId, final String refsetInternalId) throws Exception {

        try {

            logger.info("*********** getConceptDetails: conceptId: " + conceptId
                    + "; refsetInternalId: " + refsetInternalId);
            try (TerminologyService service = new TerminologyService()) {

                final Refset refset = service.findSingle(
                        "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

                if (refset == null) {
                    throw new Exception("Unable to retrieve refset " + refsetInternalId);
                }

                final Concept concept = RefsetMemberService.getConceptDetails(conceptId, refset);

                logger.info(
                        "*********** getConceptDetails: concept: " + ModelUtility.toJson(concept));

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
    @RequestMapping(method = RequestMethod.GET, value = "/admin/migration/rtt",
            produces = "application/json")
    public @ResponseBody String migrateRttData(@RequestParam(required = false)
    final String force) throws Exception {

        try {

            try (TerminologyService service = new TerminologyService()) {

                final ResultList<String> editions = service.findIds("", null, Edition.class, null);
                String message = "";

                if (editions.size() > 2) {

                    if (force == null || !force.equals("true")) {
                        return "Database not empty, migration cancelled";
                    } else {

                        message =
                                "RTT data migration: Database not empty, migration WOULD NORMALLY BE cancelled. ";
                        logger.info(message);
                    }
                }

                logger.info("*********** Starting RTT data migration");

                HistoricDataMigrator migrator = new HistoricDataMigrator();
                migrator.migrate();

                logger.info("*********** Finished RTT data migration");

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
    @RequestMapping(method = RequestMethod.GET, value = "/refset/versionStatuses",
            produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getVersionStatuses() throws Exception {

        try {

            logger.info("*********** getVersionStatuses ");

            try (TerminologyService service = new TerminologyService()) {

                final List<TypeKeyValue> versionStatuses = new ArrayList<>();

                for (VersionStatus value : VersionStatus.values()) {
                    TypeKeyValue typeKeyValue =
                            new TypeKeyValue("status", value.toString(), value.toString());
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

            logger.info("*********** getVersions ");

            try (TerminologyService service = new TerminologyService()) {

                ResultList<Refset> refsets = new ResultList<Refset>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();

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
    @RequestMapping(method = RequestMethod.GET, value = "/refset/editions",
            produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getEditions() throws Exception {

        try {

            logger.info("*********** getEditions ");

            try (TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Edition> results = new ResultList<Edition>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();

                results = service.find(query, pfs, Edition.class, null);

                results.setTimeTaken(System.currentTimeMillis() - start);
                results.setTotalKnown(true);

                logger.debug("******** results: " + ModelUtility.toJson(results));
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
    @RequestMapping(method = RequestMethod.GET, value = "/general/refsetConcepts",
            produces = "application/json")
    public @ResponseBody ConceptResultList getRefsetConcepts(final String branch, final boolean areParentConcepts) throws Exception {

        try {

            logger.debug("*********** getRefsetConcepts: branch: " + branch + "; areParentConcepts: " + areParentConcepts);

            ConceptResultList results = RefsetService.getRefsetConcepts(branch, areParentConcepts);
            
            logger.debug("*********** getRefsetConcepts: results: " + ModelUtility.toJson(results));

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
    @RequestMapping(method = RequestMethod.GET, value = "/refset/organizations",
            produces = "application/json")
    public @ResponseBody ResultList<TypeKeyValue> getOrganizations() throws Exception {

        try {

            logger.info("*********** getOrganizations ");

            try (TerminologyService service = new TerminologyService()) {

                final long start = System.currentTimeMillis();
                ResultList<Organization> results = new ResultList<Organization>();
                final PfsParameter pfs = new PfsParameter();
                final QueryParameter query = new QueryParameter();

                results = service.find(query, pfs, Organization.class, null);

                results.setTimeTaken(System.currentTimeMillis() - start);
                results.setTotalKnown(true);

                logger.debug("******** results: " + ModelUtility.toJson(results));
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

}
