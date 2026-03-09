package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.MapNote;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link MapNote}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MapNoteUnitTest extends BaseTest {

    private MapNote object;

    private MapUser user;

    @BeforeEach
    public void setup() throws Exception {

        object = new MapNote();
        final ProxyTester userTester = new ProxyTester(new MapUser());
        user = (MapUser) userTester.createObject(1);
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("user");
        tester.test();
    }

    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        tester.exclude("id");
        tester.include("note");
        tester.exclude("user");
        tester.exclude("timestamp");
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
        tester.exclude("user");
        assertTrue(tester.testJsonSerialization());
    }
}
