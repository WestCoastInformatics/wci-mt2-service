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
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.EmailUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
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
    private SyncDatabaseHandler dbHandler;

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

    /** The Constant EDITION_MODULES_MAP. */
    // Edition shortName to Set<Module SctIds>
    private static final Map<String, Set<String>> EDITION_MODULES_MAP = new HashMap<>();

    /** The Constant FEEDBACK_TESTING_USER_NAME. */
    public static final String FEEDBACK_TESTING_USER_NAME = "FeedbackTesting";

    /** The Constant SYNC_USER_NAME. */
    public static final String SYNC_USER_NAME = "Snowstorm Sync";

    /** The Constant UNDEFINED_USER_NAME. */
    private static final String UNDEFINED_USER_NAME = "Undefined";

    /** The Constant METADATA. */
    private static final SyncPersistenceMetadata METADATA = new SyncPersistenceMetadata(new Date(), UNDEFINED_USER_NAME);

    /** The Constant DEFAULT_LANGUAGE_REFSET. */
    private static final String DEFAULT_LANGUAGE_REFSET = "900000000000509007";

    /** The Constant CORE_MODULE_PARENT. */
    private static final String CORE_MODULE_PARENT = "900000000000443000";

    /** The Constant SIMPLE_REFSET_TYPE_CONCEPT. */
    static final String SIMPLE_REFSET_TYPE_CONCEPT = "446609009";

    /** The Constant DEFAULT_ORGANIZATION_PREFACE. */
    private static final String DEFAULT_ORGANIZATION_PREFACE = "Owner of ";

    private static String developerTestingEditionShortName = null;

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
    public User getUser(final TerminologyService service, final String name, final String userName, final String email, final Set<String> roles) throws Exception {

        User user = getUser(service, userName);

        if (user == null) {

            user = dbHandler.addUser(service, name, userName, email);
        }

        return user;

    }

    /**
     * Returns the core refsets.
     *
     * @return the core refsets
     * @throws Exception the exception
     */
    public Set<String> getCoreRefsets() throws Exception {

        if (CORE_REFSETS != null && !CORE_REFSETS.isEmpty()) {

            return CORE_REFSETS;
        }

        // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/MAIN/concepts/446609009/descendants?stated=false&offset=0&limit=50
        final String url = SnowstormConnection.getBaseUrl() + "MAIN/concepts/" + SIMPLE_REFSET_TYPE_CONCEPT + "/descendants?stated=false&offset=0&limit=50";

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Failed calling concept-descendents on Simple Refset Concept in SI-CORE (to identify international refsets)");
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            // get RefSets from CORE as long as active
            final Iterator<JsonNode> refsetIterator = root.get("items").iterator();

            while (refsetIterator.hasNext()) {

                final JsonNode refset = refsetIterator.next();

                if (!refset.has("conceptId")) {

                    LOG.error("Refset must have conceptId: " + refset);
                } else {

                    LOG.info("Core Refset: " + refset.get("conceptId").asText());
                    CORE_REFSETS.add(refset.get("conceptId").asText());
                }

            }

        } catch (Exception e) {

            throw new Exception("Failed finding descendents on Simple Refset Concept in SI-CORE to identify international refsets");
        }

        return CORE_REFSETS;
    }

    /**
     * Returns the core modules.
     *
     * @return the core modules
     * @throws Exception the exception
     */
    public Set<String> getCoreModules() throws Exception {

        if (CORE_MODULES != null && !CORE_MODULES.isEmpty()) {

            return CORE_MODULES;
        }

        // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/MAIN/concepts/900000000000443000/descendants?stated=false&offset=0&limit=50
        final String url = SnowstormConnection.getBaseUrl() + "MAIN/concepts/" + CORE_MODULE_PARENT + "/descendants?stated=false&offset=0&limit=50";

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Failed calling concept-descendents on CORE MModule Parent in MAIN (to identify international modules)");
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            // get RefSets from edition as long as a) active & b) within edition's module
            final Iterator<JsonNode> moduleIterator = root.get("items").iterator();

            while (moduleIterator.hasNext()) {

                final JsonNode module = moduleIterator.next();

                if (!module.has("conceptId")) {

                    LOG.error("Module must have conceptId: " + module);
                } else {

                    CORE_MODULES.add(module.get("conceptId").asText());
                }

            }

        } catch (final Exception e) {

            throw new Exception("Failed finding descendents of CORE MModule Parent in MAIN to identify international modules");
        }

        return CORE_MODULES;
    }

    /**
     * Identify modules.
     *
     * @param shortName the short name
     * @param editionName the edition name
     * @param editionBranch the edition branch
     * @param codeSystem the code system
     * @return the sets the
     * @throws Exception the exception
     */
    public Set<String> identifyModules(final String shortName, final String editionName, final String editionBranch, final JsonNode codeSystem) throws Exception {

        final Set<String> editionModules = new HashSet<>();

        if (isInternationalEdition(editionName)) {

            final Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            while (moduleIterator.hasNext()) {

                final JsonNode module = moduleIterator.next();

                if (module.get("active").asBoolean()) {

                    getCoreModules().add(module.get("conceptId").asText());
                    editionModules.add(module.get("conceptId").asText());
                }

            }

        } else {

            final Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            // Ignore CORE Modules
            while (moduleIterator.hasNext()) {

                final JsonNode module = moduleIterator.next();

                if (module.get("active").asBoolean() && !getCoreModules().contains(module.get("conceptId").asText())) {

                    editionModules.add(module.get("conceptId").asText());

                }

            }

            if (editionModules.isEmpty()) {

                if (!isDeveloperEdition(editionName)) {

                    // All non-core code systems must have a non-core module.
                    // throw new Exception("Did not find any modules for code system " + editionName);
                    LOG.error("Did not find any edition-specific modules for code system: " + editionName + ". Will default to CORE modules");
                }

                editionModules.addAll(getCoreModules());
            }

        }

        EDITION_MODULES_MAP.put(shortName, editionModules);

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
     */
    public Set<String> identifyDefaultLanguageRefsets(final JsonNode codeSystem, final String shortName, String branch) throws Exception {

        final Set<String> retSet = new HashSet<>();

        // Identify Edition's Default Language Refsets
        if (codeSystem.has("defaultLanguageReferenceSets")) {

            final JsonNode defaultLanguageReferenceSets = codeSystem.get("defaultLanguageReferenceSets");

            final Iterator<JsonNode> defaultLanguageReferencesSetIterator = defaultLanguageReferenceSets.iterator();

            while (defaultLanguageReferencesSetIterator.hasNext()) {

                retSet.add(defaultLanguageReferencesSetIterator.next().asText());
            }

        } else if (undefinedDefaultLanguageRefsets.containsKey(shortName)) {

            retSet.addAll(undefinedDefaultLanguageRefsets.get(shortName));
        }

        // Ensure that DEFAULT_LANG_REFSET is always listed even if not explicitly listed
        retSet.add(DEFAULT_LANGUAGE_REFSET);

        // Search for optional language refsets associated with the branch metadata
        // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches/MAIN%2FSNOMEDCT-BE?includeInheritedMetadata=false
        final String url = SnowstormConnection.getBaseUrl() + "branches/" + branch + "?includeInheritedMetadata=false";

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Failed to get branch information to obtain optional language refsets for edition's main branch");
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

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
                        retSet.add(refset.get("refsetId").asText());
                    }

                }

            }

        } catch (Exception e) {

            throw new Exception("Failed to process the optional language refsets defined for this branch: " + branch);
        }

        return retSet;
    }
    
    /**
     * Determine organization name.
     *
     * @param codeSystem the code system
     * @return the string
     * @throws Exception 
     */
    public String determineEditionShortName(final JsonNode codeSystem) throws Exception {

        // If owner defined, return it as organization name
        if (codeSystem.has("shortName") && !codeSystem.get("shortName").asText().trim().isBlank()) {

            return codeSystem.get("shortName").asText();
        }

        throw new Exception("Code system " + codeSystem + " doesn't have a shortName");
    }

    /**
     * Determine organization name.
     *
     * @param codeSystem the code system
     * @return the string
     */
    public String determineOrganizationName(final JsonNode codeSystem) {

        // If owner defined, return it as organization name
        if (codeSystem.has("owner") && !codeSystem.get("owner").asText().trim().isBlank()) {

            return codeSystem.get("owner").asText();
        }

        // Create generic organization name
        final String editionName = codeSystem.has("name") ? codeSystem.get("name").asText() : "";
        return DEFAULT_ORGANIZATION_PREFACE + editionName;
    }

    /**
     * Determine organization description.
     *
     * @param organizationName the organization name
     * @return the string
     */
    public String determineOrganizationDescription(final String organizationName) {

        if (organizationName.startsWith(DEFAULT_ORGANIZATION_PREFACE)) {

            return "Organizational administrators can update this edition's default description.";
        } else {

            return "Two things to change." + System.lineSeparator()
                + "1) Your organization name isn't defined on Snowstorm yet, so we have provided you with a temporary one that matches your edition name." + System.lineSeparator()
                + "Have your organization's administrator(s) contact SNOMED International to have it changed." + System.lineSeparator()
                + "2) Organizational administrator(s) can update this default description at any time";
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

        return EDITION_MODULES_MAP;
    }

    /**
     * Returns the sync results.
     *
     * @param service the service
     * @return the sync results
     * @throws Exception the exception
     */
    private String getSyncResults(final TerminologyService service) throws Exception {

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

        } catch (IOException e) {

            e.printStackTrace();
        }

        final StringBuilder result = new StringBuilder();

        // Collect results

        for (final String query : sqlQueries) {

            if (query != null && !query.contains("--") && query.contains("select ")) {

                @SuppressWarnings("unchecked")
                final List<Object[]> rows = service.getEntityManager().createNativeQuery(query).getResultList();
                result.append(query).append("\r\n");

                if (rows != null) {

                    for (final Object[] row : rows) {

                        for (final Object field : row) {

                            result.append(field).append("|");
                        }

                        result.append("\r\n");
                    }

                }

                result.append("\r\n");
            }

        }

        LOG.info("DONE POST SYNC DATA QUERIES");

        return result.toString();
    }

    /**
     * Email sync results.
     *
     * @param service the service
     * @throws Exception the exception
     */
    public void emailSyncResults(final TerminologyService service) throws Exception {

        final String results = getSyncResults(service);

        try {

            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            final String fileName = String.format(System.getProperty("java.io.tmpdir") + FileSystems.getDefault().getSeparator() + "refset-sync-results-%s.txt", dateFormat.format(new Date()));
            final Path path = Paths.get(fileName);
            final byte[] queryResultsToBytes = results.getBytes();

            Files.write(path, queryResultsToBytes);
        } catch (IOException e) {

            LOG.error("Error occured writing post sync report to file", e);
        }

        RefsetService.clearAllRefsetCaches(null);
        RefsetMemberService.clearAllMemberCaches(null);

        final String emailReceipients = PropertyUtility.getProperties().getProperty("mail.smtp.postsync.report.to");

        if (StringUtils.isNotBlank(emailReceipients)) {

            EmailUtility.sendEmail("RT2 Post Sync Report", null, emailReceipients, results);
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
     * Indicates whether or not developer edition is the case.
     *
     * @param editionName the edition name
     * @return <code>true</code> if so, <code>false</code> otherwise
     */
    // In WCI case, accepts either name or shortName
    public boolean isDeveloperEdition(final String editionName) {

        return editionName.toLowerCase().contains(DEVELOPER_ORGANIZATION_NAME_KEYWORD.toLowerCase());
    }

    /**
     * Clear previous run.
     */
    public void clearPreviousRun() {

        EDITION_MODULES_MAP.clear();
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

        if (!isDeveloperEdition(refset.getEdition().getShortName())) {

            throw new Exception("Cannot modify the workflow status of anything other than the developer org");
        }

        final String currentStatus = refset.getWorkflowStatus();

        try {

            // if the status is Published then create a new version of the refset that is ready to be edited
            final Refset updatedRefset = WorkflowService.setWorkflowStatusByAction(service, SecurityService.getUserFromSession(), WorkflowService.FINISH_EDIT, refset, "");

            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(updatedRefset.getWorkflowStatus())) {

                return updatedRefset;
            } else {

                return null;
            }

        } catch (Exception e) {

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
}
