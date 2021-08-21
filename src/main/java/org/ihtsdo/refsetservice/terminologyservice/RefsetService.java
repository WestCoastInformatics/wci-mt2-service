package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.RefsetEditParameters;
import org.ihtsdo.refsetservice.util.ResultList;
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
            
            edition = service.get(refsetEditParameters.getEditionId(), Edition.class);

            if (edition == null) {
                throw new Exception("Edition Id: " + refsetEditParameters.getEditionId()
                        + " does not exist in the RT2 database");
            }
            
            project = service.get(refsetEditParameters.getProjectId(), Project.class);

            if (project == null) {
                throw new Exception("Project Id: " + refsetEditParameters.getProjectId()
                        + " does not exist in the RT2 database");
            }
        }
        
        // if a new refset concept needs to be created
        if (refsetConceptId == null) {
            
            // if null set the parent to "Simple Type Reference Set" ID: 446609009
            if (parentConceptId == null) {
                parentConceptId = "446609009";
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
            refset.setEdition(edition);
            
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
}
