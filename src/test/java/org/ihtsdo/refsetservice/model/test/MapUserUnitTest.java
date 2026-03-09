package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link MapUser}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MapUserUnitTest extends BaseTest {

    private MapUser object;

    @BeforeEach
    public void setup() throws Exception {

        object = new MapUser();
        object.setApplicationRole(MapUserRole.LEAD);
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("authToken");
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
        tester.include("userName");
        tester.include("name");
        tester.include("email");
        tester.include("applicationRole");
        tester.include("team");
        tester.exclude("authToken");
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
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");
        assertTrue(tester.testCopyConstructor(MapUser.class));
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("authToken");
        assertTrue(tester.testJsonSerialization());
    }

    @Test
    public void testPersistence() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            final ProxyTester tester = new ProxyTester(new MapUser());
            final MapUser object = (MapUser) tester.createObject(1);
            object.setId(null);
            object.setUserName("test-map-user-" + System.currentTimeMillis());

            service.add(object);

            final MapUser retrieved = service.get(object.getId(), MapUser.class);
            if (!object.getId().equals(retrieved.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrieved.getId());
            }

            service.remove(object);

            final MapUser afterRemove = service.get(object.getId(), MapUser.class);
            if (afterRemove != null) {
                throw new Exception("Search results unexpectedly not empty = " + afterRemove.getId());
            }
        }
    }
}
