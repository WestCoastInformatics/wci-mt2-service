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

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ResultList;
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
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class OrganizationControllerIntegrationTest.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class OrganizationControllerIntegrationTest extends BaseTest {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(OrganizationControllerIntegrationTest.class);

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The object mapper. */
    private ObjectMapper objectMapper;

    /** The base url. */
    private String baseUrl = "";

    /** The env. */
    @Autowired
    private Environment env;

    /** The edition. */
    private Edition edition = null;

    /** The url. */
    private String url = null;

    /** The result. */
    private MvcResult result = null;

    /** The content. */
    private String content = null;

    /**
     * Creates a required edition.
     */
    @BeforeAll
    public void addPrerequisiteData() {

        final Edition tempEdition = new Edition();
        tempEdition.setId(null);
        tempEdition.setName("Organization Unit Test Edition");
        tempEdition.setShortName("orgTestShortName");
        tempEdition.setNamespace("orgTestNamespace");
        tempEdition.setIconUri("orgTestIconUri");
        tempEdition.setBranch("/SNOMEDCT");

        try (TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("orgTestUser");
            edition = service.add(tempEdition);
        } catch (Exception e) {
            logger.error("ERROR {}", e.getMessage(), e);
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

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/organization";

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

        final Organization originalOrg = new Organization();
        originalOrg.setId(null);
        originalOrg.setName("Unit Test Create");
        originalOrg.setActive(true);
        originalOrg.setDescription("Generated from unit test");
        originalOrg.setIconUri("/organization/icon/");
        originalOrg.setPrimaryContactEmail("org@test.com");
        originalOrg.setEdition(edition);

        logger.info(" organization = {}", originalOrg.toString());
        // forbidden - unit test user is set so this does not happen
        // mvc.perform(post(url).content(org.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isForbidden()).andReturn();

        // unsupported media type
        mvc.perform(post(url).content(originalOrg.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType()).andReturn();

        // method not found
        mvc.perform(post(url + "xyz").content(originalOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        // created
        result = mvc.perform(post(url).content(originalOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final Organization newOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(originalOrg, newOrg, true)).isTrue();
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

        final Organization originalOrg = new Organization();
        originalOrg.setId(null);
        originalOrg.setName("Unit Test Update");
        originalOrg.setActive(true);
        originalOrg.setDescription("Generated from unit test");
        originalOrg.setIconUri("/organization/icon/");
        originalOrg.setPrimaryContactEmail("org@test.com");
        originalOrg.setEdition(edition);

        logger.info(" new organization = {}", originalOrg.toString());

        // CREATE
        result = mvc.perform(post(url).content(originalOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final Organization newOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(originalOrg, newOrg, true)).isTrue();

        // UPDATE
        url = baseUrl + "/" + newOrg.getId();
        newOrg.setName("Unit Test Update 2");
        newOrg.setActive(true);
        newOrg.setDescription("Generated from update unit test");
        // FAILURE - is updated in different API call to update icon
        // newOrg.setIconUri("/organization/icon/updated");
        newOrg.setPrimaryContactEmail("updated.org@test.com");

        // unsupported media type
        mvc.perform(put(url).content(newOrg.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType()).andReturn();

        // bad request
        mvc.perform(put(url + "xyz").content(newOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest()).andReturn();

        // method not found
        mvc.perform(put(baseUrl + "xyz" + "/" + newOrg.getId()).content(newOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        // update
        url = baseUrl + "/" + newOrg.getId();
        result = mvc.perform(put(url).content(newOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);

        final Organization updatedOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(newOrg, updatedOrg, true)).isTrue();
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

        final Organization originalOrg = new Organization();
        originalOrg.setId(null);
        originalOrg.setName("Unit Test Get");
        originalOrg.setActive(true);
        originalOrg.setDescription("Generated from unit test");
        originalOrg.setIconUri("/organization/icon/");
        originalOrg.setPrimaryContactEmail("org@test.com");
        originalOrg.setEdition(edition);

        logger.info(" organization = {}", originalOrg.toString());

        // create
        result = mvc.perform(post(url).content(originalOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final Organization newOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(originalOrg, newOrg, true)).isTrue();

        // get
        url = baseUrl + "/" + newOrg.getId() + "xyz";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + newOrg.getId();
        result = mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);

        final Organization getOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(getOrg, newOrg, true)).isTrue();
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

        final Organization originalOrg = new Organization();
        originalOrg.setId(null);
        originalOrg.setName("FindMe");
        originalOrg.setActive(true);
        originalOrg.setDescription("Generated from unit test");
        originalOrg.setIconUri("/organization/icon/");
        originalOrg.setPrimaryContactEmail("org@test.com");
        originalOrg.setEdition(edition);

        logger.info(" organization = {}", originalOrg.toString());

        // create
        result = mvc.perform(post(url).content(originalOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final Organization newOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(originalOrg, newOrg, true)).isTrue();

        // get - search
        url = baseUrl + "/" + "searchxyz";
        mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + "search";

        // find by id
        result = mvc.perform(get(url).queryParam("query", "id:" + newOrg.getId()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final ResultList<Organization> resultList1 = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Organization>>() {
        }));
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getItems()).isNotNull();
        assertThat(resultList1.getItems().size()).isEqualTo(1);
        assertThat(resultList1.getItems().get(0)).isNotNull();
        assertThat(compareOrganization(resultList1.getItems().get(0), newOrg, true)).isTrue();

        // find by name
        result = mvc.perform(get(url).queryParam("query", "name:" + newOrg.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final ResultList<Organization> resultList2 = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Organization>>() {
        }));
        assertThat(resultList2).isNotNull();
        assertThat(resultList2.getItems()).isNotNull();
        assertThat(resultList2.getItems().size()).isEqualTo(1);
        assertThat(resultList2.getItems().get(0)).isNotNull();
        assertThat(compareOrganization(resultList2.getItems().get(0), newOrg, true)).isTrue();
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

        final Organization originalOrg = new Organization();
        originalOrg.setId(null);
        originalOrg.setName("TestInactive");
        originalOrg.setActive(true);
        originalOrg.setDescription("Generated from unit test");
        originalOrg.setIconUri("/organization/icon/");
        originalOrg.setPrimaryContactEmail("org@test.com");
        originalOrg.setEdition(edition);

        logger.info(" organization = {}", originalOrg.toString());

        // create
        result = mvc.perform(post(url).content(originalOrg.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isCreated()).andReturn();

        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final Organization newOrg = new ObjectMapper().readValue(content, Organization.class);
        assertThat(compareOrganization(originalOrg, newOrg, true)).isTrue();

        // inactive tests
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_XML)).andExpect(status().isMethodNotAllowed()).andReturn();

        url = baseUrl + "/" + newOrg.getId() + "xyz";
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/" + newOrg.getId();
        mvc.perform(delete(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isAccepted()).andReturn();

        // fetch to validate
        result = mvc.perform(get(url).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isNotFound()).andReturn();

        url = baseUrl + "/search";
        // find by id
        result = mvc.perform(get(url).queryParam("query", "id:" + newOrg.getId()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final ResultList<Organization> resultList1 = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Organization>>() {
        }));
        assertThat(resultList1).isNotNull();
        assertThat(resultList1.getItems()).isNotNull();
        assertThat(resultList1.getItems().size()).isEqualTo(0);

        // find by name
        result = mvc.perform(get(url).queryParam("query", "name:" + newOrg.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);
        final ResultList<Organization> resultList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<Organization>>() {
        }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems()).isNotNull();
        assertThat(resultList.getItems().size()).isEqualTo(0);

    }

    /**
     * Compare organization.
     *
     * @param newOrganization the new organization
     * @param originalOrganization the original organization
     * @param nonUpdatedAttributes the non updated attributes
     * @return true, if successful
     */
    private boolean compareOrganization(final Organization newOrganization, final Organization originalOrganization, final boolean nonUpdatedAttributes) {

        boolean pass = false;
        logger.info("new org record = {}", newOrganization);
        assertThat(newOrganization).isNotNull();
        assertThat(newOrganization.getName()).isEqualTo(originalOrganization.getName());
        assertThat(newOrganization.isActive()).isEqualTo(originalOrganization.isActive());
        assertThat(newOrganization.getEdition()).isEqualTo(originalOrganization.getEdition());
        assertThat(newOrganization.getDescription()).isEqualTo(originalOrganization.getDescription());
        assertThat(newOrganization.getPrimaryContactEmail()).isEqualTo(originalOrganization.getPrimaryContactEmail());
        if (nonUpdatedAttributes) {
            assertThat(newOrganization.getIconUri()).isEqualTo(originalOrganization.getIconUri());
        }
        pass = true;
        return pass;
    }

}
