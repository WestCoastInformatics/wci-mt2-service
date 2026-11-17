/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.hibernate7.Hibernate7Module;

/**
 * Unit test for {@link User}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class UserUnitTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(UserUnitTest.class);

    /** The model object to test. */
    private User object;

    /** The team object. */
    private Set<Team> teams;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new User();

        final ProxyTester testerTeam = new ProxyTester(new Team());
        teams = new HashSet<>();
        teams.add((Team) testerTeam.createObject(1));
        teams.add((Team) testerTeam.createObject(2));
        object.getTeams().addAll(teams);

    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.test();
    }

    /**
     * Test equals and hashcode methods.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        // from AbstractHasModified
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");

        tester.include("authToken");
        tester.include("company");
        tester.include("email");
        tester.include("iconUri");
        tester.include("name");
        tester.exclude("roles");
        tester.include("title");
        tester.include("userName");
        tester.exclude("teams");

        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    /**
     * Test model copy.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelCopy() throws Exception {

        final User copyObject = new User();
        copyObject.setTeams(teams);

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        assertTrue(tester.testCopyConstructor(User.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("roles");
        tester.exclude("teams");
        tester.exclude("authToken");
        assertTrue(tester.testJsonSerialization());
    }

    /**
     * Hibernate7Module skips {@code @Transient} fields unless they have {@code @JsonGetter}. Session roles and the auth token must still appear in API JSON.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRolesAndAuthTokenSerializedWhenPresent() throws Exception {

        final User user = new User();
        user.setUserName("rwood@westcoastinformatics.com");
        user.setAuthToken("token");
        user.getRoles().add("all-all-all-admin");
        user.getRoles().add("all-all-all-lead");

        final JsonNode node = hibernateObjectMapper().readTree(hibernateObjectMapper().writeValueAsString(user));
        assertTrue(node.has("authToken"), node.toString());
        assertEquals("token", node.get("authToken").asString());
        assertTrue(node.has("roles"), node.toString());
        assertTrue(node.get("roles").isArray());
        assertEquals(2, node.get("roles").size());
    }

    /**
     * Object mapper matching production {@code JacksonConfiguration} (Hibernate7Module).
     *
     * @return the object mapper
     */
    private static JsonMapper hibernateObjectMapper() {

        return JsonMapper.builder().addModule(new Hibernate7Module()).build();
    }

    /**
     * Test persistence.
     *
     * @throws Exception the exception
     */
    @Test
    public void testPersistence() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            // user
            final ProxyTester tester2 = new ProxyTester(new User());
            final User object = (User) tester2.createObject(1);
            LOG.info("************ Object: {}", object);
            object.setId(null);
            object.getRoles().add("1");
            object.getRoles().add("2");

            service.add(object);
            service.update(object);

            User retrievedObject = service.get(object.getId(), object.getClass());

            // test that the team can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            service.remove(object);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }

        }
    }
}
