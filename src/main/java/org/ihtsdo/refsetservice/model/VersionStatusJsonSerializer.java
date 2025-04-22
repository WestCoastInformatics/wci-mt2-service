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

import java.io.IOException;

import org.ihtsdo.refsetservice.model.enums.VersionStatus;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * The Class VersionStatusJsonSerializer.
 */
public class VersionStatusJsonSerializer extends JsonSerializer<VersionStatus> {

    /* see superclass */
    @Override
    public void serialize(final VersionStatus value, final JsonGenerator gsonGenerator, final SerializerProvider serializers) throws IOException {

        if (value != null) {
            gsonGenerator.writeString(value.getLabel());
        } else {
            gsonGenerator.writeNull();
        }
    }
}
