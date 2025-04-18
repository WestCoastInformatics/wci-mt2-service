/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm.export;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;

/**
 * Builder for export request parameters.
 */
public class ExportRequestBuilder {

    /** The parameters. */
    private final StringBuilder parameters = new StringBuilder();

    /**
     * Instantiates a new export request builder.
     */
    public ExportRequestBuilder() {

        parameters.append("{");
    }

    /**
     * With refset id.
     *
     * @param refsetId the refset id
     * @return the export request builder
     */
    public ExportRequestBuilder withRefsetId(final String refsetId) {

        parameters.append("\"refsetIds\": [\"").append(refsetId).append("\"]");
        return this;
    }

    /**
     * With branch path.
     *
     * @param branchPath the branch path
     * @return the export request builder
     */
    public ExportRequestBuilder withBranchPath(final String branchPath) {

        parameters.append(", \"branchPath\": \"").append(branchPath).append("\"");
        return this;
    }

    /**
     * With concepts and relationships only.
     *
     * @param conceptsAndRelationshipsOnly the concepts and relationships only
     * @return the export request builder
     */
    public ExportRequestBuilder withConceptsAndRelationshipsOnly(final boolean conceptsAndRelationshipsOnly) {

        parameters.append(", \"conceptsAndRelationshipsOnly\": ").append(conceptsAndRelationshipsOnly);
        return this;
    }

    /**
     * With filename effective date.
     *
     * @param filenameEffectiveDate the filename effective date
     * @return the export request builder
     */
    public ExportRequestBuilder withFilenameEffectiveDate(final String filenameEffectiveDate) {

        parameters.append(", \"filenameEffectiveDate\": \"").append(filenameEffectiveDate).append("\"");
        return this;
    }

    /**
     * With legacy zip naming.
     *
     * @param legacyZipNaming the legacy zip naming
     * @return the export request builder
     */
    public ExportRequestBuilder withLegacyZipNaming(final boolean legacyZipNaming) {

        parameters.append(", \"legacyZipNaming\": ").append(legacyZipNaming);
        return this;
    }

    /**
     * With type.
     *
     * @param type the type
     * @return the export request builder
     */
    public ExportRequestBuilder withType(FileFormatType type) {

        parameters.append(", \"type\": \"").append(type).append("\"");
        return this;
    }

    /**
     * With unpromoted changes only.
     *
     * @param unpromotedChangesOnly the unpromoted changes only
     * @return the export request builder
     */
    public ExportRequestBuilder withUnpromotedChangesOnly(final boolean unpromotedChangesOnly) {

        parameters.append(", \"unpromotedChangesOnly\": ").append(unpromotedChangesOnly);
        return this;
    }

    /**
     * With module ids.
     *
     * @param mapProject the map project
     * @return the export request builder
     */
    public ExportRequestBuilder withModuleIds(final Set<String> moduleIds) {

        if (moduleIds == null || moduleIds.isEmpty()) {
            return this;
        }
        final String moduleIdsString = moduleIds.stream().map(moduleId -> "\"" + moduleId + "\"").collect(Collectors.joining(", "));

        parameters.append(", \"moduleIds\": [").append(moduleIdsString).append("]");
        return this;
    }

    /**
     * With transient effective time.
     *
     * @param transientEffectiveTime the transient effective time
     * @return the export request builder
     */
    public ExportRequestBuilder withTransientEffectiveTime(final String transientEffectiveTime) {

        if (StringUtils.isNotBlank(transientEffectiveTime)) {
            parameters.append(", \"transientEffectiveTime\": \"").append(transientEffectiveTime).append("\"");
        }
        return this;
    }

    /**
     * With start effective time.
     *
     * @param startEffectiveTime the start effective time
     * @return the export request builder
     */
    public ExportRequestBuilder withStartEffectiveTime(final String startEffectiveTime) {

        if (StringUtils.isNotBlank(startEffectiveTime)) {
            parameters.append(", \"startEffectiveTime\": \"").append(startEffectiveTime).append("\"");
        }
        return this;
    }

    /**
     * Build the export request string.
     *
     * @return the string
     */
    public String build() {

        parameters.append("}");
        return parameters.toString();
    }
}