package org.ihtsdo.refsetservice.sync;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
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

    /** Execution options. */
    private static Boolean isProductionSystem = null;

    private static Boolean isPerVersionSync = null;

    private static Boolean isIgnoreCoreRefsets = null;

    /** Testing options. */
    private static boolean testing = true;

    protected static String testingEditionShortName = "SNOMEDCT-BE";

    protected static String testingRefset = null; // To test entire edition
    // protected static String testingRefset = "561000172108"; // Default refset created upon Default Project
    // protected static String testingRefset = "64641000052102"; // Tim's for ugprade testing (on Swedish)
    // protected static String testingRefset = "741000172102"; // Refset with project defined in RTT
    // protected static String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)

    protected static Edition developerTestingEdition = null;

    protected static Organization develeperTestingOranization = null;

    /** Other process fields **/
    // Owner Name to Organization Description
    protected static Set<JsonNode> filteredCodeSystems = new HashSet<>();

    // ShortName to Project
    protected static final Map<String, Project> defaultEditionProjects = new HashMap<>();

    /** Abstract Method **/
    public abstract void sync() throws Exception;

    private static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    // TODO: Define when called vs normal one
    public static void sync(TerminologyService service, boolean refsetPerVersionSync, boolean runForProduction, boolean ignoreCoreRefsets) throws Exception {

        if (isProductionSystem == null || !isProductionSystem) {

            initialize(refsetPerVersionSync, runForProduction, ignoreCoreRefsets);
        }

        sync(service);

    }

    public static void sync(TerminologyService service) throws Exception {

        final Date startDate = new Date();

        if (isProductionSystem == null) {

            initialize(false, false, false);
        }

        logger.info("Starting Syncing of Code System, Branches, and Refsets from Snowstorm");

        // Only identify branches on filtered code systems and on runShortSync value
        SyncAgent agent = new SyncCodeSystemAgent();
        agent.sync();

        // Find all refsets from filtered branches
        agent = new SyncRefsetAgent();
        agent.sync();

        int a = 0;
        if (a < 1) {
            logger.info(statistics.printStatistics());
            logger.error("Stopping here on purpose");
            return;
        }

        postCodeSystemProcessing();

        // Update imported refsets with RTT-based metadata (as defined in parseRttData())
        if (!isProductionSystem) {

            SyncOperationsInitializer initializer = new SyncOperationsInitializer(utilities);

            initializer.initialize(getDeveleperTestingEdition(), service.getAll(Edition.class), service.getAll(Refset.class));
        }

        // Post processing
        AuditEntryHelper.syncEntry(startDate);

        utilities.emailSyncResults();

        logger.info(statistics.printStatistics());
        logger.info("Completed Syncing with Snowstorm");
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

    }

    public static void setRefsetToSync(final String refsetId, final String editionShortName) throws Exception {

        setTesting(true);
        testingRefset = refsetId;
        testingEditionShortName = editionShortName;

        RefsetMemberService.clearUniqueRefsetVersions(refsetId);
    }

    protected static void clearPreviousRun() {

        developerTestingEdition = null;

        filteredCodeSystems.clear();

        statistics.clearStatistics();

        if (utilities != null) {
            utilities.clearPreviousRun();
        }
    }

    protected static void updateDatabaseCache() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            defaultEditionProjects.clear();

            /** Process supporting collections **/

            List<Project> defaultProjects = service.getAll(Project.class).stream().filter(p -> p.getName().toLowerCase().contains("default") || p.getDescription().toLowerCase().contains(("default")))
                    .collect(Collectors.toList());
            defaultProjects.stream().forEach(p -> defaultEditionProjects.put(p.getEdition().getShortName(), p));
        }

    }

    // Organization is done at this point. Check if Developer Edition. If not, create a default UAT project
    private static void postCodeSystemProcessing() throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            List<Edition> allEditions = service.getAll(Edition.class);
            int a = 0;
            if (a < 1) {
                return;
            }

            for (Edition syncedEdition : allEditions) {
                if (DEVELOPER_CODE_SYSTEM_SHORTNAME.equalsIgnoreCase(syncedEdition.getShortName())) {

                    // Support Developer Edition
                    if (getIsProductionSystem()) {

                        throw new Exception("Can't have a WCI Edition on a Prod instance");
                    }

                    if (developerTestingEdition != null) {

                        throw new Exception("Can't have two WCI Editions with new one having shortName: " + syncedEdition.getShortName());
                    } else {

                        // identified WCI Edition
                        developerTestingEdition = syncedEdition;
                    }

                } else {

                    // Create a Default Project for the edition
                    if (!defaultEditionProjects.containsKey(syncedEdition.getShortName())) {

                        final String projectName = syncedEdition.getName() + " Default Project";
                        final String projectDescription =
                                "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + syncedEdition.getName() + ".";

                        // Create default project
                        final Project project = dbHandler.addProject(projectName, projectDescription, syncedEdition);
                        defaultEditionProjects.put(syncedEdition.getShortName(), project);
                    }
                }
            }
        }

        // TODO: For now, ignore this, but shouldn't ever throw exception at this point
        if (developerTestingEdition == null && !getIsProductionSystem()) {
            // throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }

    }

    protected boolean isDifferentAttribute(String shortName, String attributeName, Object databaseAttribute, Object snowstormAttribute) {

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

    public static Edition getDeveleperTestingEdition() {

        return developerTestingEdition;
    }

    public Map<String, Project> getDefaultEditionProjects() {

        return defaultEditionProjects;
    }

    public static boolean isTesting() {

        return testing;

    }

    public static Boolean getIsIgnoreCoreRefsets() {

        return isIgnoreCoreRefsets == null ? true : isIgnoreCoreRefsets;
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

}