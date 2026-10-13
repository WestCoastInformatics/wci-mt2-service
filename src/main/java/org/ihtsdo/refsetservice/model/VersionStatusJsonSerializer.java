/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import org.ihtsdo.refsetservice.model.enums.VersionStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * The Class VersionStatusJsonSerializer.
 */
public class VersionStatusJsonSerializer extends ValueSerializer<VersionStatus> {

    /**
     * Serialize.
     *
     * @param value the value
     * @param gsonGenerator the gson generator
     * @param serializers the serializers
     * @throws JacksonException the jackson exception
     */
    @Override
    public void serialize(final VersionStatus value, final JsonGenerator gsonGenerator, final SerializationContext serializers) throws JacksonException {

        if (value != null) {
            gsonGenerator.writeString(value.getLabel());
        } else {
            gsonGenerator.writeNull();
        }
    }
}
