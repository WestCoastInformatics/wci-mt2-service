/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.List;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to get refset member concept information from a terminology
 * service.
 */
/**
 * @author jesseefron
 *
 */
public final class MappingService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingService.class);

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
     * Instantiates an empty {@link MappingService}.
     */
    private MappingService() {

        // n/a
    }

    /**
     * Returns the mappings.
     *
     * @param mapSet the map set
     * @return the mappings
     * @throws Exception the exception
     */
    public static List<Mapping> getMappings(MapSet mapSet) throws Exception {

        return terminologyHandler.getMappings(mapSet);
    }
}
