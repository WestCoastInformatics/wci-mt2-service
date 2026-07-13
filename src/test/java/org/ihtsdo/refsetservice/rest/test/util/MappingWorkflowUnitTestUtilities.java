package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Utilities for per-concept mapping workflow API tests.
 */
public class MappingWorkflowUnitTestUtilities {

    /** ObjectMapper that deserializes VersionStatus from label (e.g. IN DEVELOPMENT). */
    private static final ObjectMapper MAPPER;
    static {
        MAPPER = ThreadLocalMapper.newMapper();
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(VersionStatus.class, new VersionStatusDeserializer());
        MAPPER.registerModule(module);
    }

    /** The mvc. */
    private final MockMvc mvc;

    /** The base url. */
    private final String baseUrl;

    /**
     * Instantiates utilities for mapping workflow API tests.
     *
     * @param mvc the mvc
     * @param baseUrl the base url (e.g. /mapset)
     */
    public MappingWorkflowUnitTestUtilities(final MockMvc mvc, final String baseUrl) {

        this.mvc = mvc;
        this.baseUrl = baseUrl;
    }

    /**
     * Advance mapping workflow via API.
     *
     * @param mapSetId the map set id
     * @param conceptCode the source concept code
     * @param action the action
     * @param note the note
     * @param asUser the acting user
     * @return the updated mapping workflow
     * @throws Exception the exception
     */
    public MappingWorkflow updateWorkflow(final String mapSetId, final String conceptCode, final MappingWorkflowAction action, final String note,
        final User asUser) throws Exception {

        return updateWorkflow(mapSetId, conceptCode, action, note, asUser, null);
    }

    /**
     * Advance mapping workflow via API, optionally targeting another user (REASSIGN).
     *
     * @param mapSetId the map set id
     * @param conceptCode the source concept code
     * @param action the action
     * @param note the note
     * @param asUser the acting user
     * @param assignToUser target user for REASSIGN, or null
     * @return the updated mapping workflow
     * @throws Exception the exception
     */
    public MappingWorkflow updateWorkflow(final String mapSetId, final String conceptCode, final MappingWorkflowAction action, final String note,
        final User asUser, final String assignToUser) throws Exception {

        final String url = workflowStatusUrl(mapSetId, conceptCode, action, note, assignToUser);
        final MvcResult result = mvc.perform(withUser(post(url), asUser).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();
        return MAPPER.readValue(result.getResponse().getContentAsString(), MappingWorkflow.class);
    }

    /**
     * Get mapping workflow state via API.
     *
     * @param mapSetId the map set id
     * @param conceptCode the source concept code
     * @param asUser the acting user
     * @return the mapping workflow
     * @throws Exception the exception
     */
    public MappingWorkflow getWorkflow(final String mapSetId, final String conceptCode, final User asUser) throws Exception {

        final String url = baseUrl + "/" + mapSetId + "/mappings/" + conceptCode + "/workflowStatus";
        final MvcResult result = mvc.perform(withUser(get(url), asUser).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        return MAPPER.readValue(result.getResponse().getContentAsString(), MappingWorkflow.class);
    }

    /**
     * Attempt workflow update and expect HTTP 401.
     *
     * @param mapSetId the map set id
     * @param conceptCode the source concept code
     * @param action the action
     * @param note the note
     * @param asUser the acting user
     * @throws Exception the exception
     */
    public void updateWorkflowExpectUnauthorized(final String mapSetId, final String conceptCode, final MappingWorkflowAction action, final String note,
        final User asUser) throws Exception {

        final String url = workflowStatusUrl(mapSetId, conceptCode, action, note, null);
        mvc.perform(withUser(post(url), asUser).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    /**
     * Update mappings via bulk PUT endpoint.
     *
     * @param mapSetId the map set id
     * @param mappings the mappings
     * @param asUser the acting user
     * @return the updated mappings
     * @throws Exception the exception
     */
    public List<Mapping> updateMappings(final String mapSetId, final List<Mapping> mappings, final User asUser) throws Exception {

        final String url = baseUrl + "/" + mapSetId + "/bulk";
        final MvcResult result = mvc.perform(withUser(put(url), asUser).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .content(ModelUtility.toJson(mappings))).andExpect(status().isOk()).andReturn();
        return ThreadLocalMapper.get().readValue(result.getResponse().getContentAsString(), new TypeReference<List<Mapping>>() {
        });
    }

    /**
     * Attempt mapping update and expect HTTP 401.
     *
     * @param mapSetId the map set id
     * @param mappings the mappings
     * @param asUser the acting user
     * @throws Exception the exception
     */
    public void updateMappingsExpectUnauthorized(final String mapSetId, final List<Mapping> mappings, final User asUser) throws Exception {

        final String url = baseUrl + "/" + mapSetId + "/bulk";
        mvc.perform(withUser(put(url), asUser).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
            .content(ModelUtility.toJson(mappings))).andExpect(status().isUnauthorized());
    }

    /**
     * Get workflow history via API.
     *
     * @param mapSetId the map set id
     * @param conceptCode the source concept code
     * @param asUser the acting user
     * @return history rows
     * @throws Exception the exception
     */
    public List<MappingWorkflowHistory> getWorkflowHistory(final String mapSetId, final String conceptCode, final User asUser) throws Exception {

        final String url = baseUrl + "/" + mapSetId + "/mappings/" + conceptCode + "/workflowHistory?limit=500&offset=0&sort=modified&sortAscending=true";
        final MvcResult result = mvc.perform(withUser(get(url), asUser).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        final ResultList<MappingWorkflowHistory> resultList =
            MAPPER.readValue(result.getResponse().getContentAsString(), new TypeReference<ResultList<MappingWorkflowHistory>>() {
            });
        return resultList.getItems();
    }

    /**
     * Validate a mapping workflow history row.
     *
     * @param history the history row
     * @param expectedUserName the expected user name
     * @param action the action
     * @param status the status after transition
     * @param note the note
     * @param workflowId the workflow id
     */
    public void validateRow(final MappingWorkflowHistory history, final String expectedUserName, final MappingWorkflowAction action,
        final MapWorkflowStatus status, final String note, final String workflowId) {

        assertThat(history.getUserName()).isEqualTo(expectedUserName);
        assertThat(history.getWorkflowAction()).isEqualTo(action);
        assertThat(history.getWorkflowStatus()).isEqualTo(status);
        assertThat(history.getNotes()).isEqualTo(note != null ? note : "");
        assertThat(history.getMappingWorkflowId()).isEqualTo(workflowId);
    }

    private String workflowStatusUrl(final String mapSetId, final String conceptCode, final MappingWorkflowAction action, final String note,
        final String assignToUser) {

        final StringBuilder url = new StringBuilder(baseUrl).append("/").append(mapSetId).append("/mappings/").append(conceptCode)
            .append("/workflowStatus?action=").append(action);
        if (note != null) {
            url.append("&notes=").append(note);
        }
        if (assignToUser != null) {
            url.append("&assignToUser=").append(assignToUser);
        }
        return url.toString();
    }

    private MockHttpServletRequestBuilder withUser(final MockHttpServletRequestBuilder builder, final User asUser) {

        return builder.with(new SessionUserRequestPostProcessor(asUser));
    }
}
