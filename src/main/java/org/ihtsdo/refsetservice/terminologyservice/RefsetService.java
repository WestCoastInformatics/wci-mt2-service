package org.ihtsdo.refsetservice.terminologyservice;

import java.text.SimpleDateFormat;
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
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.DefinitionClauseEditHistory;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetEditHistory;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

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

    /** The module Id of the SIMPLE_TYPE_REFERENCE_SET */
    private static final String SIMPLE_TYPE_REFERENCE_SET_MODULE_ID = "900000000000012004";

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
     * @param refsetParameters the paramters for creating the refset
     * @return the new refset's internal ID
     * @throws Exception the exception
     */
    public static String createRefset(final User user, final Refset refsetParameters) throws Exception {

        String newInternalRefsetId = null;
        String refsetConceptId = refsetParameters.getRefsetId();
        String parentConceptId = refsetParameters.getParentConceptId();
        String refsetId = refsetParameters.getRefsetId();
        Edition edition = null;
        Project project = null;
        List<String> conceptIdList = new ArrayList<>();

        // get the edition and project for the new refset
        try (final TerminologyService service = new TerminologyService()) {

            if (refsetConceptId != null && doesRefsetExist(refsetConceptId, null)) {

                return "Error - Concept Id '" + refsetConceptId + "' is already used as a refset.";
            }

            project = service.get(refsetParameters.getProjectId(), Project.class);

            if (project == null) {

                throw new Exception("Project Id: " + refsetParameters.getProjectId() + " does not exist in the RT2 database");
            }

            edition = project.getOrganization().getEdition();
        }

        if (refsetParameters.getType().equals(Refset.INTENSIONAL)) {

            try {

                String ecl = getEclFromDefinition(refsetParameters.getDefinitionClauses());

                // get the list of concepts from the ECL
                conceptIdList = RefsetMemberService.getConceptIdsFromEcl(edition.getBranch(), ecl);

                // if there are no concepts in the definition then stop the creation
                if (conceptIdList.size() == 0) {

                    return "Error - Definition returns no concepts.";
                }

            } catch (Exception e) {

                return "Error - Invalid ECL Definition";
            }

        }

        // if a new refset concept needs to be created get the ID to use
        if (refsetConceptId == null) {

            refsetConceptId = WorkflowService.getNewRefsetId(edition.getBranch());
        }

        // create a refset and edit branch for the new refset
        final String refsetBranch = WorkflowService.createRefsetBranch(edition.getBranch(), refsetConceptId, edition.getBranch());

        // if a new refset concept needs to be created
        if (refsetParameters.getRefsetId() == null) {

            // if null set the parent to "Simple Type Reference Set"
            if (parentConceptId == null) {

                parentConceptId = SIMPLE_TYPE_REFERENCE_SET;
            }

            final ObjectMapper mapper = new ObjectMapper();

            final ObjectNode descriptions = mapper.createObjectNode().set("descriptions", mapper.createArrayNode()
                .add(mapper.createObjectNode().put("term", refsetParameters.getName()).put("typeId", "900000000000013009").put("caseSignificance", "CASE_INSENSITIVE").put("lang", "en")
                    .set("acceptabilityMap", mapper.createObjectNode().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED")))
                .add(
                    mapper.createObjectNode().put("term", refsetParameters.getName() + " (foundation metadata concept)").put("typeId", "900000000000003001").put("caseSignificance", "CASE_INSENSITIVE")
                        .put("lang", "en").set("acceptabilityMap", mapper.createObjectNode().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED"))));

            final ObjectNode relationships =
                mapper.createObjectNode().set("relationships", mapper.createArrayNode().add(mapper.createObjectNode().put("destinationId", parentConceptId).put("typeId", "116680003").put("groupId", 0)
                    .put("lang", "en").set("acceptabilityMap", mapper.createObjectNode().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED"))));

            final long start = System.currentTimeMillis();
            final ObjectNode body = mapper.createObjectNode().put("conceptId", refsetConceptId);
            body.setAll(relationships);
            body.setAll(descriptions);

            final String url = SnowstormConnection.BASE_URL + "browser/" + refsetBranch + "/" + "concepts/";

            logger.debug("createRefset URL: " + url);
            logger.debug("createRefset URL body: " + body.toString());

            try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
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

            logger.debug("Create Refset: newly created refset concept ID: " + refsetConceptId + ". Time: " + (System.currentTimeMillis() - start));
        }

        final String editBranch = WorkflowService.createEditBranch(user, edition.getBranch(), null, refsetConceptId);

        // add the new refset to the database
        try (final TerminologyService service = new TerminologyService()) {

            final long start = System.currentTimeMillis();
            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);

            Refset refset = new Refset(refsetParameters);
            refset.setRefsetId(refsetConceptId);
            refset.setLatestVersion(true);
            refset.setVersionStatus(Refset.IN_DEVELOPMENT);
            refset.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
            refset.setProject(project);
            refset.setVersionDate(null);

            String originBranchPath = edition.getBranch();

            if (refsetParameters.getVersionDate() != null) {

                originBranchPath += "/" + getFormattedRefsetDate(refsetParameters.getVersionDate());
            }

            refset.setEditOriginBranchPath(edition.getBranch() + originBranchPath);

            if (refset.getType().equals(Refset.INTENSIONAL)) {

                // Add definition clauses to the DB
                for (final DefinitionClause clause : refset.getDefinitionClauses()) {

                    service.add(clause);
                }

            }

            // Add an object
            service.add(refset);
            newInternalRefsetId = refset.getId();

            // Add a workflow history entry for READY_FOR_EDIT and then update the workflow to IN_EDIT
            WorkflowService.addWorkflowHistory(user, WorkflowService.CREATE, refset, "");
            refset = WorkflowService.setWorkflowStatus(user, WorkflowService.EDIT, refset, "", WorkflowService.IN_EDIT, user.getUserName());

            // create an edit history entry based on the new refset version.
            createRefsetEditHistory(user, newInternalRefsetId);

            RefsetMemberService.refsetsUpdatedMembers.put(newInternalRefsetId, new HashMap<>());

            if (refset.getType().equals(Refset.INTENSIONAL)) {

                // add the list of concepts as members to the refset
                final List<String> unaddedConcepts = RefsetMemberService.addRefsetMembers(user, refset.getId(), conceptIdList);
            }

            logger.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added. Time: " + (System.currentTimeMillis() - start));
            logger.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));
        }

        return newInternalRefsetId;
    }

    /**
     * Generate an ECL statement from a list of definition clauses.
     *
     * @param definitionClauses the definition clauses
     * @return the generated ECL statement
     * @throws Exception the exception
     */
    public static String getEclFromDefinition(final List<DefinitionClause> definitionClauses) throws Exception {

        String additiveEcl = "";
        String negatedEcl = "";
        String ecl = "";

        // loop thru the clauses to get the combined ECL
        for (final DefinitionClause clause : definitionClauses) {

            if (clause.getNegated()) {

                negatedEcl += "(" + clause.getValue() + ") OR ";
            } else {

                additiveEcl += "(" + clause.getValue() + ") OR ";
            }

        }

        ecl = StringUtils.removeEnd(additiveEcl, " OR ");

        if (!negatedEcl.equals("")) {

            ecl = "(" + ecl + ") MINUS (" + StringUtils.removeEnd(negatedEcl, " OR ") + ")";
        }

        return ecl;
    }

    /**
     * Generate lists of concept ID for inclusion and exclusion clauses from a list of definition clauses.
     *
     * @param definitionClauses the definition clauses
     * @param branchPath the refset branch path
     * @return the generated ECL statement
     * @throws Exception the exception
     */
    public static Map<String, List<String>> getInclusionExclusionLists(final List<DefinitionClause> definitionClauses, final String branchPath) throws Exception {

        String additiveEcl = "";
        String negatedEcl = "";
        List<String> inclusionList = new ArrayList<>();
        List<String> exclusionList = new ArrayList<>();
        final Map<String, List<String>> returnMap = new HashMap<>();

        // loop thru the clauses to get the combined ECL
        for (int i = 1; i < definitionClauses.size(); i++) {

            final DefinitionClause clause = definitionClauses.get(i);

            if (clause.getNegated()) {

                negatedEcl += "(" + clause.getValue() + ") OR ";
            } else {

                additiveEcl += "(" + clause.getValue() + ") OR ";
            }

        }

        if (!additiveEcl.equals("")) {

            additiveEcl = StringUtils.removeEnd(additiveEcl, " OR ");
            inclusionList = RefsetMemberService.getConceptIdsFromEcl(branchPath, additiveEcl);
        }

        if (!negatedEcl.equals("")) {

            negatedEcl = StringUtils.removeEnd(negatedEcl, " OR ");
            exclusionList = RefsetMemberService.getConceptIdsFromEcl(branchPath, negatedEcl);
        }

        returnMap.put(Refset.INCLUSION, inclusionList);
        returnMap.put(Refset.EXCLUSION, exclusionList);

        return returnMap;
    }

    /**
     * Modify a refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @param refsetEditParameters the paramters for creating the refset
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String modifyRefset(final User user, final String refsetInternalId, final Refset refsetEditParameters) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            String statusMessage = "Success";
            Refset refset = getRefset(user, refsetInternalId);

            if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

                throw new Exception("Refset is not in the proper status to be modified " + refsetInternalId);
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            // set user changed fields
            refset.setTags(refsetEditParameters.getTags());
            refset.setVersionNotes(refsetEditParameters.getVersionNotes());
            refset.setNarrative(refsetEditParameters.getNarrative());
            refset.setPrivateRefset(refsetEditParameters.isPrivateRefset());
            refset.setType(refsetEditParameters.getType());
            refset.setExternalUrl(refsetEditParameters.getExternalUrl());

            // update an object
            service.update(refset);

            // if this is an intensional refset update the definition
            if (refset.getType().equals(Refset.INTENSIONAL)) {

                statusMessage = modifyRefsetDefinition(user, service, refset, refsetEditParameters.getDefinitionClauses());
            }

            logger.info("Refset " + refset.getRefsetId() + " successfully modified");
            logger.debug("Modify Refset: Refset: " + ModelUtility.toJson(refset));
            return statusMessage;
        }

    }

    /**
     * Save the details of a refset before editing.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @throws Exception the exception
     */
    public static void createRefsetEditHistory(final User user, final String refsetInternalId) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            Refset refset = getRefset(user, refsetInternalId);

            if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

                throw new Exception("Refset is not in the proper status to have history saved " + refsetInternalId);
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            RefsetEditHistory history = new RefsetEditHistory();
            history.populateFrom(refset);
            history.setId(null);

            // if this is an intensional refset save the definition
            if (refset.getType().equals(Refset.INTENSIONAL)) {

                List<DefinitionClauseEditHistory> clauseHistoryList = new ArrayList<>();

                for (DefinitionClause originalClause : refset.getDefinitionClauses()) {

                    DefinitionClauseEditHistory clauseHistory = new DefinitionClauseEditHistory(originalClause);
                    clauseHistory.setId(null);
                    service.add(clauseHistory);
                    clauseHistoryList.add(clauseHistory);
                }

                history.setDefinitionClauses(clauseHistoryList);
            }

            // update an object
            service.add(history);

            logger.info("Refset " + refset.getRefsetId() + " edit history saved");
            logger.debug("createRefsetEditHistory: Refset: " + ModelUtility.toJson(refset));
        }

    }

    /**
     * Save the details of a refset before editing.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @throws Exception the exception
     */
    public static void replaceRefsetWithEditHistory(final User user, final String refsetInternalId) throws Exception {

        logger.debug("replaceRefsetWithEditHistory: refsetInternalId: " + refsetInternalId);

        try (TerminologyService service = new TerminologyService()) {

            Refset refset = getRefset(user, refsetInternalId);

            RefsetEditHistory history = service.findSingle("refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + "", RefsetEditHistory.class, null);

            if (history == null) {

                return;
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            refset.setRefsetId(history.getRefsetId());
            refset.setName(history.getName());
            refset.setType(history.getType());
            refset.setNarrative(history.getNarrative());
            refset.setVersionDate(history.getVersionDate());
            refset.setVersionNotes(history.getVersionNotes());
            refset.setVersionStatus(history.getVersionStatus());
            refset.setExternalUrl(history.getExternalUrl());
            refset.setModuleId(history.getModuleId());
            refset.setEditOriginBranchPath(history.getEditOriginBranchPath());
            refset.setPrivateRefset(history.isPrivateRefset());
            refset.setTags(new HashSet<String>(history.getTags()));
            refset.setDefinitionClauses(new ArrayList<>());
            refset.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);

            service.update(refset);

            // if this is an intensional refset save the definition
            if (refset.getType().equals(Refset.INTENSIONAL)) {

                List<DefinitionClause> clauseList = new ArrayList<>();

                for (DefinitionClauseEditHistory historyClause : history.getDefinitionClauses()) {

                    DefinitionClause clause = new DefinitionClause();
                    clause.setValue(historyClause.getValue());
                    clause.setNegated(historyClause.getNegated());

                    service.add(clause);
                    clauseList.add(clause);
                }

                refset.setDefinitionClauses(clauseList);

                // update the refset with the definition
                service.update(refset);
            }

            logger.info("Refset " + refset.getRefsetId() + " replaced with edit history");
            logger.debug("replaceRefsetWithEditHistory: Refset: " + ModelUtility.toJson(refset));
        }

    }

    /**
     * Save the details of a refset before editing.
     *
     * @param user the user
     * @param refsetId the refset ID to remove history for
     * @throws Exception the exception
     */
    public static void removeRefsetEditHistory(final User user, final String refsetId) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            RefsetEditHistory history = service.findSingle("refsetId:" + QueryParserBase.escape(refsetId) + "", RefsetEditHistory.class, null);

            if (history == null) {

                return;
            }

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            // update an object
            service.remove(history);

            logger.info("Refset " + refsetId + " edit history removed");
        }

    }

    /**
     * Add an inclusion or exclusion clause to a refset definition.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @param ecl the ecl to use for the clause
     * @param definitionExceptionType is the exception an inclusion or exclusion
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String addDefinitionException(final User user, final String refsetInternalId, final String ecl, final String definitionExceptionType) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            String statusMessage = "Success";
            Refset refset = getRefset(user, refsetInternalId);

            if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

                throw new Exception("Refset is not in the proper status to be modified " + refsetInternalId);
            }

            if (!refset.getType().equals(Refset.INTENSIONAL)) {

                throw new Exception("This is not an Intensional Refset " + refsetInternalId);
            }

            final List<DefinitionClause> currentClauses = refset.getDefinitionClauses();
            final List<DefinitionClause> newClauses = new ArrayList<>();
            boolean isNewClause = true;

            for (final DefinitionClause currentClause : currentClauses) {

                if (currentClause.getValue().equals(ecl)) {

                    isNewClause = false;
                    break;

                } else {

                    newClauses.add(new DefinitionClause(currentClause));
                }

            }

            if (isNewClause) {

                boolean negated = false;

                if (definitionExceptionType.equals(Refset.EXCLUSION)) {

                    negated = true;
                }

                newClauses.add(new DefinitionClause(ecl, negated));
                statusMessage = modifyRefsetDefinition(user, service, refset, newClauses);
                logger.debug("addDefinitionException: Refset: " + ModelUtility.toJson(refset));
            }

            return statusMessage;
        }

    }

    /**
     * Remove an inclusion or exclusion clause from a refset definition.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @param definitionExceptionId the exception ID
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String removeDefinitionException(final User user, final String refsetInternalId, final String definitionExceptionId) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            String statusMessage = "Success";
            Refset refset = getRefset(user, refsetInternalId);

            if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

                throw new Exception("Refset is not in the proper status to be modified " + refsetInternalId);
            }

            if (!refset.getType().equals(Refset.INTENSIONAL)) {

                throw new Exception("This is not an Intensional Refset " + refsetInternalId);
            }

            final List<DefinitionClause> currentClauses = refset.getDefinitionClauses();
            final List<DefinitionClause> newClauses = new ArrayList<>();
            boolean removeClause = false;

            for (final DefinitionClause currentClause : currentClauses) {

                if (currentClause.getId().equals(definitionExceptionId)) {

                    removeClause = true;
                } else {

                    newClauses.add(new DefinitionClause(currentClause));
                }

            }

            if (removeClause) {

                statusMessage = modifyRefsetDefinition(user, service, refset, newClauses);
                logger.debug("removeDefinitionException: Refset: " + ModelUtility.toJson(refset));
            }

            return statusMessage;
        }

    }

    /**
     * Modify a refset definition.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID to modify
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String modifyRefsetDefinition(final User user, final TerminologyService service, final Refset refset, final List<DefinitionClause> modifiedDefinitionClauses) throws Exception {

        List<String> unprocessedConcepts = new ArrayList<>();
        String statusMessage = "Success";

        if (refset.getType().equals(Refset.INTENSIONAL)) {

            final String oldDefinition = getEclFromDefinition(refset.getDefinitionClauses());
            final String newDefinition = getEclFromDefinition(modifiedDefinitionClauses);
            logger.debug("modifyRefsetDefinition oldDefinition: " + oldDefinition);
            logger.debug("modifyRefsetDefinition newDefinition: " + newDefinition);

            if (!oldDefinition.equals(newDefinition)) {

                final String branchPath = getBranchPath(refset.getId());
                List<String> oldMembersTemp = new ArrayList<>();
                List<String> newMembersTemp = new ArrayList<>();

                try {

                    oldMembersTemp = RefsetMemberService.getConceptIdsFromEcl(branchPath, oldDefinition);
                    newMembersTemp = RefsetMemberService.getConceptIdsFromEcl(branchPath, newDefinition);

                } catch (Exception e) {

                    logger.error("modifyRefsetDefinition error: ", e);
                    return "Error - Invalid ECL Definition";
                }

                final List<String> oldMembers = oldMembersTemp;
                final List<String> newMembers = newMembersTemp;

                logger.debug("modifyRefsetDefinition oldMembers: " + oldMembers);
                logger.debug("modifyRefsetDefinition newMembers: " + newMembers);

                final List<DefinitionClause> definitionClauses = refset.getDefinitionClauses();
                final List<DefinitionClause> clausesToRemove = new ArrayList<>();

                // loop thru the existing clauses see what has been removed
                for (final DefinitionClause existingClause : definitionClauses) {

                    int matchIndex = -1;

                    for (final DefinitionClause newClause : modifiedDefinitionClauses) {

                        if (existingClause.getId().equals(newClause.getId())) {

                            // if the clause is changed then update the existing clause
                            if (!existingClause.getValue().equals(newClause.getValue()) || (existingClause.getNegated() != newClause.getNegated())) {

                                existingClause.setValue(newClause.getValue());
                                existingClause.setNegated(newClause.getNegated());
                                logger.debug("modifyRefsetDefinition updating clause: " + existingClause);
                                service.update(existingClause);
                            }

                            matchIndex = modifiedDefinitionClauses.indexOf(newClause);
                            break;
                        }

                    }

                    // if the clause still exists remove it from the new clauses, otherwise mark the old clause for removal
                    if (matchIndex >= 0) {

                        logger.debug("modifyRefsetDefinition removing clause from editParams: " + modifiedDefinitionClauses.get(matchIndex));
                        modifiedDefinitionClauses.remove(matchIndex);
                    } else {

                        clausesToRemove.add(existingClause);
                    }

                }

                // Remove any marked clauses from the DB
                if (clausesToRemove.size() > 0) {

                    for (final DefinitionClause clauseToRemove : clausesToRemove) {

                        // remove an object
                        logger.debug("modifyRefsetDefinition removing clause: " + clauseToRemove);
                        service.remove(clauseToRemove);
                        definitionClauses.remove(clauseToRemove);
                    }

                }

                // loop thru the new clauses to add to the DB
                for (final DefinitionClause newClause : modifiedDefinitionClauses) {

                    logger.debug("modifyRefsetDefinition adding clause: " + newClause);
                    service.add(newClause);
                }

                // add the new clauses to the refset and save the refset
                definitionClauses.addAll(modifiedDefinitionClauses);
                service.update(refset);

                // Get the list of members to remove
                List<String> conceptsToRemove = oldMembers.stream().filter(oldMember -> !newMembers.contains(oldMember)).collect(Collectors.toList());

                if (conceptsToRemove.size() > 0) {

                    logger.debug("modifyRefsetDefinition intensional conceptsToRemove: " + conceptsToRemove);
                    unprocessedConcepts.addAll(RefsetMemberService.removeRefsetMembers(user, refset.getId(), String.join(",", conceptsToRemove)));
                }

                // Get the list of members to add
                List<String> conceptsToAdd = newMembers.stream().filter(newMember -> !oldMembers.contains(newMember)).collect(Collectors.toList());

                if (conceptsToAdd.size() > 0) {

                    logger.debug("modifyRefsetDefinition intensional conceptsToAdd: " + conceptsToAdd);
                    unprocessedConcepts.addAll(RefsetMemberService.addRefsetMembers(user, refset.getId(), conceptsToAdd));
                }

            }

        }

        if (unprocessedConcepts.size() > 0) {

            statusMessage = "Error - Refset definition modified but unable to process concepts: ";

            for (final String unprocessedConcept : unprocessedConcepts) {

                statusMessage += unprocessedConcept + ", ";
            }

            statusMessage = StringUtils.removeEnd(statusMessage, ", ");
        }

        logger.info("Refset " + refset.getRefsetId() + " definition successfully modified");
        return statusMessage;
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

                throw new Exception("Refset Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
            }

            refsetId = refset.getRefsetId();

            // if the refset has never been versioned before then delete it
            if (!doesRefsetExist(refsetId, "AND (versionStatus: " + Refset.PUBLISHED + " OR versionStatus: " + Refset.BETA + ")")) {

                return deleteInDevelopmentVersion(user, refsetInternalId, true);
            }

            // inactive the underlying refset concept
            inactivateRefsetConcept(refsetId, getBranchPath(refset));

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
        final String getUrl = SnowstormConnection.BASE_URL + "browser/" + branch + "/" + "concepts/" + refsetId;
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

        final String updateUrl = SnowstormConnection.BASE_URL + "browser/" + branch + "/" + "concepts/" + refsetId;

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
    public static String deleteInDevelopmentVersion(final User user, final String refsetInternalId, final boolean deleteConcept) throws Exception {

        String status = "deleted";
        String refsetId = "";

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("RT2");
            service.setModifiedFlag(true);

            boolean otherVersions = false;
            final Refset refset = service.get(refsetInternalId, Refset.class);

            if (refset == null) {

                throw new Exception("Refset Internal Id: " + refsetInternalId + " does not exist in the RT2 database");

            } else if (!refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {

                throw new Exception("Refset Internal Id: " + refsetInternalId + " is not 'In Development' and can not be removed.");
            }

            refsetId = refset.getRefsetId();

            // find out if the refset has been versioned before
            if (doesRefsetExist(refsetId, "AND (versionStatus: " + Refset.PUBLISHED + " OR versionStatus: " + Refset.BETA + ")")) {

                otherVersions = true;
            }

            // remove the edit and refset branches with all terminology changes
            WorkflowService.deleteEditBranch(user, refset.getEditionBranch(), refsetId);
            WorkflowService.deleteRefsetBranch(refset.getEditionBranch(), refsetId);

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
     * @param returnParentConceptsOnly Do these concepts represent parent concepts for a new refset, or will they be the underlying concepts for a the refset itself
     * @return the list of refset concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetConcepts(final String branch, final boolean returnParentConceptsOnly) throws Exception {

        final ConceptResultList results = new ConceptResultList();
        final Set<String> existingRefsetIds = new HashSet<>();
        final String ecl = StringUtility.encodeValue(QueryParserBase.escape("<<" + SIMPLE_TYPE_REFERENCE_SET));
        final List<Edition> editions = getEditionForBranch(branch);
        String modules = editions.stream().map(Edition::getTopLevelModule).collect(Collectors.joining(","));

        String url;

        if (editions == null || editions.isEmpty()) {

            throw new Exception("Branch path '" + branch + "' doesn't have any editions associated with it");
        } else if (modules == null || modules.isEmpty()) {

            throw new Exception("Editions '" + editions.toString() + "' doesn't have any Top Level Modules associated with it");
        }

        url = SnowstormConnection.BASE_URL + branch + "/" + "concepts?ecl=" + ecl + "&limit=1000&module=" + editions.stream().map(Edition::getTopLevelModule).collect(Collectors.joining(",")) + ","
            + SIMPLE_TYPE_REFERENCE_SET_MODULE_ID;

        logger.debug("getRefsetConcepts URL: " + url);

        if (returnParentConceptsOnly) {

            try (final TerminologyService service = new TerminologyService()) {

                // get all the existing refsets for latest branch version
                final ResultList<Refset> refsets = service.find("latestVersion: true", null, Refset.class, null);

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
                if (returnParentConceptsOnly && existingRefsetIds.contains(conceptId)) {

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

            final Project project = service.findSingle("id:" + QueryParserBase.escape(projectId) + "", Project.class, null);

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

            Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class, null);

            if (refset == null) {

                throw new Exception("Unable to retrieve refset " + refsetInternalId);
            }

            refset = setRefsetPermissions(user, refset);
            refset.setVersionList(getSortedRefsetVersionList(refset.getRefsetId(), service));
            refset.setBranchPath(getBranchPath(refset));

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

                pfs.setSortFields(Arrays.asList(searchParameters.getSort(), "modified desc", "id"));
            }

            if (query != null && !query.equals("")) {

                final List<String> directoryColumns =
                    Arrays.asList("id", "refsetId", "name", "editionName", "organizationName", "versionStatus", "versionDate", "modified", "privateRefset", "editionShortName", "assignedUser");
                String[] queryParts = query.split(" AND ");
                String filterQuery = "";
                String termQuery = "";
                String termQueryForRt2 = "";

                for (final String queryPart : queryParts) {

                    String[] keyValue = queryPart.split(":");

                    if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {

                        final String value = QueryParserBase.escape(String.join(":", Arrays.copyOfRange(keyValue, 1, keyValue.length)));
                        filterQuery += keyValue[0] + ":" + value + " AND ";
                    } else {

                        termQuery += queryPart + "* AND ";
                        termQueryForRt2 += QueryParserBase.escape(queryPart) + "* AND ";
                    }

                }

                // if the term query isn't empty then search members and build the full term query string
                if (!termQuery.equals("")) {

                    termQuery = StringUtils.removeEnd(termQuery, " AND ");
                    termQueryForRt2 = StringUtils.removeEnd(termQueryForRt2, " AND ");
                    Set<String> refsetIds = new HashSet<>();

                    // if it was requested search member concepts
                    if (searchConcepts) {

                        refsetIds.addAll(RefsetMemberService.searchDirectoryMembers(searchParameters));

                        // search descriptions of Simple type reference set (foundation metadata concept) "<446609009"
                        refsetIds.addAll(RefsetMemberService.searchMultisearchDescriptions(searchParameters, "<446609009"));
                    }

                    if (!refsetIds.isEmpty()) {

                        termQueryForRt2 = "((" + termQueryForRt2 + ")";

                        if (!refsetIds.isEmpty()) {

                            termQueryForRt2 = termQueryForRt2 + " OR refsetId:(" + String.join(" OR ", refsetIds) + ")";
                        }

                        termQueryForRt2 += ")";

                    } else {

                        termQueryForRt2 = "(" + termQueryForRt2 + ")";
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

                query = filterQuery + termQueryForRt2;
            }

            if (query != null && !query.equals("")) {

                query += " AND latestVersion: true";
            } else {

                query = "latestVersion: true";
            }

            if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {

                query += " AND privateRefset: false";
            }

            logger.debug("******** searchRefsets query: " + query);

            results = service.find(query, pfs, Refset.class, null);

            for (Refset refset : results.getItems()) {

                refset = setRefsetPermissions(user, refset);
                refset.setVersionList(RefsetService.getSortedRefsetVersionList(refset.getRefsetId(), service));
            }

            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            logger.debug("******** searchRefsets results: " + ModelUtility.toJson(results));

            return results;
        }

    }

    /**
     * Returns a list of all refset IDs.
     *
     * @return the list of refset IDs
     * @throws Exception the exception
     */
    public static List<String> getRefsetIds() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            List<String> results = new ArrayList<String>();

            final ResultList<Refset> refsets = service.find("latestVersion: true", new PfsParameter(), Refset.class, null);

            for (final Refset refset : refsets.getItems()) {

                results.add(refset.getRefsetId());
            }

            return results;
        }

    }

    /**
     * Get a list of refsets containing descriptions matching the search.
     *
     * @param searchParameters the search parameters
     * @return a list of refsets containing descriptions matching the search
     * @throws Exception the exception
     */
    public static String searchRefsetDescriptions(final SearchParameters searchParameters) throws Exception {

        String refsetQuery = "";
        final String query = searchParameters.getQuery();

        final List<String> directoryColumns = Arrays.asList("id", "refsetId", "name", "editionName", "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
        String snowstormQuery = "";
        String[] queryParts = query.split(" AND ");

        for (final String queryPart : queryParts) {

            String[] keyValue = queryPart.split(":");

            if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {

                continue;
            } else {

                snowstormQuery += queryPart + " AND ";
            }

        }

        // if there are no query terms just exit the method
        if (snowstormQuery.equals("")) {

            return refsetQuery;
        }

        // get the list of refset IDs to look up descriptions for
        final String refsetIds = String.join(",", getRefsetIds());

        snowstormQuery = StringUtils.removeEnd(snowstormQuery, " AND ");

        String url = SnowstormConnection.BASE_URL + "concepts?offset=0&limit=3000&term=" + StringUtility.encodeValue(QueryParserBase.escape(snowstormQuery)) + "&conceptIds=" + refsetIds;

        logger.debug("searchRefsetDescriptions URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
            }

            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            final JsonNode root = mapper.readTree(resultString.toString());
            final JsonNode items = root.get("items");
            final Iterator<JsonNode> iterator = items.iterator();

            // loop thru the returned concepts add them to the list
            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final String refsetId = conceptNode.get("conceptId").asText();

                refsetQuery += refsetId + " OR ";
            }

            if (!refsetQuery.equals("")) {

                refsetQuery = "refsetId:(" + StringUtils.removeEnd(refsetQuery, " OR ") + ")";
            }

        } catch (Exception ex) {

            throw new Exception("Could not retrieve refset members from snowstorm: " + ex.getMessage(), ex);
        }

        logger.debug("searchRefsetDescriptions refsetQuery: " + refsetQuery);

        return refsetQuery;
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

            // create a refset and edit branch for the new refset
            final String refsetBranch = WorkflowService.createRefsetBranch(refset.getEditionBranch(), refset.getRefsetId(), getBranchPath(refset));
            final String editBranch = WorkflowService.createEditBranch(user, refset.getEditionBranch(), null, refset.getRefsetId());

            newRefsetVersion.populateFrom(refset);

            // set automatic changed fields
            newRefsetVersion.setVersionDate(null);
            newRefsetVersion.setLatestVersion(true);
            newRefsetVersion.setId(null);
            newRefsetVersion.setVersionStatus(Refset.IN_DEVELOPMENT);
            newRefsetVersion.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
            newRefsetVersion.setEditOriginBranchPath(refset.getEditionBranch() + "/" + getFormattedRefsetDate(refset.getVersionDate()));

            // find the previous latest version
            if (refset.isLatestVersion()) {

                oldLatestVersionRefset = refset;

            } else {

                oldLatestVersionRefset = service.findSingle("refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + " AND latestVersion: true", Refset.class, null);
            }

            // Add an object
            service.add(newRefsetVersion);
            newInternalRefsetId = newRefsetVersion.getId();

            // Add a workflow history entry for READY_FOR_EDIT and then update the workflow to IN_EDIT
            WorkflowService.addWorkflowHistory(user, WorkflowService.CREATE, newRefsetVersion, "");
            newRefsetVersion = WorkflowService.setWorkflowStatus(user, WorkflowService.EDIT, newRefsetVersion, "", WorkflowService.IN_EDIT, user.getUserName());

            // create an edit history entry based on the new refset version.
            createRefsetEditHistory(user, newInternalRefsetId);

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

            final ResultList<Refset> results = service.find("refsetId: " + QueryParserBase.escape(refsetId), pfs, Refset.class, null);

            // see if there is an "In Development" version as that should be the latest.
            for (final Refset result : results.getItems()) {

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
            branchPath = refset.getEditionBranch() + pathDate;
        } else {

            branchPath = refset.getEdition().getBranch() + "/" + WorkflowService.REFSET_BRANCH_PREFIX + refset.getRefsetId();

            if (refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

                branchPath += "/" + WorkflowService.EDIT_BRANCH_NAME;
            }

        }

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

                throw new Exception("Refset Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
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

        }

        refset.setAvailableActions(WorkflowService.getAllowedActions(user, refset));

        return refset;
    }

    /**
     * Generate a list of version dates sorted in descending order.
     *
     * @param refsetId the refset Id
     * @param service the Terminology Service
     * @return the list of version dates sorted in descending order
     * @throws Exception the exception
     */
    public static List<Map<String, String>> getSortedRefsetVersionList(final String refsetId, final TerminologyService service) throws Exception {

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        final List<Map<String, String>> versionList = new ArrayList<>();
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("versionDate");
        pfs.setAscending(false);

        // ResultList<Refset> test = service.find("id:
        // 78659156-b6d6-4935-bcdf-c4692bcee10d", pfs, Refset.class, null);
        // logger.debug("******** test: " + ModelUtility.toJson(test));

        final ResultList<Refset> results = service.find("refsetId: " + QueryParserBase.escape(refsetId), pfs, Refset.class, null);

        for (Refset refset : results.getItems()) {

            final Map<String, String> version = new HashMap<>();
            version.put("status", refset.getVersionStatus());
            version.put("refsetInternalId", refset.getId());

            boolean inDevelopmentVersionFound = false;

            if (refset.getVersionStatus().toLowerCase().equals("in development")) {

                if (inDevelopmentVersionFound) {

                    throw new Exception("May only have a single version at 'in development' at any given time, and we found 2nd for refsetId: " + refset.getRefsetId());
                }

                version.put("date", DateUtility.formatDate(new Date(), DateUtility.DATE_FORMAT_REVERSE, null));

                versionList.add(0, version);

                inDevelopmentVersionFound = true;
            } else if ("beta, published".contains(refset.getVersionStatus().toLowerCase())) {

                version.put("date", DateUtility.formatDate(refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null));

                if (versionList.isEmpty()) {

                    versionList.add(version);
                } else {

                    final Date dateToInsert = refset.getVersionDate();
                    int versionIndex = (inDevelopmentVersionFound) ? 1 : 0;

                    for (Map<String, String> currentVersion : versionList) {

                        final Date dateInspecting = sdf.parse(currentVersion.get("date"));

                        if (dateToInsert.before(dateInspecting)) {

                            break;
                        }

                        versionIndex++;
                    }

                    versionList.add(versionIndex, version);
                }

            }

        }

        return versionList;
    }

    /**
     * Generate a list of line strings from a file removing empty lines.
     *
     * @param conceptFile the input file
     * @param fileType the type of file (list or rf2)
     * @return the line string List
     * @throws Exception the exception
     */
    public static List<String> getConceptIdsFromFile(final MultipartFile conceptFile, final String fileType) throws Exception {

        List<String> conceptIds = null;

        if (fileType.equals("list")) {

            conceptIds = FileUtility.readFileToArray(conceptFile);
            return conceptIds;
        } else {

            final List<String> rf2Lines = FileUtility.readFileToArray(conceptFile);
            conceptIds = new ArrayList<>();

            for (final String line : rf2Lines) {

                try {

                    conceptIds.add(line.split("\t")[RefsetMemberService.REFEST_RF2_CONCEPTID_COLUMN]);

                } catch (Exception e) {

                    continue;
                }

            }

        }

        return conceptIds;
    }

    /**
     * Get refset dates as properly formatted strings.
     *
     * @param date the date to format
     * @return the formatted date string
     * @throws Exception the exception
     */
    public static String getFormattedRefsetDate(final Date date) throws Exception {

        return DateUtility.formatDate(date, DateUtility.DATE_FORMAT_REVERSE, null);
    }

    /**
     * Get refset dates as Date objects from properly formatted strings.
     *
     * @param date the date to format
     * @return the formatted date string
     * @throws Exception the exception
     */
    public static Date getRefsetDateFromFormattedString(final String date) throws Exception {

        return DateUtility.getDateWithNoTime(date, DateUtility.DATE_FORMAT_REVERSE);
    }

    /**
     * Fetch editions for given branch
     * 
     * @param branch the branch
     * @return List <Edition> list of editions matching branch
     * @throws Exception
     */
    private static List<Edition> getEditionForBranch(final String branch) throws Exception {

        ResultList<Edition> editions = new ResultList<>();

        try (TerminologyService service = new TerminologyService()) {

            editions = service.find("active:true AND branch:" + QueryParserBase.escape(branch), null, Edition.class, null);
        } catch (Exception e) {

            logger.error("Error finding edition for branch {}", branch, e);
            throw e;
        }

        return (editions != null) ? editions.getItems() : new ArrayList<Edition>();

    }
}
