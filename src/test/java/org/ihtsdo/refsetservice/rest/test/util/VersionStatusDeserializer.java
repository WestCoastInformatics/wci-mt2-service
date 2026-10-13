/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import org.ihtsdo.refsetservice.model.enums.VersionStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * Deserializer for VersionStatus that accepts both enum name (IN_DEVELOPMENT) and label (IN DEVELOPMENT). The API serializes using VersionStatusJsonSerializer
 * which outputs the label.
 */
public class VersionStatusDeserializer extends ValueDeserializer<VersionStatus> {

    /**
     * Deserialize.
     *
     * @param p the JsonParser
     * @param ctxt the DeserializationContext
     * @return the version status
     * @throws JacksonException the jackson exception
     */
    @Override
    public VersionStatus deserialize(final JsonParser p, final DeserializationContext ctxt) throws JacksonException {

        final String value = p.getString();
        if (value == null || value.isEmpty()) {
            return null;
        }
        for (final VersionStatus status : VersionStatus.values()) {
            if (status.name().equals(value) || status.getLabel().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown VersionStatus: " + value);
    }
}
