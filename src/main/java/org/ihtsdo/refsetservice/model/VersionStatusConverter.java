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

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

import org.ihtsdo.refsetservice.model.enums.VersionStatus;

/**
 * The Class VersionStatusConverter.
 */
@Converter
public class VersionStatusConverter implements AttributeConverter<VersionStatus, String> {

    /* see superclass */
    @Override
    public String convertToDatabaseColumn(final VersionStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getLabel();
    }

    /* see superclass */
    @Override
    public VersionStatus convertToEntityAttribute(final String dbData) {
        if (dbData == null) {
            return null;
        }
        for (final VersionStatus status : VersionStatus.values()) {
            if (status.getLabel().equals(dbData)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown database value: " + dbData);
    }
}
