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

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Team;
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
 * Unit test for {@link Project}.
 */
public class ProjectUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ProjectUnitTest.class);

    /** The model object to test. */
    private Project object;

    /** The edition. */
    private Edition edition;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        // edition
        final ProxyTester tester2 = new ProxyTester(new Edition());
        edition = new Edition();

        // project
        object = new Project();
        object.setEdition((Edition) tester2.createObject(1));

    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);

        // tester.exclude("edition");
        // tester.exclude("teams");
        // tester.exclude("roles");
        // tester.exclude("memberList");

        tester.exclude("editionId");
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
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");

        tester.include("name");
        tester.include("description");
        tester.include("privateProject");
        tester.include("primaryContactEmail");
        tester.include("crowdProjectId");

        tester.exclude("edition");
        tester.exclude("teams");
        tester.exclude("roles");
        tester.exclude("memberList");

        tester.exclude("editionId");
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
        assertTrue(tester.testCopyConstructor(Project.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("edition");
        tester.exclude("teams");
        tester.exclude("roles");
        tester.exclude("memberList");

        tester.exclude("editionId");
        tester.exclude("organizationId");

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

            // edition
            final ProxyTester tester2 = new ProxyTester(new Edition());
            final Edition edition = (Edition) tester2.createObject(1);
            LOG.info("************ Edition: {}", edition);
            edition.setId(null);
            edition.setOrganization(organization);

            service.add(edition);

            // teams
            final ProxyTester tester3 = new ProxyTester(new Team());
            final Team team1 = (Team) tester3.createObject(1);
            team1.setId(null);
            team1.setOrganization(organization);
            LOG.info("************ Team1: {}", team1);
            final Team team2 = (Team) tester3.createObject(2);
            team2.setId(null);
            team2.setOrganization(organization);
            LOG.info("************ Team2: {}", team2);

            service.add(team1);
            service.add(team2);

            // project
            final ProxyTester tester4 = new ProxyTester(new Project());
            final Project object = (Project) tester4.createObject(1);
            LOG.info("************ Object: {}", object);
            object.setId(null);
            object.setEdition(edition);
            object.getTeams().add(team1.getId());
            object.getTeams().add(team2.getId());

            service.add(object);
            service.update(object);

            Project retrievedObject = service.get(object.getId(), object.getClass());

            // test that the Project can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            // test that the correct number of members are present.
            if (retrievedObject.getTeams().size() != 2) {
                throw new Exception("Expected 2 members (users), found = " + retrievedObject.getTeams().size());
            }

            service.remove(object);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }
        }

    }

}
