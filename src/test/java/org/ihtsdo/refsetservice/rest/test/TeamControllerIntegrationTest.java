/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
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

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.ProjectService;
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
 * The Class TeamControllerIntegrationTest.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TeamControllerIntegrationTest extends AbstractRefsetTests {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(TeamControllerIntegrationTest.class);

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The base url. */
    private String baseUrl = "";

    /** The test user. */
    private User testUser = null;

    /** The edition. */
    private Edition edition = null;

    /** The organization. */
    private Organization organization = null;

    /** The project. */
    private Project project = null;

    /** The url. */
    private String url = null;

    /** The result. */
    private MvcResult result = null;

    /** The content. */
    private String content = null;

    /**
     * Creates a required edition, organization and project for unit tests.
     */
    @BeforeAll
    public void addData() {

        testUser = new User();
        testUser.setUserName("teamUnitTestUser");
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
        tempOrganization.setName("Organization for Team Unit Tests");
        tempOrganization.setActive(true);
        tempOrganization.setDescription("Generated from unit test");
        tempOrganization.setIconUri("/organization/icon/");
        tempOrganization.setPrimaryContactEmail("org@test.com");

        final Edition tempEdition = new Edition();
        tempEdition.setId(null);
        tempEdition.setName("Team Unit Test Edition");
        tempEdition.setShortName("teamTestShortName");
        tempEdition.setNamespace("teamTestNamespace");
        tempEdition.setIconUri("teamTestIconUri");
        tempEdition.setBranch("/SNOMEDCT");
        tempEdition.setOrganization(organization);

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

            organization = OrganizationService.createAffiliateOrganization(service, testUser, tempOrganization);

            service.commit();

            assertThat(organization).isNotNull();
            assertThat(organization.getId()).isNotNull();
    
            final Project tempProject = new Project();
            tempProject.setId(null);
            tempProject.setName("Unit Test Create");
            tempProject.setActive(true);
            tempProject.setDescription("Generated from unit test");
            tempProject.setPrivateProject(false);
            tempProject.setPrimaryContactEmail("project@test.com");
            tempProject.setEdition(edition);
            tempProject.getTeams().add(UUID.randomUUID().toString());
            tempProject.getTeams().add(UUID.randomUUID().toString());
            tempProject.getRoles().add("author");
            tempProject.getRoles().add("reviewer");

            project = ProjectService.addProject(service, testUser, tempProject);
            
            service.commit();
        } catch (final Exception e) {
            LOG.error("ERROR {}", e.getMessage(), e);
            assertTrue(false);
        }

        assertThat(project).isNotNull();
        assertThat(project.getId()).isNotNull();

    }

    /**
     * Re-sets variables before each test.
     */
    @BeforeEach
    public void setUp() {

        JacksonTester.initFields(this, ThreadLocalMapper.get());
        baseUrl = "/team";

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

        final Team originalTeam = new Team();
        originalTeam.setId(null);
        originalTeam.setName("Unit Test Create");
        originalTeam.setDescription("Generate from unit test");
        originalTeam.setPrimaryContactEmail("team@test.com");
        originalTeam.getRoles().add("author");
        originalTeam.getRoles().add("reviewer");

        originalTeam.setOrganization(organization);
        final Set<String> members = new HashSet<>();
        members.add(UUID.randomUUID().toString());
        members.add(UUID.randomUUID().toString());
        originalTeam.setMembers(members);

        LOG.info(" team = {}", originalTeam.toString());
        // forbidden - unit test user is set so this does not happen
        // mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()).andReturn();

        // unsupported media type
        mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType()).andReturn();

        // method not found
        mvc.perform(post(url + "xyz").content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        result = mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Team newTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(originalTeam, newTeam, true)).isTrue();
    }

    /**
     * Test update.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(2)
    public void testUpdate() throws Exception {

        // create organization
        // result =
        // mvc.perform(post("/organization").content(organization.toString())
        // .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();
        // content = result.getResponse().getContentAsString();
        // LOG.info(" content = {}", content);
        // organization = ThreadLocalMapper.get().readValue(content, Organization.class);
        // assertThat(organization).isNotNull();
        // assertThat(organization.getId()).isNotNull();

        url = baseUrl;

        final Team originalTeam = new Team();
        originalTeam.setId(null);
        originalTeam.setName("Unit Test Update");
        originalTeam.setDescription("Generated for team update unit test");
        originalTeam.setPrimaryContactEmail("team@test.com");
        originalTeam.getRoles().add("author");
        originalTeam.getRoles().add("reviewer");

        originalTeam.setOrganization(organization);
        final Set<String> members = new HashSet<>();
        members.add(UUID.randomUUID().toString());
        members.add(UUID.randomUUID().toString());
        originalTeam.setMembers(members);

        LOG.info(" team = {}", originalTeam.toString());
        // forbidden - unit test user is set so this does not happen
        // mvc.perform(put(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()).andReturn();

        result = mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Team newTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(originalTeam, newTeam, true)).isTrue();

        // UPDATE
        url = baseUrl + "/" + newTeam.getId();
        newTeam.setName("Unit Test Update - updated");
        newTeam.setDescription("Generated for team update unit test - updated");
        newTeam.setPrimaryContactEmail("updated@test.com");

        // unsupported media type
        mvc.perform(put(url).content(newTeam.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType()).andReturn();

        // bad request
        mvc.perform(put(url + "xyz").content(newTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest()).andReturn();

        // method not found
        mvc.perform(put(baseUrl + "xyz" + "/" + newTeam.getId()).content(newTeam.toString()).contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound()).andReturn();

        // update
        url = baseUrl + "/" + newTeam.getId();
        result = mvc.perform(put(url).content(newTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);

        final Team updatedTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(newTeam, updatedTeam, true)).isTrue();
    }

    /**
     * Test get.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(3)
    public void testGet() throws Exception {

        // create organization
        // result =
        // mvc.perform(post("/organization").content(organization.toString())
        // .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();
        // content = result.getResponse().getContentAsString();
        // LOG.info(" content = {}", content);
        // organization = ThreadLocalMapper.get().readValue(content, Organization.class);
        // assertThat(organization).isNotNull();
        // assertThat(organization.getId()).isNotNull();

        url = baseUrl;

        final Team originalTeam = new Team();
        originalTeam.setId(null);
        originalTeam.setName("Unit Test Update");
        originalTeam.setDescription("Generated for team update unit test");
        originalTeam.setPrimaryContactEmail("team@test.com");
        originalTeam.getRoles().add("author");
        originalTeam.getRoles().add("reviewer");

        originalTeam.setOrganization(organization);
        final Set<String> members = new HashSet<>();
        members.add(UUID.randomUUID().toString());
        members.add(UUID.randomUUID().toString());
        originalTeam.setMembers(members);

        LOG.info(" team = {}", originalTeam.toString());
        result = mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Team newTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(originalTeam, newTeam, true)).isTrue();

        // get
        url = baseUrl + "/" + newTeam.getId() + "xyz";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + newTeam.getId();
        result = mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);

        final Team getTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(getTeam, newTeam, true)).isTrue();
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

        final Team originalTeam = new Team();
        originalTeam.setId(null);
        originalTeam.setName("FindMe");
        originalTeam.setDescription("Generated for team update unit test");
        originalTeam.setPrimaryContactEmail("team@test.com");
        originalTeam.getRoles().add("author");
        originalTeam.getRoles().add("reviewer");

        originalTeam.setOrganization(organization);
        final Set<String> members = new HashSet<>();
        members.add(UUID.randomUUID().toString());
        members.add(UUID.randomUUID().toString());
        originalTeam.setMembers(members);

        LOG.info(" team = {}", originalTeam.toString());

        // create
        result = mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Team newTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(originalTeam, newTeam, true)).isTrue();

        // get - search
        url = baseUrl + "/" + "searchxyz";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + "search";

        // find by id
        result =
            mvc.perform(get(url).queryParam("query", "id:" + newTeam.getId()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Team> resultList1 = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Team>>() {
            // n/a
        }));
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getItems()).isNotNull();
        assertThat(resultList1.getItems().size()).isEqualTo(1);
        assertThat(resultList1.getItems().get(0)).isNotNull();
        assertThat(compareTeams(resultList1.getItems().get(0), newTeam, true)).isTrue();

        // find by name
        result = mvc.perform(get(url).queryParam("query", "name:" + newTeam.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Team> resultList2 = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Team>>() {
            // n/a
        }));
        assertThat(resultList2).isNotNull();
        assertThat(resultList2.getItems()).isNotNull();
        assertThat(resultList2.getItems().size()).isEqualTo(1);
        assertThat(resultList2.getItems().get(0)).isNotNull();
        assertThat(compareTeams(resultList2.getItems().get(0), newTeam, true)).isTrue();
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

        final Team originalTeam = new Team();
        originalTeam.setId(null);
        originalTeam.setName("Test Inactive");
        originalTeam.setDescription("Generated for team update unit test");
        originalTeam.setPrimaryContactEmail("team@test.com");
        originalTeam.getRoles().add("author");
        originalTeam.getRoles().add("reviewer");

        originalTeam.setOrganization(organization);
        final Set<String> members = new HashSet<>();
        members.add(UUID.randomUUID().toString());
        members.add(UUID.randomUUID().toString());
        originalTeam.setMembers(members);

        LOG.info(" team = {}", originalTeam.toString());

        // create
        result = mvc.perform(post(url).content(originalTeam.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final Team newTeam = ThreadLocalMapper.get().readValue(content, Team.class);
        assertThat(compareTeams(originalTeam, newTeam, true)).isTrue();

        // inactive tests
        mvc.perform(delete(url).content(newTeam.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isMethodNotAllowed()).andReturn();

        url = baseUrl + "/" + newTeam.getId() + "xyz";
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + newTeam.getId();
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isAccepted()).andReturn();

        result = mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/search";
        // find by id
        result =
            mvc.perform(get(url).queryParam("query", "id:" + newTeam.getId()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Team> resultList1 = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Team>>() {
            // n/a
        }));
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getItems()).isNotNull();
        assertThat(resultList1.getItems().size()).isEqualTo(0);

        // find by name
        result = mvc.perform(get(url).queryParam("query", "name:" + newTeam.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
            .andReturn();
        content = result.getResponse().getContentAsString();
        LOG.info(" content = {}", content);
        final ResultList<Team> resultList = ThreadLocalMapper.get().readValue(content, (new TypeReference<ResultList<Team>>() {
            // n/a
        }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems()).isNotNull();
        assertThat(resultList.getItems().size()).isEqualTo(0);
    }

    /**
     * Compare teams.
     *
     * @param newTeam the new team
     * @param originalTeam the original team
     * @param nonUpdatedAttributes the non updated attributes
     * @return true, if successful
     */
    private boolean compareTeams(final Team newTeam, final Team originalTeam, final boolean nonUpdatedAttributes) {

        boolean pass = false;
        LOG.info("new project record = {}", newTeam);
        assertThat(newTeam).isNotNull();
        assertThat(newTeam.getName()).isEqualTo(originalTeam.getName());
        assertThat(newTeam.isActive()).isEqualTo(originalTeam.isActive());
        assertThat(newTeam.getDescription()).isEqualTo(originalTeam.getDescription());
        assertThat(compareOrganizations(newTeam.getOrganization(), originalTeam.getOrganization(), true)).isTrue();
        assertThat(newTeam.getPrimaryContactEmail()).isEqualTo(originalTeam.getPrimaryContactEmail());
        assertThat(newTeam.getRoles()).isEqualTo(originalTeam.getRoles());

        pass = true;
        return pass;
    }

    /**
     * Compare organization.
     *
     * @param newOrganization the new organization
     * @param originalOrganization the original organization
     * @param nonUpdatedAttributes the non updated attributes
     * @return true, if successful
     */
    private boolean compareOrganizations(final Organization newOrganization, final Organization originalOrganization, final boolean nonUpdatedAttributes) {

        boolean pass = false;
        LOG.info("new org record = {}", newOrganization);
        assertThat(newOrganization).isNotNull();
        assertThat(newOrganization.getName()).isEqualTo(originalOrganization.getName());
        assertThat(newOrganization.isActive()).isEqualTo(originalOrganization.isActive());
        assertThat(newOrganization.getDescription()).isEqualTo(originalOrganization.getDescription());
        assertThat(newOrganization.getPrimaryContactEmail()).isEqualTo(originalOrganization.getPrimaryContactEmail());
        if (nonUpdatedAttributes) {
            assertThat(newOrganization.getIconUri()).isEqualTo(originalOrganization.getIconUri());
        }
        pass = true;
        return pass;
    }
}
