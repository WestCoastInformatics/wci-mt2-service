/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utilities for MapSet workflow API integration tests.
 */
public class MapSetWorkflowUnitTestUtilities {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetWorkflowUnitTestUtilities.class);

    /** The mvc. */
    private final MockMvc mvc;

    /** The base url. */
    private final String baseUrl;

    /** The Constant MAPSET_BASE_URL. */
    public static final String MAPSET_BASE_URL = "/mapset";

    /** ObjectMapper that deserializes VersionStatus from label (e.g. IN DEVELOPMENT). */
    private static final ObjectMapper MAPPER;
    static {
        MAPPER = ThreadLocalMapper.newMapper();
        final SimpleModule module = new SimpleModule();
        module.addDeserializer(VersionStatus.class, new VersionStatusDeserializer());
        MAPPER.registerModule(module);
    }

    /**
     * Instantiates a {@link MapSetWorkflowUnitTestUtilities}.
     *
     * @param mvc the mvc
     * @param baseUrl the base url (e.g. /mapset)
     */
    public MapSetWorkflowUnitTestUtilities(final MockMvc mvc, final String baseUrl) {

        this.mvc = mvc;
        this.baseUrl = baseUrl;
    }

    /**
     * Advance workflow status via API.
     *
     * @param mapSet the map set
     * @param action the action
     * @param note the note
     * @return the updated map set
     * @throws Exception the exception
     */
    public MapSet updateWorkflow(final MapSet mapSet, final WorkflowAction action, final String note) throws Exception {

        final String url = baseUrl + "/" + mapSet.getId() + "/workflowStatus?action=" + action + "&notes=" + (note != null ? note : "");

        final MvcResult result =
            mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        final String content = result.getResponse().getContentAsString();

        if (content == null || content.isEmpty()) {
            return null;
        }

        return MAPPER.readValue(content, MapSet.class);
    }

    /**
     * Attempt workflow status change and expect an error (invalid transition). The API rejects invalid transitions by throwing; we assert an exception is
     * thrown.
     *
     * @param mapSet the map set
     * @param action the action
     * @param note the note
     */
    public void updateWorkflowExpectError(final MapSet mapSet, final WorkflowAction action, final String note) {

        final String url = baseUrl + "/" + mapSet.getId() + "/workflowStatus?action=" + action + "&notes=" + (note != null ? note : "");

        final Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
            () -> mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn());

        Throwable cause = thrown;
        while (cause != null) {
            if (cause.getClass().getSimpleName().equals("RestException")
                || (cause.getCause() == null && cause.getMessage() != null && cause.getMessage().contains("workflow status"))) {
                return;
            }
            cause = cause.getCause();
        }
        org.junit.jupiter.api.Assertions.fail("Expected RestException or workflow-related error, got: " + thrown);
    }

    /** Max retries for updateWorkflowNote when Hibernate Search indexing may lag. */
    private static final int WORKFLOW_NOTE_RETRY_COUNT = 5;

    /** Delay in ms between retries. */
    private static final int WORKFLOW_NOTE_RETRY_DELAY_MS = 2000;

    /**
     * Update workflow note via API. MapSet endpoint expects JSON body (string). Retries on failure to handle Hibernate Search indexing lag.
     *
     * @param mapSet the map set
     * @param note the note
     * @throws Exception the exception
     */
    public void updateWorkflowNote(final MapSet mapSet, final String note) throws Exception {

        final String url = baseUrl + "/" + mapSet.getId() + "/workflowNote";
        final String jsonBody = new ObjectMapper().writeValueAsString(note != null ? note : "");

        Exception lastException = null;
        for (int attempt = 0; attempt < WORKFLOW_NOTE_RETRY_COUNT; attempt++) {
            try {
                mvc.perform(put(url).content(jsonBody).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                    .andReturn();
                return;
            } catch (final Exception e) {
                lastException = e;
                if (attempt < WORKFLOW_NOTE_RETRY_COUNT - 1) {
                    LOG.debug("updateWorkflowNote attempt {} failed, retrying in {}ms: {}", attempt + 1, WORKFLOW_NOTE_RETRY_DELAY_MS, e.getMessage());
                    Thread.sleep(WORKFLOW_NOTE_RETRY_DELAY_MS);
                }
            }
        }
        throw lastException != null ? lastException : new IllegalStateException("updateWorkflowNote failed");
    }

    /**
     * Get workflow history via API.
     *
     * @param mapSetInternalId the map set internal id
     * @return the workflow history items
     * @throws Exception the exception
     */
    public List<MapSetWorkflowHistory> getWorkflowHistory(final String mapSetInternalId) throws Exception {

        final String url = baseUrl + "/" + mapSetInternalId + "/workflowHistory?limit=500&offset=0&sort=modified";

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        final ResultList<MapSetWorkflowHistory> resultList = new ObjectMapper().readValue(content, new TypeReference<ResultList<MapSetWorkflowHistory>>() {
        });
        assertThat(resultList).isNotNull();

        return resultList.getItems();
    }

    /**
     * Validate a workflow history row. Uses expectedUserName for dev bypass (devUser) or session user.
     *
     * @param workflowHistory the workflow history
     * @param expectedUserName the expected user name (e.g. devUser for test profile)
     * @param action the action
     * @param status the status
     * @param note the note
     */
    public void validateRow(final MapSetWorkflowHistory workflowHistory, final String expectedUserName, final WorkflowAction action,
        final WorkflowStatus status, final String note) {

        assertThat(workflowHistory.getUserName()).isEqualTo(expectedUserName);
        assertThat(workflowHistory.getWorkflowAction()).isEqualTo(action);
        assertThat(workflowHistory.getWorkflowStatus()).isEqualTo(status);
        assertThat(workflowHistory.getNotes()).isEqualTo(note != null ? note : "");
    }
}
