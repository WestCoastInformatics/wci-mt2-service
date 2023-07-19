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
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
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

    /** The Constant BRANCH_DATE_FORMAT. */
    public static final String BRANCH_DATE_FORMAT = "yyyy-MM-dd";

    /** The utilities. */
    private static SyncUtilities utilities;

    /** The db handler. */
    private static SyncDatabaseHandler dbHandler;

    /** The Constant STATISTICS. */
    protected static final SyncStatistics STATISTICS = new SyncStatistics();

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
    private static boolean testing = false;

    /** The testing edition short name. */
    protected static String testingEditionShortName = "SNOMEDCT-DK";

    /** The testing refset. */

    protected static String testingRefset = null; // To test entire edition
    // private static String testingRefset = "450970008";
    // private static String testingRefset = "733991000"; // Core - Dentistry (in multiple projects in RTT)
    // protected static String testingRefset = "751000172100"; // 751000172100 - from Belgium
    // protected static String testingRefset = "723264001"; // 723264001 - TAGS (only one today) - from sct-core
    // protected static String testingRefset = "64641000052102"; // Tim's for ugprade testing (on Swedish)
    // protected static String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)

    /** The developer testing edition short name. */
    private static String developerTestingEditionShortName = null;

    /** The Constant DEVELOPER_CODE_SYSTEM_SHORTNAME. */
    protected static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    /** The Constant SNOMED_ADMIN_USERNAME. */
    protected static final String SNOMED_ADMIN_USERNAME = "rdavidson";

    private static final String SUPER_USERNAME = "refset-dev";

    private static final String DEVELOPER_ADMIN_USERNAME = "rt2-dev-admin";

    /** The Constant ADMIN_USERNAMES. */
    protected static final Set<String> ADMIN_USERNAMES = new HashSet<>();

    /**
     * Sync.
     *
     * @param service the service
     * @param refsetPerVersionSync the refset per version sync
     * @param runForProduction the run for production
     * @param ignoreCoreRefsets the ignore core refsets
     * @throws Exception the exception
     */
    // Call when launching sync
    public static void sync(final TerminologyService service, final boolean refsetPerVersionSync, final boolean runForProduction, final boolean ignoreCoreRefsets) throws Exception {

        if (isProductionSystem == null || !isProductionSystem) {

            isPerVersionSync = refsetPerVersionSync;
            isProductionSystem = runForProduction;
            isIgnoreCoreRefsets = ignoreCoreRefsets;
        }

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

        SyncCodeSystemDeterminer termServerCodeSystemConsumer = new SyncCodeSystemDeterminer(service, getUtilities(), STATISTICS, isTesting(), testingEditionShortName);

        Set<JsonNode> filteredCodeSystems = termServerCodeSystemConsumer.determineCodeSystemsToProcess();
        final HashMap<String, String> termServerEditionToOrganizationMap = termServerCodeSystemConsumer.getEditionToOrganizationMap(filteredCodeSystems);

        LOG.info("Starting Syncing of Users, Projects, Code System, Branches, and Refsets from Termserver");

        // Only identify branches on filtered code systems and on runShortSync value
        LOG.info("Running sync on organizations & editions.");
        SyncAgent agent = new SyncCodeSystemAgent(filteredCodeSystems, termServerEditionToOrganizationMap);
        agent.syncComponent(service);

        // Update available code systems due to potential migrations
        filteredCodeSystems = termServerCodeSystemConsumer.determineCodeSystemsToProcess();

        if (isCleanDatabase(service)) {

            // If first time processing, then and only then update users, teams, and projects based on crowd.
            LOG.info("Running sync on an empty database. Thus add users to orgs, users to admin teams, and new projects");

            agent = new SyncCrowdAgent(filteredCodeSystems);
            agent.syncComponent(service);
        }

        // Find all refsets from filtered branches
        LOG.info("Running sync on refsets.");
        agent = new SyncRefsetAgent(filteredCodeSystems);
        agent.syncComponent(service);

        // Post processing
        LOG.info(STATISTICS.printStatistics());

        // TODO: Replace
        // utilities.emailSyncResults(service);

        final long processingMinutes = utilities.getProcessingMinutes("FULL", startOperationStartTime);
        service.add(AuditEntryHelper.syncFinishEntry(new Date(), processingMinutes));

        LOG.info("Completed Syncing with Termserver");
    }

    /**
     * @return the utilities
     */
    protected static SyncUtilities getUtilities() {

        return utilities;
    }

    /**
     * @param utilities the utilities to set
     */
    protected static void setUtilities(final SyncUtilities utilities) {

        SyncAgent.utilities = utilities;
    }

    /**
     * @return the dbHandler
     */
    protected static SyncDatabaseHandler getDbHandler() {

        return dbHandler;
    }

    /**
     * @param dbHandler the dbHandler to set
     */
    protected static void setDbHandler(final SyncDatabaseHandler dbHandler) {

        SyncAgent.dbHandler = dbHandler;
    }

    /**
     * @param developerTestingEditionShortName the developerTestingEditionShortName to set
     */
    public void setDeveloperTestingEditionShortName(final String editionShortName) {

        developerTestingEditionShortName = editionShortName;
    }

    /**
     * @return the developerTestingEditionShortName
     */
    public String getDeveloperTestingEditionShortName() {

        return developerTestingEditionShortName;
    }

    /**
     * @return the testingRefset
     */
    protected static String getTestingRefset() {

        return testingRefset;
    }

    /**
     * @param testingRefset the testingRefset to set
     */
    protected static void setTestingRefset(final String testingRefset) {

        SyncAgent.testingRefset = testingRefset;
    }

    /**
     * @return the branchdateformatter
     */
    protected static String getBranchdateformatter() {

        return BRANCH_DATE_FORMAT;
    }

    private static boolean isCleanDatabase(TerminologyService service) throws Exception {

        return service.getAll(Project.class).isEmpty();
    }

    /**
     * Initialize.
     *
     * @param service the service
     */
    private static void initialize(final TerminologyService service) {

        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

        if (dbHandler == null) {

            dbHandler = new SyncDatabaseHandler(null, STATISTICS);
        }

        if (utilities == null) {

            utilities = new SyncUtilities(dbHandler);
        }

        dbHandler.setUtilities(utilities);

        ADMIN_USERNAMES.add(SNOMED_ADMIN_USERNAME);
        ADMIN_USERNAMES.add(SUPER_USERNAME);
        ADMIN_USERNAMES.add(DEVELOPER_ADMIN_USERNAME);
    }

    /**
     * Sets the refset to sync.
     *
     * @param refsetId the refset id
     * @param editionShortName the edition short name
     * @throws Exception the exception
     */
    public static void setRefsetToSync(final String refsetId, final String editionShortName) throws Exception {

        setTesting(true);
        testingRefset = refsetId;
        testingEditionShortName = editionShortName;

        RefsetMemberService.clearRefsetVersionsWithChanges(refsetId);
    }

    /**
     * Clear previous run.
     */
    protected static void clearPreviousRun() {

        developerTestingEditionShortName = null;

        STATISTICS.clearStatistics();

        if (utilities != null) {

            utilities.clearPreviousRun();
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
    protected boolean isDifferentAttribute(final String shortName, final String attributeName, final Object databaseAttribute, final Object termserverAttribute) {

        if (termserverAttribute == null && databaseAttribute == null) {

            // Both null, no difference
            return false;
        } else if (termserverAttribute != null && databaseAttribute != null && databaseAttribute.equals(termserverAttribute)) {

            // Both not null with identical value, no difference
            return false;
        }

        // values are different. List them
        if (databaseAttribute instanceof Long) {

            LOG.info(" inconsistency found on attribute " + attributeName + " for edition " + shortName + " where termserver has " + new Date((Long) termserverAttribute) + "' and DB is '"
                + new Date((Long) databaseAttribute) + "'");
        } else {

            LOG.info(" inconsistency found on attribute " + attributeName + " for edition " + shortName + " where termserver has '" + termserverAttribute + "' and DB is '" + databaseAttribute + "'");
        }

        return true;
    }

    /**
     * Returns the develeper testing edition short name.
     *
     * @return the develeper testing edition short name
     */
    public static String getDeveleperTestingEditionShortName() {

        return developerTestingEditionShortName;
    }

    /**
     * Indicates whether or not testing is the case.
     *
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public static boolean isTesting() {

        return testing;

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
     * Sets the testing.
     *
     * @param testing the testing
     */
    public static void setTesting(final boolean testing) {

        SyncAgent.testing = testing;
    }

    /**
     * Returns the admin usernames.
     *
     * @return the admin usernames
     */
    public static Set<String> getAdminUsernames() {

        return ADMIN_USERNAMES;
    }

    /**
     * Read db organizations.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    public List<Organization> readDbOrganizations(final TerminologyService service) throws Exception {

        return service.getAll(Organization.class);
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

    /**
     * Read db inactive editions.
     *
     * @param service the service
     * @return the list
     * @throws Exception the exception
     */
    public List<Edition> readDbInactiveEditions(final TerminologyService service) throws Exception {

        return readDbAllEditions(service).stream().filter(e -> !e.isActive()).collect(Collectors.toList());
    }
}
