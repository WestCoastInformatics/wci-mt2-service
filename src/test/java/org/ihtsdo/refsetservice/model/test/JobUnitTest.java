package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Date;

import org.ihtsdo.refsetservice.model.Job;
import org.ihtsdo.refsetservice.service.TerminologyService;
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
 * Unit test for {@link Job}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class JobUnitTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(JobUnitTest.class);

    private Job object;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new Job();

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
        // from AbstractHasModified
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");

        tester.include("jobType");
        tester.include("resourceId");
        tester.include("parameters");
        tester.include("completedDate");
        tester.include("status");
        tester.include("errorMessage");
        tester.include("result");

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

        // Use the same date for testing to avoid comparison issues
        Date testDate = new Date(11);
        tester.proxy("completedDate", 1, testDate);

        assertTrue(tester.testCopyConstructor(Job.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);

        // Use the exact same date for all date fields to ensure consistency
        Date testDate = new Date(11);
        tester.proxy("completedDate", 1, testDate);
        tester.proxy("modified", 1, testDate);
        tester.proxy("created", 1, testDate);

        // Set other required fields
        tester.proxy("id", 1, "1");
        tester.proxy("parameters", 1, null);
        tester.proxy("modifiedBy", 1, "1");
        tester.proxy("active", 1, false);
        tester.proxy("jobType", 1, Job.Type.IMPORT);
        tester.proxy("resourceId", 1, "1");
        tester.proxy("status", 1, Job.Status.PROCESSING);
        tester.proxy("errorMessage", 1, "1");
        tester.proxy("result", 1, "1");

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

            // user
            final ProxyTester tester2 = new ProxyTester(new Job());
            final Job object = (Job) tester2.createObject(1);
            LOG.info("************ Object: {}", object);
            object.setId(null);

            service.add(object);
            service.update(object);

            Job retrievedObject = service.get(object.getId(), object.getClass());

            // test that the team can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            service.remove(object);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }

        }
    }
}
