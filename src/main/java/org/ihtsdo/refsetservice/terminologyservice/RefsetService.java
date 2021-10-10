package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.RefsetEditParameters;
import org.ihtsdo.refsetservice.util.RefsetUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Service class to handle getting and modifying internal refset information.
 */
public class RefsetService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetService.class);
    
    /** The refset to language map. */
    private static final Map<String, String> refsetToLanguagesMap = new HashMap<>();
    
    /** The refset to language map. */
    private static final String SIMPLE_TYPE_REFERENCE_SET = "446609009";
    
    static {

        // TODO: Remove once Edition updated
        refsetToLanguagesMap.put("450828004", "es");
        refsetToLanguagesMap.put("32570271000036106", "en");
        refsetToLanguagesMap.put("900000000000509007", "en");
        refsetToLanguagesMap.put("21000172104", "fr");
        refsetToLanguagesMap.put("31000172101", "nl");
        refsetToLanguagesMap.put("554461000005103", "da");
        refsetToLanguagesMap.put("71000181105", "et");
        refsetToLanguagesMap.put("5641000179103", "es");
        refsetToLanguagesMap.put("21000220103", "en");
        refsetToLanguagesMap.put("61000202103", "no");
        refsetToLanguagesMap.put("46011000052107", "sv");
    }
    
    /**
     * Create a refset with the given parameters .
     *
     * @param user the user
     * @param refsetEditParameters the paramters for creating the refset
     * @return the new refset's internal ID
     * @throws Exception the exception
     */
    public static String createRefset(final User user, final Refset refsetEditParameters) throws Exception {
        
        String newInternalRefsetId = null;
        String refsetConceptId = refsetEditParameters.getRefsetId();
        String parentConceptId = refsetEditParameters.getParentConceptId();
        String refsetId = refsetEditParameters.getRefsetId();
        Edition edition = null;
        Project project = null;
        
        // get the edition and project for the new refset
        try (final TerminologyService service = new TerminologyService()) {

            if (refsetId != null && doesRefsetExist(refsetId, null)) {
                return "Concept Id '" + refsetId
                + "' is already used as a refset.";
            }
            
            project = service.get(refsetEditParameters.getProjectId(), Project.class);

            if (project == null) {
                throw new Exception("Project Id: " + refsetEditParameters.getProjectId()
                        + " does not exist in the RT2 database");
            }
            
            edition = project.getOrganization().getEdition();
        }
        
        // if a new refset concept needs to be created
        if (refsetConceptId == null) {
            
            // if null set the parent to "Simple Type Reference Set"
            if (parentConceptId == null) {
                parentConceptId = SIMPLE_TYPE_REFERENCE_SET;
            }
            
            final ObjectMapper mapper = new ObjectMapper();
           
            final ObjectNode descriptions = mapper.createObjectNode()
                    .set("descriptions", mapper.createArrayNode()
                            .add(mapper.createObjectNode()
                                    .put("term", refsetEditParameters.getName())
                                    .put("typeId", "900000000000013009")
                                    .put("caseSignificance", "CASE_INSENSITIVE")
                                    .put("lang", "en")
                                    .set("acceptabilityMap", mapper.createObjectNode()
                                            .put("900000000000509007", "PREFERRED")
                                            .put("900000000000508004", "PREFERRED")
                                    )
                            )
                            .add(mapper.createObjectNode()
                                    .put("term", refsetEditParameters.getName() + " (foundation metadata concept)")
                                    .put("typeId", "900000000000003001")
                                    .put("caseSignificance", "CASE_INSENSITIVE")
                                    .put("lang", "en")
                                    .set("acceptabilityMap", mapper.createObjectNode()
                                            .put("900000000000509007", "PREFERRED")
                                            .put("900000000000508004", "PREFERRED")
                                    )
                            )
                    );
            
            final ObjectNode relationships = mapper.createObjectNode()
                    .set("relationships", mapper.createArrayNode()
                            .add(mapper.createObjectNode()
                                    .put("destinationId", parentConceptId)
                                    .put("typeId", "116680003")
                                    .put("groupId", 0)
                                    .put("lang", "en")
                                    .set("acceptabilityMap", mapper.createObjectNode()
                                            .put("900000000000509007", "PREFERRED")
                                            .put("900000000000508004", "PREFERRED")
                                    )
                            )
                    );
            
            final ObjectNode body = mapper.createObjectNode();
            body.setAll(relationships);
            body.setAll(descriptions);
            
            final String url = SnowstormConnection.BASE_URL + "browser/" + edition.getBranch()
            + "/" + "concepts/";
            
            logger.debug("createRefset URL: " + url);
            logger.debug("createRefset URL body: " + body.toString());
            
            try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                            "call to url '" + url + "' wasn't successful. " + response.toString());
                }

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new Exception(Integer.toString(response.getStatus()));
                }
                
                final String resultString = response.readEntity(String.class);
                
                final JsonNode root = mapper.readTree(resultString.toString());
                JsonNode conceptNode = root;
                
                if (conceptNode.has("conceptId")) {
                    refsetConceptId = conceptNode.get("conceptId").asText();
                } else {
                    throw new Exception("Unable to create new refset concept.");
                }
            }
            
            logger.debug("Create Refset: newly created refset concept ID: " + refsetConceptId);
        }
        
        // add the new refset to the database
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);

            Refset refset = new Refset(refsetEditParameters);
            refset.setRefsetId(refsetConceptId);
            refset.setLatestVersion(true);
            refset.setVersionStatus(Refset.IN_DEVELOPMENT);
            refset.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
            refset.setProject(project);
            refset.setVersionDate(null);
            
            if (refset.getType().equals(Refset.INTENSIONAL)) {
                //refset.getDefinitionClauses().addAll(definitionList);
            }

            // Add an object
            service.add(refset);
            newInternalRefsetId = refset.getId();
            
            // Add a workflow history entry for READY_FOR_EDIT and then update the workflow to IN_EDIT
            WorkflowService.addWorkflowHistory(user, WorkflowService.CREATE, refset, "");
            refset = WorkflowService.setWorkflowStatus(user, WorkflowService.EDIT, refset, "", WorkflowService.IN_EDIT);
            
            logger.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added");
            logger.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));
        }
        
        return newInternalRefsetId;
        
    }
    
    /**
     * Inactivate a refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String inactivateRefset(final User user, final String refsetInternalId) throws Exception {
        
        String status = "inactivated";
        String refsetId = "";
        
        try (final TerminologyService service = new TerminologyService()) {
            
            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);
            
            final Refset refset = service.get(refsetInternalId, Refset.class);
            
            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }
            
            refsetId = refset.getRefsetId();
            
            // if the refset has never been versioned before then delete it
            if (!doesRefsetExist(refsetId, "AND (versionStatus: " + Refset.PUBLISHED + " OR versionStatus: " + Refset.BETA + ")")) {
                return deleteEditVersion(user, refsetInternalId, true);
            }
            
            // inactive the underlying refset concept
            inactivateRefsetConcept(refsetId, refset.getEdition().getBranch());
            
            // inactivate the refset object in the DB    
            refset.setActive(false);
            service.update(refset);
            logger.info("Inactivated refset in database: " + refsetInternalId);
        }
        
        return status;
    }
    
    /**
     * Inactivate an underlying refset concept.
     *
     * @param refsetId the refset ID
     * @param branch the branch to inactivate the concept on
     * @throws Exception the exception
     */
    private static void inactivateRefsetConcept(final String refsetId, final String branch) throws Exception {
            
        // first retrieve the concept so all fields will be present for the update
        final String getUrl = SnowstormConnection.BASE_URL + "browser/" + branch
                + "/" + "concepts/" + refsetId;
        final ObjectMapper mapper = new ObjectMapper();
        ObjectNode memberBody = null;
        
        logger.debug("inactivateRefsetConcept inactivate concept search URL: " + getUrl);
        
        try (final Response response = SnowstormConnection.getResponse(getUrl)) {
            
            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to retrieve refset concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
            }
            
            // create the body entity for the update call from the retrieved concept
            final String resultString = response.readEntity(String.class);
            memberBody = (ObjectNode) mapper.readTree(resultString.toString()).deepCopy();
        }
        
        // set active to false
        memberBody.put("active", "false");
        
        // set the inactivation indicator
        memberBody.put("inactivationIndicator", "OUTDATED");
        
        // loop thru the class axioms and set them as inactive
        final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();
        
        while (axiomIterator.hasNext()) {
            
            final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
            axiomNode.put("active", "false");
        }
        
        // loop thru the relationships and set them as inactive
        final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();
        
        while (relationshipsIterator.hasNext()) {
            
            final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
            relationshipsNode.put("active", "false");
        }
        
        final String updateUrl = SnowstormConnection.BASE_URL + "browser/" + branch
                + "/" + "concepts/" + refsetId;
        
        logger.debug("inactivateRefsetConcept inactivate URL: " + updateUrl);
        
        // update the concept with the new data
        try (final Response response = SnowstormConnection.putResponse(updateUrl, memberBody.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to inactivate refset concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
            }
            
            logger.info("Inactivated refset concept: " + refsetId);
        }
    }
    
    /**
     * Delete the edit version of a refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param deleteConcept if the underyling refset concept should be deleted
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String deleteEditVersion(final User user, final String refsetInternalId, final boolean deleteConcept) throws Exception {
        
        String status = "deleted";
        String refsetId = "";
        
        try (final TerminologyService service = new TerminologyService()) {
            
            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);
            
            boolean canDeleteConcept = deleteConcept;
            boolean otherVersions = false;
            final Refset refset = service.get(refsetInternalId, Refset.class);
            
            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
                
            } else if (!refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " is not 'In Development' and can not be removed.");
            }
            
            refsetId = refset.getRefsetId();
            
            // find out if the refset has been versioned before
            if (doesRefsetExist(refsetId, "AND (versionStatus: " + Refset.PUBLISHED + " OR versionStatus: " + Refset.BETA + ")")) {
                
                canDeleteConcept = false;
                otherVersions = true;
            }
            
            // if the concept can be deleted try to delete the underlying concept
            if (canDeleteConcept) {
                
                final String url = SnowstormConnection.BASE_URL + refset.getEdition().getBranch()
                        + "/" + "concepts/" + refsetId;
                
                logger.debug("deleteEditVersion delete URL: " + url);
                
                try (final Response response = SnowstormConnection.deleteResponse(url)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        logger.info("Unable to delete refset concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
                    } else {
                        logger.info("Deleted refset concept: " + refsetId);
                    }
                }
            }
            
            // remove any workflow history that exists
            ResultList<WorkflowHistory> workflowResults = WorkflowService.getWorkflowHistory(refset, new SearchParameters());
            
            for (final WorkflowHistory workflow : workflowResults.getItems()) {
                service.remove(workflow);
            }
            
            // remove the refset from the database
            service.remove(refset);
            logger.info("Deleted refset from database: " + refsetInternalId);
            
            // if there were other versions of this refset set the lastest version flag appropriately
            if (otherVersions) {
                
                final Refset mostRecentVersion = getLatestRefsetVersion(refsetId);
                mostRecentVersion.setLatestVersion(true);
                service.update(mostRecentVersion);
                logger.info("Refset " + mostRecentVersion.getId() + " version marked as latest.");
            }
        }
        
        return status;
    }
    
    /**
     * Does a refset ID exist in the database.
     *
     * @param refsetId the refset ID
     * @param addedQueryParameters additional query string to limit the refset versions
     * @return does the refset exist (true/fase)
     * @throws Exception the exception
     */
    public static boolean doesRefsetExist(final String refsetId, final String addedQueryParameters) throws Exception {
    
        String expandedQuery = "";
        
        try (final TerminologyService service = new TerminologyService()) {
            
            if (addedQueryParameters != null) {
                expandedQuery = addedQueryParameters;
            }
            
            // find out if the refset exists
            final ResultList<Refset> results = service.find("refsetId: " + refsetId + " " + expandedQuery, null, Refset.class, null);
            
            if (results.getItems().isEmpty()) {
                return false;
            } else {
                return true;
            }
        }
    }
    
    /**
     * Gets the list of Refset Concepts that can be used as parents to a refset or as the underlying concept for a new refset.
     *
     * @param branch the branch to retrieve the concepts from
     * @param areParentConcepts Do these concepts represent parent concepts for a new refset, or will they be the underlying concepts for a the refset itself
     * @return the list of refset concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetConcepts(final String branch, final boolean areParentConcepts) throws Exception {
    
        final ConceptResultList results = new ConceptResultList();
        final Set<String> existingRefsetIds = new HashSet<>();
        final String ecl = StringUtility.encodeValue(QueryParserBase.escape("<<" + SIMPLE_TYPE_REFERENCE_SET));
        final String url = SnowstormConnection.BASE_URL + branch + "/" + "concepts?ecl=" + ecl + "&limit=1000";
        
        logger.debug("getRefsetConcepts URL: " + url);
        
        if (!areParentConcepts) {
            
            try (final TerminologyService service = new TerminologyService()) {
                
                // get all the existing refsets for latest branch version
                final ResultList<Refset> refsets = service.find("active: true AND editionBranch: " + branch + " AND (latestVersion: true OR versionStatus: \"" + Refset.IN_DEVELOPMENT + "\")", null, Refset.class, null);
                
                for (final Refset refset : refsets.getItems()) {
                    existingRefsetIds.add(refset.getRefsetId());
                }
                
                logger.debug("getRefsetConcepts existingRefsetIds: " + existingRefsetIds);
            }
        }
        
        // update the concept with the new data
        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to get refset concepts. Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
            }
            
            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.get("items").iterator();
            
            // loop thru the returned member details and inactivate it or add it to the list to delete    
            while (iterator != null && iterator.hasNext()) {
                
                final JsonNode conceptNode = iterator.next();
                final Concept concept = new Concept();
                final String conceptId = conceptNode.get("conceptId").asText();
                
                // if this isn't for a parent concept and the refset already exists then skip it 
                if (!areParentConcepts && existingRefsetIds.contains(conceptId)) {
                    continue;                    
                }
                
                concept.setCode(conceptId);
                concept.setName(conceptNode.get("pt").get("term").asText());
                concept.setTerminology("SNOMEDCT");
                
                results.getItems().add(concept);
            }
            
            // sort the results
            Collections.sort(results.getItems(), (o1, o2) -> (o1.getName().compareTo(o2.getName())));
        }
        
        return results;
    }
    
    /**
     * Gets the list of version dates for a branch.
     *
     * @param branch the branch to retrieve the concepts from
     * @return the list of branch versions
     * @throws Exception the exception
     */
    public static ResultList<String> getBranchVersions(final String branch) throws Exception {
    
        final ResultList<String> results = new ResultList<>();
        final String url = SnowstormConnection.BASE_URL + "branches/" + branch + "/" + "children?limit=500&immediateChildren=true";
        
        logger.debug("getBranchVersions URL: " + url);
        
        // get the versions from snowstorm
        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to get branch versions. Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
            }
            
            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.iterator();
               
            while (iterator != null && iterator.hasNext()) {
                
                final JsonNode node = iterator.next();
                
                if (node.get("deleted").asBoolean()) {
                    continue;
                }
                
                String path = node.get("path").asText();
                path = path.replace(branch + "/", "");
                
                // if this path isn't in date format then skip it 
                if (!path.matches("\\d{4}-\\d{2}-\\d{2}")) {
                    continue;                    
                }
                
                results.getItems().add(path);
            }
            
            // sort the results
            Collections.sort(results.getItems(), (o1, o2) -> (o2.compareTo(o1)));
        }
        
        return results;
    }
    
    /**
     * Returns a specific project.
     *
     * @param projectId the project ID
     * @return the project
     * @throws Exception the exception
     */
    public static Project getProject(final String projectId) throws Exception {
        
        try (TerminologyService service = new TerminologyService()) {

            final Project project = service.findSingle(
                    "id:" + QueryParserBase.escape(projectId) + "", Project.class, null);

            if (project == null) {
                throw new Exception("Unable to retrieve project " + projectId);
            }

            return project;
        }
    }
    
    /**
     * Returns a specific refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @return the refset
     * @throws Exception the exception
     */
    public static Refset getRefset(final User user, final String refsetInternalId) throws Exception {
        
        try (TerminologyService service = new TerminologyService()) {

            Refset refset = service.findSingle(
                    "id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

            if (refset == null) {
                throw new Exception("Unable to retrieve refset " + refsetInternalId);
            }

            refset = getRefsetDescriptions(refset);
            refset = setRefsetPermissions(user, refset);
            refset.setVersionList(
                    RefsetUtility.getSortedRefsetVersionList(refset.getRefsetId(), service));

            logger.debug("*********** getRefset: refset: " + ModelUtility.toJson(refset));
            return refset;
        }
    }
    
    /**
     * Searches for refset with filters and member concept search.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @param searchConcepts should refset members be searched
     * @return the list of found refsets
     * @throws Exception the exception
     */
    public static ResultList<Refset> searchRefsets(final User user, final SearchParameters searchParameters, final boolean searchConcepts) throws Exception {
        
        try (TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            ResultList<Refset> results = new ResultList<Refset>();
            String query = searchParameters.getQuery();

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
                    
                    String memberRefsetQuery = "";
                    
                    // if it was requested search member concepts
                    if (searchConcepts) {
                        memberRefsetQuery = RefsetMemberService.searchDirectoryMembers(searchParameters);
                    }
                    
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

                refset = setRefsetPermissions(user, refset);
                refset.setVersionList(
                        RefsetUtility.getSortedRefsetVersionList(refset.getRefsetId(), service));
            }

            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            return results;
        }
    }
    
    /**
     * Modify a refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @return the updated refset
     * @throws Exception the exception
     */
    public static String modifyRefset(final User user, final String refsetInternalId, final Refset refsetEditParameters) throws Exception {
       
        try (TerminologyService service = new TerminologyService()) {

            Refset refset = getRefset(user, refsetInternalId);
            
            if (!refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {
                throw new Exception("Refset is not in the proper status to be modified " + refsetInternalId);
            }

            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);
            
            // set user changed fields
            refset.setTags(refsetEditParameters.getTags());
            refset.setVersionNotes(refsetEditParameters.getVersionNotes());
            refset.setNarrative(refsetEditParameters.getNarrative());
            refset.setExternalUrl(refsetEditParameters.getExternalUrl()); 
            refset.setDefinitionClauses(refsetEditParameters.getDefinitionClauses()); 
            
            // update an object
            service.update(refset);

            logger.info("Refset " + refset.getRefsetId() + " successfully modified");
            logger.debug("Modify Refset: Refset: " + ModelUtility.toJson(refset));
            return refsetInternalId;
        }
    }
    
    /**
     * Create a new version of a refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to base the new version on
     * @return the new internal refset ID
     * @throws Exception the exception
     */
    public static String createNewRefsetVersion(final User user, final String refsetInternalId) throws Exception {
        
        Refset newRefsetVersion = new Refset();
        String newInternalRefsetId = "";
        
        try (TerminologyService service = new TerminologyService()) {

            Refset oldLatestVersionRefset = null;
            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);
            
            Refset refset = getRefset(user, refsetInternalId);
            
            newRefsetVersion.populateFrom(refset);

            // set automatic changed fields
            newRefsetVersion.setVersionDate(null);
            newRefsetVersion.setLatestVersion(true);
            newRefsetVersion.setId(null);
            newRefsetVersion.setVersionStatus(Refset.IN_DEVELOPMENT);
            newRefsetVersion.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
            
            // find the previous latest version
            if (refset.isLatestVersion()) {
                oldLatestVersionRefset = refset;

            } else {
                oldLatestVersionRefset = service.findSingle(
                        "refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + " AND latestVersion: true", Refset.class, null);
            }
            
            // Add an object
            service.add(newRefsetVersion);
            newInternalRefsetId = newRefsetVersion.getId();
            
            // Add a workflow history entry for READY_FOR_EDIT and then update the workflow to IN_EDIT
            WorkflowService.addWorkflowHistory(user, WorkflowService.CREATE, newRefsetVersion, "");
            newRefsetVersion = WorkflowService.setWorkflowStatus(user, WorkflowService.EDIT, newRefsetVersion, "", WorkflowService.IN_EDIT);
            
            // update the previous latest version so it no longer is marked as latest
            if (oldLatestVersionRefset != null) {
                
                // update an object
                oldLatestVersionRefset.setLatestVersion(false);
                service.update(oldLatestVersionRefset);
                logger.info("Refset " + oldLatestVersionRefset.getId() + " version marked as not latest.");
            }
            
            logger.info("Refset " + newRefsetVersion.getRefsetId() + " version ID '" + newInternalRefsetId + "' successfully added");
            logger.debug("createNewRefsetVersion: Refset: " + ModelUtility.toJson(newRefsetVersion));
            
            return newInternalRefsetId;
        }
    }
    
    /**
     * Get the latest version of a refset without using the latest version flag.
     *
     * @param refsetId the refset ID
     * @return the refset version
     * @throws Exception the exception
     */
    public static Refset getLatestRefsetVersion(final String refsetId) throws Exception {
        
        Refset refsetLatestVersion = null;
        
        try (TerminologyService service = new TerminologyService()) {      
            
            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            final ResultList<Refset> results = service
                    .find("refsetId: " + QueryParserBase.escape(refsetId), pfs, Refset.class, null);
            
            // see if there is an "In Development" version as that should be the latest.
            for (final Refset result: results.getItems()) {
                
                if (result.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {
                    
                    refsetLatestVersion = result;
                    break;
                }
            }
            
            if (refsetLatestVersion == null && results.getItems().size() > 0) {
                refsetLatestVersion = results.getItems().get(0);
            }
            
            return refsetLatestVersion;
        }
    }
    
    /**
     * Search Projects.
     *
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Project> searchProjects(final SearchParameters searchParameters) throws Exception {
        
        try (TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            ResultList<Project> results = new ResultList<Project>();
            String query = searchParameters.getQuery();

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
                query = IndexUtility.addWildcardsToQuery(query, Refset.class);
            }

            results = service.find(query, pfs, Project.class, null);
            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            return results;
        }
    }
    
    /**
     * Get the branch and version path for a refset.
     *
     * @param refset the refset
     * @return the branch and version path
     * @throws Exception the exception
     */
    public static String getBranchPath(final Refset refset) throws Exception {

        String branchPath = "";
        String pathDate = "";

        if (refset.getVersionDate() != null) {
            Date tmpDate = refset.getVersionDate();
            pathDate = "/" + DateUtility.formatDate(tmpDate, DateUtility.DATE_FORMAT_REVERSE, null);
        }

        branchPath = refset.getEdition().getBranch() + pathDate;

        return branchPath;
    }
    
    /**
     * Get all the descriptions for a refset.
     *
     * @param refset the refset
     * @return the refset with descriptions
     * @throws Exception the exception
     */
    public static Refset getRefsetDescriptions(final Refset refset) throws Exception {
        
        final List<Concept> refsetConceptList = new ArrayList<>();
        final Concept refsetConcept = new Concept();
        refsetConcept.setCode(refset.getRefsetId());
        refsetConcept.setName(refset.getName());
        refsetConceptList.add(refsetConcept);
        
        RefsetMemberService.populateAllLanguageDescriptions(refset, refsetConceptList);
        
        refset.setDescriptions(refsetConceptList.get(0).getDescriptions());
        
        return refset;
    }
    
    /**
     * Get the branch and version path for a refset from the internal refset ID.
     *
     * @param refsetInternalId the internal ID of the refset
     * @return the branch and version path
     * @throws Exception the exception
     */
    public static String getBranchPath(final String refsetInternalId) throws Exception {

        final Refset refset = getRefsetFromInternalId(refsetInternalId);
        return getBranchPath(refset);
    }
    
    /**
     * Get a refset from the internal refset ID.
     *
     * @param refsetInternalId the internal ID of the refset
     * @return the refset
     * @throws Exception the exception
     */
    public static Refset getRefsetFromInternalId(final String refsetInternalId) throws Exception {
        
        Refset refset = null;
        
        try (final TerminologyService service = new TerminologyService()) {
            
            refset = service.get(refsetInternalId, Refset.class);
            
            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }
        }
        
        return refset;
    }
    
    /**
     * Set the user permissions for a refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the refset with permissions
     * @throws Exception the exception
     */
    public static Refset setRefsetPermissions(final User user, final Refset refset) throws Exception {
        
        refset.setDownloadable(true);
        refset.setFeedbackVisible(true);
        refset.setCanView(true);
        
        // Edit permissions
        if (refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {
            
            // set the assigned user for the refset if it is being edited or reviewed
            if (Arrays.asList(WorkflowService.IN_EDIT, WorkflowService.IN_REVIEW).contains(refset.getWorkflowStatus())) {
                refset.setAssignedUser(WorkflowService.getAssignedUserName(refset));
            }
                
            final List<String> allowedStatuses = WorkflowService.getAllowedStatuses(user, refset);
            
            if (allowedStatuses.contains(WorkflowService.IN_EDIT)) {
                refset.setCanEdit(true);
            } else {
                refset.setCanEdit(false);
            }
            
            if (allowedStatuses.contains(WorkflowService.IN_REVIEW)) {
                refset.setCanReview(true);
            } else {
                refset.setCanReview(false);
            }
            
            if (allowedStatuses.contains(WorkflowService.READY_FOR_PUBLICATION)) {
                refset.setCanPublish(true);
            } else {
                refset.setCanPublish(false);
            }
            
            refset.setAvailableActions(WorkflowService.getAllowedActions(user, refset));
        }
       
        return refset;
    }
}
