package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link UpgradeReplacementConcept}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class UpgradeReplacementConceptUnitTest extends BaseTest {

    private UpgradeReplacementConcept object;

    @BeforeEach
    public void setup() throws Exception {

        object = new UpgradeReplacementConcept();
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
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
        tester.include("code");
        tester.include("memberId");
        tester.include("descriptions");
        tester.include("reason");
        tester.include("existingMember");
        tester.include("added");
        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    @Test
    public void testModelCopy() throws Exception {

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        assertTrue(tester.testCopyConstructor(UpgradeReplacementConcept.class));
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        assertTrue(tester.testJsonSerialization());
    }
}
