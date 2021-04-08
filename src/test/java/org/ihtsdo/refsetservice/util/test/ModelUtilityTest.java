
package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the norm utility.
 */
public class ModelUtilityTest extends BaseTest {

    /** The logger. */
    @SuppressWarnings("unused")
    private final Logger logger = LoggerFactory.getLogger(ModelUtilityTest.class);

    /**
     * Test equals null.
     */
    @Test
    public void testEqualsNull() {
        assertTrue(ModelUtility.equalsNullSafe(null, null));
        assertTrue(ModelUtility.equalsNullSafe("a", "a"));
        assertFalse(ModelUtility.equalsNullSafe("a", null));
        assertFalse(ModelUtility.equalsNullSafe(null, "b"));
        assertFalse(ModelUtility.equalsNullMatch("a", "b"));
        assertTrue(ModelUtility.equalsNullMatch(null, null));
        assertTrue(ModelUtility.equalsNullMatch("a", "a"));
        assertTrue(ModelUtility.equalsNullMatch("a", null));
        assertTrue(ModelUtility.equalsNullMatch(null, "b"));
        assertFalse(ModelUtility.equalsNullMatch("a", "b"));
    }

    /**
     * Test first not null.
     *
     * @throws Exception the exception
     */
    @Test
    public void testFirstNotNull() throws Exception {
        assertEquals("abc", ModelUtility.firstNotNull(null, "abc"));
        assertEquals("abc", ModelUtility.firstNotNull(null, "abc", null));
        assertEquals("abc", ModelUtility.firstNotNull("abc", null));
        assertEquals("abc", ModelUtility.firstNotNull("abc", "def", null));
        assertEquals("abc", ModelUtility.firstNotNull("abc", "def", null, "ghi"));
        assertNull(ModelUtility.firstNotNull(null, null));
        assertNull(ModelUtility.firstNotNull());
    }
}
