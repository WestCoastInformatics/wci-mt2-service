/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.CollectionUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class CollectionUtilityUnitTest.
 */
public class CollectionUtilityUnitTest extends BaseTest {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(CollectionUtilityUnitTest.class);

    /**
     * Test is same collection.
     */
    @Test
    public void testIsSameCollectionWithStrings() {

        assertFalse(CollectionUtility.isDifferentCollection(null, null));
        assertFalse(CollectionUtility.isDifferentCollection(Set.of("A", "B", "C"), Set.of("A", "B", "C")));
        assertFalse(CollectionUtility.isDifferentCollection(List.of("A", "B", "C"), List.of("A", "B", "C")));
        assertFalse(CollectionUtility.isDifferentCollection(List.of("A", "B", "C"), Set.of("A", "B", "C")));

        assertFalse(CollectionUtility.isDifferentCollection(Set.of("A", "B", "C"), Set.of("C", "A", "B")));
        assertFalse(CollectionUtility.isDifferentCollection(Set.of("C", "A", "B"), Set.of("A", "B", "C")));

        assertFalse(CollectionUtility.isDifferentCollection(List.of("A", "B", "C"), List.of("C", "A", "B")));
        assertFalse(CollectionUtility.isDifferentCollection(List.of("C", "A", "B"), List.of("A", "B", "C")));

    }

    /**
     * Test is different collection.
     */
    @Test
    public void testIsDifferentCollectionWithStrings() {

        assertTrue(CollectionUtility.isDifferentCollection(Set.of(""), null));
        assertTrue(CollectionUtility.isDifferentCollection(null, Set.of("")));

        assertTrue(CollectionUtility.isDifferentCollection(Set.of("A", "B"), Set.of("A", "B", "C")));
        assertTrue(CollectionUtility.isDifferentCollection(Set.of("A", "B", "C"), Set.of("A", "B")));
        assertTrue(CollectionUtility.isDifferentCollection(Set.of("A", "B", "C"), Set.of("A", "B", "D")));

        assertTrue(CollectionUtility.isDifferentCollection(List.of("A", "B"), List.of("A", "B", "C")));
        assertTrue(CollectionUtility.isDifferentCollection(List.of("A", "B", "C"), List.of("A", "B")));
        assertTrue(CollectionUtility.isDifferentCollection(List.of("A", "B", "C"), List.of("A", "B", "D")));

        assertTrue(CollectionUtility.isDifferentCollection(List.of("A", "B", "C"), Set.of("A", "B")));
        assertTrue(CollectionUtility.isDifferentCollection(Set.of("A", "B"), List.of("A", "B", "C")));

    }

}
