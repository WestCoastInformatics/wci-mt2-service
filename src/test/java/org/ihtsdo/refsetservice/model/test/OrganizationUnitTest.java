/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Organization;
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

/**
 * Unit test for {@link Organization}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class OrganizationUnitTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(OrganizationUnitTest.class);

    /** The model object to test. */
    private Organization object;

    /** The members. */
    private Set<User> members;

    /**
     * Setup before each test.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new Organization();

        final ProxyTester tester1 = new ProxyTester(new User());
        members = new HashSet<>();
        members.add((User) tester1.createObject(1));
        members.add((User) tester1.createObject(2));
        object.getMembers().addAll(members);

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

        tester.include("name");
        tester.include("description");
        tester.include("primaryContactEmail");
        tester.exclude("members");
        tester.include("iconUri");
        tester.include("affiliate");
        tester.include("countryCode");
        tester.include("crowdId");
        tester.exclude("roles");

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

        final Organization copyObject = new Organization();
        copyObject.setMembers(members);

        final CopyConstructorTester tester = new CopyConstructorTester(copyObject);
        assertTrue(tester.testCopyConstructor(Organization.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
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

            final ProxyTester tester2 = new ProxyTester(new Organization());
            final Organization object = (Organization) tester2.createObject(1);
            LOG.info("************ object: " + object);
            object.setId(null);
            object.setMembers(null);

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            service.add(object);

            for (final User user : members) {

                user.setId(null);
                service.add(user);
                object.getMembers().add(user);
            }

            service.update(object);

            Organization retrievedObject = service.get(object.getId(), object.getClass());

            // test that the organzation can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            // test that the correct number of members are present.
            if (retrievedObject.getMembers().size() != 2) {
                throw new Exception("Expected 2 members (users), found = " + retrievedObject.getMembers().size());
            }

            service.remove(object);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }
        }

    }
}
