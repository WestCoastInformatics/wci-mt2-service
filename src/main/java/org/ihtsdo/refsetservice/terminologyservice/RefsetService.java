/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.DefinitionClauseEditHistory;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.InviteRequest;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetEditHistory;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.SyncAgent;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.EmailUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
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

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(RefsetService.class);

	/** The refset to language map. */
	private static final Map<String, String> REFSET_TO_LANGUAGE_MAP = new HashMap<>();

	// /** The project list cache. */
	// private static final LinkedHashMap<String, Project> projectCache = new
	// LinkedHashMap<>();

	/** The refset to language map. */
	public static final String SIMPLE_TYPE_REFERENCE_SET = "446609009";

	/** The module Id of the SIMPLE_TYPE_REFERENCE_SET. */
	private static final String SNOMED_CORE_MODULE_ID = "900000000000012004";

	/** A cache of the sorted branch versions. */
	private static final Map<String, List<String>> BRANCH_VERSION_CACHE = new HashMap<>();

	/** A cache of the branches to use for refset searches. */
	private static final Set<String> BRANCH_SEARCH_CACHE = new HashSet<>();

	/** A list of refset actively being updated. */
	public static final Set<String> REFSETS_TO_SHOW_UPGRADE_WARNING = new HashSet<>();

	/** A cache of the unique refset IDs in the system. */
	private static final Set<String> UNIQUE_REFSET_IDS = new HashSet<>();

	/** The Constant EMAIL_SUBJECT. */
	private static final String EMAIL_SUBJECT = "SNOMED International Reference Set Tool - ";

	/** The Constant SHARE_ACTION. */
	private static final String SHARE_ACTION = "Share-Reference-Set";

	/** The Constant INVITE_ACTION. */
	private static final String INVITE_ACTION = "Invite";

	/** The Constant INVITE_ACCEPTED. */
	private static final String INVITE_ACCEPTED = "Invite accepted";

	/** The Constant INVITE_DECLINED. */
	private static final String INVITE_DECLINED = "Invite declined";

	/** The Constant AND_SUFFIX. */
	private static final String AND_SUFFIX = " AND ";

	static {

		// TODO: Remove once Edition updated
		REFSET_TO_LANGUAGE_MAP.put("450828004", "es");
		REFSET_TO_LANGUAGE_MAP.put("32570271000036106", "en");
		REFSET_TO_LANGUAGE_MAP.put("900000000000509007", "en");
		REFSET_TO_LANGUAGE_MAP.put("21000172104", "fr");
		REFSET_TO_LANGUAGE_MAP.put("31000172101", "nl");
		REFSET_TO_LANGUAGE_MAP.put("554461000005103", "da");
		REFSET_TO_LANGUAGE_MAP.put("71000181105", "et");
		REFSET_TO_LANGUAGE_MAP.put("5641000179103", "es");
		REFSET_TO_LANGUAGE_MAP.put("21000220103", "en");
		REFSET_TO_LANGUAGE_MAP.put("61000202103", "no");
		REFSET_TO_LANGUAGE_MAP.put("46011000052107", "sv");
	}

	// /** The config properties. */
	// private static final Properties PROPERTIES = PropertyUtility.getProperties();

	/** The app url root. */
	private static String appUrlRoot;

	static {
		appUrlRoot = PropertyUtility.getProperties().getProperty("app.url.root");
	}

	/**
	 * Create a refset with the given parameters .
	 *
	 * @param service              the Terminology Service
	 * @param user                 the user
	 * @param refsetEditParameters the parameters for creating the refset
	 * @return the new refset or a string error
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
		if (refsetConceptId != null && doesRefsetExist(refsetConceptId, null)) {

			return "Error - Concept Id '" + refsetConceptId + "' is already used as a reference set.";
		}

		project = service.get(refsetEditParameters.getProjectId(), Project.class);

		if (project == null) {

			throw new Exception(
					"Project Id: " + refsetEditParameters.getProjectId() + " does not exist in the RT2 database");
		}

		edition = project.getEdition();

		// if a new refset concept needs to be created get the ID to use
		if (refsetConceptId == null) {

			refsetConceptId = WorkflowService.getNewRefsetId(edition.getBranch());
		}

		// create a refset branch for the new refset
		final String refsetBranchId = WorkflowService.generateBranchId();
		final String refsetBranch = WorkflowService.createRefsetBranch(edition.getBranch(), refsetConceptId,
				refsetBranchId, refsetEditParameters.isLocalSet());

		// if a new refset concept needs to be created
		if (refsetEditParameters.getRefsetId() == null) {

			// if null set the parent to "Simple Type Reference Set"
			if (parentConceptId == null) {

				parentConceptId = SIMPLE_TYPE_REFERENCE_SET;
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

			final ObjectNode relationships = mapper.createObjectNode().set("relationships", mapper.createArrayNode()
					.add(mapper.createObjectNode().put("moduleId", moduleId).put("destinationId", parentConceptId)
							.put("typeId", "116680003").put("groupId", 0).put("lang", "en").set("acceptabilityMap",
									mapper.createObjectNode().put("900000000000509007", "PREFERRED")
											.put("900000000000508004", "PREFERRED")))
					.add(mapper.createObjectNode().put("destinationId", "446609009").put("typeId", "116680003")
							.put("groupId", 0)));

			final ObjectNode classAxioms = mapper.createObjectNode().set("classAxioms",
					mapper.createArrayNode()
							.add(mapper.createObjectNode().put("moduleId", moduleId)
									.put("definitionStatusId", "900000000000074008").set("relationships",
											mapper.createArrayNode()
													.add(mapper.createObjectNode().put("moduleId", moduleId)
															.put("destinationId", "446609009")
															.put("typeId", "116680003").put("groupId", 0)))));

			final long start = System.currentTimeMillis();
			final ObjectNode body = mapper.createObjectNode().put("conceptId", refsetConceptId).put("moduleId",
					moduleId);
			body.setAll(relationships);
			body.setAll(classAxioms);
			body.setAll(descriptions);

			final String url = SnowstormConnection.getBaseUrl() + "browser/" + refsetBranch + "/" + "concepts/";

			LOG.debug("createRefset URL: " + url);
			LOG.debug("createRefset URL body: " + body.toString());

			try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

				if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

					throw new Exception("call to url '" + url + "' wasn't successful. " + response.getStatus() + ": "
							+ response.getStatusInfo().getReasonPhrase());
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

			refset.setBranchPath(getBranchPath(refset));

			try {

				final String ecl = getEclFromDefinition(refsetEditParameters.getDefinitionClauses());

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
			WorkflowService.mergeEditIntoRefsetBranch(refset.getEditionBranch(), refset.getRefsetId(), editBranchId,
					refsetBranchId, "Initial intensional refset creation.", refset.isLocalSet());
		}

		clearAllRefsetCaches(refset.getEditionBranch());

		LOG.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added. Time: "
				+ (System.currentTimeMillis() - start));
		LOG.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));

		return refset;
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
	 * Generate lists of concept ID for inclusion and exclusion clauses from a list
	 * of definition clauses.
	 *
	 * @param definitionClauses the definition clauses
	 * @param branchPath        the refset branch path
	 * @return the generated ECL statement
	 * @throws Exception the exception
	 */
	public static Map<String, List<String>> getInclusionExclusionLists(final List<DefinitionClause> definitionClauses,
			final String branchPath) throws Exception {

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
	 * @param service              the Terminology Service
	 * @param user                 the user
	 * @param refset               the refset to modify
	 * @param refsetEditParameters the parameters for creating the refset
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String modifyRefset(final TerminologyService service, final User user, final Refset refset,
			final Refset refsetEditParameters) throws Exception {

		String statusMessage = "Success";

		if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

			throw new Exception("Reference set is not in the proper status to be modified " + refset.getId());
		}

		// set user changed fields
		refset.setTags(refsetEditParameters.getTags());
		refset.setVersionNotes(refsetEditParameters.getVersionNotes());
		refset.setNarrative(refsetEditParameters.getNarrative());
		refset.setPrivateRefset(refsetEditParameters.isPrivateRefset());
		refset.setExternalUrl(refsetEditParameters.getExternalUrl());
		refset.setLocalSet(refsetEditParameters.isLocalSet());

		if (!StringUtility.isEmpty(refsetEditParameters.getModuleId())
				&& !refsetEditParameters.getModuleId().equals(refset.getModuleId())) {

			updateRefsetConcept(refset, refset.isActive(), refsetEditParameters.getModuleId());
			refset.setModuleId(refsetEditParameters.getModuleId());
		}

		// If module id is changed
		if (!refset.getModuleId().equals(refsetEditParameters.getModuleId())) {
			refset.setModuleId(refsetEditParameters.getModuleId());

			// TODO: RT2-1513: I believe there's something that needs to change in snowstorm
			// here also)
		}

		if (refset.getType().equals(Refset.EXTERNAL)) {

			refset.setName(refsetEditParameters.getName());
		}

		// update an object
		service.update(refset);

		// if this is an intensional refset update the definition
		if (refset.getType().equals(Refset.INTENSIONAL)) {

			AuditEntryHelper.updateRefsetMetadataEntry(refset, true);
			statusMessage = modifyRefsetDefinition(user, service, refset, refsetEditParameters.getDefinitionClauses());
		} else {

			AuditEntryHelper.updateRefsetMetadataEntry(refset, false);
		}

		// we also need to clear the refset export cache on S3
		final ExportHandler exportHandler = new ExportHandler();
		exportHandler.deleteFilesFromBranchPath(refset.getBranchPath());

		LOG.info("Refset " + refset.getRefsetId() + " successfully modified");
		LOG.debug("Modify Refset: Refset: " + ModelUtility.toJson(refset));
		return statusMessage;

	}

	/**
	 * Save the details of a refset before editing.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset to modify
	 * @throws Exception the exception
	 */
	public static void createRefsetEditHistory(final TerminologyService service, final User user, final Refset refset)
			throws Exception {

		final RefsetEditHistory oldHistory = service.findSingle(
				"refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + "", RefsetEditHistory.class, null);

		if (oldHistory != null) {
			return;
		}

		final RefsetEditHistory history = new RefsetEditHistory();
		history.populateFrom(refset);
		history.setId(null);
		history.setEditBranchId(null);

		// if this is an intensional refset save the definition
		if (refset.getType().equals(Refset.INTENSIONAL)) {

			final List<DefinitionClauseEditHistory> clauseHistoryList = new ArrayList<>();

			for (final DefinitionClause originalClause : refset.getDefinitionClauses()) {

				final DefinitionClauseEditHistory clauseHistory = new DefinitionClauseEditHistory(originalClause);
				clauseHistory.setId(null);
				service.add(clauseHistory);
				clauseHistoryList.add(clauseHistory);
			}

			history.setDefinitionClauses(clauseHistoryList);
		}

		// update an object
		service.add(history);

		LOG.info("Refset " + refset.getRefsetId() + " edit history saved");

		LOG.debug("createRefsetEditHistory: Refset: " + ModelUtility.toJson(refset));
	}

	/**
	 * Save the details of a refset before editing.
	 *
	 * @param service          the Terminology Service
	 * @param user             the user
	 * @param refsetInternalId the internal refset ID to modify
	 * @throws Exception the exception
	 */
	public static void replaceRefsetWithEditHistory(final TerminologyService service, final User user,
			final String refsetInternalId) throws Exception {

		LOG.debug("replaceRefsetWithEditHistory: refsetInternalId: " + refsetInternalId);

		final Refset refset = getRefset(service, user, refsetInternalId);

		final RefsetEditHistory history = service.findSingle(
				"refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + "", RefsetEditHistory.class, null);

		if (history == null) {

			return;
		}

		// TODO: Add unique Audit Entry Helper for this case

		refset.setRefsetId(history.getRefsetId());
		refset.setName(history.getName());
		refset.setType(history.getType());
		refset.setNarrative(history.getNarrative());
		refset.setVersionDate(history.getVersionDate());
		refset.setVersionNotes(history.getVersionNotes());
		refset.setVersionStatus(history.getVersionStatus());
		refset.setExternalUrl(history.getExternalUrl());
		refset.setModuleId(history.getModuleId());
		refset.setEditBranchId(null);
		refset.setPrivateRefset(history.isPrivateRefset());
		refset.setTags(new HashSet<String>(history.getTags()));
		refset.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
		refset.setAssignedUser(null);
		refset.setMemberCount(history.getMemberCount());

		service.update(refset);

		// if this is an intensional refset save the definition
		if (refset.getType().equals(Refset.INTENSIONAL)) {

			// List<DefinitionClause> clauseList = new ArrayList<>();
			//
			// for (DefinitionClauseEditHistory historyClause :
			// history.getDefinitionClauses()) {
			//
			// DefinitionClause clause = new DefinitionClause();
			// clause.setValue(historyClause.getValue());
			// clause.setNegated(historyClause.getNegated());
			//
			// service.add(clause);
			// clauseList.add(clause);
			// }
			//
			// modifyRefsetDefinition(user, service, refset, clauseList);

			for (final DefinitionClause oldClause : new ArrayList<DefinitionClause>(refset.getDefinitionClauses())) {

				refset.getDefinitionClauses().remove(oldClause);
				service.remove(oldClause);
			}

			service.update(refset);

			for (final DefinitionClauseEditHistory historyClause : history.getDefinitionClauses()) {

				final DefinitionClause clause = new DefinitionClause();
				clause.setValue(historyClause.getValue());
				clause.setNegated(historyClause.getNegated());

				service.add(clause);
				refset.getDefinitionClauses().add(clause);
			}

			service.update(refset);
		}

		LOG.info("Refset " + refset.getRefsetId() + " replaced with edit history");
		LOG.debug("replaceRefsetWithEditHistory: Refset: " + ModelUtility.toJson(refset));

	}

	/**
	 * Save the details of a refset before editing.
	 *
	 * @param service  the Terminology Service
	 * @param user     the user
	 * @param refsetId the refset ID to remove history for
	 * @throws Exception the exception
	 */
	public static void removeRefsetEditHistory(final TerminologyService service, final User user, final String refsetId)
			throws Exception {

		final RefsetEditHistory history = service.findSingle("refsetId:" + QueryParserBase.escape(refsetId) + "",
				RefsetEditHistory.class, null);

		if (history == null) {

			return;
		}

		// update an object
		service.remove(history);

		LOG.info("Refset " + refsetId + " edit history removed");
	}

	/**
	 * Add an inclusion or exclusion clause to a refset definition.
	 *
	 * @param service                 the Terminology Service
	 * @param user                    the user
	 * @param refset                  the refset to modify
	 * @param ecl                     the ecl to use for the clause
	 * @param definitionExceptionType is the exception an inclusion or exclusion
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String addDefinitionException(final TerminologyService service, final User user, final Refset refset,
			final String ecl, final String definitionExceptionType) throws Exception {

		String statusMessage = "Success";

		if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

			throw new Exception("Reference set is not in the proper status to be modified " + refset.getId());
		}

		if (!refset.getType().equals(Refset.INTENSIONAL)) {

			throw new Exception("This is not an Intensional reference set " + refset.getId());
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
			LOG.debug("addDefinitionException: Refset: " + ModelUtility.toJson(refset));
		}

		return statusMessage;
	}

	/**
	 * Remove an inclusion or exclusion clause from a refset definition.
	 *
	 * @param service               the Terminology Service
	 * @param user                  the user
	 * @param refset                the refset to modify
	 * @param definitionExceptionId the exception ID
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String removeDefinitionException(final TerminologyService service, final User user,
			final Refset refset, final String definitionExceptionId) throws Exception {

		String statusMessage = "Success";

		if (!refset.getWorkflowStatus().equals(WorkflowService.IN_EDIT)) {

			throw new Exception("Reference set is not in the proper status to be modified " + refset.getId());
		}

		if (!refset.getType().equals(Refset.INTENSIONAL)) {

			throw new Exception("This is not an Intensional reference set " + refset.getId());
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
			LOG.debug("removeDefinitionException: Refset: " + ModelUtility.toJson(refset));
		}

		return statusMessage;
	}

	/**
	 * Modify a refset definition.
	 *
	 * @param user                      the user
	 * @param service                   the service
	 * @param refset                    the refset
	 * @param modifiedDefinitionClauses the modified definition clauses
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String modifyRefsetDefinition(final User user, final TerminologyService service, final Refset refset,
			final List<DefinitionClause> modifiedDefinitionClauses) throws Exception {

		final List<String> unprocessedConcepts = new ArrayList<>();
		String statusMessage = "Success";

		if (refset.getType().equals(Refset.INTENSIONAL)) {

			final String oldDefinition = getEclFromDefinition(refset.getDefinitionClauses());
			final String newDefinition = getEclFromDefinition(modifiedDefinitionClauses);
			LOG.debug("modifyRefsetDefinition oldDefinition: " + oldDefinition);
			LOG.debug("modifyRefsetDefinition newDefinition: " + newDefinition);

			if (!oldDefinition.equals(newDefinition)) {

				final String branchPath = getBranchPath(service, refset.getId());
				List<String> oldMembersTemp = new ArrayList<>();
				List<String> newMembersTemp = new ArrayList<>();

				try {

					oldMembersTemp = RefsetMemberService.getConceptIdsFromEcl(branchPath, oldDefinition);
					newMembersTemp = RefsetMemberService.getConceptIdsFromEcl(branchPath, newDefinition);

				} catch (final Exception e) {
					throw new RestException(false, 417, "Expectation failed",
							"Error - Invalid ECL Definition - " + e.getMessage());
				}

				final List<String> oldMembers = oldMembersTemp;
				final List<String> newMembers = newMembersTemp;

				LOG.debug("modifyRefsetDefinition oldMembers: " + oldMembers);
				LOG.debug("modifyRefsetDefinition newMembers: " + newMembers);

				final List<DefinitionClause> definitionClauses = refset.getDefinitionClauses();
				final List<DefinitionClause> clausesToRemove = new ArrayList<>();

				// loop thru the existing clauses see what has been removed
				for (final DefinitionClause existingClause : definitionClauses) {

					int matchIndex = -1;

					for (final DefinitionClause newClause : modifiedDefinitionClauses) {

						if (existingClause.getId().equals(newClause.getId())) {

							// if the clause is changed then update the existing clause
							if (!existingClause.getValue().equals(newClause.getValue())
									|| (existingClause.getNegated() != newClause.getNegated())) {

								existingClause.setValue(newClause.getValue());
								existingClause.setNegated(newClause.getNegated());
								LOG.debug("modifyRefsetDefinition updating clause: " + existingClause);
								service.update(existingClause);
							}

							matchIndex = modifiedDefinitionClauses.indexOf(newClause);
							break;
						}

					}

					// if the clause still exists remove it from the new clauses, otherwise mark the
					// old clause for removal
					if (matchIndex >= 0) {

						LOG.debug("modifyRefsetDefinition removing clause from editParams: "
								+ modifiedDefinitionClauses.get(matchIndex));
						modifiedDefinitionClauses.remove(matchIndex);
					} else {

						clausesToRemove.add(existingClause);
					}

				}

				// Remove any marked clauses from the DB
				if (clausesToRemove.size() > 0) {

					for (final DefinitionClause clauseToRemove : clausesToRemove) {

						// remove an object
						LOG.debug("modifyRefsetDefinition removing clause: " + clauseToRemove);
						service.remove(clauseToRemove);
						definitionClauses.remove(clauseToRemove);
					}

				}

				// loop thru the new clauses to add to the DB
				for (final DefinitionClause newClause : modifiedDefinitionClauses) {

					LOG.debug("modifyRefsetDefinition adding clause: " + newClause);
					service.add(newClause);
				}

				// add the new clauses to the refset and save the refset
				definitionClauses.addAll(modifiedDefinitionClauses);
				service.update(refset);

				// Get the list of members to remove
				final List<String> conceptsToRemove = oldMembers.stream()
						.filter(oldMember -> !newMembers.contains(oldMember)).collect(Collectors.toList());

				if (conceptsToRemove.size() > 0) {

					LOG.debug("modifyRefsetDefinition intensional conceptsToRemove: " + conceptsToRemove);
					unprocessedConcepts.addAll(RefsetMemberService.removeRefsetMembers(service, user, refset,
							String.join(",", conceptsToRemove)));
				}

				// Get the list of members to add
				final List<String> conceptsToAdd = newMembers.stream()
						.filter(newMember -> !oldMembers.contains(newMember)).collect(Collectors.toList());

				if (conceptsToAdd.size() > 0) {

					LOG.debug("modifyRefsetDefinition intensional conceptsToAdd: " + conceptsToAdd);
					unprocessedConcepts
							.addAll(RefsetMemberService.addRefsetMembers(service, user, refset, conceptsToAdd));
				}

			}

		}

		if (unprocessedConcepts.size() > 0) {

			statusMessage = "Error - Reference set definition modified but unable to process concepts: ";

			for (final String unprocessedConcept : unprocessedConcepts) {

				statusMessage += unprocessedConcept + ", ";
			}

			statusMessage = StringUtils.removeEnd(statusMessage, ", ");
		}

		LOG.info("Refset " + refset.getRefsetId() + " definition successfully modified");
		return statusMessage;
	}

	/**
	 * Update the status a refset.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset
	 * @param active  the active
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String updatedRefsetStatus(final TerminologyService service, final User user, final Refset refset,
			final boolean active) throws Exception {

		String status = "inactivated";

		if (active) {
			status = "reactivated";
		}

		// if the refset has never been versioned before then delete it
		if (!active && !doesRefsetExist(refset.getRefsetId(), "AND (versionStatus: " + Refset.PUBLISHED + ")")) {

			refset.setActive(active);
			service.add(AuditEntryHelper.changeRefsetStatusEntry(refset));
			return deleteInDevelopmentVersion(service, user, refset, true);
		}

		// inactive the underlying refset concept
		updateRefsetConcept(refset, active, refset.getModuleId());

		// change the refset object in the DB
		refset.setActive(active);
		service.update(refset);
		service.add(AuditEntryHelper.changeRefsetStatusEntry(refset));

		return status;
	}

	/**
	 * Inactivate an underlying refset concept.
	 *
	 * @param refset   the refset
	 * @param active   the new active state
	 * @param moduleId the new module ID
	 * @throws Exception the exception
	 */
	private static void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId)
			throws Exception {

		// first retrieve the concept so all fields will be present for the update
		final String refsetId = refset.getRefsetId();
		final String branch = refset.getBranchPath();
		final String url = SnowstormConnection.getBaseUrl() + "browser/" + branch + "/" + "concepts/" + refsetId;
		final ObjectMapper mapper = new ObjectMapper();
		ObjectNode memberBody = null;

		LOG.debug("updateRefsetConcept URL: " + url);

		try (final Response response = SnowstormConnection.getResponse(url)) {

			// Only process payload if Rest call is successful
			if (response.getStatus() != Response.Status.OK.getStatusCode()) {

				throw new Exception("Unable to retrieve reference set concept: " + refsetId + " Status: "
						+ Integer.toString(response.getStatus()) + ". Error: "
						+ response.getStatusInfo().getReasonPhrase());
			}

			// create the body entity for the update call from the retrieved concept
			final String resultString = response.readEntity(String.class);
			memberBody = (ObjectNode) mapper.readTree(resultString.toString()).deepCopy();
		}

		if (active != refset.isActive()) {

			LOG.info("Changing refset concept active status to: " + active);

			// set the concept status
			memberBody.put("active", active);

			// set the concept inactivation indicator
			if (!active) {
				memberBody.put("inactivationIndicator", "OUTDATED");
			} else {
				memberBody.put("inactivationIndicator", "");
			}

			// loop thru the class axioms and set the status
			final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();

			while (axiomIterator.hasNext()) {

				final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
				axiomNode.put("active", active);
			}

			// loop thru the relationships and set the status
			final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();

			while (relationshipsIterator.hasNext()) {

				final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
				relationshipsNode.put("active", active);
			}
		}

		if (!moduleId.equals(refset.getModuleId())) {

			LOG.info("Changing Refset Concept Module ID from: " + refset.getModuleId() + " to: " + moduleId);
			memberBody.put("moduleId", moduleId);

			// loop thru the descriptions and set the moduleId
			final Iterator<JsonNode> descriptionsIterator = memberBody.get("descriptions").iterator();

			while (descriptionsIterator.hasNext()) {

				final ObjectNode descriptionNode = (ObjectNode) descriptionsIterator.next();
				descriptionNode.put("moduleId", moduleId);
			}

			// loop thru the class axioms and set the moduleId
			final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();

			while (axiomIterator.hasNext()) {

				final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
				axiomNode.put("moduleId", moduleId);

				// loop thru the axiom relationships and set the moduleId
				final Iterator<JsonNode> relationshipsIterator = axiomNode.get("relationships").iterator();

				while (relationshipsIterator.hasNext()) {

					final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
					relationshipsNode.put("moduleId", moduleId);
				}
			}

			// loop thru the relationships and set the moduleId
			final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();

			while (relationshipsIterator.hasNext()) {

				final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
				relationshipsNode.put("moduleId", moduleId);
			}
		}

		LOG.debug("updateRefsetConcept update concept URL body: " + memberBody.toString());

		// update the concept with the new data
		try (final Response response = SnowstormConnection.putResponse(url, memberBody.toString())) {

			// Only process payload if Rest call is successful
			if (response.getStatus() != Response.Status.OK.getStatusCode()) {
				throw new Exception("Unable to update reference set concept: " + refsetId + ". Status: "
						+ Integer.toString(response.getStatus()) + ". Error: "
						+ response.getStatusInfo().getReasonPhrase());
			}

			LOG.info("updateRefsetConcept refset concept: " + refsetId);
		}

	}

	/**
	 * Delete the edit version of a refset.
	 *
	 * @param service       the Terminology Service
	 * @param user          the user
	 * @param refset        the refset
	 * @param deleteConcept if the underlying refset concept should be deleted
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String deleteInDevelopmentVersion(final TerminologyService service, final User user,
			final Refset refset, final boolean deleteConcept) throws Exception {

		if (refset == null) {
			throw new RestException(false, 417, "Expectation failed", "Reference set is null and can not be removed.");

		} else if (!refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {
			throw new RestException(false, 417, "Expectation failed", "Reference set Internal Id: " + refset.getId()
					+ " is not 'In Development' and can not be removed.");
		}

		final String status = "deleted";
		final String refsetId = refset.getRefsetId();

		boolean otherVersions = false;

		// find out if the refset has been versioned before
		if (doesRefsetExist(refsetId,
				"AND (versionStatus: " + Refset.PUBLISHED + " OR versionStatus: " + Refset.BETA + ")")) {

			otherVersions = true;
		}

		deleteRefset(service, user, refset);

		// if there were other versions of this refset set the lastest version flag
		// appropriately
		if (otherVersions) {

			final Refset mostRecentVersion = getLatestRefsetVersion(service, refsetId);
			mostRecentVersion.setLatestPublishedVersion(true);
			mostRecentVersion.setHasVersionInDevelopment(false);
			service.update(mostRecentVersion);
			LOG.info("Refset " + mostRecentVersion.getId()
					+ " version marked as latest with no In Development version.");
		}

		AuditEntryHelper.addEditingCycleEntry(refset, false);

		return status;
	}

	/**
	 * Delete the refset and all edit and refset specific branches.
	 *
	 * @param service the service
	 * @param user    the user
	 * @param refset  the refset
	 * @throws Exception the exception
	 */
	public static void deleteRefset(final TerminologyService service, final User user, final Refset refset)
			throws Exception {

		// this won't happen without access to snowstorm delete branches
		// WorkflowService.deleteRefsetBranch(service, user, refset.getEditionBranch(),
		// refset.getId(), refset.getRefsetBranchId());

		// remove any edit history that exists
		RefsetService.removeRefsetEditHistory(service, user, refset.getRefsetId());

		// remove any workflow history that exists
		final ResultList<WorkflowHistory> workflowResults = WorkflowService.getWorkflowHistory(service, refset,
				new SearchParameters());

		for (final WorkflowHistory workflow : workflowResults.getItems()) {

			service.remove(workflow);
		}

		// remove the refset from the database
		service.remove(refset);
		LOG.info("Deleted refset " + refset.getRefsetId() + " In Development version from database: " + refset.getId());
	}

	/**
	 * Does a refset ID exist in the database.
	 *
	 * @param refsetId             the refset ID
	 * @param addedQueryParameters additional query string to limit the refset
	 *                             versions
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
			final ResultList<Refset> results = service.find("refsetId: " + refsetId + " " + expandedQuery, null,
					Refset.class, null);

			return !(results.getItems().isEmpty());

		}

	}

	/**
	 * Gets the list of Refset Concepts that can be used as parents to a refset or
	 * as the underlying concept for a new refset.
	 *
	 * @param service           the Terminology Service
	 * @param branch            the branch to retrieve the concepts from
	 * @param areParentConcepts Do these concepts represent parent concepts for a
	 *                          new refset, or will they be the underlying concepts
	 *                          for a the refset itself
	 * @return the list of refset concepts
	 * @throws Exception the exception
	 */
	public static ConceptResultList getRefsetConcepts(final TerminologyService service, final String branch,
			final boolean areParentConcepts) throws Exception {

		final ConceptResultList results = new ConceptResultList();
		final Set<String> existingRefsetIds = new HashSet<>();
		String ecl = StringUtility.encodeValue(QueryParserBase.escape("<<" + SIMPLE_TYPE_REFERENCE_SET));

		if (!areParentConcepts) {

			ecl = StringUtility.encodeValue(QueryParserBase.escape("<" + SIMPLE_TYPE_REFERENCE_SET));
		}

		final List<Edition> editions = getEditionForBranch(branch);

		String modules = "";

		if (editions.size() > 0) {

			for (final Edition edition : editions) {
				modules += edition.getModules().stream().collect(Collectors.joining(",")) + ", ";
			}
		}

		modules += SNOMED_CORE_MODULE_ID;

		final String url = SnowstormConnection.getBaseUrl() + branch + "/" + "concepts?ecl=" + ecl
				+ "&limit=1000&module=" + modules;

		LOG.debug("getRefsetConcepts URL: " + url);

		// If we are looking for concepts to represent a refset, then we are filtering
		// out those concept that are currently refsets
		if (!areParentConcepts) {

			// get all the existing refsets for latest branch version
			final String query = "(latestPublishedVersion: true AND hasVersionInDevelopment: false) OR versionStatus: ("
					+ Refset.IN_DEVELOPMENT + ")";
			final ResultList<Refset> refsets = service.find(query, null, Refset.class, null);

			for (final Refset refset : refsets.getItems()) {

				if (!existingRefsetIds.contains(refset.getRefsetId())) {

					existingRefsetIds.add(refset.getRefsetId());
				}

			}

			LOG.debug("getRefsetConcepts existingRefsetIds: " + existingRefsetIds);
		}

		final Set<String> excludeList = conceptsToRemove(!areParentConcepts);

		// update the concept with the new data
		try (final Response response = SnowstormConnection.getResponse(url)) {

			// Only process payload if Rest call is successful
			if (response.getStatus() != Response.Status.OK.getStatusCode()) {

				throw new Exception(
						"Unable to get reference set concepts. Status: " + Integer.toString(response.getStatus())
								+ ". Error: " + response.getStatusInfo().getReasonPhrase());
			}

			final ObjectMapper mapper = new ObjectMapper();
			final String resultString = response.readEntity(String.class);
			final JsonNode root = mapper.readTree(resultString.toString());
			final Iterator<JsonNode> iterator = root.get("items").iterator();

			// loop thru the returned member details and inactivate it or add it to the list
			// to delete
			while (iterator != null && iterator.hasNext()) {

				final JsonNode conceptNode = iterator.next();
				final Concept concept = new Concept();
				final String conceptId = conceptNode.get("conceptId").asText();

				// if this isn't for a parent concept and the refset already exists then skip it
				if ((!areParentConcepts && existingRefsetIds.contains(conceptId)) || excludeList.contains(conceptId)) {

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
	 * Returns a specific refset by internal ID.
	 *
	 * @param service the Terminology Service
	 * @param refset  the refset
	 * @param force   should the recount be forced
	 * @return the if the refset needed the count set
	 * @throws Exception the exception
	 */
	public static boolean setRefsetMemberCount(final TerminologyService service, final Refset refset,
			final boolean force) throws Exception {

		if (refset.getMemberCount() == -1 || force) {

			LOG.debug("setRefsetMemberCount Setting the member count for refset: " + refset.getId());
			refset.setMemberCount(RefsetMemberService.getMemberCount(refset));

			// save the refset
			service.update(refset);
			return true;
		}

		return false;
	}

	/**
	 * Returns a specific refset by internal ID.
	 *
	 * @param service          the Terminology Service
	 * @param user             the user
	 * @param refsetInternalId the internal refset ID
	 * @return the refset
	 * @throws Exception the exception
	 */
	public static Refset getRefset(final TerminologyService service, final User user, final String refsetInternalId)
			throws Exception {

		service.setModifiedBy(user.getUserName());
		service.setModifiedFlag(true);

		final Refset refset = service.findSingle("id:" + QueryParserBase.escape(refsetInternalId) + "", Refset.class,
				null);

		if (refset == null) {

			throw new Exception("Unable to retrieve reference set " + refsetInternalId);
		}

		setCommonRefsetProperties(service, user, refset);

		LOG.debug("getRefset: refset: " + ModelUtility.toJson(refset));
		return refset;

	}

	/**
	 * Returns a specific refset by Refset ID and version date.
	 *
	 * @param service     the Terminology Service
	 * @param user        the user
	 * @param refsetId    the refset ID
	 * @param versionDate the version date or IN DEVELOPMENT
	 * @return the refset
	 * @throws Exception the exception
	 */
	public static Refset getRefset(final TerminologyService service, final User user, final String refsetId,
			final String versionDate) throws Exception {

		service.setModifiedBy(user.getUserName());
		service.setModifiedFlag(true);

		String query = "latestPublishedVersion: true";

		if (versionDate != null && !versionDate.equals("") && !versionDate.equalsIgnoreCase(Refset.IN_DEVELOPMENT)) {

			query = "versionDate:" + versionDate;

		} else if (versionDate != null && versionDate.equalsIgnoreCase(Refset.IN_DEVELOPMENT)) {

			query = "versionStatus: " + Refset.IN_DEVELOPMENT;
		}

		final Refset refset = service.findSingle(query + " AND refsetId:" + refsetId, Refset.class, null);

		if (refset == null) {

			throw new Exception("Unable to retrieve reference set " + refsetId + " with version date: " + versionDate);
		}

		setCommonRefsetProperties(service, user, refset);

		return refset;

	}

	/**
	 * Returns a specific refset by internal ID.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset
	 * @throws Exception the exception
	 */
	public static void setCommonRefsetProperties(final TerminologyService service, final User user, final Refset refset)
			throws Exception {

		setRefsetPermissions(user, refset);
		refset.setVersionList(getSortedRefsetVersionList(refset, service, false));
		refset.setBranchPath(getBranchPath(refset));
		setRefsetMemberCount(service, refset, false);

		final List<String> editionVersions = RefsetService.getBranchVersions(refset.getEditionBranch());
		String versionDate = null;

		if (editionVersions.size() == 0) {
			editionVersions.add("Never Published"); // DateUtility.formatDate(new Date(),
													// DateUtility.DATE_FORMAT_REVERSE, null);
		}

		if (refset.getVersionDate() != null) {
			versionDate = DateUtility.formatDate(refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null);

		} else if (refset.getVersionList().size() > 1) {
			versionDate = refset.getVersionList().get(1).get("date");
		}

		refset.setTerminologyVersionDate(versionDate);

		if (versionDate == null) {

			refset.setTerminologyVersionDate(editionVersions.get(0));
			refset.setBasedOnLatestVersion(true);

		} else if (editionVersions.indexOf(versionDate) >= 0) {

			if (editionVersions.indexOf(versionDate) == 0 || editionVersions.size() == 1) {
				refset.setBasedOnLatestVersion(true);
			}

		} else {

			if (editionVersions.size() == 1) {

				refset.setTerminologyVersionDate(editionVersions.get(0));
				refset.setBasedOnLatestVersion(true);
			} else {

				String lastEditionDate = "";
				long lastDifference = 0;
				final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm:ss");
				final LocalDateTime dateOfVersion = LocalDateTime.parse(versionDate + ":00:00:00", formatter);

				for (final String editionVersion : editionVersions) {

					final LocalDateTime editionDate = LocalDateTime.parse(editionVersion + ":00:00:00", formatter);
					final long duration = Duration.between(editionDate, dateOfVersion).toDays();

					// if the duration is positive and larger than the previous then the previous is
					// the answer
					if (!lastEditionDate.equals("") && duration > 0 && lastDifference > 0
							&& lastDifference < duration) {
						break;
					}

					lastEditionDate = editionVersion;
					lastDifference = duration;
				}

				if (editionVersions.indexOf(lastEditionDate) == 0) {
					refset.setBasedOnLatestVersion(true);
				}

				refset.setTerminologyVersionDate(lastEditionDate);
			}
		}
	}

	/**
	 * Gets a list of projects ordered by name.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @return the list of found refsets
	 * @throws Exception the exception
	 */
	public static LinkedHashMap<String, Project> getUserProjects(final TerminologyService service, final User user)
			throws Exception {

		@SuppressWarnings("unchecked")
		LinkedHashMap<String, Project> userProjects = (LinkedHashMap<String, Project>) SecurityService
				.getFromSession(SecurityService.SESSION_USER_PROJECTS);

		if (userProjects != null) {

			return userProjects;
		} else {

			userProjects = new LinkedHashMap<>();
			final LinkedHashMap<String, Project> projects = getOrderedProjects(service);

			for (final Project project : projects.values()) {

				setProjectPermissions(user, project);
				userProjects.put(project.getId(), project);
			}

			return userProjects;
		}

	}

	/**
	 * Gets a list of projects ordered by name.
	 *
	 * @param service the Terminology Service
	 * @return the list of found refsets
	 * @throws Exception the exception
	 */
	public static LinkedHashMap<String, Project> getOrderedProjects(final TerminologyService service) throws Exception {

		final LinkedHashMap<String, Project> projects = new LinkedHashMap<>();
		final PfsParameter pfs = new PfsParameter();
		pfs.setAscending(true);
		pfs.setSortFields(Arrays.asList("name", "id"));

		final ResultList<Project> results = service.find("", pfs, Project.class, null);

		for (final Project project : results.getItems()) {

			projects.put(project.getId(), project);
		}

		return projects;
	}

	/**
	 * Searches for refset with filters and member concept search.
	 *
	 * @param user              the user
	 * @param service           the terminology service
	 * @param searchParameters  the search parameters
	 * @param searchConcepts    should refset members be searched
	 * @param setPermissions    should permissions and roles be set on the refsets
	 * @param setVersions       should the version list be set on the refsets
	 * @param showInDevelopment flag on whether to include IN_DEVELOPMENT refsets
	 * @param showOnlyPermitted flag on whether to only show refsets user has
	 *                          specific permission to and not general public
	 *                          refsets
	 * @return the list of found refsets
	 * @throws Exception the exception
	 */
	public static ResultList<Refset> searchRefsets(final User user, final TerminologyService service,
			final SearchParameters searchParameters, final boolean searchConcepts, final boolean setPermissions,
			final boolean setVersions, final boolean showInDevelopment, final boolean showOnlyPermitted)
			throws Exception {

		final long start = System.currentTimeMillis();
		String query = searchParameters.getQuery();
		final String elasticSearchReplaceRegEx = "[" + Pattern.quote("+=&|><!{}[]^\"~*?:\\/") + "]+?";
		final Set<String> refsetIdsFromMembers = new HashSet<>();

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

			query = URLDecoder.decode(query, StandardCharsets.UTF_8);
			searchParameters.setQuery(query);

			final List<String> directoryColumns = Arrays.asList("id", "refsetId", "name", "editionName",
					"organizationName", "versionStatus", "versionDate", "modified", "privateRefset", "editionShortName",
					"assignedUser", "projectId", "workflowStatus");
			final String[] queryParts = query.split(AND_SUFFIX);
			StringBuilder termQuery = new StringBuilder();
			StringBuilder termQueryForRt2 = new StringBuilder();
			StringBuilder filterQuery = new StringBuilder();

			for (final String queryPart : queryParts) {

				final String[] keyValue = queryPart.split(":");

				if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {

					final String value = (String.join(":", Arrays.copyOfRange(keyValue, 1, keyValue.length)))
							.replaceAll(elasticSearchReplaceRegEx, Matcher.quoteReplacement("\\") + "$0");
					filterQuery.append(keyValue[0]).append(":").append(value).append(" AND ");

				} else {

					termQuery.append(queryPart).append("*").append(AND_SUFFIX);
					termQueryForRt2.append(
							queryPart.replaceAll(elasticSearchReplaceRegEx, Matcher.quoteReplacement("\\") + "$0"))
							.append("*").append(AND_SUFFIX);
				}

			}

			// if the term query isn't empty then search members and build the full term
			// query string
			if (termQuery.length() > 0) {

				final Set<String> refsetIdsFromTermServer = new HashSet<>();
				termQuery.setLength(termQuery.length() - 5);
				termQueryForRt2.setLength(termQueryForRt2.length() - 5);

				// if it was requested search member concepts
				if (searchConcepts) {

					refsetIdsFromMembers.addAll(RefsetMemberService.searchDirectoryMembers(searchParameters));
					refsetIdsFromTermServer.addAll(refsetIdsFromMembers);

					Set<String> nonPublishedBranchPaths = new HashSet<>();

					if (showInDevelopment && !user.getName().equals(SecurityService.GUEST_USERNAME)) {
						nonPublishedBranchPaths = getInDevelopmentBranchPaths(service);
					}

					// search descriptions of Simple type reference set (foundation metadata
					// concept) "<446609009"
					refsetIdsFromTermServer.addAll(RefsetMemberService.searchMultisearchDescriptions(searchParameters,
							"<446609009", nonPublishedBranchPaths));
				}

				termQueryForRt2.insert(0, "(tags: (").append(")");

				if (!refsetIdsFromTermServer.isEmpty()) {

					getUniqueRefsetIds(service);
					refsetIdsFromTermServer.retainAll(UNIQUE_REFSET_IDS);

					if (!refsetIdsFromTermServer.isEmpty()) {
						termQueryForRt2.append(" OR refsetId:(").append(String.join(" OR ", refsetIdsFromTermServer))
								.append(")");
					}
				}

				termQueryForRt2.append(")");

			}

			// if the filter query isn't empty then prepare the query with wildcards
			if (filterQuery.length() > 0) {

				int index = filterQuery.lastIndexOf(AND_SUFFIX);
				if (index != -1 && index == filterQuery.length() - AND_SUFFIX.length()) {
					filterQuery.delete(index, filterQuery.length());
				}
				filterQuery = new StringBuilder(IndexUtility.addWildcardsToQuery(filterQuery.toString(), Refset.class));
				filterQuery.insert(0, "(").append(")");

				// if the term query isn't empty then append an 'AND' to the filter query
				if (termQuery.length() > 0) {

					filterQuery.append(AND_SUFFIX);
				}

			}

			query = filterQuery.toString() + termQueryForRt2.toString();
		}

		if (query != null && !query.equals("")) {

			query += AND_SUFFIX;
		} else {

			query = "";
		}

		String projectFilter = "(";

		// @SuppressWarnings("unchecked")
		final LinkedHashMap<String, Project> userProjects = getUserProjects(service, user);

		for (final Project project : userProjects.values()) {

			if (!project.isPrivateProject() || project.getRoles().contains(User.ROLE_VIEWER)) {

				// if only including refsets the user has specific access to make sure they have
				// access to this project
				if (showOnlyPermitted && !project.getRoles().contains(User.ROLE_VIEWER)) {
					continue;
				}

				projectFilter += "(projectId:" + project.getId();

				// if the user isn't allowed to view private refsets for this project restrict
				// them, otherwise show in development or the latest published
				// version
				if (!project.getRoles().contains(User.ROLE_VIEWER)) {

					projectFilter += " AND privateRefset: false AND latestPublishedVersion: true";
				} else {

					// if this is the directory then only show the latest published version, if it
					// is the projects then show in development or the latest
					// published version
					if (showInDevelopment) {
						projectFilter += " AND ((latestPublishedVersion: true AND hasVersionInDevelopment: false) OR versionStatus: ("
								+ Refset.IN_DEVELOPMENT + "))";
					} else {
						projectFilter += " AND latestPublishedVersion: true";
					}
				}

				projectFilter += ") OR ";
			}

		}

		query += StringUtils.removeEnd(projectFilter, " OR ") + ")";

		if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {

			query += " AND privateRefset: false";
		}

		// incase query is empty. search will fail with parentheses
		if ("()".equals(query)) {
			query = "";
		}

		LOG.debug("searchRefsets query: " + query);
		final ResultList<Refset> results = service.find(query, pfs, Refset.class, null);

		if (setPermissions || setVersions) {

			for (Refset refset : results.getItems()) {

				if (refsetIdsFromMembers.contains(refset.getRefsetId())) {
					refset.setMemberSearchMatch(true);
				}

				if (setPermissions) {

					refset = setRefsetPermissions(user, refset);
				}

				if (setVersions) {

					refset.setVersionList(getSortedRefsetVersionList(refset, service, false));
				}

			}

		}

		results.setTimeTaken(System.currentTimeMillis() - start);
		results.setTotalKnown(true);

		// LOG.debug("searchRefsets results: " + ModelUtility.toJson(results));

		return results;
	}

	/**
	 * Get the set of unique Refset IDs.
	 *
	 * @param service the Terminology Service
	 * @return the set of unique Refset IDs
	 * @throws Exception the exception
	 */
	public static Set<String> getInDevelopmentBranchPaths(final TerminologyService service) throws Exception {

		if (!BRANCH_SEARCH_CACHE.isEmpty()) {
			return BRANCH_SEARCH_CACHE;
		}

		final ResultList<Refset> results = service.find("versionStatus: " + Refset.IN_DEVELOPMENT, new PfsParameter(),
				Refset.class, null);

		for (final Refset refset : results.getItems()) {

			final String branchPath = getBranchPath(refset);
			BRANCH_SEARCH_CACHE.add(branchPath);
		}

		return BRANCH_SEARCH_CACHE;
	}

	/**
	 * Create a new version of a refset.
	 *
	 * @param service          the Terminology Service
	 * @param user             the user
	 * @param refsetInternalId the internal refset ID to base the new version on
	 * @param inEdit           should the refset be set into IN_EDIT status, if
	 *                         false it will be in READY_FOR_EDIT
	 * @return the new internal refset ID
	 * @throws Exception the exception
	 */
	public static String createNewRefsetVersion(final TerminologyService service, final User user,
			final String refsetInternalId, final boolean inEdit) throws Exception {

		Refset oldLatestVersionRefset = null;
		Refset newRefsetVersion = new Refset();
		String newInternalRefsetId = "";

		final Refset refset = getRefset(service, user, refsetInternalId);

		final ResultList<Refset> results = service.find("versionStatus: (" + Refset.IN_DEVELOPMENT + ") AND refsetId: "
				+ QueryParserBase.escape(refset.getRefsetId()), null, Refset.class, null);

		if (results.getItems().size() > 0) {

			throw new Exception(
					"There is already a version of this reference set that is 'In Development', and there can only be one");
		}

		newRefsetVersion.populateFrom(refset);

		// set automatic changed fields
		final String editBranchId = WorkflowService.generateBranchId();
		final String refsetBranchId = WorkflowService.generateBranchId();
		newRefsetVersion.setVersionDate(null);
		newRefsetVersion.setId(null);
		newRefsetVersion.setVersionStatus(Refset.IN_DEVELOPMENT);
		newRefsetVersion.setLatestPublishedVersion(false);
		newRefsetVersion.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
		newRefsetVersion.setEditBranchId(editBranchId);
		newRefsetVersion.setRefsetBranchId(refsetBranchId);

		// Add an object
		service.add(newRefsetVersion);
		newInternalRefsetId = newRefsetVersion.getId();

		// create a refset and edit branch for the new refset
		WorkflowService.createRefsetBranch(refset.getEditionBranch(), refset.getRefsetId(), refsetBranchId,
				refset.isLocalSet());
		WorkflowService.createEditBranch(service, user, newRefsetVersion, editBranchId);

		// Add a workflow history entry for CREATE
		WorkflowService.addWorkflowHistory(service, user, WorkflowService.CREATE, newRefsetVersion, "");

		// update the workflow to IN_EDIT if required
		if (inEdit) {
			newRefsetVersion = WorkflowService.setWorkflowStatus(service, user, WorkflowService.EDIT, newRefsetVersion,
					"", WorkflowService.IN_EDIT, user.getUserName());
		}

		// find the previous latest version
		if (refset.isLatestPublishedVersion()) {
			oldLatestVersionRefset = refset;
		} else {
			oldLatestVersionRefset = service.findSingle(
					"refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + " AND latestPublishedVersion: true",
					Refset.class, null);
		}

		// update the previous latest version so it no longer is marked as latest
		if (oldLatestVersionRefset != null) {

			// update an object
			oldLatestVersionRefset.setHasVersionInDevelopment(true);
			service.update(oldLatestVersionRefset);
			LOG.info("Refset " + oldLatestVersionRefset.getId() + " version marked as having in development version.");
		}

		LOG.info("Refset " + newRefsetVersion.getRefsetId() + " version ID '" + newInternalRefsetId
				+ "' successfully added");
		LOG.debug("createNewRefsetVersion: Refset: " + ModelUtility.toJson(newRefsetVersion));

		return newInternalRefsetId;
	}

	/**
	 * Get the latest version of a refset without using the latest version flag.
	 *
	 * @param service  the Terminology Service
	 * @param refsetId the refset ID
	 * @return the refset version
	 * @throws Exception the exception
	 */
	public static Refset getLatestRefsetVersion(final TerminologyService service, final String refsetId)
			throws Exception {

		Refset refsetLatestVersion = null;

		final PfsParameter pfs = new PfsParameter();
		pfs.setSort("versionDate");
		pfs.setAscending(false);

		final ResultList<Refset> results = service.find("refsetId: " + QueryParserBase.escape(refsetId), pfs,
				Refset.class, null);

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

	/**
	 * Get the set of unique Refset IDs.
	 *
	 * @param service the Terminology Service
	 * @return the set of unique Refset IDs
	 * @throws Exception the exception
	 */
	public static Set<String> getUniqueRefsetIds(final TerminologyService service) throws Exception {

		if (!UNIQUE_REFSET_IDS.isEmpty()) {
			return UNIQUE_REFSET_IDS;
		}

		final ResultList<Refset> results = service
				.find("(latestPublishedVersion: true AND hasVersionInDevelopment: false) OR (versionStatus: "
						+ Refset.IN_DEVELOPMENT + ")", new PfsParameter(), Refset.class, null);

		for (final Refset refset : results.getItems()) {
			UNIQUE_REFSET_IDS.add(refset.getRefsetId());
		}

		return UNIQUE_REFSET_IDS;
	}

	/**
	 * Get the branch and version path for a refset from the internal refset ID.
	 *
	 * @param service          the Terminology Service
	 * @param refsetInternalId the internal ID of the refset
	 * @return the branch and version path
	 * @throws Exception the exception
	 */
	public static String getBranchPath(final TerminologyService service, final String refsetInternalId)
			throws Exception {

		final Refset refset = getRefsetFromInternalId(service, refsetInternalId);
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

		if (!refset.isLocalSet()) {

			if (refset.getVersionDate() != null) {

				final Date tmpDate = refset.getVersionDate();
				pathDate = "/" + DateUtility.formatDate(tmpDate, DateUtility.DATE_FORMAT_REVERSE, null);
				branchPath = refset.getEditionBranch() + pathDate;
			} else {

				if (Arrays.asList(WorkflowService.IN_EDIT, WorkflowService.IN_UPGRADE)
						.contains(refset.getWorkflowStatus())) {
					branchPath = WorkflowService.getEditBranchPath(refset.getEditionBranch(), refset.getRefsetId(),
							refset.getEditBranchId(), refset.getRefsetBranchId(), refset.isLocalSet());
				} else {
					branchPath = WorkflowService.getRefsetBranchPath(refset.getEditionBranch(), refset.getRefsetId(),
							refset.getRefsetBranchId(), refset.isLocalSet());
				}
			}
		} else {

			// for localsets if it isn't being edited always pull from the refset branch
			if (Arrays.asList(WorkflowService.IN_EDIT, WorkflowService.IN_UPGRADE)
					.contains(refset.getWorkflowStatus())) {
				branchPath = WorkflowService.getEditBranchPath(refset.getEditionBranch(), refset.getRefsetId(),
						refset.getEditBranchId(), refset.getRefsetBranchId(), refset.isLocalSet());
			} else {
				branchPath = WorkflowService.getRefsetBranchPath(refset.getEditionBranch(), refset.getRefsetId(),
						refset.getRefsetBranchId(), refset.isLocalSet());
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
	 * @param service          the Terminology Service
	 * @param refsetInternalId the internal ID of the refset
	 * @return the refset
	 * @throws Exception the exception
	 */
	public static Refset getRefsetFromInternalId(final TerminologyService service, final String refsetInternalId)
			throws Exception {

		Refset refset = null;

		refset = service.get(refsetInternalId, Refset.class);

		if (refset == null) {

			throw new Exception(
					"Reference set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
		}

		return refset;
	}

	/**
	 * Set the user permissions for a refset.
	 *
	 * @param user    the user
	 * @param project the project
	 * @return the refset with permissions
	 * @throws Exception the exception
	 */
	public static Project setProjectPermissions(final User user, final Project project) throws Exception {

		final List<String> roles = project.getRoles();
		setRoles(user, project, roles);
		project.setRoles(roles);

		return project;
	}

	/**
	 * Set the user permissions for a refset.
	 *
	 * @param user   the user
	 * @param refset the refset
	 * @return the refset with permissions
	 * @throws Exception the exception
	 */
	public static Refset setRefsetPermissions(final User user, final Refset refset) throws Exception {

		refset.setDownloadable(true);
		refset.setFeedbackVisible(true);
		refset.setAvailableActions(WorkflowService.getAllowedActions(user, refset));

		final Project project = refset.getProject();
		final List<String> roles = refset.getRoles();
		boolean userCanView = true;

		setRoles(user, project, roles);
		project.setRoles(roles);

		// make sure the user is allowed to view this refset
		if (project.isPrivateProject() && !project.getRoles().contains(User.ROLE_VIEWER)) {
			userCanView = false;

		} else if (!project.getRoles().contains(User.ROLE_VIEWER)
				&& refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {
			userCanView = false;
		}

		if (!userCanView) {

			final String message = "User does not have permission to view this reference set.";
			throw new RestException(false, 403, "Forbidden", message);
		}

		return refset;
	}

	/**
	 * set the list of roles a user has for a project.
	 *
	 * @param user    the user
	 * @param project the project
	 * @param roles   the role list to populate
	 * @return the list of roles for the project
	 * @throws Exception the exception
	 */
	public static List<String> setRoles(final User user, final Project project, final List<String> roles)
			throws Exception {

		boolean giveViewerRole = false;

		if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

			roles.add(User.ROLE_AUTHOR);
			giveViewerRole = true;
		}

		if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {

			roles.add(User.ROLE_REVIEWER);
			giveViewerRole = true;
		}

		if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {

			roles.add(User.ROLE_ADMIN);
			giveViewerRole = true;
		}

		if (user.doesUserHavePermission(User.ROLE_VIEWER, project) || giveViewerRole) {

			roles.add(User.ROLE_VIEWER);
		}

		return roles;
	}

	/**
	 * Generate a list of version dates sorted in descending order.
	 *
	 * @param refset        the refset
	 * @param service       the Terminology Service
	 * @param sortAscending should the versions be sorted in ascending order
	 * @return the list of version dates sorted in descending order
	 * @throws Exception the exception
	 */
	public static List<Map<String, String>> getSortedRefsetVersionList(final Refset refset,
			final TerminologyService service, final boolean sortAscending) throws Exception {

		final List<Map<String, String>> versionList = new ArrayList<>();
		final PfsParameter pfs = new PfsParameter();
		pfs.setSort("versionDate");
		pfs.setAscending(sortAscending);
		Map<String, String> inDevelopmentVersion = null;

		final ResultList<Refset> results = service.find("refsetId: " + QueryParserBase.escape(refset.getRefsetId()),
				pfs, Refset.class, null);

		for (final Refset refsetVersion : results.getItems()) {

			final Map<String, String> version = new HashMap<>();
			version.put("status", refsetVersion.getVersionStatus());
			version.put("refsetInternalId", refsetVersion.getId());

			if (refsetVersion.getVersionStatus().equals(Refset.IN_DEVELOPMENT)
					&& refset.getRoles().contains(User.ROLE_VIEWER)) {

				version.put("date", DateUtility.formatDate(new Date(), DateUtility.DATE_FORMAT_REVERSE, null));
				inDevelopmentVersion = new HashMap<>(version);

			} else if (Refset.PUBLISHED.equals(refsetVersion.getVersionStatus())) {

				version.put("date",
						DateUtility.formatDate(refsetVersion.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null));
				versionList.add(version);
			}

			if (inDevelopmentVersion != null) {

				if (sortAscending) {

					versionList.add(inDevelopmentVersion);
				} else {

					versionList.add(0, inDevelopmentVersion);
				}

			}

		}

		return versionList;
	}

	/**
	 * Search for refsets for display in dropdown options.
	 *
	 * @param user             the user
	 * @param service          the terminology service
	 * @param searchParameters the search parameters
	 * @param setPermissions   should permissions and roles be set on the refsets
	 * @param setVersions      should the version list be set on the refsets
	 * @return the upgrade replacement concept result list
	 * @throws Exception the exception
	 */
	public static ResultList<Refset> refsetDropdownSearch(final User user, final TerminologyService service,
			final SearchParameters searchParameters, final boolean setPermissions, final boolean setVersions)
			throws Exception {

		if (searchParameters.getLimit() <= 0) {

			searchParameters.setLimit(10);
		}

		// set the query appropriately based on what was passed in
		if (NumberUtils.isNumber(searchParameters.getQuery())) {

			searchParameters.setQuery("refsetId:" + searchParameters.getQuery());
		} else {

			searchParameters.setQuery("name:" + searchParameters.getQuery());
		}

		final ResultList<Refset> refsets = searchRefsets(user, service, searchParameters, false, setPermissions,
				setVersions, true, false);

		LOG.debug("refsetDropdownSearch: results: " + ModelUtility.toJson(refsets));

		return refsets;
	}

	/**
	 * Generate a list of line strings from a file removing empty lines.
	 *
	 * @param conceptFile the input file
	 * @param fileType    the type of file (list or rf2)
	 * @return the line string List
	 * @throws Exception the exception
	 */
	public static List<String> getConceptIdsFromFile(final MultipartFile conceptFile, final String fileType)
			throws Exception {

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

				} catch (final Exception e) {

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
	 * Fetch editions for given branch.
	 *
	 * @param branch the branch
	 * @return List <Edition> list of editions matching branch
	 * @throws Exception the exception
	 */
	private static List<Edition> getEditionForBranch(final String branch) throws Exception {

		ResultList<Edition> editions = new ResultList<>();

		try (final TerminologyService service = new TerminologyService()) {

			editions = service.find("active:true AND branch:" + QueryParserBase.escape(branch), null, Edition.class,
					null);
		}

		return (editions != null) ? editions.getItems() : new ArrayList<Edition>();
	}

	/**
	 * Get a branch version cache collection for a branch path.
	 *
	 * @param branchPath the branch path of cache collection to return
	 * @return the cache collection
	 * @throws Exception the exception
	 */
	public static List<String> getCacheForBranchVersions(final String branchPath) throws Exception {

		if (BRANCH_VERSION_CACHE.containsKey(branchPath)) {

			return BRANCH_VERSION_CACHE.get(branchPath);
		} else {

			return new ArrayList<>();
		}

	}

	/**
	 * Clear all caches related to refsets.
	 *
	 * @param branchPath the branch to clear the cache collections for
	 * @throws Exception the exception
	 */
	public static void clearAllRefsetCaches(final String branchPath) throws Exception {

		if (branchPath != null) {

			LOG.debug("clearAllRefsetCaches: Clearing caches for branch path: " + branchPath);
			BRANCH_VERSION_CACHE.remove(branchPath);

		} else {

			LOG.debug("clearAllRefsetCaches: Clearing caches for all branches");
			BRANCH_VERSION_CACHE.clear();
		}

		BRANCH_SEARCH_CACHE.clear();
		UNIQUE_REFSET_IDS.clear();

	}

	/**
	 * Get a sorted list of versions for a branch.
	 *
	 * @param editionPath the branch path of the edition
	 * @return the map
	 * @throws Exception the exception
	 */
	public static List<String> getBranchVersions(final String editionPath) throws Exception {

		final String url = SnowstormConnection.getBaseUrl() + "branches/" + editionPath
				+ "/children?immediateChildren=true";
		final List<String> branchCache = getCacheForBranchVersions(editionPath);

		// check if the concept call has been cached
		if (branchCache.size() > 0) {

			LOG.debug("getBranchVersions USING CACHE");
			return branchCache;
		}

		try (final Response response = SnowstormConnection.getResponse(url)) {

			// Only process payload if Rest call is successful
			if (response.getStatus() != Response.Status.OK.getStatusCode()) {

				throw new Exception("Unable to get edition versions. Status: " + Integer.toString(response.getStatus())
						+ ". Error: " + response.getStatusInfo().getReasonPhrase());
			}

			final String resultString = response.readEntity(String.class);
			final ObjectMapper mapper = new ObjectMapper();
			final JsonNode root = mapper.readTree(resultString.toString());
			final Iterator<JsonNode> branchIterator = root.iterator();

			// get versions from edition as long as active & within edition's module
			while (branchIterator.hasNext()) {

				final JsonNode child = branchIterator.next();
				final String childBranch = child.get("path").asText();
				String childDate = childBranch.replace(editionPath, "");

				if (childDate.startsWith("/")) {

					childDate = childDate.substring(1);
				}

				// Only get pure date branches
				if (childDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {

					final Date branchDate = DateUtility.getDate(childDate, DateUtility.DATE_FORMAT_REVERSE, null);

					if (branchDate.before(new Date())) {

						branchCache.add(childDate);
					}

				}

				// stop when branch does not start with a date
				else if (!childDate.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {

					break;
				}

			}

			// sort the results in reverse order since that is the usual way they are
			// consumed
			Collections.sort(branchCache, (o1, o2) -> (o2.compareTo(o1)));
		}

		BRANCH_VERSION_CACHE.put(editionPath, branchCache);
		return branchCache;
	}

	/**
	 * Request access to the refset for the specified ID.
	 *
	 * @param user             the user
	 * @param service          the service
	 * @param refsetInternalId the refset internal id
	 * @param comments         the comments
	 * @throws Exception the exception
	 */
	public void requestRefsetAccess(final User user, final TerminologyService service, final String refsetInternalId,
			final String comments) throws Exception {

		final Refset refset = service.get(refsetInternalId, Refset.class);
		final Project project = refset.getProject();
		final String projectAdminEmail = project.getPrimaryContactEmail();
		final String subject = "Reference set Request: " + refset.getName() + " (" + refset.getRefsetId() + ")";
		final String body = "A user is requesting access to a project you administer.\n\n" + "Edition: "
				+ refset.getEditionName() + "\n" + "Project: " + project.getName() + "\n" + "Reference set: "
				+ refset.getName() + " (" + refset.getRefsetId() + ")" + "\n" + "User: " + user.getName() + " ("
				+ user.getEmail() + ")" + "\n\n" + "Comments: " + comments;

		EmailUtility.sendEmail(subject, user.getEmail(), projectAdminEmail, body);
	}

	/**
	 * Concepts to remove.
	 *
	 * @param excludeCurrentSiRefsets the exclude current si refsets
	 * @return the sets the
	 */
	private static Set<String> conceptsToRemove(final boolean excludeCurrentSiRefsets) {

		final Set<String> conceptCodes = new HashSet<>();
		final String[] refsetExclude = PropertyUtility.getProperty("refset-copy-concept-exclude").split("\\|");

		for (int i = 0; i < refsetExclude.length; i++) {

			conceptCodes.add(refsetExclude[i]);
			i++;
		}

		if (excludeCurrentSiRefsets) {

			final String[] currentSiRefsets = PropertyUtility.getProperty("current_si_refsets").split("\\|");

			for (int i = 0; i < currentSiRefsets.length; i++) {

				conceptCodes.add(currentSiRefsets[i]);
				i++;
			}

		}

		return conceptCodes;
	}

	/**
	 * Convert the intensional refset to be extensional as new version of same
	 * refset i.e., without changing refsetId.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String convertToExtensional(final TerminologyService service, final User user, final Refset refset)
			throws Exception {

		final String status = "convert";
		LOG.debug("refset is: " + refset);

		if (!refset.getType().equals(Refset.INTENSIONAL)) {
			throw new Exception(
					"Reference set Internal Id: " + refset.getId() + " is not 'Intensional' and can not be converted.");
		}

		// Convert Metadata
		refset.setType(Refset.EXTENSIONAL);
		refset.getDefinitionClauses().clear();

		final Refset updatedRefset = service.update(refset);

		service.add(AuditEntryHelper.convertToExtensionalRefsetEntry(updatedRefset));
		LOG.info("Converted refset from database: " + updatedRefset);

		return status;
	}

	/**
	 * Share refset.
	 *
	 * @param user              the user
	 * @param refsetInternalId  the refset internal id
	 * @param recipient         the recipient
	 * @param additionalMessage the additional message
	 * @throws Exception the exception
	 */
	public static void shareRefset(final User user, final String refsetInternalId, final String recipient,
			final String additionalMessage) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			if (recipient == null || recipient.isEmpty()) {

				final String message = "There was no recipient email provided.";
				throw new RestException(false, 417, "Expectation failed", message);
			}

			final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

			final StringBuffer emailBody = new StringBuffer();
			final String version = (refset.getVersionDate() != null)
					? refset.getVersionDate().toString().substring(0, 10)
					: refset.getVersionStatus();

			final String refsetUrl = appUrlRoot + "/details/" + refset.getRefsetId() + "/"
					+ StringUtility.encodeValue(version).replace("+", "%20");

			// Title
			emailBody.append("Hello, ").append(recipient).append(",").append(System.getProperty("line.separator"))
					.append(System.getProperty("line.separator"));

			// Main announcement
			emailBody.append("A SNOMED International Reference Set Tool user named '").append(user.getUserName())
					.append("' (").append(user.getEmail()).append(") would like to share the reference set named: ")
					.append(refset.getName()).append(" with you. Here is a direct link to access that reference set: ")
					.append(refsetUrl).append(System.getProperty("line.separator"))
					.append(System.getProperty("line.separator"));

			// Additional Info from Sender
			if (!StringUtils.isBlank(additionalMessage)) {

				emailBody.append("In addition, they have included the additional message:")
						.append(System.getProperty("line.separator"));
				emailBody.append(additionalMessage).append(System.getProperty("line.separator"))
						.append(System.getProperty("line.separator"));
			}

			// Warning
			emailBody.append("If this email was received in error, you can safely ignore it.")
					.append(System.getProperty("line.separator"));
			emailBody.append(System.getProperty("line.separator"));

			// Signature
			emailBody.append("Thank you,").append(System.getProperty("line.separator"));
			emailBody.append("The SNOMED International Reference Set Tooling Team");

			final String action = SHARE_ACTION;
			EmailUtility.sendEmail(EMAIL_SUBJECT + action, null, new HashSet<>(Arrays.asList(recipient)),
					emailBody.toString());

			AuditEntryHelper.sendCommunicationEmailEntry(refset, action, user.getUserName(), recipient);
		}

	}

	/**
	 * Request project access.
	 *
	 * @param user              the user
	 * @param refsetInternalId  the refset internal id
	 * @param recipient         the recipient
	 * @param additionalMessage the additional message
	 * @throws Exception the exception
	 */
	public static void requestProjectAccess(final User user, final String refsetInternalId, final String recipient,
			final String additionalMessage) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
			final Project project = refset.getProject();
			final Organization organization = project.getEdition().getOrganization();

			final Map<String, User> adminEmailRecipients = new HashMap<>();
			final List<Team> adminTeams = new ArrayList<>();
			final List<Team> allTeams = new ArrayList<>();

			if (project.getPrimaryContactEmail() != null && !project.getPrimaryContactEmail().isEmpty()) {

				final User projectUser = new User();
				projectUser.setEmail(project.getPrimaryContactEmail());
				projectUser.setUserName("ProjectPrimaryEmail");
				projectUser.setName("Project Primary Email");
				adminEmailRecipients.put(project.getPrimaryContactEmail(), projectUser);

			} else if (organization.getPrimaryContactEmail() != null
					&& !organization.getPrimaryContactEmail().isEmpty()) {

				final User organizationUser = new User();
				organizationUser.setEmail(organization.getPrimaryContactEmail());
				organizationUser.setUserName("OrganizationPrimaryEmail");
				organizationUser.setName("Organization Primary Email");
				adminEmailRecipients.put(organization.getPrimaryContactEmail(), organizationUser);
			}

			for (final String teamId : project.getTeams()) {

				final Team team = TeamService.getTeam(teamId, true);
				allTeams.add(team);

				// Identify Admins who each get an email
				if (team.getRoles().stream().anyMatch(r -> r.equals("ADMIN"))) {

					adminTeams.add(team);
				}

			}

			// if there are no teams add organization admin team
			if (allTeams.size() == 0) {

				final Team organizationAdminTeam = OrganizationService.getActiveOrganizationAdminTeam(service,
						organization.getId());

				if (organizationAdminTeam != null) {
					allTeams.add(organizationAdminTeam);
				}
			}

			// Verify not already in project before sending emails to admins
			for (final Team team : allTeams) {

				if (team.getMemberList().stream().anyMatch(u -> u.getId().equals(user.getId()))) {

					final String message = "User: " + user.getUserName() + " is already a member of team: "
							+ team.getName() + "in  project: " + project.getName() + " under "
							+ project.getEdition().getName() + ".";
					throw new RestException(false, 417, "Expectation failed", message);
				}

			}

			// Add to admin list
			adminTeams.stream().forEach(team -> team.getMemberList().stream()
					.forEach(teamUser -> adminEmailRecipients.put(teamUser.getEmail(), teamUser)));

			// make sure there is at least once recipient
			if (adminEmailRecipients.size() == 0) {

				final String message = "There are no emails set up to request access from in project: "
						+ project.getName() + " under " + project.getEdition().getName() + ".";
				throw new RestException(false, 417, "Expectation failed", message);
			}

			/** Create Email **/
			final StringBuffer emailBody = new StringBuffer();

			// Greeting
			emailBody.append("Hello, {projectAdminName},").append(System.getProperty("line.separator"))
					.append(System.getProperty("line.separator"));

			// Static Message
			emailBody.append(user.getName()).append(" (").append(user.getEmail()).append(") has requested access to ")
					.append(project.getName()).append(" via the " + refset.getName()).append(".")
					.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"));

			// Additional Info from Sender
			if (additionalMessage != null) {

				emailBody.append(user.getName()).append(" has included the additional message in their request:")
						.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"))
						.append(additionalMessage).append(System.getProperty("line.separator"))
						.append(System.getProperty("line.separator"));
			}

			// Warning
			emailBody.append("Users can be added and configured through the SNOMED CT Reference Set Tool Team pages. ")
					.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"));

			emailBody.append("Go to the Reference Set Tool: ").append(appUrlRoot)
					.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"));

			emailBody.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"))
					.append(System.getProperty("line.separator"));

			// Signature
			emailBody.append("Note that this email has been sent to the other ADMIN teams on this project.");

			for (final User adminRecipient : adminEmailRecipients.values()) {

				AuditEntryHelper.sendCommunicationEmailEntry(refset, "Request access (via reference set)",
						adminRecipient.getUserName(), project.getName() + "'s admins");
				final Set<String> adminEmail = new HashSet<>();

				adminEmail.add(adminRecipient.getEmail());
				EmailUtility.sendEmail(EMAIL_SUBJECT + " access requested", null, adminEmail,
						emailBody.toString().replace("{projectAdminName}", adminRecipient.getName()));
			}

		}

	}

	/**
	 * Copy refset.
	 *
	 * @param service            the service
	 * @param user               the user
	 * @param refsetInternalId   the refset internal id
	 * @param name               the name
	 * @param projectId          the project id
	 * @param localSet           the local set
	 * @param privateRefset      the private refset
	 * @param comboSet           the combo set
	 * @param narrative          the narrative
	 * @param tags               the tags
	 * @param parentConceptId    the parent concept id
	 * @param newRefsetConceptId the new refset concept id
	 * @return the object
	 * @throws Exception the exception
	 */
	public static Object copyRefset(final TerminologyService service, final User user, final String refsetInternalId,
			final String name, final String projectId, final Boolean localSet, final Boolean privateRefset,
			final Boolean comboSet, final String narrative, final Set<String> tags, final String parentConceptId,
			final String newRefsetConceptId) throws Exception {
		// TODO: Add unique Audit Entry Helper for this case

		String newRefsetInternalId = null;
		final Refset originalRefset = getRefset(service, user, refsetInternalId);
		String projectIdToGet = projectId;

		if (projectIdToGet == null || projectIdToGet.isEmpty()) {

			projectIdToGet = originalRefset.getProjectId();
		}

		Project project = service.get(projectIdToGet, Project.class);

		if (originalRefset.isPrivateRefset() && !originalRefset.getRoles().contains(User.ROLE_VIEWER)) {

			throw new Exception(
					"User does not have the permission to copy this reference set " + originalRefset.getRefsetId());
		}

		if (project == null) {

			throw new Exception("Project Id: " + projectId + " does not exist in the RT2 database");
		}

		project = setProjectPermissions(user, project);

		if (!project.getRoles().contains(User.ROLE_AUTHOR) && !project.getRoles().contains(User.ROLE_ADMIN)) {
			throw new Exception(
					"User does not have the permission to create a reference set in this project " + project.getName());
		}

		Refset newRefset = new Refset(originalRefset);
		newRefset.setId(null);
		newRefset.setProject(project);
		newRefset.setActive(true);
		newRefset.setVersionStatus(null);
		newRefset.setWorkflowStatus(null);
		newRefset.setVersionNotes("");
		newRefset.setLatestPublishedVersion(false);
		newRefset.setMemberCount(0);

		if (newRefsetConceptId == null || newRefsetConceptId.isEmpty()) {

			newRefset.setRefsetId(null);

			if (name == null || name.equals("")) {

				throw new Exception("A name for the new reference set must be supplied");
			}

			newRefset.setName(name);

		} else {

			newRefset.setRefsetId(newRefsetConceptId);
		}

		if (narrative != null) {

			newRefset.setNarrative(narrative);
		}

		if (tags != null) {

			newRefset.setTags(tags);
		}

		if (parentConceptId != null) {

			newRefset.setParentConceptId(parentConceptId);
		}

		if (localSet != null) {

			newRefset.setLocalSet(localSet);
		}

		if (comboSet != null) {

			newRefset.setComboRefset(comboSet);
		}

		if (privateRefset != null) {

			newRefset.setPrivateRefset(privateRefset);
		}

		// Fix Descriptions
		for (final Map<String, String> descriptionMap : newRefset.getDescriptions()) {

			for (final String key : descriptionMap.keySet()) {

				final String description = descriptionMap.get(key);

				if (description.toLowerCase().contains(originalRefset.getName().toLowerCase())) {

					// Replace the name-based aspects of the description
					// TODO: I'm making everying to lower case for expediency. Rather than a hard
					// replace, find index and replcae with original desc & requested
					// name i.e.,
					// without altering case in new description
					descriptionMap.put(key, description.toLowerCase().replaceAll(originalRefset.getName().toLowerCase(),
							name.toLowerCase()));
				}

			}

		}

		// fix Narrative
		if (newRefset.getNarrative().toLowerCase().contains(originalRefset.getName().toLowerCase())) {

			// Replace the name-based aspects of the description
			// TODO: I'm making everying to lower case for expediency. Rather than a hard
			// replace, find index and replcae with original desc & requested name
			// i.e., without
			// altering case in new description
			newRefset.setNarrative(newRefset.getNarrative().toLowerCase()
					.replaceAll(originalRefset.getName().toLowerCase(), name.toLowerCase()));
		}

		if (newRefset.getType().equals(Refset.INTENSIONAL)) {

			// clear definition clauses IDs
			for (final DefinitionClause clause : newRefset.getDefinitionClauses()) {

				clause.setId(null);
			}

		}

		// create the new refset object
		final Object returned = createRefset(service, user, newRefset);

		if (returned instanceof String) {

			return returned;
		}

		newRefset = (Refset) returned;
		newRefsetInternalId = newRefset.getId();

		try {

			final SearchParameters searchParameters = new SearchParameters();
			final ConceptResultList members = RefsetMemberService.getRefsetMembers(service, user,
					originalRefset.getId(), searchParameters, "list", null);
			final List<String> conceptIds = new ArrayList<>();

			if (newRefset.getType().equals(Refset.EXTENSIONAL)) {

				members.getItems().stream().forEach(member -> conceptIds.add(member.getCode()));
				RefsetMemberService.addRefsetMembers(service, user, newRefset, conceptIds);
			}

			LOG.info("Copied refset from " + refsetInternalId + ": " + newRefset);

		} catch (final Exception e) {

			throw new Exception(e);

		} finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(newRefsetInternalId);
		}

		return newRefset;
	}

	/**
	 * Reset refset.
	 *
	 * @param service  the service
	 * @param user     the user
	 * @param refsetId the refset id
	 * @return the string
	 * @throws Exception the exception
	 */
	public static String resetRefset(final TerminologyService service, final User user, final String refsetId)
			throws Exception {

		if (!RefsetService.doesRefsetExist(refsetId, null)) {

			service.find("refsetId: " + refsetId, null, Refset.class, null);
			return "unnecessary as it doesn't reside in RT2";
		}

		final Refset latestVersion = RefsetService.getLatestRefsetVersion(service, refsetId);

		// if the refset has never been versioned before then delete it
		if (!RefsetService.doesRefsetExist(refsetId,
				"AND (versionStatus: " + Refset.PUBLISHED + " OR versionStatus: " + Refset.BETA + ")")) {

			RefsetService.deleteInDevelopmentVersion(service, user, latestVersion, true);
		}

		final ResultList<Refset> results = service.find("refsetId: " + refsetId, null, Refset.class, null);

		for (final Refset refset : results.getItems()) {

			service.add(AuditEntryHelper.resetRefsetEntry(refset));

			RefsetService.deleteRefset(service, user, refset);

		}

		/** Now that refset deleted, resync **/
		final boolean testingStatus = SyncAgent.isTesting();

		SyncAgent.setRefsetToSync(refsetId, latestVersion.getEditionShortName());
		SyncAgent.sync(service);
		SyncAgent.setTesting(testingStatus);

		LOG.info("Successfully reset all versions in database of refsetId: " + refsetId);

		return "successfully";

	}

	/**
	 * Invite user to refset.
	 *
	 * @param authUser          the auth user
	 * @param refsetInternalId  the refset id
	 * @param recipientEmail    the recipient email
	 * @param additionalMessage the additional message
	 * @throws Exception the exception
	 */
	public static void inviteUserToOrganization(final User authUser, final String refsetInternalId,
			final String recipientEmail, final String additionalMessage) throws Exception {

		if (StringUtils.isBlank(recipientEmail)) {

			throw new Exception("Recipient must have an email address to invite to reference set.");
		}

		// TODO: move this URL to properties.
		final String accountSetupUrl = "https://confluence.ihtsdotools.org/display/ILS/Confluence+User+Accounts";

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = getRefset(service, authUser, refsetInternalId);
			final String organizationName = refset.getOrganizationName();
			final String editionName = refset.getEdition().getShortName();

			if (!authUser.checkPermission(User.ROLE_VIEWER, organizationName, editionName, null)) {
				throw new RestException(false, 403, "Forbidden",
						"This user does not have permission to perform this action");
			}

			final User crowdUser = CrowdAPIClient.findUserByEmail(recipientEmail.trim());
			// final boolean isCrowdMember = (crowdUser != null);

			// TODO: Determine needs of hasMembership based on approach implemented
			if (crowdUser != null) {
				// final Set<String> memberships =
				// CrowdAPIClient.getMembershipsForUser(crowdUser.getUserName());
				// final boolean hasMemberships = (memberships != null) ?
				// memberships.stream().anyMatch(m -> m.startsWith("rt2-")) : false;

				// Ensure not already members of the organization
				if (refset.getEdition().getOrganization().getMembers().stream()
						.anyMatch(u -> u.getId().equals(crowdUser.getId()))) {
					throw new Exception("User: " + crowdUser.getUserName() + " is already a member of organization: "
							+ refset.getOrganizationName());
				}
			}

			// add in invite request
			final InviteRequest request = new InviteRequest();
			request.setAction(INVITE_ACTION);
			request.setActive(true);
			request.setRequester(authUser.getId());
			request.setRecipientEmail(recipientEmail);
			request.setPayload("refset:" + refsetInternalId);

			service.setModifiedBy(authUser.getUserName());
			service.add(request);

			final String acceptUrl = appUrlRoot + "/invite/response?ir=" + request.getId() + "&r=true";
			final String declineUrl = appUrlRoot + "/invite/response?ir=" + request.getId() + "&r=false";

			final String ahrefStyle = "'padding: 8px 12px; border: 1px solid #c3e7fe;border-radius: 2px;"
					+ "font-family: Helvetica, Arial, sans-serif;font-size: 14px; color: #000000;"
					+ "text-decoration: none;font-weight:bold;display: inline-block;'";
			final String button = "<table style='width: 100%; padding-right: 50px; padding-left: 50px'><tr><td>"
					+ "  <table style='padding: 0'><tr><td style='border-radius: 2px; background-color: #c3e7fe'>"
					+ "    <a href='{{BUTTION_LINK}}' target='_blank' style=" + ahrefStyle + ">"
					+ "      {{BUTTON_TEXT}}" + "</a></td></tr></table></td></tr></table>";

			final StringBuffer emailBody = new StringBuffer();
			emailBody.append("<html>");
			emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
			emailBody.append("<div>");

			emailBody.append("    <span>Hello ").append(crowdUser != null ? crowdUser.getName() : "")
					.append(",</span><br/><br/>");

			// Main invite
			emailBody.append("    <span>").append(authUser.getName())
					.append(" would like to invite you to work with the Organization '")
					.append(refset.getOrganizationName())
					.append("' in order to participate in the reference set modeling project with the RT2 tool.</span><br/><br/>");
			emailBody.append("    <span>To accept this invitation, and alert ").append(authUser.getName())
					.append(" of your acceptance, please click the button below.</span><br/><br/>");

			// Additional Information
			if (!StringUtils.isBlank(additionalMessage)) {

				emailBody.append("In addition, they have included the additional message:").append("<br/><br/>");
				emailBody.append(additionalMessage).append("<br/><br/>");
			}

			// accept
			emailBody
					.append("    <span style='width: 300px; display: inline-block'>").append(button
							.replace("{{BUTTION_LINK}}", acceptUrl).replace("{{BUTTON_TEXT}}", "Accept Invitation"))
					.append("</span>");

			// decline
			emailBody
					.append("    <span style='width: 300px; display: inline-block'>").append(button
							.replace("{{BUTTION_LINK}}", declineUrl).replace("{{BUTTON_TEXT}}", "Decline Invitation"))
					.append("</span>");

			if (crowdUser == null) {

				emailBody.append("    <span><a href='").append(accountSetupUrl)
						.append("' target='_blank'></a></span><br/><br/>");
			}

			emailBody.append("    <br/><br/>");
			// Warning
			emailBody.append(
					"    <span>If you do not wish to accept the invitation, or this email was received in error, you can safely ignore it.</span><br/><br/>");

			// Signature
			emailBody.append("    <span>Thank you,</span><br/>");
			emailBody.append("    <span>The SNOMED CT Reference Set Tool Team</span>");
			emailBody.append("</div>");
			emailBody.append("</body>");
			emailBody.append("</html>");

			final String action = INVITE_ACTION;
			final Set<String> recipients = new HashSet<>(Arrays.asList(recipientEmail.trim()));
			EmailUtility.sendEmail(EMAIL_SUBJECT + action, authUser.getEmail(), recipients, emailBody.toString());

			LOG.info("INVITE request - from {} to {} for refset {}", authUser.getEmail(), recipients, refsetInternalId);

			AuditEntryHelper.sendRefsetInvite(refset, authUser, recipientEmail.trim());

		}

	}

	/**
	 * Accept invitation.
	 *
	 * @param service       the service
	 * @param inviteRequest the invite request
	 * @param acceptance    the acceptance
	 * @throws Exception the exception
	 */
	public static void processRefsetInvitation(final TerminologyService service, final InviteRequest inviteRequest,
			final boolean acceptance) throws Exception {

		final User memberUser = CrowdAPIClient.findUserByEmail(inviteRequest.getRecipientEmail());
		final StringBuffer emailBody = new StringBuffer();

		final String ahrefStyle = "'padding: 8px 12px; border: 1px solid #c3e7fe;border-radius: 2px;"
				+ "font-family: Helvetica, Arial, sans-serif;font-size: 14px; color: #000000;"
				+ "text-decoration: none;font-weight:bold;display: inline-block;'";
		final String button = "<table style='width: 100%; padding-right: 50px; padding-left: 50px'><tr><td>"
				+ "  <table style='padding: 0'><tr><td style='border-radius: 2px; background-color: #c3e7fe'>"
				+ "    <a href='{{BUTTION_LINK}}' target='_blank' style=" + ahrefStyle + ">" + "      {{BUTTON_TEXT}}"
				+ "</a></td></tr></table></td></tr></table>";

		final User requesterUser = UserService.getUser(inviteRequest.getRequester(), false);
		if (requesterUser == null) {
			throw new RestException(false, 417, "Expectation failed",
					"Requester not found: " + inviteRequest.getRequester());
		}

		// get refset from payload
		final Map<String, String> nameValuePairs = new HashMap<>();
		final String[] pairs = inviteRequest.getPayload().split("&");
		for (final String pair : pairs) {
			final String[] keyValue = pair.split(":");
			nameValuePairs.put(keyValue[0], keyValue[1]);
		}

		final String refsetId = nameValuePairs.get("refset");
		LOG.info("Requester is: {}", requesterUser);
		final Refset refset = getRefset(service, requesterUser, refsetId);
		final String organizationName = refset.getOrganizationName();
		final String editionName = refset.getEdition().getShortName();

		if (!requesterUser.checkPermission(User.ROLE_VIEWER, organizationName, editionName, null)) {
			throw new RestException(false, 403, "Forbidden",
					"This user does not have permission to perform this action");
		}

		service.setModifiedBy(requesterUser.getUserName());
		inviteRequest.setResponse(String.valueOf(acceptance));
		inviteRequest.setResponseDate(new Date());
		service.update(inviteRequest);

		// if rejected, send notification to requester
		if (!acceptance) {

			emailBody.append("<html>");
			emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
			emailBody.append("<div>");

			emailBody.append("    <span>Hello, ").append(requesterUser.getName()).append("</span><br/><br/>");

			// Main invite
			emailBody.append("    <span>")
					.append(memberUser != null ? memberUser.getName() : inviteRequest.getRecipientEmail())
					.append(" has declined your invitation to join ").append(refset.getOrganizationName())
					.append(" as a collaborator.</span><br/><br/>");

			// Go to app
			emailBody.append("    <span style='width: 400px; display: inline-block'>").append(button
					.replace("{{BUTTION_LINK}}", appUrlRoot).replace("{{BUTTON_TEXT}}", "Go to the Reference Set Tool"))
					.append("</span>");

			emailBody.append("</div>");
			emailBody.append("</body>");
			emailBody.append("</html>");

			final String action = INVITE_DECLINED;
			final Set<String> recipients = new HashSet<>(Arrays.asList(requesterUser.getEmail()));
			LOG.info("REFSET INVITE declined - from {} to {}", requesterUser.getEmail(), recipients);
			EmailUtility.sendEmail(EMAIL_SUBJECT + action, requesterUser.getEmail(), recipients, emailBody.toString());

		}

		// if accepted, add user to org, admin has to add to team and project since we
		// can't determine here which of the project's team to add the user.
		if (acceptance && memberUser != null) {

			// add user to org as a viewer, will not error if already a member.
			OrganizationService.addUserToOrganization(service, requesterUser, refset.getEdition().getOrganizationId(),
					memberUser.getEmail());

			emailBody.append("<html>");
			emailBody.append("<body style='font-family: Segoe UI, Tahoma, Geneva, Verdana, sans-serif;'>");
			emailBody.append("<div>");

			emailBody.append("    <span>Hello, ").append(requesterUser.getName()).append("</span><br/><br/>");

			// Main invite
			emailBody.append("    <span>").append(memberUser.getName()).append(" has accepted your invitation to join ")
					.append(refset.getOrganizationName()).append(" as a collaborator.</span><br/><br/>");
			emailBody.append("    <span>").append(memberUser.getName()).append("has been added to ")
					.append(refset.getOrganizationName()).append(" as a <b>Viewer</b>.</span><br/><br/>");

			// Warning
			emailBody.append(
					"    <span>Additional permissions can be configured through the SNOMED CT Reference Set Tool</span><br/><br/>");

			// Go to app
			emailBody.append("    <span style='width: 400px; display: inline-block'>").append(button
					.replace("{{BUTTION_LINK}}", appUrlRoot).replace("{{BUTTON_TEXT}}", "Go to the Reference Set Tool"))
					.append("</span>");

			emailBody.append("</div>");
			emailBody.append("</body>");
			emailBody.append("</html>");

			final String action = INVITE_ACCEPTED;
			final Set<String> recipients = new HashSet<>(Arrays.asList(requesterUser.getEmail()));
			LOG.info("REFSET INVITE accepted - from {} to {}", requesterUser.getEmail(), recipients);
			EmailUtility.sendEmail(EMAIL_SUBJECT + action, requesterUser.getEmail(), recipients, emailBody.toString());

		}

		AuditEntryHelper.responseForRefsetInvite(refset, requesterUser, inviteRequest.getRecipientEmail(), acceptance);

	}
}
