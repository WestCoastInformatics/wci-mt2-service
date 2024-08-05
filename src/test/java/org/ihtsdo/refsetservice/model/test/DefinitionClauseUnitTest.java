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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link DefinitionClause}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DefinitionClauseUnitTest extends BaseTest {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(DefinitionClauseUnitTest.class);

	/** The model object to test. */
	private DefinitionClause object;

	/** The edition object. */
	@SuppressWarnings("unused")
	private Edition edition;

	/** The organization object. */
	@SuppressWarnings("unused")
	private Organization organization;

	/** The DefinitionClause list. */
	private List<DefinitionClause> definitionList;

	/**
	 * Setup.
	 *
	 * @throws Exception the exception
	 */
	@BeforeEach
	public void setup() throws Exception {

		object = new DefinitionClause();

		final ProxyTester tester = new ProxyTester(new Edition());
		edition = (Edition) tester.createObject(1);

		final ProxyTester tester2 = new ProxyTester(new DefinitionClause());
		definitionList = new ArrayList<>();
		definitionList.add((DefinitionClause) tester2.createObject(1));
		definitionList.add((DefinitionClause) tester2.createObject(2));

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
		tester.include("value");
		tester.include("negated");

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

		final DefinitionClause copyObject = new DefinitionClause();

		final CopyConstructorTester tester = new CopyConstructorTester(copyObject);
		assertTrue(tester.testCopyConstructor(DefinitionClause.class));
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
			edition.setName("1");
			edition.setOrganization(organization);

			service.add(edition);

			// project
			final ProxyTester tester4 = new ProxyTester(new Project());
			final Project project = (Project) tester4.createObject(1);
			LOG.info("************ Project: {}", project);
			project.setId(null);
			project.setEdition(edition);

			service.add(project);

			final ProxyTester tester3 = new ProxyTester(new Refset());
			final Refset object = (Refset) tester3.createObject(1);
			LOG.info("************ Object: " + object);
			object.setId(null);
			object.setEdition(null);
			object.setProject(project);
			object.setDefinitionClauses(null);
			object.setTags(null);

			service.add(object);

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
				throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId()
						+ ", " + retrievedObject.getId());
			}

			// test that the correct number of definitions clauses are present.
			if (retrievedObject.getDefinitionClauses().size() != 2) {
				throw new Exception(
						"Expected 2 definition clauses, found = " + retrievedObject.getDefinitionClauses().size());
			}

			// test that the correct number of tags are present.
			if (retrievedObject.getTags().size() != 2) {
				throw new Exception("Expected 2 tags, found = " + retrievedObject.getTags().size());
			}

			// test that the edition was properly added.
			if (retrievedObject.getEdition() == null || !retrievedObject.getEdition().getName().equals("1")) {
				throw new Exception("Refset edition not properly saved = " + retrievedObject.getId() + " "
						+ retrievedObject.getEdition().getName());
			}

			// test that project and organization were properly added.
			if (retrievedObject.getProject() == null || !retrievedObject.getProject().getName().equals("1")
					|| retrievedObject.getProject().getEdition().getOrganization() == null
					|| !retrievedObject.getProject().getEdition().getOrganization().getName().equals("1")) {
				throw new Exception("Refset project and organization not properly saved = " + retrievedObject.getId());
			}

			service.remove(object);
			service.remove(edition);
			service.remove(organization);

			retrievedObject = service.get(object.getId(), object.getClass());

			if (retrievedObject != null) {
				throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
			}
		}

	}
}
