package org.ihtsdo.refsetservice.sync.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The Enum ReasonEditionSkipped.
 */
public enum ReasonEditionSkipped {

    /** The wrong testing edition. */
    WRONG_TESTING_EDITION,
    /** The inactive edition. */
    INACTIVE_EDITION,
    /** The ignored per file edition. */
    IGNORED_PER_FILE_EDITION,
    /** A non-managed service maintainer type. */
    NON_MANAGED_SERVICE;

    /** Enums as list. */
    public static final List<ReasonEditionSkipped> ALL_REASONS = new ArrayList<>();

    /**
     * Returns the all roles.
     *
     * @return the all roles
     */
    public static List<ReasonEditionSkipped> getAllReasons() {

        if (ALL_REASONS.isEmpty()) {

            ALL_REASONS.add(WRONG_TESTING_EDITION);
            ALL_REASONS.add(INACTIVE_EDITION);
            ALL_REASONS.add(IGNORED_PER_FILE_EDITION);
            ALL_REASONS.add(NON_MANAGED_SERVICE);
        }
        return ALL_REASONS;
    }

}
