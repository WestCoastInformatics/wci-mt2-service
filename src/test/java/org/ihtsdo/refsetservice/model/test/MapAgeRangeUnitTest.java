package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.MapAgeRange;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link MapAgeRange}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MapAgeRangeUnitTest extends BaseTest {

    private MapAgeRange object;

    @BeforeEach
    public void setup() throws Exception {

        object = new MapAgeRange();
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
        tester.include("name");
        tester.include("lowerValue");
        tester.include("lowerUnits");
        tester.include("lowerInclusive");
        tester.include("upperValue");
        tester.include("upperUnits");
        tester.include("upperInclusive");
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
        assertTrue(tester.testJsonSerialization());
    }
}
