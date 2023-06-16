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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Organization;
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

/**
 * Unit test for {@link Team}.
 */
public class TeamUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(TeamUnitTest.class);

    /** The model object to test. */
    private Team object;

    /** The organization. */
    private Organization organization;

    /** The members. */
    private Set<String> roles;

    /** The members. */
    private Set<String> members;

    /** The users. */
    private List<User> users;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new Team();

        // team belongs to an organization
        final ProxyTester tester1 = new ProxyTester(new Organization());
        organization = new Organization();
        organization.setActive(true);
        organization.setPrimaryContactEmail("1");
        organization.setModified(null);
        organization.setModifiedBy("none");
        organization.setCreated(null);
        object.setOrganization((Organization) tester1.createObject(1));

        // team members
        final ProxyTester tester2 = new ProxyTester(new String());
        members = new HashSet<>();
        members.add((String) tester2.createObject(1));
        members.add((String) tester2.createObject(2));
        object.getMembers().addAll(members);

        // team roles
        final ProxyTester tester3 = new ProxyTester(new String());
        roles = new HashSet<>();
        roles.add((String) tester3.createObject(1));
        roles.add((String) tester3.createObject(2));
        object.getRoles().addAll(roles);

        // member list
        final ProxyTester tester4 = new ProxyTester(new User());
        users = new ArrayList<>();
        users.add((User) tester4.createObject(1));
        users.add((User) tester4.createObject(2));
        users.add((User) tester4.createObject(3));
        object.getMemberList().addAll(users);

    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("organizationId");
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
        tester.include("id");

        tester.include("name");
        tester.include("description");

        tester.proxy("organization", 1, organization);
        tester.include("primaryContactEmail");
        tester.include("type");

        tester.proxy("roles", 1, roles); // set<string>
        tester.proxy("members", 1, members); // set<string>
        tester.exclude("memberList"); // list<users>
        tester.exclude("userRoles"); // list<string>

        tester.exclude("organizationId");

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

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        assertTrue(tester.testCopyConstructor(Team.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.include("id");
        tester.include("modified");
        tester.include("created");
        tester.include("modifiedBy");
        tester.include("name");
        tester.include("description");
        tester.proxy("organization", 1, organization);
        tester.include("primaryContactEmail");
        tester.proxy("roles", 1, roles); // set<string>
        tester.proxy("members", 1, members); // set<string>
        tester.proxy("memberList", 1, users); // list<users>
        tester.proxy("userRoles", 1, members); // list<string>
        tester.include("organizationId");

        assertTrue(tester.testJsonSerialization());
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

            // organization
            final ProxyTester tester1 = new ProxyTester(new Organization());
            final Organization organization = (Organization) tester1.createObject(1);
            LOG.info("************ Organization: {}", organization);
            organization.setId(null);

            service.add(organization);

            // team
            final ProxyTester tester2 = new ProxyTester(new Team());
            final Team object = (Team) tester2.createObject(1);
            LOG.info("************ Object: {}", object);
            object.setId(null);
            object.setOrganization(organization);

            service.add(object);
            service.update(object);

            Team retrievedObject = service.get(object.getId(), object.getClass());

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
