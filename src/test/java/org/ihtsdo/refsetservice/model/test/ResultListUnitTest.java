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

import org.ihtsdo.refsetservice.model.AbstractHasId;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link ResultList}.
 */
public class ResultListUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ResultListUnitTest.class);

    /** The model object to test. */
    private ResultList<AbstractHasId> object;

    /** The sc 1. */
    private SearchParameters sc1;

    /** The sc 2. */
    private SearchParameters sc2;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new ResultList<>();

        final ProxyTester tester = new ProxyTester(new SearchParameters());
        sc1 = (SearchParameters) tester.createObject(1);
        sc2 = (SearchParameters) tester.createObject(2);
    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.proxy(SearchParameters.class, 1, sc1);
        tester.proxy(SearchParameters.class, 2, sc2);
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

        tester.proxy(SearchParameters.class, 1, sc1);
        tester.proxy(SearchParameters.class, 2, sc2);

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
        tester.proxy(SearchParameters.class, 1, sc1);
        assertTrue(tester.testCopyConstructor(ResultList.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.proxy(SearchParameters.class, 1, sc1);

        assertTrue(tester.testJsonSerialization());
    }
}
