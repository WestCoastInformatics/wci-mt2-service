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
    private static final Logger LOG = LoggerFactory.getLogger(SyncAgent.class);

    protected static final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    protected static SyncUtilities utilities;

    protected static SyncDatabaseHandler dbHandler;

    protected static final SyncStatistics statistics = new SyncStatistics();

    protected abstract void syncComponent(TerminologyService service) throws Exception;

    /** Execution options. */
    private static Boolean isProductionSystem = null;

    private static Boolean isPerVersionSync = null;

    private static Boolean isIgnoreCoreRefsets = null;

    /** Testing options. */
    private static boolean testing = false;

    protected static String TESTING_EDITION_SHORT_NAME = "SNOMEDCT-NZ";
    
     protected static String testingRefset = "421000210109"; // Core - Dentistry (in multiple projects in RTT)

    // protected static String testingRefset = null; // To test entire edition
    // protected static String testingRefset = "751000172100"; // 751000172100 - from Belgium
    // protected static String testingRefset = "723264001"; // 723264001 - TAGS (only one today) - from sct-core
    // protected static String testingRefset = "64641000052102"; // Tim's for ugprade testing (on Swedish)
    // protected static String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)

    protected static String developerTestingEditionShortName = null;

    protected static Organization develeperTestingOrganization = null;

    /** Other process fields **/
    // Owner Name to Organization Description
    protected static final Set<JsonNode> filteredCodeSystems = new HashSet<>();

    protected static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    protected static final String SNOMED_ADMIN_USERNAME = "rdavidson";

    protected static final String DEVELOPER_ADMIN_USERNAME_PREFIX = "refset-";

    protected static final Set<String> adminUsernames = new HashSet<>();

    // Call when launching sync
    public static void sync(final TerminologyService service, final boolean refsetPerVersionSync, final boolean runForProduction, final boolean ignoreCoreRefsets) throws Exception {

        if (isProductionSystem == null || !isProductionSystem) {

            isPerVersionSync = refsetPerVersionSync;
            isProductionSystem = runForProduction;
            isIgnoreCoreRefsets = ignoreCoreRefsets;
        }

        sync(service);

    }

    // Call when launching a sync service were launching sync is secondary i.e., resetRefset
    public static void sync(final TerminologyService service) throws Exception {

        final Date startOperationStartTime = new Date();

        initialize(service);

        LOG.info("Starting Syncing of Users, Projects, Code System, Branches, and Refsets from Termserver");

        service.add(AuditEntryHelper.syncBeginEntry(startOperationStartTime));

        // Only identify branches on filtered code systems and on runShortSync value
        SyncAgent agent = new SyncCodeSystemAgent();
        agent.syncComponent(service);

        agent = new SyncCrowdAgent();
        agent.syncComponent(service);

        // Find all refsets from filtered branches
        agent = new SyncRefsetAgent();
        agent.syncComponent(service);

        // Post processing
        //utilities.emailSyncResults(service);

        LOG.info(statistics.printStatistics());
        LOG.info("Completed Syncing with Termserver");

        final long processingMinutes = utilities.getProcessingMinutes("FULL", startOperationStartTime);
        service.add(AuditEntryHelper.syncFinishEntry(new Date(), processingMinutes));
    }

    private static void initialize(final TerminologyService service) {

        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

        if (dbHandler == null) {
            dbHandler = new SyncDatabaseHandler(null);
        }
        if (utilities == null) {

            utilities = new SyncUtilities(dbHandler);
        }

        dbHandler.setUtilities(utilities);

        adminUsernames.add(SNOMED_ADMIN_USERNAME);
        adminUsernames.add(DEVELOPER_ADMIN_USERNAME_PREFIX);
    }

    public static void setRefsetToSync(final String refsetId, final String editionShortName) throws Exception {

        setTesting(true);
        testingRefset = refsetId;
        TESTING_EDITION_SHORT_NAME = editionShortName;

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

    public static String getDeveleperTestingEditionShortName() {

        return developerTestingEditionShortName;
    }

    public static boolean isTesting() {

        return testing;

    }

    public static Boolean getIsIgnoreCoreRefsets() {
        return isIgnoreCoreRefsets == null ? false : isIgnoreCoreRefsets;
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

    List<Organization> readDbOrganizations(final TerminologyService service) throws Exception {

        return service.getAll(Organization.class);
    }

    List<Edition> readDbAllEditions(final TerminologyService service) throws Exception {
        return service.getAll(Edition.class);
    }

    List<Edition> readDbActiveEditions(final TerminologyService service) throws Exception {
        return readDbAllEditions(service).stream().filter(e -> e.isActive()).collect(Collectors.toList());
    }

    List<Edition> readDbInactiveEditions(final TerminologyService service) throws Exception {
        return readDbAllEditions(service).stream().filter(e -> !e.isActive()).collect(Collectors.toList());
    }
}
