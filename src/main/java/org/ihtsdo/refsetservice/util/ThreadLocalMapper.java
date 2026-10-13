/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Supply a shared, thread-safe object mapper.
 */
public final class ThreadLocalMapper {

    /**
     * Instantiates a new thread local mapper.
     */
    private ThreadLocalMapper() {

        // n/a
    }

    /** The shared mapper. */
    private static final JsonMapper MAPPER = newMapper();

    /**
     * New mapper.
     *
     * @return the json mapper
     */
    public static JsonMapper newMapper() {

        return JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> JsonInclude.Value.construct(JsonInclude.Include.NON_EMPTY, JsonInclude.Include.NON_EMPTY))
            .findAndAddModules().disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).defaultTimeZone(TimeZone.getDefault()).build();
    }

    /**
     * Gets the shared mapper.
     *
     * @return the json mapper
     */
    public static JsonMapper get() {

        return MAPPER;
    }
}
