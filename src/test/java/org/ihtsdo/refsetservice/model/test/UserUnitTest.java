package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
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
 * Unit test for {@link User}.
 */
public class UserUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(UserUnitTest.class);

    /** The model object to test. */
    private User object;

    /** The team object. */
    private Set<Team> teams;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new User();

        final ProxyTester testerTeam = new ProxyTester(new Team());
        teams = new HashSet<>();
        teams.add((Team) testerTeam.createObject(1));
        teams.add((Team) testerTeam.createObject(2));
        object.getTeams().addAll(teams);

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
        tester.include("authToken");
        tester.include("company");
        tester.include("email");
        tester.include("iconUri");
        tester.include("name");
        tester.exclude("roles");
        tester.include("title");
        tester.include("userName");
        tester.exclude("teams");

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

        final User copyObject = new User();
        copyObject.setTeams(teams);

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        assertTrue(tester.testCopyConstructor(User.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    // TODO Fix @Test
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
    }
}
