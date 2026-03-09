package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.RefsetEditHistory;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link RefsetEditHistory}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class RefsetEditHistoryUnitTest extends BaseTest {

    private RefsetEditHistory object;

    @BeforeEach
    public void setup() throws Exception {

        object = new RefsetEditHistory();
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("definitionClauses");
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
        tester.include("refsetId");
        tester.include("name");
        tester.include("type");
        tester.include("versionStatus");
        tester.include("workflowStatus");
        tester.include("versionDate");
        tester.include("narrative");
        tester.include("versionNotes");
        tester.include("moduleId");
        tester.include("editBranchId");
        tester.include("externalUrl");
        tester.include("memberCount");
        tester.include("privateRefset");
        tester.include("localSet");
        tester.include("inUpgrade");
        tester.include("inInactivate");
        tester.exclude("definitionClauses");
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
        assertTrue(tester.testCopyConstructor(RefsetEditHistory.class));
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("definitionClauses");
        assertTrue(tester.testJsonSerialization());
    }
}
