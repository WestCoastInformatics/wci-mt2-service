
package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.AbstractHasId;
import org.ihtsdo.refsetservice.model.SearchParameters;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link ResultList}.
 */
public class ResultListUnitTest extends BaseTest {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(ResultListUnitTest.class);

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
        tester.include("parameters");
        tester.exclude("items");
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
