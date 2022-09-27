
package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.PersistenceTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link Edition}.
 */
public class EditionUnitTest extends BaseTest {

    /** The logger. */
    @SuppressWarnings("unused")
    private final Logger logger = LoggerFactory.getLogger(EditionUnitTest.class);

    /** The model object to test. */
    private Edition object;

    /** The organization object. */
    private Organization organization;

    private static final String DEFAULT_LANGUAGE_REFSET = "900000000000509007";

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
        tester.include("name");
        tester.include("namespace");
        tester.include("shortName");
        tester.include("iconUri");
        tester.include("branch");
        tester.include("topLevelModule");
        tester.include("defaultLanguageCode");
        tester.exclude("defaultLanguageRefsets");

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

        final Edition copyObject = new Edition();
        copyObject.setOrganization(organization);

        final CopyConstructorTester tester = new CopyConstructorTester(copyObject);
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
        assertTrue(tester.testJsonSerialization());
    }

    /**
     * Test persistence.
     *
     * @throws Exception the exception
     */
    @Test
    public void testPersistence() throws Exception {

        final PersistenceTester tester = new PersistenceTester(object, true, true);
        tester.test();

        try (final TerminologyService service = new TerminologyService()) {

            final ProxyTester tester2 = new ProxyTester(new Edition());
            final Edition object = (Edition) tester2.createObject(1);
            logger.info("************ object: " + object);
            object.setId(null);
            object.setOrganization(null);
            object.setDefaultLanguageRefsets(null);

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            service.add(object);

            organization.setId(null);
            service.add(organization);
            object.setOrganization(organization);

            object.getDefaultLanguageRefsets().add(DEFAULT_LANGUAGE_REFSET);

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
