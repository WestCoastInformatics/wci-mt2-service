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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncCodeSystemDeterminer;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CollectionUtility;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SyncCodeSystemAgent.
 */
public class SyncCodeSystemAgent extends SyncAgent {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncCodeSystemAgent.class);

    /** The filtered code systems. */
    private Set<JsonNode> filteredCodeSystems;

    /** The term server edition to organization map. */
    private HashMap<String, String> termServerShortNameToOwnerMap;

    private SyncCodeSystemDeterminer termServerCodeSystemDeterminer;

    private final Map<String, Edition> dbActiveShortNameToEditionMap = new HashMap<>();

    private final Map<String, Edition> dbInactiveShortNameToEditionMap = new HashMap<>();

    private final Map<String, JsonNode> termserverShortNameCodeSystemMap = new HashMap<>();

    private Set<String> shortNamesToCreate = new HashSet<>();

    private Set<String> shortNamesWithNewOwner = new HashSet<>();

    private Set<String> shortNamesToReactivate = new HashSet<>();

    private Set<String> shortNamesToInactivate = new HashSet<>();

    private final Map<String, String> shortNameToCountryCodeMap = new HashMap<>();

    /**
     * Instantiates a {@link SyncCodeSystemAgent} from the specified parameters.
     *
     * @param service the service
     * @param filteredCodeSystems the filtered code systems
     * @param termServerEditionToOrganizationMap the term server edition to organization map
     * @param termServerCodeSystemDeterminer
     * @throws Exception the exception
     */
    public SyncCodeSystemAgent(final TerminologyService service, final Set<JsonNode> filteredCodeSystems,
        final HashMap<String, String> termServerEditionToOrganizationMap, SyncCodeSystemDeterminer termServerCodeSystemDeterminer) throws Exception {

        this.filteredCodeSystems = filteredCodeSystems;
        this.termServerShortNameToOwnerMap = termServerEditionToOrganizationMap;
        this.termServerCodeSystemDeterminer = termServerCodeSystemDeterminer;
    }

    /* see superclass */
    public void syncComponent(final TerminologyService service) throws Exception {

        List<Edition> dbEditions = service.getAll(Edition.class);

        // TODO: Determine if can remove this approach altogether (moduleIds by concept descendants) rather than metadata. Need to wait until solution made for
        // AU. At that point, look at private method: identifyCoreModules(service)

        initializeSync(service, dbEditions);
        LOG.info("Starting sync of Code System");

        // Group the types of edition changes.
        shortNamesToCreate = addNewEditions(service, dbEditions, termserverShortNameCodeSystemMap);

        shortNamesWithNewOwner = identifyOrganizationNameChanges(service, dbEditions, dbActiveShortNameToEditionMap, dbInactiveShortNameToEditionMap);

        shortNamesToReactivate = reactivateEditionOrganizations(service, dbActiveShortNameToEditionMap, dbInactiveShortNameToEditionMap);

        shortNamesToInactivate = inactivateEditionOrganizations(service, dbActiveShortNameToEditionMap, dbInactiveShortNameToEditionMap);

        // Sync Organizations (driven by shortnames) based on findings
        syncOrganizations(service, dbEditions);
        service.commit();

        dbEditions = service.getAll(Edition.class);
        initializeSync(service, dbEditions);

        // Address any new editions or changes to their statuses
        syncEditions(service, dbEditions);
        service.commit();

        dbEditions = service.getAll(Edition.class);
        initializeSync(service, dbEditions);

        // Address any new editions or changes to their statuses
        comapareEditions(service);
        service.commit();

        LOG.info("Finished syncing Code System");
    }

    private void initializeSync(TerminologyService service, List<Edition> dbEditions) throws Exception {

        service.beginTransaction();

        dbActiveShortNameToEditionMap.clear();
        dbInactiveShortNameToEditionMap.clear();
        termserverShortNameCodeSystemMap.clear();
        shortNameToCountryCodeMap.clear();

        // Setup the edition-based database mapping

        for (Edition edition : dbEditions) {
            final String shortName = edition.getShortName();

            if (edition.isActive()) {
                dbActiveShortNameToEditionMap.put(shortName, edition);
            } else {
                dbInactiveShortNameToEditionMap.put(shortName, edition);
            }
        }

        // For helping later, identify each code system's country code from the term server's filtered code systems
        for (JsonNode codeSystem : filteredCodeSystems) {
            final String shortName = codeSystem.get("shortName").asText();
            final String countryCode = codeSystem.has("countryCode") ? codeSystem.get("countryCode").asText() : "";

            shortNameToCountryCodeMap.put(shortName, countryCode);
            termserverShortNameCodeSystemMap.put(shortName, codeSystem);
        }

        // TODO: For TESTING RT2-1979
        // this.filteredCodeSystems.clear();
        // this.termServerShortNameToOwnerMap.clear();
    }

    private void syncEditions(TerminologyService service, List<Edition> dbEditions) throws Exception {

        // Add editions
        Set<JsonNode> updatedFilteredCodeSystems = filteredCodeSystems;
        for (String shortName : shortNamesToCreate) {
            try {
                final Edition addedEdition =
                    getDbHandler().addEdition(service, termserverShortNameCodeSystemMap.get(shortName), termServerShortNameToOwnerMap.get(shortName));

                if (addedEdition != null) {
                    LOG.info("Added edition: {}", shortName);
                } else {
                    // Error in processing edition. Must remove it from filteredList
                    updatedFilteredCodeSystems.clear();
                    updatedFilteredCodeSystems
                        .addAll(filteredCodeSystems.stream().filter(cs -> !cs.get("shortName").asText().equals(shortName)).collect(Collectors.toList()));
                }
            } catch (Exception e) {
                e.printStackTrace();

                LOG.error("Failed to process edition: " + shortName + " successfully though proceeding to next edition. Issue was: " + e.getMessage());
            }

        }

        // Inactivate Editions
        filteredCodeSystems = updatedFilteredCodeSystems;
        for (String shortName : shortNamesToInactivate) {
            final Edition edition = dbActiveShortNameToEditionMap.get(shortName);

            EditionService.updateEditionStatus(service, SecurityService.getUserFromSession(), edition, false);

            STATISTICS.incrementEditionsInactivated();
            LOG.info("Inactivated edition: {}", shortName);
        }

        // Reactivate Editions
        for (String shortName : shortNamesToReactivate) {
            final Edition edition = dbInactiveShortNameToEditionMap.get(shortName);

            EditionService.updateEditionStatus(service, SecurityService.getUserFromSession(), edition, true);

            STATISTICS.incrementEditionsReactivated();
            LOG.info("Reactivated edition: {}", shortName);
        }

        // Update editions' organizations' names as needed
        for (String shortName : shortNamesWithNewOwner) {
            for (Edition dbEdition : dbEditions) {
                if (dbEdition.getShortName().equals(shortName)) {
                    dbEdition.getOrganization().setName(termServerShortNameToOwnerMap.get(shortName));
                    service.update(dbEdition);

                    STATISTICS.incrementEditionOrganizationMapChanged();
                    LOG.info("Changed Owner for edition: {}", shortName);
                }
            }
        }

        LOG.info("Total termServer codeSystems: {}", filteredCodeSystems.size());
        LOG.info("Total editions created: {}", shortNamesToCreate.size());
        LOG.info("Total editions reactivated: {}", shortNamesToReactivate.size());
        LOG.info("Total editions inactivated: {}", shortNamesToInactivate.size());
        LOG.info("Total editions whose owner changed: {}", shortNamesWithNewOwner.size());
    }

    private void syncOrganizations(TerminologyService service, List<Edition> dbEditions) throws Exception {

        int newCounter = 0;
        int reactivedCounter = 0;
        int inactivedCounter = 0;

        List<Organization> dbOrganizations = service.getAll(Organization.class);

        // Look for editions expecting an active organization. For each organization, see if one needs to be created or if an existing one needs to be
        // reactivated.
        Set<String> codeSystemShortNamesToReview = new HashSet<>();
        codeSystemShortNamesToReview.addAll(shortNamesToCreate);
        codeSystemShortNamesToReview.addAll(shortNamesWithNewOwner);
        codeSystemShortNamesToReview.addAll(shortNamesToReactivate);
        codeSystemShortNamesToReview.addAll(shortNamesToInactivate);

        /* Activation */
        // For expected active organization, see if code system's owner needs to be added or reactivated in the database
        Set<String> organizationNamesProcessed = new HashSet<>();
        for (final String codeSystemShortName : codeSystemShortNamesToReview) {
            if (termServerShortNameToOwnerMap.containsKey(codeSystemShortName)) {
                final String organizationName = termServerShortNameToOwnerMap.get(codeSystemShortName);

                if (!organizationNamesProcessed.contains(organizationName)) {
                    // Handle organization by use case
                    if (dbOrganizations.stream().noneMatch(o -> o.getName().equals(organizationName))) {
                        // Don't create new organization for the new name as organization name is updated in the last step.
                        if (!shortNamesWithNewOwner.contains(codeSystemShortName)) {
                            // Use case #1: Organization is new, create it and define as affiliate
                            final Organization newOrganization =
                                getDbHandler().addOrganziation(service, organizationName, shortNameToCountryCodeMap.get(codeSystemShortName));

                            // TODO: Determine if 1) Derivatives shouldn't be affiliates? and 2) How does affiliate support handle inactivate/reactivate?
                            addOrganizationToAffiliateEdition(service, newOrganization);

                            newCounter++;
                            LOG.info("Added organization: {}", organizationName);
                        }
                    } else {

                        if (dbOrganizations.stream().anyMatch(o -> !o.isActive() && o.getName().equals(organizationName))) {
                            // Use case #2: Organization is in database, but inactive, reactivate its
                            final String organizationIdToActivate =
                                dbOrganizations.stream().filter(o -> !o.isActive() && o.getName().equals(organizationName)).findAny().get().getId();

                            getDbHandler().updateEditionStatus(service, organizationIdToActivate, true);

                            reactivedCounter++;
                            LOG.info("Reactivated organization: {}", organizationName);
                        }
                    }

                    organizationNamesProcessed.add(organizationName);
                }
            }
        }

        /* Inactivation */
        // Review editions found in shortNamesWithNewOwner and shortNamesToInactivate to see if an existing owner needs to be inactivated. This is only the case
        // if no other editions rely upon the organization

        /*-
         * Uncertain if necessary, so commenting out for now
         * 
        codeSystemShortNamesToReview.clear();
        codeSystemShortNamesToReview.addAll(shortNamesWithNewOwner);
        codeSystemShortNamesToReview.addAll(shortNamesToInactivate);
        
        final Map<Organization, Set<String>> organizationsToShortNamesMap = new HashMap<>();
        
        // Group the code system short names by owner
        for (final String codeSystemShortName : codeSystemShortNamesToReview) {
            // if organization is inactive, add it to the reactivation list
            final String ownerName = termServerShortNameToOwnerMap.get(codeSystemShortName);
        
            // Identify if any organizations to be relied upon that are currently inactive
            final Organization organization = dbOrganizations.stream().filter(o -> !o.isActive() && o.getName().equals(ownerName)).findAny().orElse(null);
        
            if (organization != null) {
                if (!organizationsToShortNamesMap.containsKey(organization)) {
                    organizationsToShortNamesMap.put(organization, new HashSet<>());
                }
        
                organizationsToShortNamesMap.get(organization).add(codeSystemShortName);
            } else {
                     
                inactivedCounter++;
                LOG.info("No need to inactivate {} as it is already inactive", ownerName);
            }
        }
        
        for (Organization organization : organizationsToShortNamesMap.keySet()) {
            Set<String> organziationEditions = organizationsToShortNamesMap.get(organization);
        
            if (organziationEditions.stream().noneMatch(e -> !newShortNames.contains(e) && !shortNamesToReactivate.contains(e))) {
                // Inactivate organizations if no editions are or will be using them
                getDbHandler().updateEditionStatus(service, organization.getId(), false);
                LOG.info("Inactivated organization: {}", organization.getName());
            }
        }
         */

        LOG.info("Total termServer codeSystems: {}", filteredCodeSystems.size());
        LOG.info("Total organizations created: {}", newCounter);
        LOG.info("Total organizations reactivated: {}", reactivedCounter);
        LOG.info("Total organizations inactivated: {}", inactivedCounter);
    }

    private Set<String> inactivateEditionOrganizations(TerminologyService service, Map<String, Edition> dbActiveShortNameToEditionMap,
        Map<String, Edition> dbInactiveShortNameToEditionMap) throws Exception {

        /* Inactivate: Identify code systems which are no longer on the termserver (inactivate edition and possibly corresponding organization) */
        final Set<String> shortNamesToInactivate = new HashSet<>();

        for (String dbActiveShortName : dbActiveShortNameToEditionMap.keySet()) {
            if (termServerCodeSystemDeterminer.isShortNameToSync(dbActiveShortName, getUtilities())) {
                if (!termServerShortNameToOwnerMap.keySet().contains(dbActiveShortName)) {
                    shortNamesToInactivate.add(dbActiveShortName);
                }
            }
        }

        return shortNamesToInactivate;
    }

    private Set<String> reactivateEditionOrganizations(TerminologyService service, Map<String, Edition> dbActiveShortNameToEditionMap,
        Map<String, Edition> dbInactiveShortNameToEditionMap) throws Exception {

        /* Reactivate: Identify code systems which are newly discovered on the termserver (activate edition and possibly corresponding organization) */
        final Set<String> shortNamesToReactivate = new HashSet<>();

        for (String dbInactiveShortName : dbInactiveShortNameToEditionMap.keySet()) {
            if (termServerShortNameToOwnerMap.keySet().contains(dbInactiveShortName)) {
                shortNamesToReactivate.add(dbInactiveShortName);
            }
        }

        return shortNamesToReactivate;
    }

    private Set<String> identifyOrganizationNameChanges(TerminologyService service, List<Edition> dbEditions,
        Map<String, Edition> dbActiveShortNameToEditionMap, Map<String, Edition> dbInactiveShortNameToEditionMap) throws Exception {

        /*
         * New Owner: Identify code systems whose owner name has changed (necessitating updating the edition's organization and possibly inactivating the //
         * organization)
         */
        final Set<String> shortNamesWithNewOwner = new HashSet<>();

        for (String termServerShortName : termServerShortNameToOwnerMap.keySet()) {
            if ((dbActiveShortNameToEditionMap.containsKey(termServerShortName)
                && !dbActiveShortNameToEditionMap.get(termServerShortName).getOrganizationName().equals(termServerShortNameToOwnerMap.get(termServerShortName)))
                || (dbInactiveShortNameToEditionMap.containsKey(termServerShortName) && !dbInactiveShortNameToEditionMap.get(termServerShortName)
                    .getOrganizationName().equals(termServerShortNameToOwnerMap.get(termServerShortName)))) {
                shortNamesWithNewOwner.add(termServerShortName);
            }
        }

        return shortNamesWithNewOwner;
    }

    private Set<String> addNewEditions(TerminologyService service, List<Edition> dbEditions, Map<String, JsonNode> termserverShortNameCodeSystemMap) {

        /*
         * New Editions: Identify new code systems on the termserver that haven't been seen before (necessitating creating a new edition and possibly a new //
         * organization)
         */
        final Set<String> newShortNames = new HashSet<>();

        for (String termServerShortName : termServerShortNameToOwnerMap.keySet()) {
            if (!newShortNames.contains(termServerShortName) && dbEditions.stream().noneMatch(e -> e.getShortName().equals(termServerShortName))) {
                newShortNames.add(termServerShortName);
            }
        }

        return newShortNames;
    }

    /**
     * Compare and modify editions.
     *
     * @param service the service
     * @param matchingEditionShortNames the matching edition short names
     * @param termserverShortNameCodeSystemMap the termserver short name code system map
     * @return the list
     * @throws Exception the exception
     */
    private Set<String> compareAndModifyEditions(final TerminologyService service, final List<String> matchingEditionShortNames,
        final Map<String, JsonNode> termserverShortNameCodeSystemMap) throws Exception {

        final Set<String> modifiedShortNames = new HashSet<>();

        // Process one Organization per Edition.
        for (final String shortName : matchingEditionShortNames) {

            if (termserverShortNameCodeSystemMap.containsKey(shortName)) {

                final Edition dbEdition = identifyMathcingEdition(service, shortName);
                final Edition modifyingEdition = new Edition(dbEdition);

                // Find values for Snowstorm Edition
                final JsonNode codeSystem = termserverShortNameCodeSystemMap.get(shortName);
                String snowStormEditionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
                final String snowStormBranch = codeSystem.has("branchPath") ? codeSystem.get("branchPath").asText() : "";
                final String snowStormMaintainerType = getUtilities().identifyMaintainerType(codeSystem, snowStormEditionName);
                final Set<String> snowStormEditionModules = SyncUtilities.determineExtendedModules(snowStormBranch);

                // start comparison
                boolean modificationMade = false;

                // TODO: Testing for RT2-1979 - Forcing a change in snowstorm name's for an edition
                // snowStormEditionName = "RT2-1979 Test";
                if (isDifferentAttribute(dbEdition.getShortName(), "Edition name ", dbEdition.getName(), snowStormEditionName)) {

                    modifyingEdition.setName(snowStormEditionName);
                    modificationMade = true;
                }

                // ignore changing if for WCI Code System
                if (dbEdition.getShortName().equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {

                    developerTestingBranchToUse = dbEdition.getBranch();
                }

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition branch ", dbEdition.getBranch(), snowStormBranch)) {

                    modifyingEdition.setBranch(snowStormBranch);
                    modificationMade = true;
                }

                if (CollectionUtility.isDifferentCollection(dbEdition.getModules(), snowStormEditionModules)) {
                    // TODO: If need to change to support multiple modules (For AU perhaps), then this must be address
                    modifyingEdition.setModules(snowStormEditionModules);
                    modificationMade = true;
                }

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition maintainerType ", dbEdition.getMaintainerType(), snowStormMaintainerType)) {

                    modifyingEdition.setMaintainerType(snowStormMaintainerType);
                    modificationMade = true;
                }

                final String termserverDefaultLanguageCode = getUtilities().identifyDefaultLanguageCode(codeSystem, snowStormEditionName);

                if (isDifferentAttribute(dbEdition.getShortName(), "Edition defaultLanguageCode ", dbEdition.getDefaultLanguageCode(),
                    termserverDefaultLanguageCode)) {

                    modifyingEdition.setDefaultLanguageCode(termserverDefaultLanguageCode);
                    modificationMade = true;
                }

                Set<String> termserverDefaultLanguageRefsets = null;

                if (dbEdition.getShortName().equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {

                    termserverDefaultLanguageRefsets = getUtilities().identifyEditionLanguageRefsets(codeSystem, shortName, snowStormBranch);
                } else {

                    termserverDefaultLanguageRefsets = getUtilities().identifyEditionLanguageRefsets(codeSystem, shortName, modifyingEdition.getBranch());
                }

                if (!dbEdition.getDefaultLanguageRefsets().equals(termserverDefaultLanguageRefsets)) {

                    modifyingEdition.setDefaultLanguageRefsets(termserverDefaultLanguageRefsets);
                    modificationMade = true;
                }

                if (modificationMade) {

                    if (dbEdition.getShortName().equals(DEVELOPER_CODE_SYSTEM_SHORTNAME)) {

                        modifyingEdition.setBranch(developerTestingBranchToUse);
                    }

                    getDbHandler().updateEdition(service, modifyingEdition);

                    modifiedShortNames.add(modifyingEdition.getShortName());
                }

            }

        }

        return modifiedShortNames;
    }

    private Edition identifyMathcingEdition(TerminologyService service, String shortName) throws Exception {

        final List<Edition> dbEditions = readDbActiveEditions(service);

        // Find associated DB edition
        final List<Edition> matchingEditions = new ArrayList<>();

        // If have multiple editions that are ALL inactive, ignore issue entirely as will inactivate these later anyway
        for (Edition edition : dbEditions) {

            if (edition.getShortName().equals(shortName)) {

                matchingEditions.add(edition);
            }

        }

        // See if the edition's organization is set to be inactivated. If so, no need to review the edition now as its fields will be updated if/when
        // reactivated
        if (matchingEditions.size() > 1) {

            throw new Exception("May only have a single edition in db per shortName " + shortName + ", but have multiple: " + matchingEditions);
        } else if (matchingEditions.isEmpty()) {

            throw new Exception("Can't find matching edition in db from shortName " + shortName + ", so nothing to compare against: " + matchingEditions);
        }

        return matchingEditions.get(0);
    }

    /**
     * Adds the organization to all affiliate organizations.
     *
     * @param service the service
     * @param organization the organization
     * @throws Exception the exception
     */
    private void addOrganizationToAffiliateEdition(final TerminologyService service, final Organization organization) throws Exception {

        final ResultList<Organization> affiliateOrganizations = service.find("active:true AND affiliate:true", null, Organization.class, null);

        // when adding a new edition, add for affiliates too.
        // create the affiliated editions for the organization
        for (final Organization affiliateOrg : affiliateOrganizations.getItems()) {

            final List<Edition> editionList = EditionService.getAffiliateEditionList();
            final SearchParameters sp = new SearchParameters();
            sp.setQuery("organizationId: " + affiliateOrg.getId());
            final ResultList<Edition> existingEditions = EditionService.searchEditions(service, sp);

            for (final Edition edition : editionList) {

                boolean found = existingEditions.getItems().stream().anyMatch(e -> edition.getBranch().equals(e.getBranch()));

                if (!found) {

                    LOG.info("ADD new organization to affiliate edition name:{} organization name: {}", edition.getName(), affiliateOrg.getName());
                    edition.setOrganization(affiliateOrg);
                    edition.setShortName(CrowdGroupNameAlgorithm.generateAffiliateShortName(edition.getShortName(), affiliateOrg.getId()));

                    service.add(edition);
                    service.add(AuditEntryHelper.addEditionEntry(edition));
                }

            }

        }

    }

    private void comapareEditions(TerminologyService service) throws Exception {

        /* Look for differences between for editions that are active in the termserver and and active in the database */
        final List<String> activeInBothShortNames = new ArrayList<>();
        Set<String> shortNamesModified = new HashSet<>();
        
        activeInBothShortNames.addAll(dbActiveShortNameToEditionMap.keySet().stream()
            .filter(dbShortName -> !shortNamesToCreate.contains(dbShortName))
            .filter(dbShortName -> termserverShortNameCodeSystemMap.keySet().contains(dbShortName)).collect(Collectors.toList()));

        // Compare editions in termserver & active in db
        if (!activeInBothShortNames.isEmpty()) {

            shortNamesModified = compareAndModifyEditions(service, activeInBothShortNames, termserverShortNameCodeSystemMap);
            STATISTICS.setEditionsModified(shortNamesModified.size());

            for (String shortName : shortNamesModified) {
                LOG.info("Modified edition: {}", shortName);
            }
        }

        LOG.info("Total editions modified: {}", shortNamesModified.size());
    }
}
