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

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link Concept}.
 */
public class ConceptUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ConceptUnitTest.class);

    /** The model object to test. */
    private Concept object;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new Concept();
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
     * Test equals and hascode methods.
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

        // from Concept
        tester.include("code");
        tester.include("name");
        tester.include("fsn");
        tester.include("terminology");
        tester.include("version");
        tester.include("memberOfRefset");
        tester.include("memberEffectiveTime");
        tester.include("hasChildren");
        tester.exclude("descriptions");
        tester.include("historyVisible");
        tester.include("feedbackVisible");
        tester.include("defined");
        tester.include("released");
        tester.include("historyVisible");
        tester.include("historyVisible");
        tester.include("definitionExceptionType");
        tester.include("definitionExceptionId");
        tester.exclude("parents");
        tester.include("hasAncestorRefsetMembers");
        tester.exclude("children");
        tester.include("hasDescendantRefsetMembers");
        tester.exclude("roleGroups");

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
        assertTrue(tester.testCopyConstructor(Concept.class));
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
}
