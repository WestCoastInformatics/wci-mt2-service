package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.util.ArrayList;
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

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.RefsetEditParameters;
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
     * @param refsetEditParameters the paramters for creating the refset
     * @return the new refset's internal ID
     * @throws Exception the exception
     */
    public static String createRefset(final Refset refsetEditParameters) throws Exception {
        
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
            refset.setProject(project);
            refset.setVersionDate(null);
            
            if (refset.getType().equals(Refset.INTENSIONAL)) {
                //refset.getDefinitionClauses().addAll(definitionList);
            }

            // Add an object
            service.add(refset);
            newInternalRefsetId = refset.getId();
            logger.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added");
            logger.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));
        }
        
        return newInternalRefsetId;
        
    }
    
    /**
     * Delete or inactivate a refset.
     *
     * @param refsetInternalId the internal refset ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String deleteRefset(final String refsetInternalId) throws Exception {
        
        String status = "inactivated";
        String refsetId = "";
        
        try (final TerminologyService service = new TerminologyService()) {
            
            boolean canDeleteConcept = true;
            boolean canDeleteRefset = false;
            final Refset refset = service.get(refsetInternalId, Refset.class);
            
            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }
            
            refsetId = refset.getRefsetId();
            
            // find out if the refset has been versioned before
            if (!doesRefsetExist(refsetId, "AND (versionStatus: PUBISHED OR versionStatus: BETA)")) {
                canDeleteRefset = true;
            }
            
            // if the refset can be deleted try to delete the underlying concept
            if (canDeleteRefset) {
                
                final String url = SnowstormConnection.BASE_URL + refset.getEdition().getBranch()
                        + "/" + "concepts/" + refsetId;
                
                logger.debug("deleteRefset delete URL: " + url);
                
                try (final Response response = SnowstormConnection.deleteResponse(url)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        
                        logger.info("Unable to delete refset concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
                        canDeleteConcept = false;
                    } else {
                        logger.info("Deleted refset concept: " + refsetId);
                    }
                }
                
                // remove the refset from the database
                service.remove(refset);
                logger.info("Deleted refset from database: " + refsetInternalId);
                status = "deleted";
            }
            
            if (!canDeleteConcept) {
                
                // first retrieve the concept so all fields will be present for the update
                final String getUrl = SnowstormConnection.BASE_URL + "browser/" + refset.getEdition().getBranch()
                        + "/" + "concepts/" + refsetId;
                final ObjectMapper mapper = new ObjectMapper();
                ObjectNode memberBody = null;
                
                logger.debug("deleteRefset inactivate concept search URL: " + getUrl);
                
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
                
                final String updateUrl = SnowstormConnection.BASE_URL + "browser/" + refset.getEdition().getBranch()
                        + "/" + "concepts/" + refsetId;
                
                logger.debug("deleteRefset inactivate URL: " + updateUrl);
                
                // update the concept with the new data
                try (final Response response = SnowstormConnection.putResponse(updateUrl, memberBody.toString())) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        throw new Exception("Unable to inactivate refset concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.toString());
                    }
                    
                    logger.info("Inactivated refset concept: " + refsetId);
                }
            }
            
            if (!canDeleteRefset) {
                
                refset.setActive(false);
                service.update(refset);
                logger.info("Inactivated refset in database: " + refsetInternalId);
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
}
