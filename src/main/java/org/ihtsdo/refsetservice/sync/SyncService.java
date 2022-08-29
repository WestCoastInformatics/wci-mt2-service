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

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SyncService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SyncService.class);

    protected static SyncUtilities utilities = null;

    /** Testing options. */
    protected static boolean testing = false;

    protected static String testingEdition = "elgi";

    // protected static final String testingRefset = null; // To test entire edition
    protected static String testingRefset = "561000172108"; // Default refset created upon Default Project
    // protected static final String testingRefset = "741000172102"; // Refset with project defined in RTT
    // protected static final String testingRefset = "11000172109"; // Sync in the single Intensional refset available on dev-integeration (Belgium Editing)
    // protected static final String testingRefset = "121000210100"; // No changes across 5 versions (NZ Edition)

    protected static final SyncStatistics statistics = new SyncStatistics();

    /** Cache for all DB values used during sync **/
    protected static final List<Edition> allDatabaseEditions = new ArrayList<>();

    protected static final List<Organization> allDatabaseOrganizations = new ArrayList<>();

    protected static final List<Refset> allDatabaseRefsets = new ArrayList<>();

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

    protected static final List<String> ignoredCodeSystemNames = new ArrayList<>();

    protected static final SimpleDateFormat branchDateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    protected static final Set<Refset> snowstormRefsets = new HashSet<>();

    protected static final Set<String> internationalModuleRefsets = new HashSet<>();

    protected static Edition developerTestingEdition = null;

    protected static Organization develeperTestingOranization = null;

    protected boolean refsetPerVersionSync;

    protected boolean forProduction;

    public SyncService(boolean perVersionCreation, boolean runForProduction) {

        if (utilities == null) {

            utilities = new SyncUtilities();

            ignoredCodeSystemNames.addAll(utilities.getPropertyReader().readCodeSystemsToIgnore());

            refsetPerVersionSync = perVersionCreation;
            forProduction = runForProduction;

            try {

                updateDatabaseCache();

            } catch (Exception e) {

                e.printStackTrace();
            }

        }

    }

    public abstract void syncSnowstorm() throws Exception;
    
    public static void setRefsetToSync(final String refsetId, final String editionName) throws Exception {

        testing = true;
        testingRefset = refsetId;
        testingEdition = editionName;
    }

    protected void clearPreviousRun() {

        developerTestingEdition = null;

        ownerDescriptionMap.clear();
        editionOwnerMap.clear();
        refsetEditions.clear();

        uniqueRefsetIds.clear();
        ignoredCodeSystemNames.clear();
        branchesToProcess.clear();

        statistics.clearStatistics();
    }

    protected void updateDatabaseCache() throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            editionOwnerMap.clear();
            allDatabaseEditions.clear();
            allDatabaseOrganizations.clear();
            allDatabaseRefsets.clear();

            allDatabaseEditions.addAll(service.getAll(Edition.class));
            allDatabaseEditions.stream().forEach(e -> editionOwnerMap.put(e.getShortName(), e.getOrganization().getName()));
            // logger.debug(" All Editions: " + allDatabaseEditions);
            // logger.debug(" Edition Owner Map: " + editionOwnerMap);

            allDatabaseOrganizations.addAll(service.getAll(Organization.class));
            // logger.debug(" All Organizations: " + allDatabaseOrganizations);

            allDatabaseRefsets.addAll(service.getAll(Refset.class));
            // logger.debug(" All Refsets: " + allDatabaseRefsets);

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

    public Map<String, Project> getDefaultEditionProjects() {

        return defaultEditionProjects;
    }

}