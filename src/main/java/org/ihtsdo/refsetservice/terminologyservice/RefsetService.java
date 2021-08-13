package org.ihtsdo.refsetservice.terminologyservice;

import java.util.Iterator;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.RefsetEditParameters;
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
    
    /**
     * Create a refset with the given parameters .
     *
     * @param refsetEditParameters the paramters for creating the refset
     * @return the new refset's internal ID
     * @throws Exception the exception
     */
    public static String createRefset(Refset refsetEditParameters) throws Exception {
        
        //refsetEditParameters.setName("ZZZ Tim Test Refset 1");
        String newInternalRefsetId = null;
        String refsetConceptId = refsetEditParameters.getRefsetId();
        String parentConceptId = refsetEditParameters.getParentConceptId();
        Edition edition = null;
        Project project = null;
        
        // get the edition and project for the new refset
        try (final TerminologyService service = new TerminologyService()) {

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
            
            // set the parent to "Simple Type Reference Set" ID: 446609009
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
            
            try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                            "call to url '" + url + "' wasn't successful. " + response.toString());
                }

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new Exception(Integer.toString(response.getStatus()));
                }
                
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
            refset.setVersionStatus(Refset.IN_DEVELOPMENT);
            refset.setProject(project);
            refset.setEdition(edition);
            
            if (refset.getType().equals(Refset.INTENSIONAL)) {
                //refset.getDefinitionClauses().addAll(definitionList);
            }

            // Add an object
            service.add(refset);
            newInternalRefsetId = refset.getRefsetId();
            logger.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added");
            logger.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));
        }
        
        return newInternalRefsetId;
        
    }
}
