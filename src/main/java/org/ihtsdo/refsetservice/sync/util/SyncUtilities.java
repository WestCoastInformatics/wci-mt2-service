/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.sync.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.TeamType;
import org.ihtsdo.refsetservice.model.enums.UserRole;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetWorkflowService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.TeamService;
import org.ihtsdo.refsetservice.util.EmailUtility;
import org.ihtsdo.refsetservice.util.LanguageUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SyncUtilities.
 */
public class SyncUtilities {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SyncUtilities.class);

    /** The db handler. */
    private final SyncDatabaseHandler dbHandler;

    private static final Map<String, Team> ORGANIZATION_TO_ADMIN_TEAM_CACHE = new HashMap<>();

    /** The Constant PROPERTY_READER. */
    private static final SyncPropertyFileReader PROPERTY_READER = new SyncPropertyFileReader();

    /** The undefined default language refsets. */
    private static Map<String, Set<String>> undefinedDefaultLanguageRefsets = PROPERTY_READER.readUndefinedDefaultLanguageRefsets();

    /** The Constant CORE_REFSETS. */
    protected static final Set<String> CORE_REFSETS = new HashSet<>();

    /** The Constant CORE_MODULES. */
    protected static final Set<String> CORE_MODULES = new HashSet<>();

    /** The Constant DEVELOPER_ORGANIZATION_NAME_KEYWORD. */
    protected static final String DEVELOPER_ORGANIZATION_NAME_KEYWORD = "wci";

    protected static final String MODULED_ID_CONCEPT = "900000000000445007";

    /** The Constant EDITION_MODULES_MAP. */
    // Edition shortName to Set<Module SctIds>
    private static final Map<String, Set<String>> EDITION_MODULES_FROM_DESCENDANTS_MAP = new HashMap<>();

    private static final Map<String, String> EDITION_MODULE_FROM_METADATA_MAP = new HashMap<>();

    /** The Constant FEEDBACK_TESTING_USER_NAME. */
    public static final String FEEDBACK_TESTING_USER_NAME = "FeedbackTesting";

    /** The Constant SYNC_USER_NAME. */
    public static final String SYNC_USER_NAME = "Snowstorm Sync";

    /** The Constant UNDEFINED_USER_NAME. */
    private static final String UNDEFINED_USER_NAME = "Undefined";

    /** The Constant METADATA. */
    private static final SyncPersistenceMetadata METADATA = new SyncPersistenceMetadata(new Date(), UNDEFINED_USER_NAME);

    /** The Constant SIMPLE_REFSET_TYPE_CONCEPT. */
    static final String SIMPLE_REFSET_TYPE_CONCEPT = "446609009";

    /**
     * Instantiates a {@link SyncUtilities} from the specified parameters.
     *
     * @param dbHandler the db handler
     */
    public SyncUtilities(final SyncDatabaseHandler dbHandler) {

        this.dbHandler = dbHandler;
    }

    /**
     * Returns the user.
     *
     * @param service the service
     * @param userName the user name
     * @return the user
     * @throws Exception the exception
     */
    public User getUser(final TerminologyService service, final String userName) throws Exception {

        User user = null;

        final PfsParameter pfs = new PfsParameter();
        final QueryParameter query = new QueryParameter();
        query.setQuery("userName:" + userName + " AND active:true");

        final ResultList<User> results = service.find(query, pfs, User.class, null);

        if (results.getItems() != null && results.getItems().size() == 1) {

            // User already exists
            user = results.getItems().iterator().next();
        }

        return user;
    }

    /**
     * Returns the user.
     *
     * @param service the service
     * @param name the name
     * @param userName the user name
     * @param email the email
     * @param roles the roles
     * @return the user
     * @throws Exception the exception
     */
    public User getUser(final TerminologyService service, final String name, final String userName, final String email, final Set<String> roles)
        throws Exception {

        User user = getUser(service, userName);

        if (user == null) {

            user = dbHandler.addUser(service, name, userName, email);
        }

        return user;

    }

    /**
     * Identify modules via the concept desendants call.
     *
     * @param shortName the short name
     * @param editionName the edition name
     * @param editionBranch the edition branch
     * @param codeSystem the code system
     * @return the sets the
     * @throws Exception the exception
     */
    public Set<String> identifyModulesByConceptDescendants(final String shortName, final String editionBranch) throws Exception {

        if (EDITION_MODULES_FROM_DESCENDANTS_MAP.containsKey(shortName)) {
            return EDITION_MODULES_FROM_DESCENDANTS_MAP.get(shortName);
        }

        // Get all modules associated with branch using Snowstorm Descendent call
        // E.G.,
        // https://uat-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/MAIN%2FSNOMEDCT-NCP/concepts/900000000000443000/descendants?stated=false&offset=0&limit=50
        final Set<String> editionModules = new HashSet<>();

        final String url = SnowstormConnection.getBaseUrl() + "{branch}/concepts/" + MODULED_ID_CONCEPT + "/descendants?stated=false&page=0&size=100";
        LOG.info("getRefsetMembers URL: " + url.replace("{branch}", editionBranch));

        try (final Response response = SnowstormConnection.getResponse(url.replace("{branch}", editionBranch))) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                if (editionBranch.startsWith("MAIN")) {
                    throw new Exception("Unable to identify modules called with: " + url.replace("{branch}", editionBranch));
                }
            }

            // get RefSets from edition as long as a) active & b) not a core refset
            final String resultString = SnowstormConnection.readEntityAsString(response);
            final ObjectMapper mapper = ThreadLocalMapper.get();

            final JsonNode root = mapper.readTree(resultString);
            final Iterator<JsonNode> moduleIterator = root.get("items").iterator();

            while (moduleIterator.hasNext()) {
                final JsonNode module = moduleIterator.next();

                if (module.get("active").asBoolean()) {
                    editionModules.add(module.get("conceptId").asText());
                }

            }
        }

        // If it's international, populate the coreModules and return.
        if (isInternationalEdition(shortName)) {
            CORE_MODULES.addAll(editionModules);
            EDITION_MODULES_FROM_DESCENDANTS_MAP.put(shortName, CORE_MODULES);

            return CORE_MODULES;
        }

        // Otherwise, return only the non-core modules.
        editionModules.removeAll(CORE_MODULES);

        if (editionModules.isEmpty()) {

            // All non-core code systems must have a non-core module.
            // throw new Exception("Did not find any modules for code system " + editionName);
            LOG.error("Did not find any edition-specific modules for code system: " + shortName + ". Will default to CORE modules");
            editionModules.addAll(CORE_MODULES);
        }

        EDITION_MODULES_FROM_DESCENDANTS_MAP.put(shortName, editionModules);

        return editionModules;
    }

    /**
     * Identify default language code.
     *
     * @param codeSystem the code system
     * @param editionName the edition name
     * @return the string
     * @throws Exception the exception
     */
    public String identifyDefaultLanguageCode(final JsonNode codeSystem, final String editionName) throws Exception {

        // Identify Edition's defaultLanguageCode - Per Kai, transform first language in set as defaultLangCode
        if (!codeSystem.has("languages")) {

            throw new Exception("All Code Systems must have lanaguages set filled in. " + editionName + " does not");
        }

        final Iterator<String> languages = codeSystem.get("languages").fieldNames();

        return languages.next();
    }

    /**
     * Identify default language refsets.
     *
     * @param codeSystem the code system
     * @param shortName the short name
     * @param branch the branch
     * @return the sets the
     * @throws Exception the exception
     */
    public Set<String> identifyEditionLanguageRefsets(final JsonNode codeSystem, final String shortName, final String branch) throws Exception {
        // TODO: Revert to sending everything through, not just the first item found, when implementing RT2-2080

        final Set<String> languageRefsets = new HashSet<>();

        // Identify Edition's Default Language Refsets
        if (codeSystem.has("defaultLanguageReferenceSets")) {

            final JsonNode defaultLanguageReferenceSets = codeSystem.get("defaultLanguageReferenceSets");

            final Iterator<JsonNode> defaultLanguageReferencesSetIterator = defaultLanguageReferenceSets.iterator();

            while (defaultLanguageReferencesSetIterator.hasNext()) {

                languageRefsets.add(defaultLanguageReferencesSetIterator.next().asText());
                languageRefsets.add(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US);

                return languageRefsets;
            }

        } else if (undefinedDefaultLanguageRefsets.containsKey(shortName)) {

            languageRefsets.add(undefinedDefaultLanguageRefsets.get(shortName).iterator().next());
            languageRefsets.add(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US);

            return languageRefsets;

            //            languageRefsets.addAll(undefinedDefaultLanguageRefsets.get(shortName));
        }

        if (languageRefsets.isEmpty()) {
            languageRefsets.add(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US);

            return languageRefsets;
        }
        // Ensure that DEFAULT_LANG_REFSET is always listed even if not explicitly listed
        languageRefsets.add(LanguageUtility.DEFAULT_LANGUAGE_REFSET_US);

        // Search for optional language refsets associated with the branch metadata
        // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches/MAIN%2FSNOMEDCT-BE?includeInheritedMetadata=false
        final String url = SnowstormConnection.getBaseUrl() + "branches/" + branch + "?includeInheritedMetadata=false";

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Failed to get branch information to obtain optional language refsets for edition's main branch");
            }

            final String resultString = SnowstormConnection.readEntityAsString(response);
            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode root = mapper.readTree(resultString);

            // get RefSets from CORE as long as active
            final JsonNode metadata = root.get("metadata");

            if (metadata.has("optionalLanguageRefsets")) {

                final Iterator<JsonNode> refsetIterator = metadata.get("optionalLanguageRefsets").iterator();

                while (refsetIterator.hasNext()) {

                    final JsonNode refset = refsetIterator.next();

                    if (!refset.has("refsetId")) {

                        LOG.error("Optional language refset must have a refsetId defined: " + refset);
                    } else {

                        LOG.info("Optional language refset: " + refset.get("refsetId").asText());
                        languageRefsets.add(refset.get("refsetId").asText());
                    }

                }

            }

        } catch (final Exception e) {

            throw new Exception("Failed to process the optional language refsets defined for this branch: " + branch);
        }

        return languageRefsets;
    }

    /**
     * Determine extended modules.
     *
     * @param branch the branch
     * @return the sets the
     * @throws Exception the exception
     */
    public static Set<String> determineExtendedModules(final String branch) throws Exception {

        final Set<String> extendedModules = new HashSet<>();

        // https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches/MAIN/SNOMEDCT-BE?includeInheritedMetadata=true
        final String url = SnowstormConnection.getBaseUrl() + "branches/" + branch + "?includeInheritedMetadata=true";

        LOG.info("branch merge necessitated status url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = SnowstormConnection.readEntityAsString(response);

            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode branchJsonRootNode = mapper.readTree(resultString);

            if (!branchJsonRootNode.has("metadata")) {
                throw new Exception("determineExtendedModules - Branch metadata doesn't exist: " + branch);
            }

            final JsonNode branchMetadata = branchJsonRootNode.get("metadata");

            if (branchMetadata.has("expectedExtensionModules")) {
                final JsonNode branchModules = branchMetadata.get("expectedExtensionModules");

                for (int i = 0; i < branchModules.size(); i++) {
                    extendedModules.add(branchModules.get(i).asText());
                }

            } else if (branchMetadata.has("defaultModuleId")) {
                final String branchModule = branchMetadata.get("defaultModuleId").asText();
                extendedModules.add(branchModule);
            }

            LOG.info("branch merge necessitated status result: " + extendedModules);

            return extendedModules;
        }
    }

    /**
     * Determine organization name.
     *
     * @param codeSystem the code system
     * @return the string
     * @throws Exception the exception
     */
    public String determineEditionShortName(final JsonNode codeSystem) throws Exception {

        // If owner defined, return it as organization name
        if (codeSystem.has("shortName") && !codeSystem.get("shortName").asText().trim().isBlank()) {

            return codeSystem.get("shortName").asText();
        }

        throw new Exception("Code system " + codeSystem + " doesn't have a shortName");
    }

    /**
     * Determine organization description.
     *
     * @param organizationName the organization name
     * @return the string
     */
    public String determineOrganizationDescription(final String organizationName) {

        if (organizationName.startsWith(SyncCodeSystemDeterminer.DEFAULT_ORGANIZATION_PREFACE)) {

            return "Organizational administrators can update this edition's default description via the Dashboard's Organization-Configuration page.";
        } else {
            return "Organizational administrators can update this edition's default name and default description via the Dashboard's Organization-Configuration page.";
        }
    }

    /**
     * Prints the edition values.
     *
     * @param service the service
     * @param edition the edition
     * @throws Exception the exception
     */
    public void printEditionValues(final TerminologyService service, final Edition edition) throws Exception {

        final List<Project> orgProjects = service.find("edition.id:" + edition.getId(), null, Project.class, null).getItems();
        final List<Team> teams = service.getAll(Team.class);

        for (final Project project : orgProjects) {

            for (final String teamId : project.getTeams()) {

                final Team team = teams.stream().filter(t -> t.getId().equals(teamId)).findFirst().orElse(null);

                if (team == null) {

                    throw new Exception("  Unable to locate team in project " + project.getName() + " for team: " + teamId);
                }

            }

        }

    }

    /**
     * Returns the iso date time format.
     *
     * @return the iso date time format
     */
    public static String getIsoDateTimeFormat() {

        return METADATA.ISO_DATE_TIME_FORMAT;
    }

    /**
     * Returns the property reader.
     *
     * @return the property reader
     */
    public SyncPropertyFileReader getPropertyReader() {

        return PROPERTY_READER;
    }

    /**
     * Returns the edition modules map.
     *
     * @return the edition modules map
     */
    public Map<String, Set<String>> getEditionModulesMap() {

        return EDITION_MODULES_FROM_DESCENDANTS_MAP;
    }

    /**
     * Returns the sync results.
     *
     * @param service the service
     * @return the sync results
     * @throws Exception the exception
     */
    public String getSyncResults(final TerminologyService service) throws Exception {

        final ClassPathResource syncTestQueries = new ClassPathResource("sync/syncTestQueries.sql");

        final List<String> sqlQueries = new ArrayList<>();

        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(syncTestQueries.getInputStream()));) {

            String line = reader.readLine();

            while (line != null) {

                if (StringUtils.isNoneBlank(line)) {

                    sqlQueries.add(line);
                }

                line = reader.readLine();
            }

        } catch (final IOException e) {

            e.printStackTrace();
        }

        final StringBuilder result = new StringBuilder();

        // Collect results

        for (final String query : sqlQueries) {

            if (query == null || query.contains("--") || !query.contains("select ")) {

                continue;
            }

            @SuppressWarnings("unchecked")
            final List<Object[]> rows = service.getEntityManager().createNativeQuery(query).getResultList();
            result.append(query).append("\r\n");

            if (rows == null) {

                result.append("\r\n");
                continue;
            }

            for (final Object[] row : rows) {

                for (final Object field : row) {

                    result.append(field).append("|");
                }

                result.append("\r\n");
            }

        }

        LOG.info("DONE POST SYNC DATA QUERIES");

        return result.toString();
    }

    /**
     * Email sync statistics.
     *
     * @param service the service
     * @throws Exception the exception
     */
    public void emailSyncStatistics(final TerminologyService service) throws Exception {

        final String results = getSyncResults(service);
        emailResults(service, results);

    }

    public void emailResults(final TerminologyService service, final String results) throws Exception {

        if (!PropertyUtility.getProperties().containsKey("refset.service.env")) {
            return;
        }

        final String serviceEnv = PropertyUtility.getProperties().getProperty("refset.service.env");
        if ("local".equalsIgnoreCase(serviceEnv)) {
            return;
        }
        try {

            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            final String fileName = String.format(System.getProperty("java.io.tmpdir") + FileSystems.getDefault().getSeparator() + "refset-sync-results-%s.txt",
                dateFormat.format(new Date()));
            final Path path = Paths.get(fileName);
            final byte[] queryResultsToBytes = results.getBytes();

            Files.write(path, queryResultsToBytes);
        } catch (final IOException e) {

            LOG.error("Error occured writing post sync report to file", e);
        }

        RefsetService.clearAllRefsetCaches(null);
        RefsetMemberService.clearAllMemberCaches(null);

        final String emailReceipients = PropertyUtility.getProperties().getProperty("mail.smtp.postsync.report.to");

        if (StringUtils.isNotBlank(emailReceipients)) {

            EmailUtility.sendEmail("RT2 " + serviceEnv + " Post Sync Report", emailReceipients, results);
        }

        LOG.info("Completed Syncing with Snowstorm");

    }

    /**
     * Indicates whether or not international edition is the case.
     *
     * @param matchingString the matching string
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    public boolean isInternationalEdition(final String matchingString) {

        return "international edition".equals(matchingString.toLowerCase()) || "snomedct".equals(matchingString.toLowerCase());
    }

    /**
     * Clear previous run.
     */
    public void clearPreviousRun() {

        EDITION_MODULES_FROM_DESCENDANTS_MAP.clear();
        CORE_MODULES.clear();
        CORE_REFSETS.clear();
        undefinedDefaultLanguageRefsets = PROPERTY_READER.readUndefinedDefaultLanguageRefsets();
    }

    /**
     * Validate matches.
     *
     * @param stream the stream
     * @param matchingValueDescription the matching value description
     * @return the object
     * @throws Exception the exception
     */
    public Object validateMatches(final Stream<?> stream, final String matchingValueDescription) throws Exception {

        final List<?> items = stream.collect(Collectors.toList());

        if (items.size() == 1) {

            return items.get(0);
        }

        if (items.isEmpty()) {

            throw new Exception("Cannot find an element to matching value: " + matchingValueDescription);
        } else {

            throw new Exception("Found multiple elements with same matching value: " + matchingValueDescription + " has items:  " + items);
        }

    }

    /**
     * Identify maintainer type.
     *
     * @param codeSystem the code system
     * @param editionShortName the edition short name
     * @return the string
     * @throws Exception the exception
     */
    public String identifyMaintainerType(final JsonNode codeSystem, final String editionShortName) throws Exception {

        String codeSystemType = codeSystem.has("maintainerType") ? codeSystem.get("maintainerType").asText() : "";

        // SNOMED Core Edition are blank in Snowstorm, but we treat them identically to the Managed Service maintainerType
        if (codeSystemType.isBlank()) {

            if (isInternationalEdition(editionShortName)) {

                codeSystemType = "Managed Service";

            } else {

                LOG.info("{} edition is missing a maintainerType {}", codeSystem, editionShortName);
            }

        }

        return codeSystemType;
    }

    /**
     * Initialize workflow status.
     *
     * @param service the service
     * @param refset the refset
     * @return the refset
     * @throws Exception the exception
     */
    public Refset initializeWorkflowStatus(final TerminologyService service, final Refset refset) throws Exception {

        final WorkflowStatus currentStatus = refset.getWorkflowStatus();

        try {

            // if the status is Published then create a new version of the refset that is ready to be edited
            final Refset updatedRefset =
                RefsetWorkflowService.setWorkflowStatusByAction(service, SecurityService.getUserFromSession(), WorkflowAction.FINISH_EDIT, refset, "");

            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(updatedRefset.getWorkflowStatus())) {

                return updatedRefset;
            } else {

                return null;
            }

        } catch (final Exception e) {

            LOG.error("Failed to initialize workflow on developer refset: " + refset + " with Exception --> " + e.getMessage());

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Returns the processing minutes.
     *
     * @param operationType the operation type
     * @param startTime the start time
     * @return the processing minutes
     */
    public long getProcessingMinutes(final String operationType, final Date startTime) {

        final Date end = new Date();
        final SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss");

        final long differenceInMinutes = ((end.getTime() - startTime.getTime()) / (1000 * 60)) % 60;

        LOG.info("start date: {}", sdf.format(new Date(startTime.getTime())));
        LOG.info("end date: {}", sdf.format(new Date(end.getTime())));
        LOG.info("Operation took " + differenceInMinutes + " minutes to run");

        return differenceInMinutes;

    }

    /**
     * Creates the admin organization team.
     *
     * @param service the service
     * @param organization the organization
     * @return the team
     * @throws Exception the exception
     */
    public Team getOrCreateAdminOrganizationTeam(final TerminologyService service, final Organization organization) throws Exception {

        if (!ORGANIZATION_TO_ADMIN_TEAM_CACHE.containsKey(organization.getId())) {
            try {
                Team adminTeam = OrganizationService.getActiveOrganizationAdminTeam(service, organization.getId());

                if (adminTeam != null) {

                    LOG.info("Using existing team '{}' ({}) for {}", adminTeam.getName(), adminTeam.getId(), organization.getName());
                    return adminTeam;
                }

                final Team inactiveAdminTeam = OrganizationService.getOrganizationAdminTeam(service, organization.getId());

                if (inactiveAdminTeam == null) {

                    adminTeam = dbHandler.addTeam(service, TeamService.generateOrganizationTeamName(organization),
                        TeamService.getOrganizationTeamDescription(organization), organization, TeamType.ORGANIZATION.getText());

                } else if (!inactiveAdminTeam.isActive()) {

                    inactiveAdminTeam.setActive(true);
                    adminTeam = service.update(inactiveAdminTeam);
                } else {

                    throw new Exception("Odd state for existing admin team: " + inactiveAdminTeam);
                }

                for (final UserRole role : UserRole.getAllRoles()) {

                    adminTeam = TeamService.addRoleToTeam(service, SecurityService.getUserFromSession(), adminTeam, UserRole.getRoleString(role).toUpperCase());
                }

                ORGANIZATION_TO_ADMIN_TEAM_CACHE.put(organization.getId(), adminTeam);

            } catch (final Exception e) {
                LOG.error("Failed to create admin team with Exception --> " + e.getMessage());

                return null;
            }
        }

        return ORGANIZATION_TO_ADMIN_TEAM_CACHE.get(organization.getId());
    }
}
