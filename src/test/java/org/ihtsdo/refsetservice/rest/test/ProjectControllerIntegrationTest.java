/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * The Class ProjectControllerIntegrationTest.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProjectControllerIntegrationTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ProjectControllerIntegrationTest.class);

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The base url. */
    private String baseUrl = "";

    /** The test user. */
    private User testUser = null;

    /** The edition. */
    private Edition edition = null;

    /** The url. */
    private String url = null;

    /** The result. */
    private MvcResult result = null;

    /** The content. */
    private String content = null;

    /**
     * Creates a required edition, organization for unit tests.
     */
    @BeforeAll
    public void addData() {

        testUser = new User();
        testUser.setUserName("projectUnitTestUser");
        testUser.setName("Unit Test User");
        testUser.setEmail("user@fake.org");
        testUser.setTitle("Senior Mapper");
        testUser.setCompany("The Company");

        try {

            testUser = addUser(testUser);
        } catch (final Exception e) {

            LOG.error("ERROR {}", e.getMessage(), e);
            assertTrue(false);
        }

        final Organization tempOrganization = new Organization();
        tempOrganization.setId(null);
        tempOrganization.setName("Organization for Project Unit Tests");
        tempOrganization.setActive(true);
        tempOrganization.setDescription("Generated from unit test");
        tempOrganization.setIconUri("/organization/icon/");
        tempOrganization.setPrimaryContactEmail("org@test.com");

        final Edition tempEdition = new Edition();
        tempEdition.setId(null);
        tempEdition.setName("Project Unit Test Edition");
        tempEdition.setShortName("projectTestShortName");
        tempEdition.setNamespace("projectTestNamespace");
        tempEdition.setIconUri("projectTestIconUri");
        tempEdition.setBranch("/SNOMEDCT");
        tempEdition.setOrganization(tempOrganization);

        try {

            edition = EditionService.createEdition(testUser, tempEdition);
        } catch (final Exception e) {

            LOG.error("ERROR {}", e.getMessage(), e);
            assertTrue(false);
        }

        assertThat(edition).isNotNull();
        assertThat(edition.getId()).isNotNull();

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(testUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            edition = EditionService.createEdition(testUser, tempEdition);

            service.commit();

        } catch (final Exception e) {

            LOG.error("ERROR {}", e.getMessage(), e);
            assertTrue(false);
        }

        assertThat(edition).isNotNull();
        assertThat(edition.getId()).isNotNull();

    }

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp() {

        JacksonTester.initFields(this, ThreadLocalMapper.get());
        baseUrl = "/project";

        url = null;
        result = null;
        content = null;

    }

    /**
     * Test create.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(1)
    public void testCreate() throws Exception {

        url = baseUrl;

        final Project originalProject = new Project();
        originalProject.setId(null);
        originalProject.setName("Unit Test Create");
        originalProject.setActive(true);
        originalProject.setDescription("Generated from unit test");
        originalProject.setPrivateProject(false);
        originalProject.setPrimaryContactEmail("project@test.com");
        originalProject.setEdition(edition);
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getRoles().add("author");
        originalProject.getRoles().add("reviewer");

        LOG.info(" project = {}", originalProject.toString());
        // forbidden - unit test user is set so this does not happen
        // mvc.perform(post(url).content(org.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()).andReturn();

        // unsupported media type
        mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType())
            .andReturn();

        // method not found
        mvc.perform(post(url + "xyz").content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        result = mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" PROJECT: content = {}", content);
        final Project newProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(originalProject, newProject, true)).isTrue();
    }

    /**
     * Test update.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(2)
    public void testUpdate() throws Exception {

        url = baseUrl;

        final Project originalProject = new Project();
        originalProject.setId(null);
        originalProject.setName("Unit Test Update");
        originalProject.setActive(true);
        originalProject.setDescription("Generated for project update unit test");
        originalProject.setPrivateProject(false);
        originalProject.setPrimaryContactEmail("project@test.com");
        originalProject.setEdition(edition);
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getRoles().add("author");
        originalProject.getRoles().add("reviewer");

        LOG.info(" project = {}", originalProject.toString());
        // forbidden - unit test user is set so this does not happen
        // mvc.perform(post(url).content(org.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()).andReturn();

        // unsupported media type
        mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType())
            .andReturn();

        // method not found
        mvc.perform(post(url + "xyz").content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        result = mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" PROJECT: content = {}", content);
        final Project newProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(originalProject, newProject, true)).isTrue();

        // UPDATE
        url = baseUrl + "/" + newProject.getId();
        newProject.setName("Unit Test Update - updated");
        newProject.setDescription("Generated for project update unit test - updated");
        newProject.setPrimaryContactEmail("updated@test.com");
        newProject.setPrivateProject(true);

        // unsupported media type
        mvc.perform(put(url).content(newProject.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType()).andReturn();

        // bad request
        mvc.perform(put(url + "xyz").content(newProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest()).andReturn();

        // method not found
        mvc.perform(put(baseUrl + "xyz" + "/" + newProject.getId()).content(newProject.toString()).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound()).andReturn();

        // update
        url = baseUrl + "/" + newProject.getId();
        LOG.info("XXXX UPDATE URL {} | Project {}", url, newProject);
        result = mvc.perform(put(url).content(newProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);

        final Project updatedProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(newProject, updatedProject, true)).isTrue();
    }

    /**
     * Test get.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(3)
    public void testGet() throws Exception {

        url = baseUrl;

        final Project originalProject = new Project();
        originalProject.setId(null);
        originalProject.setName("Unit Test Get");
        originalProject.setActive(true);
        originalProject.setDescription("Generated for project get unit test");
        originalProject.setPrivateProject(false);
        originalProject.setPrimaryContactEmail("project@test.com");
        originalProject.setEdition(edition);
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getRoles().add("author");
        originalProject.getRoles().add("reviewer");

        LOG.info(" project = {}", originalProject.toString());
        result = mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" PROJECT: content = {}", content);
        final Project newProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(originalProject, newProject, true)).isTrue();

        // get
        url = baseUrl + "/" + newProject.getId() + "xyz";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + newProject.getId();
        result = mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);

        final Project getProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(getProject, newProject, true)).isTrue();
    }

    /**
     * Test find.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(4)
    public void testFind() throws Exception {

        url = baseUrl;

        final Project originalProject = new Project();
        originalProject.setId(null);
        originalProject.setName("FindMe");
        originalProject.setActive(true);
        originalProject.setDescription("Generated for project get unit test");
        originalProject.setPrivateProject(false);
        originalProject.setPrimaryContactEmail("project@test.com");
        originalProject.setEdition(edition);
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getRoles().add("author");
        originalProject.getRoles().add("reviewer");

        LOG.info(" project = {}", originalProject.toString());

        // create
        result = mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Project newProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(originalProject, newProject, true)).isTrue();

        // get - search
        url = baseUrl + "/" + "searchxyz";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + "search";

        // find by id
        result = mvc.perform(get(url).queryParam("query", "id:" + newProject.getId()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Project> resultList1 = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Project>>() {
            // n/a
        }));
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getItems()).isNotNull();
        assertThat(resultList1.getItems().size()).isEqualTo(1);
        assertThat(resultList1.getItems().get(0)).isNotNull();
        assertThat(compareProjects(resultList1.getItems().get(0), newProject, true)).isTrue();

        // find by name
        result = mvc.perform(get(url).queryParam("query", "name:" + newProject.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Project> resultList2 = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Project>>() {
            // n/a
        }));
        assertThat(resultList2).isNotNull();
        assertThat(resultList2.getItems()).isNotNull();
        assertThat(resultList2.getItems().size()).isEqualTo(1);
        assertThat(resultList2.getItems().get(0)).isNotNull();
        assertThat(compareProjects(resultList2.getItems().get(0), newProject, true)).isTrue();
    }

    /**
     * Test inactivate.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(5)
    public void testInactivate() throws Exception {

        url = baseUrl;

        final Project originalProject = new Project();
        originalProject.setId(null);
        originalProject.setName("Test Inactive");
        originalProject.setActive(true);
        originalProject.setDescription("Generated for project get unit test");
        originalProject.setPrivateProject(false);
        originalProject.setPrimaryContactEmail("project@test.com");
        originalProject.setEdition(edition);
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getTeams().add(UUID.randomUUID().toString());
        originalProject.getRoles().add("author");
        originalProject.getRoles().add("reviewer");

        LOG.info(" project = {}", originalProject.toString());

        // create
        result = mvc.perform(post(url).content(originalProject.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Project newProject = ThreadLocalMapper.get().readValue(content, Project.class);
        assertThat(compareProjects(originalProject, newProject, true)).isTrue();

        // inactive tests
        mvc.perform(delete(url).content(newProject.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isMethodNotAllowed()).andReturn();

        url = baseUrl + "/" + newProject.getId() + "xyz";
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + newProject.getId();
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isAccepted()).andReturn();

        // fetch to validate
        result = mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/search";
        // // find by id
        result = mvc.perform(get(url).queryParam("query", "id:" + newProject.getId()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Project> resultList1 = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Project>>() {
            // n/a
        }));
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getItems()).isNotNull();
        assertThat(resultList1.getItems().size()).isEqualTo(0);

        // find by name
        result = mvc.perform(get(url).queryParam("query", "name:" + newProject.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Project> resultList = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Project>>() {
            // n/a
        }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems()).isNotNull();
        assertThat(resultList.getItems().size()).isEqualTo(0);
    }

    /**
     * Compare projects.
     *
     * @param newProject the new project
     * @param originalProject the original project
     * @param nonUpdatedAttributes the non updated attributes
     * @return true, if successful
     */
    private boolean compareProjects(final Project newProject, final Project originalProject, final boolean nonUpdatedAttributes) {

        boolean pass = false;
        LOG.info("new project record = {}", newProject);
        assertThat(newProject).isNotNull();
        assertThat(newProject.getName()).isEqualTo(originalProject.getName());
        assertThat(newProject.isActive()).isEqualTo(originalProject.isActive());
        assertThat(newProject.getDescription()).isEqualTo(originalProject.getDescription());
        assertThat(compareEditions(newProject.getEdition(), originalProject.getEdition(), true)).isTrue();
        assertThat(newProject.isPrivateProject()).isEqualTo(originalProject.isPrivateProject());
        assertThat(newProject.getPrimaryContactEmail()).isEqualTo(originalProject.getPrimaryContactEmail());
        // not returned in json
        // assertThat(newProject.getCrowdProjectId()).isEqualTo(originalProject.getCrowdProjectId());
        assertThat(newProject.getTeams()).isEqualTo(originalProject.getTeams());

        // TODO Get method does not return roles.
        // assertThat(newProject.getRoles()).isEqualTo(originalProject.getRoles());

        pass = true;
        return pass;
    }

    /**
     * Compare organization.
     *
     * @param newEdition the new organization
     * @param originalEdition the original organization
     * @param nonUpdatedAttributes the non updated attributes
     * @return true, if successful
     */
    private boolean compareEditions(final Edition newEdition, final Edition originalEdition, final boolean nonUpdatedAttributes) {

        boolean pass = false;
        LOG.info("new org record = {}", newEdition);
        assertThat(newEdition).isNotNull();
        assertThat(newEdition.getName()).isEqualTo(originalEdition.getName());
        assertThat(newEdition.isActive()).isEqualTo(originalEdition.isActive());
        assertThat(newEdition.getOrganization()).isEqualTo(originalEdition.getOrganization());
        assertThat(newEdition.getNamespace()).isEqualTo(originalEdition.getNamespace());
        assertThat(newEdition.getShortName()).isEqualTo(originalEdition.getShortName());
        assertThat(newEdition.getBranch()).isEqualTo(originalEdition.getBranch());
        assertThat(newEdition.getModules()).isEqualTo(originalEdition.getModules());
        assertThat(newEdition.getDefaultLanguageCode()).isEqualTo(originalEdition.getDefaultLanguageCode());

        if (nonUpdatedAttributes) {

            assertThat(newEdition.getIconUri()).isEqualTo(originalEdition.getIconUri());
        }

        pass = true;
        return pass;
    }

}
