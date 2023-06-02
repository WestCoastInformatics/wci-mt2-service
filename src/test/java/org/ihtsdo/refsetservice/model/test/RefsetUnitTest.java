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

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
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
 * Unit test for {@link Refset}.
 */
public class RefsetUnitTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(RefsetUnitTest.class);

    /** The model object to test. */
    private Refset object;

    /** The edition object. */
    private Edition edition;

    /** The organization object. */
    private Organization organization;

    /** The project object. */
    private Project project;

    /** The DefinitionClause list. */
    private List<DefinitionClause> definitionList;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new Refset();

        final ProxyTester tester = new ProxyTester(new Edition());
        edition = (Edition) tester.createObject(1);

        final ProxyTester tester2 = new ProxyTester(new DefinitionClause());
        definitionList = new ArrayList<>();
        definitionList.add((DefinitionClause) tester2.createObject(1));
        definitionList.add((DefinitionClause) tester2.createObject(2));

        final ProxyTester tester3 = new ProxyTester(new Organization());
        organization = (Organization) tester3.createObject(1);

        final ProxyTester tester4 = new ProxyTester(new Project());
        project = (Project) tester4.createObject(1);
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
        tester.exclude("editionName");
        tester.exclude("editionShortName");
        tester.exclude("versionList");
        tester.exclude("edition");
        tester.exclude("editionBranch");
        tester.exclude("editionId");
        tester.exclude("projectId");

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
        tester.include("refsetId");
        tester.include("name");
        tester.include("type");
        tester.include("versionStatus");
        tester.include("workflowStatus");
        tester.include("versionDate");
        tester.include("narrative");
        tester.include("versionNotes");
        tester.include("privateRefset");
        tester.include("localSet");
        tester.include("latestPublishedVersion");
        tester.include("hasVersionInDevelopment");
        tester.include("assignedUser");
        tester.include("memberCount");
        tester.include("downloadable");
        tester.include("feedbackVisible");
        tester.exclude("roles");
        tester.include("locked");
        tester.include("upgradeWarning");
        tester.exclude("availableActions");
        tester.include("parentConceptId");
        tester.include("branchPath");
        tester.exclude("descriptions");
        tester.exclude("versionList");
        tester.include("moduleId");
        tester.include("editBranchId");
        tester.include("externalUrl");
        tester.exclude("project");
        tester.exclude("projectId");
        tester.exclude("tags");
        tester.exclude("definitionClauses");

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

        final Refset copyObject = new Refset();
        copyObject.setDefinitionClauses(definitionList);
        copyObject.setProject(project);
        copyObject.setEdition(edition);
        // copyObject.setTags(tagList);

        final CopyConstructorTester tester = new CopyConstructorTester(copyObject);
        assertTrue(tester.testCopyConstructor(Refset.class));
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

            final ProxyTester tester2 = new ProxyTester(new Refset());
            final Refset object = (Refset) tester2.createObject(1);
            LOG.info("************ object: " + object);
            object.setId(null);
            object.setEdition(null);
            object.setProject(null);
            object.setDefinitionClauses(null);
            object.setTags(null);

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            service.add(object);

            edition.setId(null);
            edition.setOrganization(organization);
            service.add(edition);

            project.setId(null);
            project.setEdition(edition);
            service.add(project);
            object.setProject(project);

            final Set<String> tags = new HashSet<>();
            tags.add("blood");
            tags.add("covid 19");
            object.setTags(tags);

            for (final DefinitionClause definition : definitionList) {

                definition.setId(null);
                service.add(definition);
                object.getDefinitionClauses().add(definition);
            }

            service.update(object);

            Refset retrievedObject = service.get(object.getId(), object.getClass());

            // test that the refset can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            // test that the correct number of definitions clauses are present.
            if (retrievedObject.getDefinitionClauses().size() != 2) {
                throw new Exception("Expected 2 definition clauses, found = " + retrievedObject.getDefinitionClauses().size());
            }

            // test that the correct number of tags are present.
            if (retrievedObject.getTags().size() != 2) {
                throw new Exception("Expected 2 tags, found = " + retrievedObject.getTags().size());
            }

            // test that the edition was properly added.
            if (retrievedObject.getEdition() == null || !retrievedObject.getEdition().getName().equals("1")) {
                throw new Exception("Refset edition not properly saved = " + retrievedObject.getId());
            }

            // test that project and organization were properly added.
            if (retrievedObject.getProject() == null || !retrievedObject.getProject().getName().equals("1")
                || retrievedObject.getProject().getEdition().getOrganization() == null
                || !retrievedObject.getProject().getEdition().getOrganization().getName().equals("1")) {
                throw new Exception("Refset project and organization not properly saved = " + retrievedObject.getId());
            }

            service.remove(object);
            service.remove(project);
            service.remove(organization);
            service.remove(edition);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }
        }
    }
}
