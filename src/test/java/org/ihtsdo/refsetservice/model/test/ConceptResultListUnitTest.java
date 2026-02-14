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
import java.util.List;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link ResultListConcept}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ConceptResultListUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ConceptResultListUnitTest.class);

    /** The model object to test. */
    private ResultListConcept object;

    /** The c 1. */
    private List<Concept> c1;

    /** The c 2. */
    private List<Concept> c2;

    /** The sc 1. */
    private SearchParameters sp1;

    /** The sc 2. */
    private SearchParameters sp2;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new ResultListConcept();

        final ProxyTester tester = new ProxyTester(new SearchParameters());
        sp1 = (SearchParameters) tester.createObject(1);
        sp2 = (SearchParameters) tester.createObject(2);

        final ProxyTester tester2 = new ProxyTester(new Concept());
        c1 = new ArrayList<>();
        c1.add((Concept) tester2.createObject(1));
        c2 = new ArrayList<>();
        c2.add((Concept) tester2.createObject(1));
        c2.add((Concept) tester2.createObject(2));
    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.proxy("concepts", 1, c1);
        tester.proxy("concepts", 2, c2);
        tester.proxy(SearchParameters.class, 1, sp1);
        tester.proxy(SearchParameters.class, 2, sp2);

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
        tester.include("total");
        tester.exclude("items");
        tester.include("limit");
        tester.include("offset");
        tester.include("miscCountA");
        tester.include("miscCountB");
        tester.include("searchAfter");
        tester.include("totalKnown");
        tester.exclude("scoreMap");
        tester.include("timeTaken");
        tester.include("parameters");

        tester.proxy("concepts", 1, c1);
        tester.proxy("concepts", 2, c2);
        tester.proxy(SearchParameters.class, 1, sp1);
        tester.proxy(SearchParameters.class, 2, sp2);

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
        tester.proxy("concepts", 1, c1);
        tester.proxy(SearchParameters.class, 1, sp1);

        assertTrue(tester.testCopyConstructor(ResultListConcept.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.proxy("items", 1, c1);
        tester.proxy(SearchParameters.class, 1, sp1);

        assertTrue(tester.testJsonSerialization());
    }
}
