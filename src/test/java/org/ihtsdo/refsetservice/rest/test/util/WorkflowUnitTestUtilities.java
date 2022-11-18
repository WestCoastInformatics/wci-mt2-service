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
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class WorkflowUnitTestUtilities {

    private static Logger logger = LoggerFactory.getLogger(WorkflowUnitTestUtilities.class);

    private MockMvc mvc;

    private String baseUrl;
    
    /** The AUTHOR_USER workflow user . */
    public static final String AUTHOR_USER = "AUTHOR_USER";

    /** The REVIEWER_USER workflow user . */
    public static final String REVIEWER_USER = "REVIEWER_USER";

    /** The ADMIN_USER workflow user . */
    public static final String ADMIN_USER = "ADMIN_USER";

    /** The VIEWER_USER workflow user . */
    public static final String VIEWER_USER = "VIEWER_USER";

    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME =
            "workflowPermutationsToFinalAction.txt";

    private static String workflowPermutationsFilePath;

    /** The actions . */
    private static List<String> actions = WorkflowService.WORKFLOW_ACTIONS;

    public WorkflowUnitTestUtilities(final MockMvc mvc, String baseUrl, String refsetFilePath) {
        this.mvc = mvc;
        this.baseUrl = baseUrl;
        workflowPermutationsFilePath = refsetFilePath + WORKFLOW_PERMUTATIONS_FILE_NAME;
    }

    /** ThepermissiblePaths . */
    private Map<String, Map<String, Map<String, String>>> permissiblePaths;

    public JsonNode exportSctIds(String internalRefsetId) {

        try {
            final String url = "/export/" + internalRefsetId + "/?format=sctids";
            logger.info("Export SctId url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(content);

            assertThat(root).isNotNull();
            return root;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    /**
     * advance workflow.
     *
     * @throws Exception the exception
     */
    public Refset updateWorkflow(final Refset refset, final String user, final String action,
        final String note) throws Exception {

        final String newStatus =
                getWorkflowStatusByAction(user, refset.getWorkflowStatus(), action);
        final String url = baseUrl + "/" + refset.getId() + "/workflowStatus?action=" + action
                + "&notes=" + note;

        final MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        final String content = result.getResponse().getContentAsString();

        if (content == null || content.equals("")) {
            return null;
        }

        final Refset updatedRefset = new ObjectMapper().readValue(content, Refset.class);

        return updatedRefset;
    }

    /**
     * updateWorkflowNote.
     *
     * @throws Exception the exception
     */
    public void updateWorkflowNote(final Refset refset, final String user, final String note)
        throws Exception {

        final String url = baseUrl + "/" + refset.getId() + "/workflowNote?&notes=" + note;

        final MvcResult result = mvc.perform(
                put(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        final String content = result.getResponse().getContentAsString();
        assertThat(content).isEqualTo("true");
    }
    
    /**
     * start publication.
     *
     * @throws Exception the exception
     */
    public String startPublication(final String editionShortName) throws Exception{
        
        final String url = "/admin/startAllRefsetPublications?codeSystem=" + editionShortName;
        
        final MvcResult result = mvc
                .perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        
        final String content = result.getResponse().getContentAsString();
        assertThat(content).contains("status");
        
        return content;
    }
    
    /**
     * complete publication.
     *
     * @throws Exception the exception
     */
    public String completePublication(final String versionDate, final String editionShortName) throws Exception{
        
        final String url = "/admin/completeAllRefsetPublications?versionDate=" + versionDate + "&codeSystem=" + editionShortName;
        
        final MvcResult result = mvc
                .perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        
        final String content = result.getResponse().getContentAsString();
        assertThat(content).contains("status");
        
        return content;
    }

    /**
     * Publication Fails call.
     *
     * @throws Exception the exception
     */
    public String failPublication(final String refsetIds, final String notes) throws Exception {

        final String url =
                "/admin/failRefsetPublications?refsetIds=" + refsetIds + "&notes=" + notes;

        final MvcResult result = mvc.perform(
                put(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        final String content = result.getResponse().getContentAsString();
        assertThat(content).isNotBlank();

        return content;
    }

    /**
     * get workflow history.
     *
     * @throws Exception the exception
     */
    public List<WorkflowHistory> getWorkflowHistory(final String refsetInternalId)
        throws Exception {

        final String url = baseUrl + "/" + refsetInternalId
                + "/workflowHistory?limit=500&offset=0&sort=modified";

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        ResultList<WorkflowHistory> resultList = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<WorkflowHistory>>() {
                }));
        assertThat(resultList).isNotNull();

        return resultList.getItems();
    }

    /**
     * Get status by action.
     *
     * @throws Exception the exception
     */
    private String getWorkflowStatusByAction(final String user, final String currentStatus,
        final String action) throws Exception {

        final String userRole = getUserRole(user);
        final Map<String, Map<String, String>> rolePaths = getPermissiblePaths().get(userRole);

        if (rolePaths == null) {
            return null;
        }

        final Map<String, String> roleStatusActions =
                getPermissiblePaths().get(userRole).get(currentStatus);

        if (roleStatusActions == null) {
            return null;
        }

        return roleStatusActions.get(action);
    }

    /**
     * Get user role.
     *
     * @throws Exception the exception
     */
    public String getUserRole(final String user) {
        return user.replace("_USER", "");
    }

    /**
     * define all action paths.
     *
     * @throws Exception the exception
     */
    private Map<String, Map<String, Map<String, String>>> defineAllPermissiblePermutations()
        throws Exception {

        Map<String, Map<String, Map<String, String>>> returnMap = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(workflowPermutationsFilePath))) {

            String line;

            while ((line = br.readLine()) != null) {

                String[] tokens = FieldedStringTokenizer.split(line, ",");
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
     * @throws Exception the exception
     */
    public Map<String, String> getWorkflowActionPaths(final String user,
        final String currentStatus) throws Exception {

        final Map<String, String> results = new HashMap<>();

        for (String action : actions) {

            String resultingStatus = getWorkflowStatusByAction(user, currentStatus, action);
            results.put(action, resultingStatus);
        }

        return results;
    }

    /**
     * validate a row.
     *
     * @throws Exception the exception
     */
    public void validateRow(final WorkflowHistory workflowHistory, final String userName,
        final String action, final String status, final String note) {

        assertThat(workflowHistory.getUserName()).isEqualTo("testUser");
        assertThat(workflowHistory.getWorkflowAction()).isEqualTo(action);
        assertThat(workflowHistory.getWorkflowStatus()).isEqualTo(status);
        assertThat(workflowHistory.getNotes()).isEqualTo(note);
    }

    public Map<String, Map<String, Map<String, String>>> getPermissiblePaths() throws Exception {
        if (permissiblePaths == null) {
            permissiblePaths = defineAllPermissiblePermutations();
        }

        return permissiblePaths;
    }
}
