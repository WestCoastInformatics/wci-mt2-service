
package org.ihtsdo.refsetservice.model;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.refsetservice.BaseTest;
import org.ihtsdo.refsetservice.CopyConstructorTester;
import org.ihtsdo.refsetservice.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.GetterSetterTester;
import org.ihtsdo.refsetservice.ProxyTester;
import org.ihtsdo.refsetservice.SerializationTester;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link ConceptResultList}.
 */
public class ConceptResultListUnitTest extends BaseTest {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(ConceptResultListUnitTest.class);

    /** The model object to test. */
    private ConceptResultList object;

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
        object = new ConceptResultList();

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
     * Test equals and hascode methods.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelEqualsHashcode() throws Exception {
        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        tester.include("total");
        tester.include("parameters");
        tester.exclude("items");

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

        assertTrue(tester.testCopyConstructor(ConceptResultList.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {
        final SerializationTester tester = new SerializationTester(object);
        tester.proxy("concepts", 1, c1);
        tester.proxy(SearchParameters.class, 1, sp1);

        assertTrue(tester.testJsonSerialization());
    }
}
