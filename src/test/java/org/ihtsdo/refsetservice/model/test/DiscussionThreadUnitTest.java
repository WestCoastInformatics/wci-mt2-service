package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link DiscussionThread}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DiscussionThreadUnitTest extends BaseTest {

    private DiscussionThread object;

    @BeforeEach
    public void setup() throws Exception {

        object = new DiscussionThread();
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("posts");
        tester.exclude("lastPost");
        tester.exclude("numberReplies");
        tester.test();
    }

    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");
        tester.include("subject");
        tester.include("type");
        tester.include("refsetInternalId");
        tester.include("conceptId");
        tester.include("privateThread");
        tester.include("visibility");
        tester.include("status");
        tester.include("resolvedBy");
        tester.exclude("posts");
        tester.exclude("lastPost");
        tester.exclude("numberReplies");
        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("posts");
        assertTrue(tester.testJsonSerialization());
    }
}
