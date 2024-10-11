/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to get refset member concept information from a terminology service.
 * 
 * @author jesseefron
 * 
 */
public final class ConceptService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ConceptService.class);

    /** The terminology handler. */
    private static TerminologyServerHandler terminologyHandler;

    static {

        // Instantiate terminology handler
        try {
            String key = "terminology.handler";
            String handlerName = PropertyUtility.getProperty(key);
            if (handlerName.isEmpty()) {
                throw new Exception("terminology.handler expected and does not exist.");
            }

            terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName, TerminologyServerHandler.class);

        } catch (Exception e) {
            LOG.error("Failed to initialize terminology.handler - serious error", e);
            terminologyHandler = null;
        }
    }

    /**
     * Instantiates an empty {@link ConceptService}.
     */
    private ConceptService() {

        // n/a
    }

    /**
     * Returns the concept.
     *
     * @param branch the branch
     * @param terminology the terminology
     * @param version the version
     * @param code the concept code
     * @return the concept
     * @throws Exception the exception
     */
    public static Concept getConcept(final String terminology, final String version, final String code) throws Exception {

        return terminologyHandler.getConcept(terminology, version, code);
    }

    /**
     * Find concepts.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @return the result list concept
     * @throws Exception the exception
     */
    public static ResultListConcept findConcepts(final String terminology, final String version, final SearchParameters searchParameters)
        throws Exception {

        return terminologyHandler.findConcepts(terminology, version, searchParameters);
    }

}
