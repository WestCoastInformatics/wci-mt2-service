package org.ihtsdo.refsetservice.sync.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The Enum ReasonEditionSkipped.
 */
public enum SyncReasonEditionSkipped {

    /** The wrong testing edition. */
    WRONG_TESTING_EDITION,
    /** The inactive edition. */
    INACTIVE_EDITION,
    /** The ignored per file edition. */
    IGNORED_PER_FILE_EDITION,
    /** A non-supported maintainer type. */
    NON_SUPPORTED_MAINTAINER_TYPE,

    /** An affiliate code system. */
    AFFILIATE_CODE_SYSTEM;

    /** Enums as list. */
    public static final List<SyncReasonEditionSkipped> ALL_REASONS = new ArrayList<>();

    /**
     * Returns the all roles.
     *
     * @return the all roles
     */
    public static List<SyncReasonEditionSkipped> getAllReasons() {

        if (ALL_REASONS.isEmpty()) {

            ALL_REASONS.add(WRONG_TESTING_EDITION);
            ALL_REASONS.add(INACTIVE_EDITION);
            ALL_REASONS.add(IGNORED_PER_FILE_EDITION);
            ALL_REASONS.add(NON_SUPPORTED_MAINTAINER_TYPE);
            ALL_REASONS.add(AFFILIATE_CODE_SYSTEM);
        }

        return ALL_REASONS;
    }

}
