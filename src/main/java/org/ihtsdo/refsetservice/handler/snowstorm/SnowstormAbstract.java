/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SnowstormAbstract.
 */
public class SnowstormAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormAbstract.class);

    /** The max number of record elasticsearch will return without erroring. */
    protected static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 10000;

    /** The Constant SNOMED_SNOMEDCT_ICD10_MAPPING_MODULE. */
    protected static final String SNOMEDCT_TO_ICD10_MAPPING_MODULE = "449080006";

    /**
     * Format error message.
     *
     * @param response the response
     * @return the string
     */
    public static String formatErrorMessage(final Response response) {

        String snowstormErrorMessage = response.readEntity(String.class);
        if (StringUtils.isEmpty(snowstormErrorMessage)) {
            return "";
        }
        if (StringUtility.isJson(snowstormErrorMessage)) {
            final ObjectMapper mapper = new ObjectMapper();
            try {
                final JsonNode json = mapper.readTree(snowstormErrorMessage);
                snowstormErrorMessage = json.has("message") ? json.get("message").asText() : "";
            } catch (final Exception e) {
                LOG.error("formatErrorMessage snowstormErrorMessage:{}", snowstormErrorMessage, e);
            }
        }
        return snowstormErrorMessage.replaceAll("[\\r\\n]+", " ");
    }

}
