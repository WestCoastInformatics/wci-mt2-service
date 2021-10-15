package org.ihtsdo.refsetservice.model;

public enum VersionStatus {

    /** Captures all non-published status. */
    IN_DEVELOPMENT("In Development"),

    /** The published status. */
    PUBLISHED("Published");

    private final String label;

    private VersionStatus(String label) {
        this.label = label;
    }

    public String getLable() {
        return label;
    }
}
