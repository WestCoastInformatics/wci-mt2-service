/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class WorkflowUnitTestUtilities.
 */
public class WorkflowUnitTestUtilities {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(WorkflowUnitTestUtilities.class);

	/** The mvc. */
    private final MockMvc mvc;

	/** The base url. */
    private final String baseUrl;

	/** The AUTHOR_USER workflow user . */
	public static final String AUTHOR_USER = "AUTHOR_USER";

	/** The REVIEWER_USER workflow user . */
	public static final String REVIEWER_USER = "REVIEWER_USER";

	/** The ADMIN_USER workflow user . */
	public static final String ADMIN_USER = "ADMIN_USER";

	/** The VIEWER_USER workflow user . */
	public static final String VIEWER_USER = "VIEWER_USER";

	/** The Constant WORKFLOW_PERMUTATIONS_FILE_NAME. */
	private static final String WORKFLOW_PERMUTATIONS_FILE_NAME = "workflowPermutationsToFinalAction.txt";

	/** The workflow permutations file path. */
    private final String workflowPermutationsFilePath;

	/** The actions . */
    private static List<WorkflowAction> actions = WorkflowAction.getValues();

	/**
	 * Instantiates a {@link WorkflowUnitTestUtilities} from the specified
	 * parameters.
	 *
	 * @param mvc            the mvc
	 * @param baseUrl        the base url
	 * @param refsetFilePath the refset file path
	 */
	public WorkflowUnitTestUtilities(final MockMvc mvc, final String baseUrl, final String refsetFilePath) {

		this.mvc = mvc;
		this.baseUrl = baseUrl;
		workflowPermutationsFilePath = refsetFilePath + WORKFLOW_PERMUTATIONS_FILE_NAME;
	}

	/** ThepermissiblePaths . */
	private Map<String, Map<String, Map<String, String>>> permissiblePaths;

	/**
	 * Export sct ids.
	 *
	 * @param internalRefsetId the internal refset id
	 * @return the json node
	 */
	public JsonNode exportSctIds(final String internalRefsetId) {

		try {
			final String url = "/export/" + internalRefsetId + "/?format=sctids";
			LOG.info("Export SctId url - " + url);

			final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
			final String content = result.getResponse().getContentAsString();
			LOG.info(" content = " + content);

            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode root = mapper.readTree(content);

			assertThat(root).isNotNull();
			return root;
		} catch (final Exception e) {
			e.printStackTrace();

			return null;
		}
	}

	/**
	 * advance workflow.
	 *
	 * @param refset the refset
	 * @param user   the user
	 * @param action the action
	 * @param note   the note
	 * @return the refset
	 * @throws Exception the exception
	 */
    public Refset updateWorkflow(final Refset refset, final String user, final WorkflowAction action, final String note) throws Exception {

		getWorkflowStatusByAction(user, refset.getWorkflowStatus(), action);
        final String url = baseUrl + "/" + refset.getId() + "/workflowStatus?action=" + action + "&notes=" + note;

        final MvcResult result =
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

		final String content = result.getResponse().getContentAsString();

		if (content == null || content.equals("")) {
			return null;
		}

		final Refset updatedRefset = ThreadLocalMapper.get().readValue(content, Refset.class);

		return updatedRefset;
	}

	/**
	 * updateWorkflowNote.
	 *
	 * @param refset the refset
	 * @param user   the user
	 * @param note   the note
	 * @throws Exception the exception
	 */
	public void updateWorkflowNote(final Refset refset, final String user, final String note) throws Exception {

		final String url = baseUrl + "/" + refset.getId() + "/workflowNote?&notes=" + note;

        final MvcResult result =
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

		final String content = result.getResponse().getContentAsString();
		assertThat(content).isEqualTo("true");
	}

	/**
	 * start publication.
	 *
	 * @param editionShortName the edition short name
	 * @return the string
	 * @throws Exception the exception
	 */
	public String startPublication(final String editionShortName) throws Exception {

        final String url = "/admin/startRefsetPublications?codeSystem=" + editionShortName;

        final MvcResult result =
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

		final String content = result.getResponse().getContentAsString();
		assertThat(content).contains("status");

		return content;
	}

	/**
	 * complete publication.
	 *
	 * @param versionDate      the version date
	 * @param editionShortName the edition short name
	 * @return the string
	 * @throws Exception the exception
	 */
	public String completePublication(final String versionDate, final String editionShortName) throws Exception {

        final String url = "/admin/completeRefsetPublications?versionDate=" + versionDate + "&codeSystem=" + editionShortName;

        final MvcResult result =
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

		final String content = result.getResponse().getContentAsString();
		assertThat(content).contains("status");

		return content;
	}

	/**
	 * Publication Fails call.
	 *
	 * @param refsetIds the refset ids
	 * @param notes     the notes
	 * @return the string
	 * @throws Exception the exception
	 */
    public String failRefsetPublication(final String refsetIds, final String notes) throws Exception {

        final String url = "/admin/publish/fail?refsetIds=" + refsetIds + "&notes=" + notes;

        final MvcResult result =
            mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

		final String content = result.getResponse().getContentAsString();
		assertThat(content).isNotBlank();

		return content;
	}

	/**
	 * get workflow history.
	 *
	 * @param refsetInternalId the refset internal id
	 * @return the workflow history
	 * @throws Exception the exception
	 */
    public List<RefsetWorkflowHistory> getWorkflowHistory(final String refsetInternalId) throws Exception {

		final String url = baseUrl + "/" + refsetInternalId + "/workflowHistory?limit=500&offset=0&sort=modified";

		final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
		final String content = result.getResponse().getContentAsString();
        final ResultList<RefsetWorkflowHistory> resultList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<RefsetWorkflowHistory>>() {
				}));
		assertThat(resultList).isNotNull();

		return resultList.getItems();
	}

	/**
	 * Get status by action.
	 *
	 * @param user          the user
	 * @param currentStatus the current status
	 * @param action        the action
	 * @return the workflow status by action
	 * @throws Exception the exception
	 */
    private String getWorkflowStatusByAction(final String user, final WorkflowStatus currentStatus, final WorkflowAction action) throws Exception {

		final String userRole = getUserRole(user);
		final Map<String, Map<String, String>> rolePaths = getPermissiblePaths().get(userRole);

		if (rolePaths == null) {
			return null;
		}

		final Map<String, String> roleStatusActions = getPermissiblePaths().get(userRole).get(currentStatus);

		if (roleStatusActions == null) {
			return null;
		}

		return roleStatusActions.get(action);
	}

	/**
	 * Get user role.
	 *
	 * @param user the user
	 * @return the user role
	 */
	public String getUserRole(final String user) {

		return user.replace("_USER", "");
	}

	/**
	 * define all action paths.
	 *
	 * @return the map
	 * @throws Exception the exception
	 */
	private Map<String, Map<String, Map<String, String>>> defineAllPermissiblePermutations() throws Exception {

		final Map<String, Map<String, Map<String, String>>> returnMap = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(workflowPermutationsFilePath))) {

			String line;

			while ((line = br.readLine()) != null) {

				final String[] tokens = FieldedStringTokenizer.split(line, ",");
				assertThat(tokens.length).isEqualTo(4);

				final String user = tokens[0].toUpperCase().strip();
				final String currentState = tokens[1].toUpperCase().strip();
				final String action = tokens[2].toUpperCase().strip();
				final String resultingState = tokens[3].toUpperCase().strip();

				if (!returnMap.containsKey(user)) {
					returnMap.put(user, new HashMap<String, Map<String, String>>());
				}

				if (!returnMap.get(user).containsKey(currentState)) {
					returnMap.get(user).put(currentState, new HashMap<>());
				}

				returnMap.get(user).get(currentState).put(action, resultingState);
			}

			return returnMap;
		}
	}

	/**
	 * test all advances.
	 *
	 * @param user          the user
	 * @param currentStatus the current status
	 * @return the workflow action paths
	 * @throws Exception the exception
	 */
    public Map<WorkflowAction, String> getWorkflowActionPaths(final String user, final WorkflowStatus currentStatus) throws Exception {

        final Map<WorkflowAction, String> results = new HashMap<>();

        for (final WorkflowAction action : actions) {

			final String resultingStatus = getWorkflowStatusByAction(user, currentStatus, action);
			results.put(action, resultingStatus);
		}

		return results;
	}

	/**
	 * validate a row.
	 *
	 * @param workflowHistory the workflow history
	 * @param userName        the user name
	 * @param action          the action
	 * @param status          the status
	 * @param note            the note
	 */
    public void validateRow(final RefsetWorkflowHistory workflowHistory, final String userName, final WorkflowAction action, final WorkflowStatus status,
        final String note) {

		assertThat(workflowHistory.getUserName()).isEqualTo("testUser");
		assertThat(workflowHistory.getWorkflowAction()).isEqualTo(action);
		assertThat(workflowHistory.getWorkflowStatus()).isEqualTo(status);
		assertThat(workflowHistory.getNotes()).isEqualTo(note);
	}

	/**
	 * Returns the permissible paths.
	 *
	 * @return the permissible paths
	 * @throws Exception the exception
	 */
	public Map<String, Map<String, Map<String, String>>> getPermissiblePaths() throws Exception {

		if (permissiblePaths == null) {
			permissiblePaths = defineAllPermissiblePermutations();
		}

		return permissiblePaths;
	}
}
