package org.ihtsdo.refsetservice.sync;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SyncService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SyncService.class);

    protected static SyncUtilities utilities = null;

    private static Boolean isProductionSystem = null;

    private static Boolean isPerVersionSync = null;

    /** Testing options. */
    private static boolean testing = false;

    protected static String testingEdition = "elgi";
    // protected static String testingEdition = "wed";

    // protected static String testingRefset = null; // To test entire edition
    // protected static String testingRefset = "561000172108"; // Default refset created upon Default Project
    // protected static String testingRefset = "64641000052102"; // Tim's for ugprade testing (on Swedish)
    // protected static String testingRefset = "741000172102"; // Refset with project defined in RTT
    // protected static String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)
    protected static String testingRefset = "721000172106"; // Belgian GP Refset (from refset defined in RTT but of which only 1 of the 2 versions came over)

    protected static final SyncStatistics statistics = new SyncStatistics();

    /** Cache for all DB values used during sync **/
    protected static final List<Edition> allDatabaseEditions = new ArrayList<>();

    protected static final List<Organization> allDatabaseOrganizations = new ArrayList<>();

    protected static final List<Refset> allDatabaseRefsets = new ArrayList<>();

    protected static final List<Project> allDatabaseProjects = new ArrayList<>();

    /** Maps to help assoicate across sync **/

    // RefsetId to Edition
    protected static final Map<String, Edition> refsetEditions = new HashMap<>();

    // Edition ShortName to Organization Name
    protected static final Map<String, String> editionOwnerMap = new HashMap<>();

    // Owner Name to Organization Description
    protected static final Map<String, String> ownerDescriptionMap = new HashMap<>();

    // Edition Short Name to map of dates to branch paths
    protected static final Map<String, SortedMap<Date, String>> branchesToProcess = new HashMap<>();

    /** Do not clear per run **/

    // rttProject Id to Rt2Project
    protected final static Map<String, Project> rttProjects = new HashMap<>();

    // ShortName to Project
    protected static final Map<String, Project> defaultEditionProjects = new HashMap<>();

    /** General class fields **/

    protected static final Set<String> uniqueRefsetIds = new HashSet<>();

    protected static final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    protected static final Set<Refset> snowstormRefsets = new HashSet<>();

    protected static final Set<String> internationalModuleRefsets = new HashSet<>();

    protected static Edition developerTestingEdition = null;

    protected static Organization develeperTestingOranization = null;

    protected boolean refsetPerVersionSync;

    protected boolean forProduction;

    public abstract void syncSnowstorm() throws Exception;

    private static void initialize(boolean refsetPerVersionSync, boolean runForProduction) {

        if (utilities == null) {

            utilities = new SyncUtilities();

            isPerVersionSync = refsetPerVersionSync;
            isProductionSystem = runForProduction;

            try {

                updateDatabaseCache();

            } catch (Exception e) {

                e.printStackTrace();
            }

        }

    }

    public static void sync(TerminologyService service, boolean refsetPerVersionSync, boolean runForProduction) throws Exception {

        if (isProductionSystem == null) {

            initialize(refsetPerVersionSync, runForProduction);
        }

        sync(service);

    }

    public static void sync(TerminologyService service) throws Exception {

        if (isProductionSystem == null) {

            initialize(false, false);
        }

        logger.info("Starting Syncing of Code System, Branches, and Refsets from Snowstorm");

        utilities.initializeService(service);

        SyncService agent = new SyncCodeSystemAgent();

        // Only identify branches on filtered code systems and on runShortSync value
        agent.syncSnowstorm();

        SyncUtilities syncUtilities = new SyncUtilities();
        syncUtilities.parseRttData();

        // Find all refsets from filtered branches
        agent = new SyncRefsetAgent();
        agent.syncSnowstorm();

        // Update imported refsets with RTT-based metadata (as defined in parseRttData())
        if (!isProductionSystem) {

            SyncOperationsInitializer initializer = new SyncOperationsInitializer();

            initializer.initialize(agent.getDeveleperTestingEdition(), agent.getAllDatabaseEditions(), agent.getAllDatabaseRefsets());
        }

        logger.info(agent.printStatistics());

        service.add(AuditEntryHelper.syncEntry(new Date()));

        logger.info("Completed Syncing with Snowstorm");

    }

    public static Boolean getIsProductionSystem() {

        return isProductionSystem == null ? false : isProductionSystem;
    }

    public static void setRefsetToSync(final String refsetId, final String editionName) throws Exception {

        setTesting(true);
        testingRefset = refsetId;
        testingEdition = editionName;

        RefsetMemberService.clearUniqueRefsetVersions(refsetId);
    }

    protected void clearPreviousRun() {

        developerTestingEdition = null;

        ownerDescriptionMap.clear();
        editionOwnerMap.clear();
        refsetEditions.clear();

        uniqueRefsetIds.clear();
        branchesToProcess.clear();

        statistics.clearStatistics();
    }

    protected static void updateDatabaseCache() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            allDatabaseEditions.clear();
            allDatabaseOrganizations.clear();
            allDatabaseProjects.clear();
            allDatabaseRefsets.clear();
            editionOwnerMap.clear();
            defaultEditionProjects.clear();

            allDatabaseEditions.addAll(service.getAll(Edition.class));
            // logger.debug(" All Editions: " + allDatabaseEditions);

            allDatabaseOrganizations.addAll(service.getAll(Organization.class));
            // logger.debug(" All Organizations: " + allDatabaseOrganizations);

            allDatabaseRefsets.addAll(service.getAll(Refset.class));
            // logger.debug(" All Refsets: " + allDatabaseRefsets);

            allDatabaseProjects.addAll(service.getAll(Project.class));
            // logger.debug(" All Projects: " + allDatabaseProjects);

            /** Process supporting collections **/
            allDatabaseEditions.stream().forEach(e -> editionOwnerMap.put(e.getShortName(), e.getOrganization().getName()));
            logger.debug(" Edition Owner Map: " + editionOwnerMap);

            List<Project> defaultProjects =
                allDatabaseProjects.stream().filter(p -> p.getName().toLowerCase().contains("default") || p.getDescription().toLowerCase().contains(("default"))).collect(Collectors.toList());
            defaultProjects.stream().forEach(p -> defaultEditionProjects.put(p.getEdition().getShortName(), p));
            logger.debug(" defaultEditionProjects: " + defaultEditionProjects);
        }

    }

    protected boolean updateAttribute(String attributeName, Object databaseAttribute, Object snowstormAttribute) {

        if (snowstormAttribute == null) {

            // Nothing to update if snowstorm is null
            return false;
        } else if (databaseAttribute == null) {

            // Handle inconsistent NULL
            logger.info(" inconsistent " + attributeName + " with DB value '" + databaseAttribute + "' and Snowstorm value '" + snowstormAttribute + "'");

            return true;

        }

        // Both have values, so compare
        if (databaseAttribute.equals(snowstormAttribute)) {

            return false;
        } else {

            if (databaseAttribute instanceof Long) {

                logger.info(" inconsistent " + attributeName + " with DB value '" + new Date((Long) databaseAttribute) + "' (" + databaseAttribute + ") and Snowstorm value '"
                    + new Date((Long) snowstormAttribute) + "' (" + snowstormAttribute + ")");
            } else {

                logger.info(" inconsistent " + attributeName + " with DB value '" + databaseAttribute + "' and Snowstorm value '" + snowstormAttribute + "'");
            }

            return true;
        }

    }

    public String printStatistics() {

        return statistics.printStatistics();
    }

    public Edition getDeveleperTestingEdition() {

        return developerTestingEdition;
    }

    public List<Edition> getAllDatabaseEditions() {

        return allDatabaseEditions;
    }

    public List<Refset> getAllDatabaseRefsets() {

        return allDatabaseRefsets;
    }

    public List<Project> getAllDatabaseProjects() {

        return allDatabaseProjects;
    }

    public Map<String, Project> getDefaultEditionProjects() {

        return defaultEditionProjects;
    }

    public static boolean isTesting() {

        return testing;

    }

    public static void setTesting(boolean testing) {

        SyncService.testing = testing;

    }

}