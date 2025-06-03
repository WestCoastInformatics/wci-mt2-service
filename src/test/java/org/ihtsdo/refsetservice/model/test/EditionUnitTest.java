/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.ihtsdo.refsetservice.util.LanguageUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link Edition}.
 */
public class EditionUnitTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(EditionUnitTest.class);

    /** The model object to test. */
    private Edition object;

    /** The organization object. */
    private Organization organization;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new Edition();

        final ProxyTester tester1 = new ProxyTester(new Organization());
        organization = (Organization) tester1.createObject(1);

    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("organizationName");
        tester.exclude("organizationId");
        tester.exclude("abbreviation");
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
        tester.include("namespace");
        tester.include("maintainerType");
        tester.include("shortName");
        tester.include("iconUri");
        tester.include("branch");
        tester.include("defaultLanguageCode");

        tester.exclude("organization");
        tester.exclude("organizationId");
        tester.exclude("organizationName");

        tester.exclude("defaultLanguageRefsets");
        tester.exclude("moduleNames");
        tester.exclude("librarySortField");

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
        tester.proxy("organization", 1, organization);
        tester.exclude("librarySortField");
        assertTrue(tester.testCopyConstructor(Edition.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.proxy("organization", 1, organization);
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
            organization.setId(null);
            service.add(organization);

            // edition
            final ProxyTester tester2 = new ProxyTester(new Edition());
            final Edition object = (Edition) tester2.createObject(1);
            LOG.info("************ edition: {}", object);
            object.setId(null);
            object.setOrganization(organization);

            service.add(object);

            object.getDefaultLanguageRefsets().add(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US);

            service.update(object);

            Edition retrievedObject = service.get(object.getId(), object.getClass());

            // test that the edition can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {

                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            // test that the organization was properly added.
            if (retrievedObject.getOrganization() == null || !retrievedObject.getOrganization().getName().equals("1")) {

                throw new Exception("Refset organization not properly saved = " + retrievedObject.getId());
            }

            // test that the correct number of default language refset.
            if (retrievedObject.getDefaultLanguageRefsets().size() != 1) {

                throw new Exception("Expected 1 default language refset, found = " + retrievedObject.getDefaultLanguageRefsets().size());
            }

            service.remove(object);
            service.remove(organization);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {

                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }

        }

    }
}
