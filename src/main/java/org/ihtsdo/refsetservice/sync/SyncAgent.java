/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncCodeSystemDeterminer;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncAgent.
 */
public abstract class SyncAgent {

    /** The logger. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncAgent.class);

    /** The utilities. */
    private static SyncUtilities utilities;

    /** The db handler. */
    private static SyncDatabaseHandler dbHandler;

    protected static String developerTestingBranchToUse;

    /** The Constant STATISTICS. */
    protected static final SyncStatistics STATISTICS = new SyncStatistics();

    public static final String SNOMED_CORE_EDITION_NAME = "SNOMEDCT";

    /**
     * Sync component.
     *
     * @param service the service
     * @throws Exception the exception
     */
    protected abstract void syncComponent(final TerminologyService service) throws Exception;

    /** Execution options. */
    private static Boolean isProductionSystem = null;

    /** The is per version sync. */
    private static Boolean isPerVersionSync = null;

    /** The is ignore core refsets. */
    private static Boolean isIgnoreCoreRefsets = null;

    /** Testing options. */
    /** The single code system short name. */
    protected final static Set<String> shortNamesToSync = new HashSet<>();

    /** The testing refset. */
    // TODO: Make this a comma-delimited list and object
    protected static String testingRefset = null; // To test entire edition
    // protected static String testingRefset = "98031000146109"; // To test entire edition
    // private static String testingRefset = "56421000052109"; // SE refset with only 6 concepts
    // private static String testingRefset = "421000210109"; // NZ with def clauses
    // private static String testingRefset = "733991000"; // Core - Dentistry (in multiple projects in RTT)
    // protected static String testingRefset = "751000172100"; // 751000172100 - from Belgium
    // protected static String testingRefset = "723264001"; // 723264001 - TAGS (only one today) - from sct-core
    // protected static String testingRefset = "64641000052102"; // Tim's for ugprade testing (on Swedish)
    // protected static String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)
    // protected static String testingRefset = "787778008"; // Global Patient Set (over 25k so should be ignored)

    /** The Constant DEVELOPER_CODE_SYSTEM_SHORTNAME. */
    public static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI-TEST";

    /**
     * Sync.
     *
     * @param service the service
     * @param shortNamesToSync The short names to sync. Options are null (which syncs all), a single code system short name, or a comma-delimited list of short
     *            names
     * @param refsetPerVersionSync the refset per version sync
     * @param runForProduction the run for production
     * @param ignoreCoreRefsets the ignore core refsets
     * @throws Exception the exception
     */
    // Call when launching sync
    public static void sync(final TerminologyService service, final String shortNamesToSync, final boolean refsetPerVersionSync, final boolean runForProduction,
        final boolean ignoreCoreRefsets) throws Exception {

        if (isProductionSystem == null || !isProductionSystem) {

            isProductionSystem = runForProduction;
            isPerVersionSync = refsetPerVersionSync;
            isIgnoreCoreRefsets = ignoreCoreRefsets;

        }

        setShortNamesToSync(shortNamesToSync);
        setTestingRefset();

        sync(service);

    }

    /**
     * Sync.
     *
     * @param service the service
     * @throws Exception the exception
     */
    // Call when launching a sync service were launching sync is secondary i.e., resetRefset
    public static void sync(final TerminologyService service) throws Exception {

        final Date startOperationStartTime = new Date();

        initialize(service);
        service.add(AuditEntryHelper.syncBeginEntry(startOperationStartTime));

        LOG.info("Starting Syncing of Users, Projects, Code System, Branches, and Refsets from Termserver");

        SyncCodeSystemDeterminer termServerCodeSystemDeterminer = new SyncCodeSystemDeterminer(service, getUtilities(), STATISTICS, shortNamesToSync);

        Set<JsonNode> filteredCodeSystems = termServerCodeSystemDeterminer.determineCodeSystemsToProcess();
        final HashMap<String, String> termServerShortNameToOwnerMap = termServerCodeSystemDeterminer.getTermServerShortNameToOwnerMap(filteredCodeSystems);

        // Only identify branches on filtered code systems and on runShortSync value
        LOG.info("Syncing - Running on organizations & editions.");
        SyncAgent agent = new SyncCodeSystemAgent(service, filteredCodeSystems, termServerShortNameToOwnerMap, termServerCodeSystemDeterminer);
        agent.syncComponent(service);

        // Update available code systems due to potential migrations (edition move from one org to another) and reactivations
        // TODO: Ensure this should be removed
        // filteredCodeSystems = termServerCodeSystemConsumer.determineCodeSystemsToProcess();

        // If first time processing a code system with projects, then and only then update users, teams, and projects based on crowd.
        LOG.info("Syncing - Review projects on crowd when encountering a code system without existing projects in system");
        agent = new SyncCrowdAgent(filteredCodeSystems);
        agent.syncComponent(service);

        // Find all refsets from filtered branches
        LOG.info("Syncing - Running on all refsets.");
        agent = new SyncRefsetAgent(filteredCodeSystems);
        agent.syncComponent(service);

        // Post processing
        LOG.info(STATISTICS.printStatistics());

        /*-
         *  Contains ability to execute a series of SQL calls from a resource file
         *  
         *  final String results = getUtilities().getSyncResults(service);
         */
        final String results = STATISTICS.printStatistics();
        getUtilities().emailResults(service, results);

        final long processingMinutes = utilities.getProcessingMinutes("FULL", startOperationStartTime);
        service.add(AuditEntryHelper.syncFinishEntry(new Date(), processingMinutes));

        LOG.info("Syncing - Completed Syncing with Termserver");
    }

    /**
     * Returns the utilities.
     *
     * @return the utilities
     */
    protected static SyncUtilities getUtilities() {

        return utilities;
    }

    /**
     * Sets the utilities.
     *
     * @param utilities the utilities to set
     */
    protected static void setUtilities(final SyncUtilities utilities) {

        SyncAgent.utilities = utilities;
    }

    /**
     * Returns the db handler.
     *
     * @return the dbHandler
     */
    protected static SyncDatabaseHandler getDbHandler() {

        return dbHandler;
    }

    /**
     * Sets the db handler.
     *
     * @param dbHandler the dbHandler to set
     */
    protected static void setDbHandler(final SyncDatabaseHandler dbHandler) {

        SyncAgent.dbHandler = dbHandler;
    }

    /**
     * Returns the testing refset.
     *
     * @return the testingRefset
     */
    protected static String getTestingRefset() {

        return testingRefset;
    }

    /**
     * Sets the testing refset. Currently supports single refset within one of the selected editions.
     *
     */
    protected static void setTestingRefset() {

        RefsetMemberService.clearRefsetVersionsWithChanges(testingRefset);
    }

    /**
     * Initialize.
     *
     * @param service the service
     */
    private static void initialize(final TerminologyService service) {

        // Agents begin and commit transactions themselves
        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);
        service.setTransactionPerOperation(false);

        developerTestingBranchToUse = "";

        if (dbHandler == null) {

            dbHandler = new SyncDatabaseHandler(null, STATISTICS);
        }

        if (utilities == null) {

            utilities = new SyncUtilities(dbHandler);
        } else {
            utilities.clearPreviousRun();
        }

        STATISTICS.clearStatistics();
        dbHandler.setUtilities(utilities);
    }

    /**
     * Sets the refset to sync.
     *
     * @param shortNames the comma or space-delimited edition short name(s)
     * @throws Exception the exception
     */
    public static void setShortNamesToSync(final String shortNames) throws Exception {

        // For now, support none, a single, or a comma-delimited set of short names (without space between).
        // TODO: Improve later when not focusing elsewhere.
        shortNamesToSync.clear();

        if (shortNames != null && !shortNames.isEmpty()) {
            if (!shortNames.contains(",") && !shortNames.contains(" ")) {
                shortNamesToSync.add(shortNames);
            } else {
                String[] shortNamesArray = shortNames.split(",");

                if (shortNamesArray.length == 0) {
                    shortNamesArray = shortNames.split(" ");
                }
                for (int i = 0; i < shortNamesArray.length; i++) {
                    shortNamesToSync.add(shortNamesArray[i]);
                }
            }
        }
    }

    /**
     * Indicates whether or not different attribute is the case.
     *
     * @param shortName the short name
     * @param attributeName the attribute name
     * @param databaseAttribute the database attribute
     * @param termserverAttribute the termserver attribute
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    protected boolean isDifferentAttribute(final String shortName, final String attributeName, final Object databaseAttribute,
        final Object termserverAttribute) {

        if (termserverAttribute == null && databaseAttribute == null) {

            // Both null, no difference
            return false;
        } else if (termserverAttribute != null && databaseAttribute != null && databaseAttribute.equals(termserverAttribute)) {

            // Both not null with identical value, no difference
            return false;
        }

        // values are different. List them
        if (databaseAttribute instanceof Long) {

            LOG.info(" inconsistency found on attribute " + attributeName + " for edition " + shortName + " where termserver has "
                + new Date((Long) termserverAttribute) + "' and DB is '" + new Date((Long) databaseAttribute) + "'");
        } else {

            LOG.info(" inconsistency found on attribute " + attributeName + " for edition " + shortName + " where termserver has '" + termserverAttribute
                + "' and DB is '" + databaseAttribute + "'");
        }

        return true;
    }

    /**
     * Returns the is ignore core refsets.
     *
     * @return the is ignore core refsets
     */
    public static Boolean getIsIgnoreCoreRefsets() {

        return isIgnoreCoreRefsets == null ? false : isIgnoreCoreRefsets;
    }

    /**
     * Returns the is production system.
     *
     * @return the is production system
     */
    public static Boolean getIsProductionSystem() {

        return isProductionSystem == null ? false : isProductionSystem;
    }

    /**
     * Returns the is per version sync.
     *
     * @return the is per version sync
     */
    public static Boolean getIsPerVersionSync() {

        return isPerVersionSync == null ? false : isPerVersionSync;
    }

    /**
     * Read db all editions.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    public List<Edition> readDbAllEditions(final TerminologyService service) throws Exception {

        return service.getAll(Edition.class);
    }

    /**
     * Read db active editions.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    public List<Edition> readDbActiveEditions(final TerminologyService service) throws Exception {

        return readDbAllEditions(service).stream().filter(e -> e.isActive()).collect(Collectors.toList());
    }

}
