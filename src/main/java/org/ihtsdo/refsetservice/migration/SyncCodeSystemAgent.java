package org.ihtsdo.refsetservice.migration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class SyncCodeSystemAgent extends SyncAgent {

    private static Logger logger = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    private static Map<String, Project> organizationToDefaultProjects = new HashMap<>();

    private static Set<String> codeSystemsNewAndInactive = new HashSet<>();

    private static final Map<String, Project> defaultOrganizationProjects = new HashMap<>();

    private static final String DEVELOPER_CODE_SYSTEM_SHORTNAME = "SNOMEDCT-WCI";

    protected SyncCodeSystemAgent() throws Exception {

        super();
    }

    protected static Organization getDeveloperTestingOrganization() {

        return develeperTestingOranization;
    }

    protected static Map<String, Project> getOrganizationToDefaultProjectMap() {

        return organizationToDefaultProjects;
    }

    protected static void syncSnowstormCodeSystems(Set<JsonNode> codeSystems) throws Exception {

        // Clear this out to validate the developer code system
        develeperTestingOranization = null;

        for (JsonNode codeSystem : codeSystems) {

            // Simplified approach is to not consider at this point if new edition was created or a new one was discovered

            syncCodeSystem(codeSystem);

        }

        if (develeperTestingOranization == null && !forProduction) {

            // TODO: For now, ignore this, but shuolldn't ever throw exception at this point
            // throw new Exception("Must have a WCI Organization on a non-Prod instance");
        }

        updateDatabaseCache();

    }

    /*-
     * See if corresponding Edition exists in RT2 DB. If not, create it if it isn't inactive. 
     * 
     * 
     * Match by Organization::Edition::id to match against all Orgs in the DB. If not successful, try name, and finally try branch. If nothing found, is new Edition.
     * 
     * For now, only must identify if there) are changes to any of the following object values during sync: 
     * 1) ShortName
     * 2) Name 
     * 3) Branch
     * 4) Active/inactive status
     * 5) defaultLanguageCode
     * 6) topLevelModule
     * 7) defaultLanguageRefsets
     */
    private static void syncCodeSystem(JsonNode codeSystem) {

        Organization syncedOrganization = null;

        try {

            /* identify comparison attributes */
            final String snowstormEditionShortName = codeSystem.has("shortName") ? codeSystem.get("shortName").asText() : "";
            final String snowstormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
            final String snowstormEditionBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
            final boolean isActiveSnowstormEdition = codeSystem.has("active") ? codeSystem.get("active").asBoolean() : true;

            logger.info(" Syncing Code System: " + generateCodeSystemCoordinates(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch));

            /* See if corresponding Edition exists in RT2 DB. If not, create it. */
            List<Edition> dbEditions = allDatabaseEditions.stream().filter(e -> e.getShortName().equals(snowstormEditionShortName)).collect(Collectors.toList());

            if (dbEditions == null || dbEditions.isEmpty()) {

                // If correspondingDbEdition is not null, we are updating an existing supported edition
                syncedOrganization = syncNewCodeSystem(snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition, codeSystem);

            } else {

                if (dbEditions.size() != 1) {

                    throw new Exception("Have encounted two editions with the same shortName on Snowstorm: " + dbEditions);
                }

                final Edition correspondingDbEdition = dbEditions.iterator().next();

                // If correspondingDbEdition is null, this is the first time we have observed this edition, so create it.
                syncedOrganization = syncExistingCodeSystem(correspondingDbEdition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSnowstormEdition, codeSystem);

            }

            // TODO: See if any persisted Editions or Orgs are not even in Snowstorm. If so, inactivate

            // Final steps whether initial or updating sync
            postCodeSystemProcessing(snowstormEditionShortName, syncedOrganization);

        } catch (Exception e) {

            logger.error("Failed in syncing Snowstorm Code System: " + codeSystem);
            e.printStackTrace();
        }

    }

    private static Organization syncExistingCodeSystem(Edition edition, String snowstormEditionShortName, String snowstormEditionName, String snowstormEditionBranch, boolean isActiveSsnowstormEdition,
        JsonNode codeSystem) {

        logger.info(" Sync existing Edition with shortName: " + snowstormEditionShortName);

        Organization syncedOrganization = null;

        try {

            // Process one Organization per Edition.
            final Edition syncedEdition = syncExistingEdition(edition, snowstormEditionShortName, snowstormEditionName, snowstormEditionBranch, isActiveSsnowstormEdition, codeSystem);

            if (syncedEdition != null) {

                // Differences found in edition
                setSnowstormEditionOwner(syncedEdition.getShortName(), syncedEdition.getName(), codeSystem);

                syncedOrganization = syncExistingOrganization(syncedEdition, isActiveSsnowstormEdition, codeSystem);
            } else {

                // No differences found in edition, but check Owner value as well
                setSnowstormEditionOwner(edition.getShortName(), edition.getName(), codeSystem);

                syncedOrganization = syncExistingOrganization(edition, isActiveSsnowstormEdition, codeSystem);
            }

            return syncedOrganization;
        } catch (Exception e) {

            logger.error("Failed in syncing Existing Snowstorm Code System: " + codeSystem);
            e.printStackTrace();

            return null;
        }

    }

    private static Organization syncExistingOrganization(Edition syncedEdition, boolean isActiveSnowstormEdition, JsonNode codeSystem) throws Exception {

        Organization organization = null;

        /* See if have organization with corresponding editionId */
        // If existingEdition is null, this is the first time we have observed this edition, so create it.
        final Organization correspondingDbOrganization =
            allDatabaseOrganizations.stream().filter(o -> syncedEdition.getId().equals(o.getEdition().getId())).collect(Collectors.toList()).iterator().next();

        /* Based on matching attributes: add new, ignore new but inactive, check for changes and modify if needed and ignore otherwise */
        if (correspondingDbOrganization == null) {

            // TODO: Once have support for 1:N Orgs:Eds, this will no longer case long term
            throw new Exception("Must be able to find an existing's Edition's corresponding Organization");
        }

        final Organization syncedOrganization = compareAndUpdateOrganizationDifferences(correspondingDbOrganization, editionOwnerMap.get(syncedEdition.getName()), isActiveSnowstormEdition);

        if (syncedOrganization != null) {

            // A modification was made, so updated edition
            organizationsSynced.add(syncedOrganization);
            organization = syncedOrganization;

        } else {

            // No changes, return existing
            organizationsUnchanged.add(correspondingDbOrganization);
            organization = correspondingDbOrganization;
        }

        logger.info("Synced " + organization.getName() + " Organization");

        return organization;
    }

    private static Edition syncExistingEdition(Edition edition, String editionShortName, String editionName, String editionBranch, boolean isActiveEdition, JsonNode codeSystem) throws Exception {

        final Edition syncedEdition = compareAndUpdateEditionDifferences(edition, editionShortName, editionName, editionBranch, isActiveEdition, codeSystem);

        if (syncedEdition == null) {

            editionsUnchanged.add(edition);
        } else {

            editionsSynced.add(syncedEdition);
        }

        logger.info("Synced " + edition.getShortName() + " Edition");

        return syncedEdition;

    }

    private static Edition compareAndUpdateEditionDifferences(Edition existingEdition, String editionShortName, String editionName, String editionBranch, boolean isActiveEdition, JsonNode codeSystem)
        throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        if (updateAttribute("Edition shortName ", existingEdition.getShortName(), editionShortName)) {

            existingEdition.setShortName(editionShortName);
            modificationMade = true;
        }

        if (updateAttribute("Edition name ", existingEdition.getName(), editionName)) {

            existingEdition.setName(editionName);
            modificationMade = true;
        }

        if (updateAttribute("Edition branch ", existingEdition.getBranch(), editionBranch)) {

            existingEdition.setBranch(editionBranch);
            modificationMade = true;
        }

        if (updateAttribute("Edition active ", existingEdition.isActive(), isActiveEdition)) {

            existingEdition.setActive(isActiveEdition);
            modificationMade = true;
        }

        final String editionTopLevelModule = utilities.identifyTopLevelModule(editionShortName, editionName, editionBranch, codeSystem);

        if (updateAttribute("Edition topLevelModule ", existingEdition.getTopLevelModule(), editionTopLevelModule)) {

            existingEdition.setTopLevelModule(editionTopLevelModule);
            modificationMade = true;
        }

        final String editionDefaultLanguageCode = utilities.identifyDefaultLanguageCode(codeSystem, editionName);

        if (updateAttribute("Edition defaultLanguageCode ", existingEdition.getDefaultLanguageCode(), editionDefaultLanguageCode)) {

            existingEdition.setDefaultLanguageCode(editionDefaultLanguageCode);
            modificationMade = true;
        }

        final Set<String> editionDefaultLanguageRefsets = utilities.identifyDefaultLanguageRefsets(codeSystem, editionShortName);

        if (!existingEdition.getDefaultLanguageRefsets().equals(editionDefaultLanguageRefsets)) {

            if (!existingEdition.getDefaultLanguageRefsets().isEmpty() && editionDefaultLanguageRefsets.isEmpty()) {

                logger.info(" False-Positive inconsistent Edition defaultLanguageRefsets with '" + existingEdition.getDefaultLanguageRefsets() + "' and '" + editionDefaultLanguageRefsets + "'");
            } else {

                logger.info(" inconsistent Edition defaultLanguageRefsets with '" + existingEdition.getDefaultLanguageRefsets() + "' and '" + editionDefaultLanguageRefsets + "'");

                existingEdition.setDefaultLanguageRefsets(editionDefaultLanguageRefsets);
                modificationMade = true;
            }

        }

        /* Update if difference identified during comparison */
        if (modificationMade) {

            // A modification was made, so updated edition
            try (TerminologyService service = new TerminologyService()) {

                utilities.initializeService(service);

                Edition syncedEdition = service.update(existingEdition);

                return syncedEdition;
            }

        } else {

            return null;
        }

    }

    private static Organization syncNewCodeSystem(String newEditionShortName, String newEditionName, String newEditionBranch, boolean isNewActiveEdition, JsonNode codeSystem) throws Exception {

        try {

            if (!isNewActiveEdition) {

                // New Code System created as inactive. Given this is being run nightly and a new org/edition that is inactive at first pass was likely made erroneously.
                // Once fixed and becomes active, we will get it at the following sync. For now, don't add to retSet
                String codeSystemCoordinates = generateCodeSystemCoordinates(newEditionShortName, newEditionName, newEditionBranch);
                codeSystemsNewAndInactive.add(codeSystemCoordinates);

                return null;
            }

            // Create new Edition
            final Edition newEdition = utilities.addEdition(newEditionShortName, newEditionName, newEditionBranch, codeSystem);
            editionsAdded.add(newEdition);

            // Create new Organization
            // TODO: 1 - Add a description default value or update snowstorm with value per codesystem
            // TODO: 2 - Once support 1 Org : N Editions, update entire syncOrg routine to first see if already have defined Organization rather than assume 1:1 relationship
            // b/w & Editions.

            setSnowstormEditionOwner(newEdition.getShortName(), newEdition.getName(), codeSystem);
            final String organizationDescription = "";

            final Organization newOrganization = utilities.addOrganziation(editionOwnerMap.get(newEditionName), organizationDescription, newEdition);

            organizationsAdded.put(newOrganization.getName(), newOrganization);

            return newOrganization;
        } catch (Exception e) {

            logger.error("Failed in syncing New Snowstorm Code System: " + codeSystem);
            e.printStackTrace();

            return null;
        }

    }
    // Organization is done at this point. Check if Developer Organization. If not, create a default UAT project

    private static void postCodeSystemProcessing(String snowstormEditionShortName, Organization syncedOrganization) throws Exception {

        if (snowstormEditionShortName.equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {

            // Support Developer Organization
            if (forProduction) {

                throw new Exception("Can't have a WCI Organization on a Prod instance");
            }

            if (develeperTestingOranization != null) {

                throw new Exception("Can't have two WCI Orgs with new one having shortName: " + snowstormEditionShortName);
            } else {

                // identified WCI Org
                develeperTestingOranization = syncedOrganization;
            }

        } else {

            // Create a Default Project for the edition
            if (syncedOrganization != null && !defaultOrganizationProjects.containsKey(syncedOrganization.getId())) {

                // Create default project
                final String projectName = syncedOrganization.getName() + " Default Project";
                final String projectDescription =
                    "This is a project to support all refsets not already associated with a project in the Refset & Translation Tool for " + syncedOrganization.getName() + ".";

                final Project project = utilities.addProject(syncedOrganization, projectName, projectDescription);

                defaultOrganizationProjects.put(syncedOrganization.getId(), project);
            }

        }

    }

    private static String generateCodeSystemCoordinates(String snowstormEditionShortName, String snowstormEditionName, String snowstormEditionBranch) {

        return snowstormEditionShortName + " / " + snowstormEditionName + " / " + snowstormEditionBranch;
    }

    private static void setSnowstormEditionOwner(String editionShortName, String editionName, JsonNode codeSystem) {

        final String owner = codeSystem.has("owner") ? codeSystem.get("owner").asText() : "";

        // Identify Code System Owner
        if (!owner.trim().isBlank()) {

            editionOwnerMap.put(editionShortName, owner);
            editionOwnerMap.put(editionName, owner);
        } else {

            editionOwnerMap.put(editionShortName, editionName);
            editionOwnerMap.put(editionName, editionName);
        }

    }

    private static Organization compareAndUpdateOrganizationDifferences(Organization existingOrganization, String snowstormOrganizationName, boolean isActiveSnowstormOrganization) throws Exception {

        /* Found existing Edition. Compare the values to determine if something changed, and if so, update the edition accordingly */
        boolean modificationMade = false;

        if (updateAttribute("Organization name ", existingOrganization.getName(), snowstormOrganizationName)) {

            existingOrganization.setName(snowstormOrganizationName);
            modificationMade = true;
        }

        if (updateAttribute("Organization active ", existingOrganization.isActive(), isActiveSnowstormOrganization)) {

            existingOrganization.setActive(isActiveSnowstormOrganization);
            modificationMade = true;
        }

        if (modificationMade) {

            try (final TerminologyService service = new TerminologyService()) {

                utilities.initializeService(service);

                return service.update(existingOrganization);
            }

        } else {

            return null;
        }

    }
}
