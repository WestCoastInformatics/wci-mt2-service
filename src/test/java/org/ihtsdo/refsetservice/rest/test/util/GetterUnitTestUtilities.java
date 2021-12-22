
package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

public class GetterUnitTestUtilities {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(GetterUnitTestUtilities.class);

    private MockMvc mvc;

    private String baseUrl;

    public GetterUnitTestUtilities(final MockMvc mvc, final String baseUrl) {
        this.mvc = mvc;
        this.baseUrl = baseUrl;
    }

    public Project getProject(String url) {

        try {
            logger.info("Get Project Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final Project project = new ObjectMapper().readValue(content, Project.class);

            assertThat(project).isNotNull();
            return project;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<Project> searchProjects(String url) {

        try {
            // Test full list
            logger.info("Project Search Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<Project> projectList = new ObjectMapper().readValue(content,
                    (new TypeReference<ResultList<Project>>() {
                    }));

            assertThat(projectList).isNotNull();
            assertThat(projectList.getItems().size()).isGreaterThanOrEqualTo(1);
            return projectList;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public Refset getRefset(String url) {
        try {
            logger.info("Get Refset Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final Refset refset = new ObjectMapper().readValue(content, Refset.class);

            assertThat(refset).isNotNull();
            return refset;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<TypeKeyValue> getEditions(String url) {
        try {
            logger.info("Get Editions Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final ResultList<TypeKeyValue> editions = new ObjectMapper().readValue(content,
                    (new TypeReference<ResultList<TypeKeyValue>>() {
                        /* NA */}));

            assertThat(editions).isNotNull();
            return editions;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<String> getBranches(String url) {
        try {

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ResultList<String> versions =
                    new ObjectMapper().readValue(content, (new TypeReference<ResultList<String>>() {
                        /* NA */}));

            assertThat(versions).isNotNull();
            return versions;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }

    public ResultList<Refset> searchDirectory(String string) {
        try {
            String url = baseUrl

                    + "/search?searchConcepts=true&limit=500&offset=0&sort=versionDate&sortAscending=false&query=name:animal";
            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            final ResultList<Refset> refsetList =
                    new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    }));
            
            assertThat(refsetList).isNotNull();
            assertThat(refsetList.getItems().size()).isGreaterThanOrEqualTo(1);
            return refsetList;
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }
    }
}
