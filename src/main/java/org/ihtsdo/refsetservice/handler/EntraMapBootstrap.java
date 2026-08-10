/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

/**
 * Shared helpers for Entra mapping-role bootstrap lists (admin / spec / lead).
 */
public final class EntraMapBootstrap {

    /** Bootstrap role granted to admin list members. */
    public static final String ROLE_ADMIN = "all-all-all-admin";

    /** Bootstrap role granted to spec list members. */
    public static final String ROLE_SPEC = "all-all-all-spec";

    /** Bootstrap role granted to lead list members. */
    public static final String ROLE_LEAD = "all-all-all-lead";

    private EntraMapBootstrap() {

    }

    /**
     * Splits a configured user list on commas and semicolons.
     *
     * @param list raw property value
     * @return trimmed non-blank entries
     */
    public static List<String> splitConfiguredList(final String list) {

        if (StringUtils.isBlank(list)) {
            return Collections.emptyList();
        }

        final String[] parts = list.split("[,;]");
        final List<String> result = new ArrayList<>(parts.length);
        for (final String part : parts) {
            final String trimmed = part.trim();
            if (StringUtils.isNotBlank(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
