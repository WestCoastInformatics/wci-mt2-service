/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.Collection;

/**
 * The Class CollectionUtility.
 */
public class CollectionUtility {

    /**
     * Indicates whether or not different a collection's contents is identical.
     *
     * @param leftCollection the left collection
     * @param rightCollection the right collection
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isDifferentCollection(final Collection<?> leftCollection, final Collection<?> rightCollection) {

        if (rightCollection == null || leftCollection == null) {
            return rightCollection != leftCollection;
        }

        return !(leftCollection.containsAll(rightCollection) && rightCollection.containsAll(leftCollection));

    }

}
