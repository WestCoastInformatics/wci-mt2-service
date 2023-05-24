package org.ihtsdo.refsetservice.sync;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class SyncAgent {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SyncAgent.class);

    protected static final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    protected static SyncUtilities utilities;

    protected static SyncDatabaseHandler dbHandler;

    protected static final SyncStatistics statistics = new SyncStatistics();

    protected abstract void sync() throws Exception;

    /** Execution options. */
    private static Boolean isProductionSystem = null;

    private static Boolean isPerVersionSync = null;

    private static Boolean isIgnoreCoreRefsets = null;

    /** Testing options. */
    private static boolean testing = false;

    protected static String testingEditionShortName = "SNOMEDCT-BE";

    protected static String testingRefset = "751000172100"; // 751000172100 - from Belgium
    // protected static String testingRefset = null; // To test entire edition
    // protected static String testingRefset = "723264001"; // 723264001 - TAGS (only one today) - from sct-core
    // protected static String testingRefset = "64641000052102"; // Tim's for ugprade testing (on Swedish)
    // protected static String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)

    protected static String developerTestingEditionShortName = null;

    protected static Organization develeperTestingOrganization = null;

    /** Other process fields **/
    // Owner Name to Organization Description
    protected static Set<JsonNode> filteredCodeSystems = new HashSet<>();

    protected static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    protected static final String SNOMED_ADMIN_USERNAME = "rdavidson";

    protected static final String DEVELOPER_ADMIN_USERNAME_PREFIX = "refset-";

    protected static final Set<String> adminUsernames = new HashSet<>();

    // TODO: Define when called vs normal one
    public static void sync(TerminologyService service, boolean refsetPerVersionSync, boolean runForProduction, boolean ignoreCoreRefsets) throws Exception {

        if (isProductionSystem == null || !isProductionSystem) {

            initialize(refsetPerVersionSync, runForProduction, ignoreCoreRefsets);
        }

        sync(service);

    }

    public static void sync(TerminologyService service) throws Exception {
      
        service.add(AuditEntryHelper.syncEntry(new Date()));

        final long startOperationStartTime = new Date().getTime();

        if (isProductionSystem == null) {

            initialize(false, false, false);
        }

        logger.info("Starting Syncing of Code System, Branches, and Refsets from Snowstorm");

        // Only identify branches on filtered code systems and on runShortSync value
        SyncAgent agent = new SyncCodeSystemAgent();
        agent.sync();

        agent = new SyncCrowdAgent();
        agent.sync();

        // Find all refsets from filtered branches
        agent = new SyncRefsetAgent();
        agent.sync();

        // Post processing
        AuditEntryHelper.syncEntry(new Date(startOperationStartTime));
        utilities.emailSyncResults();

        logger.info(statistics.printStatistics());
        logger.info("Completed Syncing with Snowstorm");

        utilities.logProcessingTime("FULL", startOperationStartTime);
    }

    private static void initialize(boolean refsetPerVersionSync, boolean runForProduction, boolean ignoreCoreRefsets) {

        if (dbHandler == null) {
            dbHandler = new SyncDatabaseHandler(null);
        }
        if (utilities == null) {

            utilities = new SyncUtilities(dbHandler);
        }

        dbHandler.setUtilities(utilities);

        isPerVersionSync = refsetPerVersionSync;
        isProductionSystem = runForProduction;
        isIgnoreCoreRefsets = ignoreCoreRefsets;

        adminUsernames.add(SNOMED_ADMIN_USERNAME);
        adminUsernames.add(DEVELOPER_ADMIN_USERNAME_PREFIX);
    }

    public static void setRefsetToSync(final String refsetId, final String editionShortName) throws Exception {

        setTesting(true);
        testingRefset = refsetId;
        testingEditionShortName = editionShortName;

        RefsetMemberService.clearRefsetVersionsWithChanges(refsetId);
    }

    protected static void clearPreviousRun() {

        developerTestingEditionShortName = null;

        filteredCodeSystems.clear();

        statistics.clearStatistics();

        if (utilities != null) {
            utilities.clearPreviousRun();
        }
    }

    protected boolean isDifferentAttribute(String shortName, String attributeName, Object databaseAttribute, Object snowstormAttribute) {
        // logger.debug("in isDifferentAttribute() w/DB: " + databaseAttribute);
        // logger.debug("in isDifferentAttribute() w/Sn: " + snowstormAttribute);
        // logger.debug("in isDifferentAttribute() w/databaseAttribute.equals(snowstormAttribute: " + databaseAttribute.equals(snowstormAttribute));
        // logger.debug("in isDifferentAttribute() w/snowstormAttribute.equals(databaseAttribute: " + snowstormAttribute.equals(databaseAttribute));

        if (snowstormAttribute == null && databaseAttribute == null) {
            // Both null, no difference
            return false;
        } else if (snowstormAttribute != null && databaseAttribute != null && databaseAttribute.equals(snowstormAttribute)) {
            // Both not null with identical value, no difference
            return false;
        }

        // values are different. List them
        if (databaseAttribute instanceof Long) {

            logger.info(" inconsistency found in " + shortName + " having " + attributeName + " with DB value '" + new Date((Long) databaseAttribute) + "' (" + databaseAttribute
                    + ") and Snowstorm value '" + new Date((Long) snowstormAttribute) + "' (" + snowstormAttribute + ")");
        } else {

            logger.info(" inconsistency found in " + shortName + " having " + attributeName + " with DB value '" + databaseAttribute + "' and Snowstorm value '" + snowstormAttribute + "'");
        }

        return true;

    }

    public static String getDeveleperTestingEditionShortName() {

        return developerTestingEditionShortName;
    }

    public static boolean isTesting() {

        return testing;

    }

    public static Boolean getIsIgnoreCoreRefsets() {
return false;
        // return isIgnoreCoreRefsets == null ? false : isIgnoreCoreRefsets;
        // return true;
    }

    public static Boolean getIsProductionSystem() {

        return isProductionSystem == null ? false : isProductionSystem;
    }

    public static Boolean getIsPerVersionSync() {

        return isPerVersionSync == null ? false : isPerVersionSync;
    }

    public static void setTesting(boolean testing) {

        SyncAgent.testing = testing;
    }

    public static Set<String> getAdminUsernames() {
        return adminUsernames;
    }

    List<Edition> readDbAllEditions() throws Exception {
        try (TerminologyService service = new TerminologyService()) {

            return readDbAllEditions(service);
        }
    }

    List<Organization> readDbOrganizations() throws Exception {
        try (TerminologyService service = new TerminologyService()) {

            return service.getAll(Organization.class);
        }
    }

    List<Edition> readDbAllEditions(TerminologyService service) throws Exception {
        return service.getAll(Edition.class);
    }

    List<Edition> readDbActiveEditions(TerminologyService service) throws Exception {
        return readDbAllEditions(service).stream().filter(e -> e.isActive()).collect(Collectors.toList());
    }

    List<Edition> readDbInactiveEditions(TerminologyService service) throws Exception {
        return readDbAllEditions(service).stream().filter(e -> !e.isActive()).collect(Collectors.toList());
    }
}