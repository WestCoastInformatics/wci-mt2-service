/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class SnowstormRefset.
 */
public class SnowstormRefset extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormRefset.class);

  /**
   * Creates the refset.
   *
   * @param service the service
   * @param user the user
   * @param refsetEditParameters the refset edit parameters
   * @return the object
   * @throws Exception the exception
   */
  public static Object createRefset(final TerminologyService service, final User user,
    final Refset refsetEditParameters) throws Exception {

    String newInternalRefsetId = null;
    Refset refset = null;
    String refsetConceptId = refsetEditParameters.getRefsetId();
    String parentConceptId = refsetEditParameters.getParentConceptId();
    Edition edition = null;
    Project project = null;
    List<String> conceptIdList = new ArrayList<>();
    final String moduleId = refsetEditParameters.getModuleId();

    // get the edition and project for the new refset
    if (refsetConceptId != null && RefsetService.doesRefsetExist(refsetConceptId, null)) {

      return "Error - Concept Id '" + refsetConceptId + "' is already used as a reference set.";
    }

    project = service.get(refsetEditParameters.getProjectId(), Project.class);

    if (project == null) {

      throw new Exception("Project Id: " + refsetEditParameters.getProjectId()
          + " does not exist in the RT2 database");
    }

    edition = project.getEdition();

    // if a new refset concept needs to be created get the ID to use
    if (refsetConceptId == null) {

      refsetConceptId = WorkflowService.getNewRefsetId(edition.getBranch());
    }

    // create a refset branch for the new refset
    final String refsetBranchId = WorkflowService.generateBranchId();
    final String refsetBranch = WorkflowService.createRefsetBranch(edition.getBranch(),
        refsetConceptId, refsetBranchId, refsetEditParameters.isLocalSet());

    // if a new refset concept needs to be created
    if (refsetEditParameters.getRefsetId() == null) {

      // if null set the parent to "Simple Type Reference Set"
      if (parentConceptId == null) {

        parentConceptId = RefsetService.SIMPLE_TYPE_REFERENCE_SET;
      }

      final ObjectMapper mapper = new ObjectMapper();

      final ObjectNode descriptions = mapper.createObjectNode().set("descriptions",
          mapper.createArrayNode().add(mapper.createObjectNode().put("moduleId", moduleId)
              .put("term", refsetEditParameters.getName()).put("typeId", "900000000000013009")
              .put("caseSignificance", "CASE_INSENSITIVE").put("lang", "en").set("acceptabilityMap",
                  mapper.createObjectNode().put("900000000000509007", "PREFERRED")
                      .put("900000000000508004", "PREFERRED")))
              .add(mapper.createObjectNode().put("moduleId", moduleId)
                  .put("term", refsetEditParameters.getName() + " (foundation metadata concept)")
                  .put("typeId", "900000000000003001").put("caseSignificance", "CASE_INSENSITIVE")
                  .put("lang", "en").set("acceptabilityMap",
                      mapper.createObjectNode().put("900000000000509007", "PREFERRED")
                          .put("900000000000508004", "PREFERRED"))));

      final ObjectNode relationships = mapper.createObjectNode().set("relationships",
          mapper.createArrayNode()
              .add(mapper.createObjectNode().put("moduleId", moduleId)
                  .put("destinationId", parentConceptId).put("typeId", "116680003")
                  .put("groupId", 0).put("lang", "en").set("acceptabilityMap",
                      mapper.createObjectNode().put("900000000000509007", "PREFERRED")
                          .put("900000000000508004", "PREFERRED")))
              .add(mapper.createObjectNode().put("destinationId", "446609009")
                  .put("typeId", "116680003").put("groupId", 0)));

      final ObjectNode classAxioms = mapper.createObjectNode().set("classAxioms",
          mapper.createArrayNode()
              .add(mapper.createObjectNode().put("moduleId", moduleId)
                  .put("definitionStatusId", "900000000000074008").set("relationships",
                      mapper.createArrayNode()
                          .add(mapper.createObjectNode().put("moduleId", moduleId)
                              .put("destinationId", "446609009").put("typeId", "116680003")
                              .put("groupId", 0)))));

      final long start = System.currentTimeMillis();
      final ObjectNode body =
          mapper.createObjectNode().put("conceptId", refsetConceptId).put("moduleId", moduleId);
      body.setAll(relationships);
      body.setAll(classAxioms);
      body.setAll(descriptions);

      final String url =
          SnowstormConnection.getBaseUrl() + "browser/" + refsetBranch + "/" + "concepts/";

      LOG.debug("createRefset URL: " + url);
      LOG.debug("createRefset URL body: " + body.toString());

      try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

          throw new Exception("call to url '" + url + "' wasn't successful. " + response.getStatus()
              + ": " + response.getStatusInfo().getReasonPhrase());
        }

        // Only process payload if Rest call is successful
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {

          throw new Exception(Integer.toString(response.getStatus()));
        }

        final String resultString = response.readEntity(String.class);

        final JsonNode root = mapper.readTree(resultString.toString());
        final JsonNode conceptNode = root;

        if (conceptNode.has("conceptId")) {

          refsetConceptId = conceptNode.get("conceptId").asText();
        } else {

          throw new Exception("Unable to create new reference set concept.");
        }

      }

      LOG.debug("Create Refset: newly created refset concept ID: " + refsetConceptId + ". Time: "
          + (System.currentTimeMillis() - start));
    }

    final String editBranchId = WorkflowService.generateBranchId();

    final long start = System.currentTimeMillis();

    // add the new refset to the database
    refset = new Refset(refsetEditParameters);
    refset.setRefsetId(refsetConceptId);
    refset.setVersionStatus(Refset.IN_DEVELOPMENT);
    refset.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
    refset.setProject(project);
    refset.setVersionDate(null);
    refset.setEditBranchId(editBranchId);
    refset.setRefsetBranchId(refsetBranchId);

    if (refset.getType().equals(Refset.INTENSIONAL)) {

      // Add definition clauses to the DB
      for (final DefinitionClause clause : refset.getDefinitionClauses()) {

        service.add(clause);
      }

    }

    // Add an object
    service.add(refset);
    newInternalRefsetId = refset.getId();

    WorkflowService.createEditBranch(service, user, refset, editBranchId);

    // Add a workflow history entry for CREATE and then update the workflow to
    // IN_EDIT
    WorkflowService.addWorkflowHistory(service, user, WorkflowService.CREATE, refset, "");
    refset = WorkflowService.setWorkflowStatus(service, user, WorkflowService.EDIT, refset, "",
        WorkflowService.IN_EDIT, user.getUserName());

    // if cloning a refset this is where extensional members are copied over
    // String originBranchPath = edition.getBranch();

    // if (refsetEditParameters.getVersionDate() != null) {
    // originBranchPath += "/" +
    // getFormattedRefsetDate(refsetEditParameters.getVersionDate());
    // }

    RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(newInternalRefsetId, new HashMap<>());

    if (refset.getType().equals(Refset.INTENSIONAL)) {

      refset.setBranchPath(RefsetService.getBranchPath(refset));

      try {

        final String ecl =
            RefsetService.getEclFromDefinition(refsetEditParameters.getDefinitionClauses());

        // get the list of concepts from the ECL
        conceptIdList = RefsetMemberService.getConceptIdsFromEcl(refset.getBranchPath(), ecl);

        // if there are no concepts in the definition then stop the creation
        if (conceptIdList.size() == 0) {

          return "Error - Definition returns no concepts.";
        }

      } catch (final Exception e) {

        return "Error - Invalid ECL Definition";
      }

      // add the list of concepts as members to the refset
      RefsetMemberService.addRefsetMembers(service, user, refset, conceptIdList);
      WorkflowService.mergeEditIntoRefsetBranch(refset.getEditionBranch(), refset.getRefsetId(),
          editBranchId, refsetBranchId, "Initial intensional refset creation.",
          refset.isLocalSet());
    }

    RefsetService.clearAllRefsetCaches(refset.getEditionBranch());

    LOG.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added. Time: "
        + (System.currentTimeMillis() - start));
    LOG.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));

    return refset;
  }

  /**
   * Checks if branch exists.
   *
   * @param editionBranchPath the edition branch path
   * @return true, if successful
   * @throws Exception the exception
   */
  public static String getNewRefsetId(final String editionBranchPath) throws Exception {

    String refsetConceptId = null;
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode body = mapper.createObjectNode();
    final String projectBranchPath = WorkflowService.getProjectBranchPath(editionBranchPath);
    String tempBranchPath = null;

    if (!SnowstormBranch.doesBranchExist(projectBranchPath)) {
      SnowstormBranch.createBranch(editionBranchPath,
          WorkflowService.getProjectBranchName(editionBranchPath));
    }

    if (SnowstormBranch
        .doesBranchExist(projectBranchPath + "/" + WorkflowService.TEMP_BRANCH_NAME)) {
      tempBranchPath = projectBranchPath + "/" + WorkflowService.TEMP_BRANCH_NAME;
    } else {
      tempBranchPath =
          SnowstormBranch.createBranch(projectBranchPath, WorkflowService.TEMP_BRANCH_NAME);
    }

    final long start = System.currentTimeMillis();
    final String url =
        SnowstormConnection.getBaseUrl() + "browser/" + tempBranchPath + "/" + "concepts/";

    LOG.debug("getNewRefsetId URL: " + url);
    LOG.debug("getNewRefsetId URL Body: " + body.toString());

    try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
        throw new Exception(
            "call to url '" + url + "' wasn't successful. " + response.readEntity(String.class));
      }

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        throw new Exception(Integer.toString(response.getStatus()));
      }

      final String resultString = response.readEntity(String.class);
      final JsonNode root = mapper.readTree(resultString.toString());
      final JsonNode conceptNode = root;

      if (conceptNode.has("conceptId")) {

        refsetConceptId = conceptNode.get("conceptId").asText();
      } else {

        throw new Exception("Unable to create new refset concept.");
      }

    }

    LOG.debug(
        "New Refset ID " + refsetConceptId + ". Time: " + (System.currentTimeMillis() - start));
    return refsetConceptId;
  }

  /**
   * Gets the latest changed version date.
   *
   * @param branch the branch
   * @param refsetId the refset id
   * @return the latest changed version date
   * @throws Exception the exception
   */
  public static Long getLatestChangedVersionDate(final String branch, final String refsetId)
    throws Exception {
    // Get all members
    // EX:
    // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/SNOMEDCT-BE/members?referenceSet=1235&offset=0&limit=10
    // EX:
    // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/SNOMEDCT-BE/members?referenceSet=1235&offset=0&limit=10

    final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(RefsetMemberService.DATE_FORMAT);
    final int limit = RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;
    String searchAfter = "";

    long refsetLatestVersion = -1;
    final long start = System.currentTimeMillis();
    boolean hasMorePages = true;
    final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;

    while (hasMorePages) {

      final String url = SnowstormConnection.getBaseUrl() + branch + "/members?referenceSet="
          + refsetId + searchAfter + "&limit=" + limit;
      LOG.debug("getRefsetMembers URL: " + url);

      try (final Response response = SnowstormConnection.getResponse(url, acceptLanguage)) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

          hasMorePages = false;
          throw new Exception("call to url '" + url + "' wasn't successful. Status: "
              + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
        }

        final String resultString = response.readEntity(String.class);

        // Only process payload if Rest call is successful
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {

          throw new Exception(Integer.toString(response.getStatus()));
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString.toString());
        final JsonNode conceptNodeBatch = root.get("items");

        searchAfter = (root.get("searchAfter") != null
            ? "&searchAfter=" + root.get("searchAfter").asText() : "");

        if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() < limit) {

          hasMorePages = false;
        }

        if (System.currentTimeMillis()
            - start > RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD) {

          hasMorePages = false;
        }

        final Iterator<JsonNode> iterator = conceptNodeBatch.iterator();

        JsonNode memberNode = null;

        long versionLatestTime = -1;

        while (iterator.hasNext()) {

          memberNode = iterator.next();

          if (memberNode.has("releasedEffectiveTime")) {

            final long memberEffectiveTime =
                simpleDateFormat.parse(memberNode.get("releasedEffectiveTime").asText()).getTime();

            if (versionLatestTime < memberEffectiveTime) {

              versionLatestTime = memberEffectiveTime;
            }

          }

        }

        if (refsetLatestVersion < versionLatestTime) {

          refsetLatestVersion = versionLatestTime;
        }

      } catch (final Exception e) {

        LOG.error("Caught during defining Reference Set version on: " + refsetId + " --- " + branch
            + "\n", e);
        throw e;
      }

    }

    // No members with release dates, so use release date of refset concept
    // itself.
    if (refsetLatestVersion < 0) {

      refsetLatestVersion = getRefsetConceptReleaseDate(refsetId, branch);
    }

    // See if version already exists.
    if (!RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.containsKey(refsetId)) {

      RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.put(refsetId, new ArrayList<Long>());
    }

    if (RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.get(refsetId)
        .contains(refsetLatestVersion)) {

      return null;
    } else {

      RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.get(refsetId).add(refsetLatestVersion);
      return refsetLatestVersion;
    }
  }

  /**
   * Gets the refset concept release date.
   *
   * @param refsetId the refset id
   * @param branch the branch
   * @return the refset concept release date
   * @throws Exception the exception
   */
  public static Long getRefsetConceptReleaseDate(final String refsetId, final String branch)
    throws Exception {

    final String url =
        SnowstormConnection.getBaseUrl() + "browser/" + branch + "/" + "concepts/" + refsetId;
    final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(RefsetMemberService.DATE_FORMAT);

    LOG.debug("Get Concept Details URL: " + url);

    final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
    lookupParameters.setGetDescriptions(false);
    lookupParameters.setGetRoleGroups(false);
    lookupParameters.setSingleConceptRequest(true);

    try (final Response response =
        SnowstormConnection.getResponse(url, SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("call to url '" + url + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()) {

        throw new Exception(Integer.toString(response.getStatus()));
      }

      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode conceptNode = mapper.readTree(resultString.toString());

      if (conceptNode.has("releasedEffectiveTime")) {

        return simpleDateFormat.parse(conceptNode.get("releasedEffectiveTime").asText()).getTime();
      }

      return null;

    } catch (final Exception ex) {

      throw new Exception("Could not get Reference Set children for concept " + refsetId
          + " from snowstorm: " + ex.getMessage(), ex);
    }
  }

}
