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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Properties;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
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
 * The Class UserControllerIntegrationTest.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UserControllerIntegrationTest extends BaseTest {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(UserControllerIntegrationTest.class);

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The test properties. */
    @Autowired
    Properties testProperties;

    /** The object mapper. */
    private ObjectMapper objectMapper;

    /** The base url. */
    private String baseUrl = "";

    /** The env. */
    @Autowired
    private Environment env;

    /** The edition. */
    private Edition edition = null;

    /** The organization. */
    private Organization organization = null;

    /** The test user. */
    private User testUser = null;

    /** The url. */
    private String url = null;

    /** The result. */
    private MvcResult result = null;

    /** The content. */
    private String content = null;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp() {

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/user";
    }

    /**
     * Sets the up.
     */
    @BeforeAll
    public void addData() {

        final Edition tempEdition = new Edition();
        tempEdition.setId(null);
        tempEdition.setName("User Unit Test Edition");
        tempEdition.setShortName("userTestShortName");
        tempEdition.setNamespace("userTestNamespace");
        tempEdition.setIconUri("userTestIconUri");
        tempEdition.setBranch("/SNOMEDCT");

        try (TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("userTestUser");
            edition = service.add(tempEdition);
        } catch (Exception e) {
            logger.error("ERROR {}", e.getMessage(), e);
            assertTrue(false);
        }

        assertThat(edition).isNotNull();
        assertThat(edition.getId()).isNotNull();

        final Organization tempOrganization = new Organization();
        tempOrganization.setId(null);
        tempOrganization.setName("User Unit Test Organization");
        tempOrganization.setActive(true);
        tempOrganization.setDescription("Generated from unit test");
        tempOrganization.setIconUri("/organization/icon/");
        tempOrganization.setPrimaryContactEmail("org@test.com");
        tempOrganization.setEdition(edition);

        try (TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("userTestUser");
            organization = service.add(tempOrganization);
        } catch (Exception e) {
            logger.error("ERROR {}", e.getMessage(), e);
            assertTrue(false);
        }

        assertThat(organization).isNotNull();
        assertThat(organization.getId()).isNotNull();

        try {

            testUser = new User();
            testUser.setUserName("unitTestUser");
            testUser.setName("Unit Test User");
            testUser.setEmail("user@fake.org");
            testUser.setTitle("Senior Mapper");
            testUser.setCompany("The Company");

            final User user2 = new User();
            user2.setUserName("secondTestUser");
            user2.setName("Second Unit Tester");
            user2.setEmail("2nduser@fake.org");
            user2.setTitle("Another Senior Mapper");
            user2.setCompany("The Company");

            try {
                testUser = addUser(testUser);
                addUser(user2);
            } catch (Exception e) {
                logger.error("Exception adding users : {}", e);
                throw e;
            }

        } catch (Exception ex) {
            logger.error("Exception setting up date for tests", ex);
            assertThat(false).isEqualTo(true);
        }

    }

    // API does not include create user - That is performed by the Security Service
    // @Test
    // @Order(1)
    // public void testCreate() throws Exception {}

    /**
     * Test get.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(2)
    public void testGet() throws Exception {

        url = baseUrl + "/" + testUser.getId();

        result = mvc.perform(get(url).queryParam("includeMembers", "true").contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = {}", content);

        final User newUser = new ObjectMapper().readValue(content, User.class);
        assertThat(compareUsers(testUser, newUser, false)).isTrue();
    }

    /**
     * Test update.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(3)
    public void testUpdate() throws Exception {

        url = baseUrl;

        // updates
        testUser.setName("Unit Test User - Update");
        testUser.setEmail("update@fake.org");
        testUser.setTitle("Senior Mapper - Update");
        testUser.setCompany("The New Company - Update");

        url = baseUrl + "/" + testUser.getId();
        mvc.perform(put(url).content(testUser.toString())).andExpect(status().isUnsupportedMediaType()).andReturn();

        mvc.perform(put(url).content(testUser.toString()).contentType(MediaType.APPLICATION_XML)).andExpect(status().isUnsupportedMediaType()).andReturn();
        result = mvc.perform(put(url).content(testUser.toString()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();

        final User updatedUser = new ObjectMapper().readValue(content, User.class);
        assertThat(compareUsers(testUser, updatedUser, false)).isTrue();

    }

    /**
     * Test find.
     *
     * @throws Exception the exception
     */
    @Test
    @Order(4)
    public void testFind() throws Exception {

        // find by user name
        url = baseUrl + "/search?";

        result = mvc.perform(
            get(url).queryParam("query", "userName:" + testUser.getUserName()).queryParam("includeOrganizations", "false").queryParam("includeTeams", "false").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        final ResultList<User> userNameResultList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<User>>() {
        }));

        assertThat(userNameResultList).isNotNull();
        assertThat(userNameResultList.getItems()).isNotNull();
        assertThat(userNameResultList.getItems().size()).isEqualTo(1);

        final User userNameUser = userNameResultList.getItems().get(0);
        assertThat(compareUsers(testUser, userNameUser, false)).isTrue();

        // find by email
        /*
         * NOT INDEXED IN USER url = baseUrl + "/search";
         * 
         * result = mvc.perform(get(url).queryParam("query", "email:" + testUser.getEmail()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
         * content = result.getResponse().getContentAsString(); logger.info(" content = {}", content); final ResultList<User> emailResultList = new
         * ObjectMapper().readValue(content, (new TypeReference<ResultList<User>>() { }));
         * 
         * assertThat(emailResultList).isNotNull(); assertThat(emailResultList.getItems()).isNotNull(); assertThat(emailResultList.getItems().size()).isEqualTo(1);
         * 
         * final User emailNewUser = emailResultList.getItems().get(0); assertThat(compareUsers(testUser, emailNewUser, true)).isTrue();
         */

        // find by name
        /*
         * NOT INDEXED IN USER url = baseUrl + "/search";
         * 
         * result = mvc.perform(get(url).queryParam("query", "name:" + testUser.getName()).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
         * content = result.getResponse().getContentAsString(); logger.info(" content = {}", content); final ResultList<User> resultList = new
         * ObjectMapper().readValue(content, (new TypeReference<ResultList<User>>() { }));
         * 
         * assertThat(resultList).isNotNull(); assertThat(resultList.getItems()).isNotNull(); assertThat(resultList.getItems().size()).isEqualTo(1);
         * 
         * final User newUser = resultList.getItems().get(0); assertThat(compareUsers(testUser, newUser, true)).isTrue(); testUser = resultList.getItems().get(0);
         */

    }

    // API does not include inactivate user - That is performed by the Security Service
    // @Test
    // @Order(3)
    // public void testInactivate() throws Exception {}

    /**
     * Compare users.
     *
     * @param newUser the new user
     * @param originalUser the original user
     * @param nonUpdatedAttributes the non updated attributes
     * @return true, if successful
     */
    private boolean compareUsers(final User newUser, final User originalUser, final boolean nonUpdatedAttributes) {

        boolean pass = false;
        logger.info("new user record = {}", newUser);
        assertThat(newUser).isNotNull();
        assertThat(newUser.getName()).isEqualTo(originalUser.getName());
        assertThat(newUser.isActive()).isEqualTo(originalUser.isActive());
        assertThat(newUser.getEmail()).isEqualTo(originalUser.getEmail());
        assertThat(newUser.getTitle()).isEqualTo(originalUser.getTitle());

        if (nonUpdatedAttributes) {
            assertThat(newUser.getIconUri()).isEqualTo(originalUser.getIconUri());
        }
        pass = true;
        return pass;

    }

    /**
     * Adds the user.
     *
     * @param user the user
     * @return the user
     * @throws Exception the exception
     */
    private User addUser(final User user) throws Exception {

        try (final SecurityService service = new SecurityService()) {
            service.addUser(user);

            return user;
        } catch (Exception e) {
            logger.error("Error adding user: {}", user, e);
            throw e;
        }

    }

}
