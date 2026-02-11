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

import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.service.TerminologyService;
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
     * Instantiates a new map set service.
     */
    private MapSetService() {

        // Utility class
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

    /**
     * Sets the workflow status.
     *
     * @param service the service
     * @param user the user
     * @param mapSetInternalId the map set internal id
     * @param action the action
     * @param notes the notes
     * @return the refset
     * @throws Exception the exception
     */
    public static MapSet setWorkflowStatus(final TerminologyService service, final User user, final String mapSetInternalId, final WorkflowAction action,
        final String notes) throws Exception {

        return terminologyHandler.setWorkflowStatus(service, user, mapSetInternalId, action, notes);
    }

    /**
     * Clear all refset caches.
     *
     * @param branch the branch
     * @throws Exception the exception
     */
    public static void clearAllRefsetCaches(final String branch) throws Exception {

        terminologyHandler.clearAllRefsetCaches(branch);
    }

    /**
     * Gets the branch path.
     *
     * @param mapSet the map set
     * @return the branch path
     * @throws Exception the exception
     */
    public static String getBranchPath(final MapSet mapSet) throws Exception {

        return terminologyHandler.getBranchPath(mapSet);
    }

    /**
     * Gets the refset date from formatted string.
     *
     * @param publicationDateString the publication date string
     * @return the refset date from formatted string
     * @throws Exception the exception
     */
    public static Date getRefsetDateFromFormattedString(final String publicationDateString) throws Exception {

        return terminologyHandler.getRefsetDateFromFormattedString(publicationDateString);
    }

    /**
     * Sets the roles.
     *
     * @param user the user
     * @param project the project
     * @param roles the roles
     * @return the list
     * @throws Exception the exception
     */
    public static List<String> setRoles(final User user, final Project project, final List<String> roles) throws Exception {

        return terminologyHandler.setRoles(user, project, roles);
    }

    /**
     * Gets the branch versions.
     *
     * @param branch the branch
     * @return the branch versions
     * @throws Exception the exception
     */
    public static List<String> getBranchVersions(final String branch) throws Exception {

        return terminologyHandler.getBranchVersions(branch);
    }

    /**
     * Removes the map set edit history.
     *
     * @param service the service
     * @param refsetCode the refset code
     * @throws Exception the exception
     */
    public static void removeMapSetEditHistory(final TerminologyService service, final String refsetCode) throws Exception {

        terminologyHandler.removeMapSetEditHistory(service, refsetCode);
    }

    /**
     * Sets the map set member count.
     *
     * @param service the service
     * @param mapSet the map set
     * @param force the force
     * @throws Exception the exception
     */
    public static void setMapSetMemberCount(final TerminologyService service, final MapSet mapSet, final boolean force) throws Exception {

        terminologyHandler.setMapSetMemberCount(service, mapSet, force);
    }

    /**
     * Removes the upgrade data.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void removeUpgradeData(final TerminologyService service, final MapSet mapSet) throws Exception {

        terminologyHandler.removeUpgradeData(service, mapSet);
    }

    /**
     * Replace map set with edit history.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void replaceMapSetWithEditHistory(final TerminologyService service, final MapSet mapSet) throws Exception {

        terminologyHandler.replaceMapSetWithEditHistory(service, mapSet);
    }

    /**
     * Sets the map set permissions.
     *
     * @param user the user
     * @param mapSet the map set
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet setMapSetPermissions(final User user, final MapSet mapSet) throws Exception {

        return terminologyHandler.setMapSetPermissions(user, mapSet);
    }

}
