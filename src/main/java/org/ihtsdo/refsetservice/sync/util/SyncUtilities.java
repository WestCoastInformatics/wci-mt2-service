package org.ihtsdo.refsetservice.sync.util;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.SyncOperationsInitializer;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.EmailUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

public class SyncUtilities {

    private final Logger logger = LoggerFactory.getLogger(SyncUtilities.class);

    private static final SyncPropertyFileReader propertyReader = new SyncPropertyFileReader();

    private static final Map<String, Set<String>> undefinedDefaultLanguageRefsets = propertyReader.readUndefinedDefaultLanguageRefsets();

    protected static final Set<String> internationalModules = new HashSet<>();

    protected static final String DEVELOPER_ORGANIZATION_NAME_KEYWORD = "wci";

    // Edition shortName to Set<Module SctIds>
    private static final Map<String, Set<String>> editionModulesMap = new HashMap<>();

    public static final String FEEDBACK_TESTING_USER_NAME = "FeedbackTesting";

    public static final String SYNC_USER_NAME = "Snowstorm Sync";

    private static final String UNDEFINED_USER_NAME = "Undefined";

    private static SyncPersistenceMetadata metadata = new SyncPersistenceMetadata(new Date(), UNDEFINED_USER_NAME);

    private static final SyncStatistics statistics = new SyncStatistics();

    private static final String DEFAULT_LANGUAGE_REFSET = "900000000000509007";

    private static final String DEFAULT_WCI_REFSET_PARENT_CONCEPT = "446609009"; // Simple Type Refset Concept

    public Organization addOrganziation(final String orgName, String orgDesc, String orgMaintainerType) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);
            org.setCodeSystemType(orgMaintainerType);

            // Persist
            final Organization o = service.add(org);
            statistics.incrementOrganizationsAdded();

            logger.info("Adding new Organziation: " + o.getId() + " (" + o.getName() + ") " + o);

            return o;
        }

    }

    public Edition addEdition(String shortName, String editionName, String editionBranch, final Organization organization, JsonNode codeSystem) throws Exception {

        final String defaultLanguageCode = identifyDefaultLanguageCode(codeSystem, editionName);

        final Set<String> defaultLanguageRefsets = identifyDefaultLanguageRefsets(codeSystem, shortName);

        // Case of no modules handled downstream
        final Set<String> editionModules = identifyModules(shortName, editionName, editionBranch, codeSystem);

        Edition newEdition = addEdition(shortName, editionName, editionBranch, defaultLanguageRefsets, editionModules, defaultLanguageCode, organization);

        return newEdition;
    }

    private Edition addEdition(String shortName, String name, String branch, Set<String> defaultLanguageRefsets, Set<String> modules, String defaultLanguageCode, Organization organization)
        throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Edition edition = new Edition();

            edition.setShortName(shortName);
            edition.setName(name);
            edition.setBranch(branch);
            edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
            edition.setModules(modules);
            edition.setDefaultLanguageCode(defaultLanguageCode);
            edition.setOrganization(organization);

            // New ones only created as new
            edition.setActive(true);

            Edition e = service.add(edition);

            logger.info("Adding new Edition: " + e.getId() + " (" + e.getName() + ")" + e);

            return e;
        }

    }

    public void removeEdition(Edition edition) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            logger.info("Removing existing RT2 Edition: " + edition.getId() + " (" + edition.getName() + ")" + edition);

            service.remove(edition);

        }
    }

    public void removeRefsetVersionPair(Refset refset) throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            logger.info("Removing existing refset: " + refset.getId() + " (" + refset.getRefsetId() + ")" + refset.getVersionDate());

            service.remove(refset);

        }
    }

    public Refset addRefset(String name, String refsetId, String moduleId, Date versionDate, String type, String narrative) throws Exception {
        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Refset refset = new Refset();

            refset.setName(name);
            refset.setRefsetId(refsetId);
            refset.setModuleId(moduleId);
            refset.setVersionStatus("PUBLISHED");
            refset.setWorkflowStatus("PUBLISHED");
            refset.setActive(true);
            refset.setVersionDate(versionDate);
            refset.setType(type);
            refset.setNarrative(narrative);
            refset.setLatestPublishedVersion(false);

            // Persist
            final Refset r = service.add(refset);

            logger.info("Adding new Refset and/or Version for : " + r.getId() + " (" + r.getName() + ") on: " + r.getVersionDate());

            return r;
        }

    }

    public Project addProject(String projectName, String projectDescription, Edition edition) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));
            project.setEdition(edition);

            // Persist
            final Project p = service.add(project);

            statistics.getProjectsProcessed().add(p);

            logger.info("Adding new Project: " + p.getId() + " (" + p.getName() + ") " + p);

            return p;

        }

    }

    public Refset addWCIRefset(User u, String name, String refsetId, String moduleId, Date versionDate, String narrative, Project project) throws Exception {

        logger.info("Adding WCI Testing Org's single project: " + project);

        final Refset refsetParameters = new Refset();

        refsetParameters.setName(name);
        refsetParameters.setRefsetId(refsetId);
        refsetParameters.setModuleId(moduleId);
        refsetParameters.setVersionStatus("PUBLISHED");
        refsetParameters.setWorkflowStatus("PUBLISHED");
        refsetParameters.setActive(true);
        refsetParameters.setVersionDate(versionDate);
        refsetParameters.setVersionNotes("");
        refsetParameters.setType(Refset.EXTENSIONAL);
        refsetParameters.setNarrative(narrative);
        refsetParameters.setParentConceptId(DEFAULT_WCI_REFSET_PARENT_CONCEPT);
        refsetParameters.setProject(project);
        refsetParameters.setLatestPublishedVersion(false);

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // Sets up completely different than normal addRefset routine
            final Object returned = RefsetService.createRefset(service, u, refsetParameters);

            if (returned instanceof String) {

                throw new Exception((String) returned);
            } else {

                final Refset refset = (Refset) returned;

                logger.info("Added new WCI Refset - " + refset.getId() + " (" + refset.getName() + ")" + refset);

                Refset updatedRefset = initializeWorkflowStatus(refset);

                logger.info(" and then updated the new WCI refset's Workflow Status - " + updatedRefset);

                return updatedRefset;
            }

        }

    }

    public Team addTeam(String teamName, String teamDescription, Organization organization, Set<String> roles, Set<String> memberIds) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Team team = new Team();
            team.setName(teamName);
            team.setDescription(teamDescription);
            team.setOrganization(organization);
            team.setPrimaryContactEmail("support-rt2@westcoastinformatics.com");
            team.setRoles(roles);
            team.setMembers(memberIds);

            // Persist
            final Team t = service.add(team);

            statistics.getTeamsProcessed().add(t);

            logger.info("Adding new Team: " + t.getId() + " (" + t.getName() + ") " + t);

            return t;
        }

    }

    public User addUser(String name, String userName, String email, Set<String> roles) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final User user = new User();

            user.setName(name);
            user.setUserName(userName);
            user.setActive(true);
            user.setEmail(email);
            user.setRoles(roles);

            // Persist
            final User u = service.add(user);

            logger.info("Adding new User: " + u.getId() + " (" + u.getName() + ") " + u);

            return u;
        }

    }

    public Set<DefinitionClause> getRefsetClauses(String rttId) throws Exception {

        Set<DefinitionClause> refsetClauses = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            for (String clauseJson : propertyReader.getRttRefsetToClausesMap().get(rttId)) {

                final DefinitionClause clause = ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                setMetadata(clause, propertyReader.getMetadataMap().get("refset-" + rttId));

                DefinitionClause persistedClause = service.add(clause);

                refsetClauses.add(persistedClause);
            }

            return refsetClauses;
        }

    }

    public User getUser(String name, String userName, String email, Set<String> roles) throws Exception {

        User user = null;

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            final QueryParameter query = new QueryParameter();
            query.setQuery("userName:" + userName + " AND active:true");

            ResultList<User> results = service.find(query, pfs, User.class, null);

            if (results.getItems() != null && results.getItems().size() == 1) {

                // User already exists
                user = results.getItems().iterator().next();
            } else {

                // Need to create user
                user = addUser(name, userName, email, roles);
            }

        }

        return user;

    }

    public Set<String> identifyModules(String shortName, String editionName, String editionBranch, JsonNode codeSystem) throws Exception {

        Set<String> editionModules = new HashSet<>();

        if (isInternationalEdition(editionName)) {

            Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            while (moduleIterator.hasNext()) {

                JsonNode module = moduleIterator.next();

                if (module.get("active").asBoolean()) {

                    internationalModules.add(module.get("conceptId").asText());
                    editionModules.add(module.get("conceptId").asText());
                }

            }

        } else {

            Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            // Ignore CORE Modules
            while (moduleIterator.hasNext()) {

                JsonNode module = moduleIterator.next();

                if (module.get("active").asBoolean() && !internationalModules.contains(module.get("conceptId").asText())) {

                    editionModules.add(module.get("conceptId").asText());

                }

            }

            if (editionModules.isEmpty()) {

                if (isDeveloperEdition(editionName)) {

                    editionModules.addAll(internationalModules);
                } else {

                    // All non-core code systems must have a non-core module.
                    throw new Exception("Did not find any modules for code system " + editionName);

                }

            }

        }

        editionModulesMap.put(shortName, editionModules);

        return editionModules;

    }

    public String identifyDefaultLanguageCode(JsonNode codeSystem, String editionName) throws Exception {

        // Identify Edition's defaultLanguageCode - Per Kai, transform first language in set as defaultLangCode
        if (!codeSystem.has("languages")) {

            throw new Exception("All Code Systems must have lanaguages set filled in. " + editionName + " does not");
        }

        Iterator<String> languages = codeSystem.get("languages").fieldNames();

        return languages.next();
    }

    public Set<String> identifyDefaultLanguageRefsets(JsonNode codeSystem, String shortName) {

        Set<String> retSet = new HashSet<>();

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

        return retSet;
    }

    void setMetadata(final HasModified object) {

        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }

    private void setMetadata(final HasModified object, final SyncPersistenceMetadata metadata) {

        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }

    private Refset initializeWorkflowStatus(Refset refset) throws Exception {

        final String currentStatus = refset.getWorkflowStatus();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // if the status is Published then create a new version of the refset that is ready to be edited
            refset = WorkflowService.setWorkflowStatusByAction(service, SyncOperationsInitializer.getSyncUser(), WorkflowService.FINISH_EDIT, refset, "");

            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(refset.getWorkflowStatus())) {

                return refset;
            } else {

                return null;
            }

        }

    }

    public void printEditionValues(Edition edition) throws Exception {

        try (TerminologyService service = new TerminologyService()) {

            final List<Project> orgProjects = service.find("edition.id:" + edition.getId(), null, Project.class, null).getItems();
            final List<Team> teams = service.getAll(Team.class);

            for (Project project : orgProjects) {

                for (String teamId : project.getTeams()) {

                    Team team = teams.stream().filter(t -> t.getId().equals(teamId)).findFirst().orElse(null);

                    if (team == null) {

                        throw new Exception("  Unable to locate team in project " + project.getName() + " for team: " + teamId);
                    }

                }

            }

        }

    }

    public void emailImportResults(String queryResults) throws Exception {

        try {
            final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
            final String fileName = String.format(System.getProperty("java.io.tmpdir") + FileSystems.getDefault().getSeparator() + "refset-sync-results-%s.txt", dateFormat.format(new Date()));
            final Path path = Paths.get(fileName);
            byte[] queryResultsToBytes = queryResults.getBytes();

            Files.write(path, queryResultsToBytes);
        } catch (IOException e) {
            logger.error("Error occured writing post sync report to file", e);
        }

        RefsetService.clearAllRefsetCaches(null);
        RefsetMemberService.clearAllMemberCaches(null);

        final String emailReceipients = PropertyUtility.getProperties().getProperty("mail.smtp.postsync.report.to");

        if (StringUtils.isNotBlank(emailReceipients)) {
            EmailUtility.sendEmail("RT2 Post Sync Report", null, emailReceipients, queryResults);
        }

        logger.info("Completed Syncing with Snowstorm");
    }

    public void initializeService(TerminologyService service) {

        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

    }

    public SimpleDateFormat getSdf() {

        return metadata.getSdf();
    }

    public SyncPropertyFileReader getPropertyReader() {

        return propertyReader;
    }

    public Set<String> getInternationalModules() {

        return internationalModules;
    }

    public Map<String, Set<String>> getEditionModulesMap() {

        return editionModulesMap;
    }

    public void parseRttData() throws Exception {

        // Identify all refset metadata, any refsets' ECL definitions, and project metadata from RTT files manually sync'd over
        // TODO: #1: Add a automated pull of the data off of RTT?
        // TODO: #2: Move this to a similar like SyncRttAgent class
        getPropertyReader().parseRttData();

    }

    public boolean isInternationalEdition(String matchingString) {

        return "international edition".equals(matchingString.toLowerCase()) || "snomedct".equals(matchingString.toLowerCase());
    }

    public boolean isDeveloperEdition(String editionName) {

        return editionName.toLowerCase().contains(DEVELOPER_ORGANIZATION_NAME_KEYWORD.toLowerCase());
    }
}
