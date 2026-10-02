/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapAdvice;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Boot 3 PathPatternParser does not equate /foo and /foo/. Dual mappings must both 200.
 */
public class TrailingSlashMappingUnitTest {

    /** The mvc. */
    private MockMvc mvc;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp() {

        mvc = MockMvcBuilders.standaloneSetup(new EditionController() {

            @Override
            public ResponseEntity<ResultList<Edition>> getEditions() {

                return new ResponseEntity<>(new ResultList<>(), HttpStatus.OK);
            }
        }, new MapAdviceController() {

            @Override
            public ResponseEntity<ResultList<MapAdvice>> getMapAdvices() {

                return new ResponseEntity<>(new ResultList<>(), HttpStatus.OK);
            }
        }, new MapRelationController() {

            @Override
            public ResponseEntity<ResultList<MapRelation>> getMapRelations() {

                return new ResponseEntity<>(new ResultList<>(), HttpStatus.OK);
            }
        }).build();
    }

    /**
     * Test edition with and without trailing slash.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditionWithAndWithoutTrailingSlash() throws Exception {

        mvc.perform(get("/edition")).andExpect(status().isOk());
        mvc.perform(get("/edition/")).andExpect(status().isOk());
    }

    /**
     * Test map advice with and without trailing slash.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMapAdviceWithAndWithoutTrailingSlash() throws Exception {

        mvc.perform(get("/mapAdvice")).andExpect(status().isOk());
        mvc.perform(get("/mapAdvice/")).andExpect(status().isOk());
    }

    /**
     * Test map relation with and without trailing slash.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMapRelationWithAndWithoutTrailingSlash() throws Exception {

        mvc.perform(get("/mapRelation")).andExpect(status().isOk());
        mvc.perform(get("/mapRelation/")).andExpect(status().isOk());
    }
}
