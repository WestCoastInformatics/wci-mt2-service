package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetEditHistory;
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
 * Unit test for {@link MapSetEditHistory}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MapSetEditHistoryUnitTest extends BaseTest {

    private MapSetEditHistory object;

    @BeforeEach
    public void setup() throws Exception {

        object = new MapSetEditHistory();
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("refsetId");
        tester.exclude("refsetBranchId");
        tester.exclude("editionBranch");
        tester.exclude("editionShortName");
        tester.exclude("isLocalSet");
        tester.exclude("isInUpgrade");
        tester.exclude("isInInactivate");
        tester.test();
    }

    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");

        tester.include("id");

        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    @Test
    public void testConstructorFromMapSet() throws Exception {

        final ProxyTester mapSetTester = new ProxyTester(new MapSet());
        final MapSet mapSet = (MapSet) mapSetTester.createObject(1);
        mapSet.setRefSetCode("123456");
        mapSet.setRefSetName("Test Refset");
        mapSet.setName("Test MapSet");

        final MapSetEditHistory history = new MapSetEditHistory(mapSet);

        assertEquals("123456", history.getRefSetCode());
        assertEquals("Test Refset", history.getRefSetName());
        assertEquals("Test MapSet", history.getName());
    }

    @Test
    public void testCompareTo() throws Exception {

        final MapSetEditHistory a = new MapSetEditHistory();
        a.setName("Alpha");
        a.setRefSetCode("A1");

        final MapSetEditHistory b = new MapSetEditHistory();
        b.setName("Beta");
        b.setRefSetCode("B1");

        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
        assertEquals(0, a.compareTo(a));
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("project");
        assertTrue(tester.testJsonSerialization());
    }
}
