package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.Artifact;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link Artifact}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class ArtifactUnitTest extends BaseTest {

    private Artifact object;

    @BeforeEach
    public void setup() throws Exception {

        object = new Artifact();
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("downloadUrl");
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
        tester.include("entityType");
        tester.include("entityId");
        tester.include("fileName");
        tester.include("storedFileName");
        tester.include("fileType");
        tester.include("description");
        tester.exclude("downloadUrl");
        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    @Test
    public void testModelCopy() throws Exception {

        final org.ihtsdo.refsetservice.test.CopyConstructorTester tester = new org.ihtsdo.refsetservice.test.CopyConstructorTester(object);
        tester.exclude("downloadUrl");
        assertTrue(tester.testCopyConstructor(Artifact.class));
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("downloadUrl");
        assertTrue(tester.testJsonSerialization());
    }
}
