/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.List;
import java.util.Properties;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to handle getting and modifying internal mapset information.
 */
public class MapSetService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetService.class);

    /** The config properties. */
    protected static final Properties PROPERTIES = PropertyUtility.getProperties();

    /** The terminology handler. */
    private static TerminologyServerHandler terminologyHandler;

    static {

        // Instantiate terminology handler
        try {
            final String key = "terminology.handler";
            final String handlerName = PropertyUtility.getProperty(key);
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
     * Returns the map set.
     *
     * @param branch the branch
     * @param code the code
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet getMapSet(final String branch, final String code) throws Exception {

        return terminologyHandler.getMapSet(branch, code);

    }

    /**
     * Returns the map sets.
     *
     * @param branch the branch
     * @return the map sets
     * @throws Exception the exception
     */
    public static List<MapSet> getMapSets(final String branch) throws Exception {

        return terminologyHandler.getMapSets(branch);

    }

    /**
     * Get the map set member concepts in RF2 format.
     *
     * @param user the user
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest) throws Exception {

        return terminologyHandler.exportMapSet(user, mapProject, mapSetExportRequest);
    }

}
