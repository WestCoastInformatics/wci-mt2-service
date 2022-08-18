package org.ihtsdo.refsetservice.sync;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;

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
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SyncAgentUtilities {

    private final Logger logger = LoggerFactory.getLogger(SyncAgentUtilities.class);

    private static final SyncPropertyFileReader propertyReader = new SyncPropertyFileReader();

    private static final Map<String, Set<String>> undefinedDefaultLanguageRefsets = propertyReader.readUndefinedDefaultLanguageRefsets();

    static final Set<String> internationalModules = new HashSet<>();

    private static final Map<String, Set<String>> editionModulesMap = new HashMap<>();

    public static final String FEEDBACK_TESTING_USER_NAME = "FeedbackTesting";

    public static final String SYNC_USER_NAME = "Snowstorm Sync";

    private static final String UNDEFINED_USER_NAME = "Undefined";

    private static SyncPersistenceMetadata metadata = new SyncPersistenceMetadata(new Date(), UNDEFINED_USER_NAME);

    private static final SyncStatistics statistics = new SyncStatistics();

    private static final String DEFAULT_SNOMED_CORE_MODULE = "900000000000012004";

    private static final String ANCESTOR_MODULE = "900000000000443000";

    private static final String DEFAULT_LANGUAGE_REFSET = "900000000000509007";

    private static final String DEFAULT_WCI_REFSET_PARENT_CONCEPT = "446609009"; // Simple Type Refset Concept

    Organization addOrganziation(final String orgName, String orgDesc, final Edition edition) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Organization org = new Organization();
            org.setName(orgName);
            org.setDescription(orgDesc);

            // Persist
            final Organization o = service.add(org);

            statistics.getOrganizationsProcessed().add(o);

            logger.info("Adding new Organziation: " + o.getId() + " (" + o.getName() + ") " + o);

            return o;
        }

    }

    Edition addEdition(String shortName, String editionName, String editionBranch, final Organization organization, JsonNode codeSystem) throws Exception {

        final String defaultLanguageCode = identifyDefaultLanguageCode(codeSystem, editionName);

        final Set<String> defaultLanguageRefsets = identifyDefaultLanguageRefsets(codeSystem, shortName);

        // Case of no modules handled downstream
        final String editionTopLevelModule = codeSystem.has("modules") ? identifyTopLevelModule(shortName, editionName, editionBranch, codeSystem) : DEFAULT_SNOMED_CORE_MODULE;

        Edition newEdition = addEdition(shortName, editionName, editionBranch, defaultLanguageRefsets, editionTopLevelModule, defaultLanguageCode, organization);

        return newEdition;
    }

    private Edition addEdition(String shortName, String name, String branch, Set<String> defaultLanguageRefsets, String topLevelModule, String defaultLanguageCode, Organization organization)
        throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            Edition edition = new Edition();

            edition.setShortName(shortName);
            edition.setName(name);
            edition.setBranch(branch);
            edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
            edition.setTopLevelModule(topLevelModule);
            edition.setDefaultLanguageCode(defaultLanguageCode);
            edition.setOrganization(organization);

            // New ones only created as new
            edition.setActive(true);

            Edition e = service.add(edition);

            statistics.getEditionsProcessed().add(e);

            logger.info("Adding new Edition: " + e.getId() + " (" + e.getName() + ")" + e);

            return e;
        }

    }

    Refset addRefset(String name, String refsetId, String moduleId, Date versionDate, String type, String narrative, Project project) throws Exception {

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
            refset.setProject(project);
            refset.setLatestPublishedVersion(false);

            // Persist
            final Refset r = service.add(refset);

            statistics.getRefsetVersionsProcessed().add(r);

            logger.info("Adding new Refset and/or Version for : " + r.getId() + " (" + r.getName() + ") on: " + r.getVersionDate());

            return r;
        }

    }

    Project addProject(Edition edition, String projectName, String projectDescription) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            final Project project = new Project();
            project.setName(projectName);
            project.setDescription(projectDescription);
            project.setEdition(edition);
            project.setPrivateProject(false);
            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(projectName));

            // Persist
            final Project p = service.add(project);

            statistics.getProjectsProcessed().add(p);

            logger.info("Adding new Project: " + p.getId() + " (" + p.getName() + ") " + p);

            return p;

        }

    }

    Refset addWCIRefset(User u, String name, String refsetId, String moduleId, Date versionDate, String narrative, Project project) throws Exception {

        logger.debug("Adding WCI Testing Org's single project: " + project);

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

                statistics.getRefsetVersionsProcessed().add(updatedRefset);

                logger.info(" and then updated the new WCI refset's Workflow Status - " + updatedRefset);

                return updatedRefset;
            }

        }

    }

    Team addTeam(String teamName, String teamDescription, Organization organization, Set<String> roles, Set<String> memberIds) throws Exception {

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

    Set<DefinitionClause> getRefsetClauses(String rttId) throws Exception {

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

    User getUser(String name, String userName, String email, Set<String> roles) throws Exception {

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

    static boolean isInternationalEdition(String editionName) {

        return ("international edition".equals(editionName.toLowerCase())) ? true : false;
    }

    String identifyTopLevelModule(String shortName, String editionName, String editionBranch, JsonNode codeSystem) throws Exception {

        Set<String> editionModules = new HashSet<>();
        String returnModule = null;

        if (isInternationalEdition(editionName)) {

            editionModules.add(DEFAULT_SNOMED_CORE_MODULE);
            returnModule = DEFAULT_SNOMED_CORE_MODULE;
        } else {

            Iterator<JsonNode> moduleIterator = codeSystem.get("modules").iterator();

            // Ignore CORE Modules
            while (moduleIterator.hasNext()) {

                JsonNode module = moduleIterator.next();

                if (!internationalModules.contains(module.get("conceptId").asText()) && !module.get("moduleId").asText().equals("900000000000012004")) {

                    editionModules.add(module.get("conceptId").asText());

                }

            }

            if (editionModules.size() == 0) {

                // If no non-CORE modules found, use the default Module

                returnModule = DEFAULT_SNOMED_CORE_MODULE;
                editionModules.add(returnModule);

            } else if (editionModules.size() == 1) {

                // If single non-CORE module found, use it as default

                returnModule = editionModules.iterator().next();
                editionModules.add(returnModule);

            } else {

                // TODO: 1) Review this especially for the work arounds. In fact, hard coded solutions should be in prop file
                // TODO: 2) If multiple non-CORE modules found... Possible??? how to handle?
                Set<String> childrenModules = new HashSet<>();

                Set<String> children = identifyModuleChildren(editionBranch);

                for (String moduleId : editionModules) {

                    if (children.contains(moduleId)) {

                        childrenModules.add(moduleId);
                    }

                }

                if (shortName.equals("SNOMEDCT-NL")) {

                    childrenModules.remove("15561000146104"); // 15561000146104
                                                              // - Represents
                                                              // Patient
                                                              // Friendly Terms
                } else if (shortName.equals("SNOMEDCT-AU")) {

                    childrenModules.add("32570231000036109");
                }

                if (shortName.equals("SNOMEDCT-NO")) {

                    childrenModules.remove("57091000202101");
                    childrenModules.remove("57101000202106");
                } else if (shortName.equals("SNOMEDCT-US")) {

                    childrenModules.remove("5991000124107");
                }

                if (childrenModules.size() == 0 || childrenModules.size() > 1) {

                    String msg = "Seeing odd number of modules during secondary analysis for " + editionName + ": " + childrenModules.toString();

                    logger.info(msg);
                    throw new Exception("This situation shouldn't happen during sync: " + msg);
                }

                returnModule = childrenModules.iterator().next();

            }

        }

        editionModulesMap.put(shortName, editionModules);

        return returnModule;
    }

    String identifyDefaultLanguageCode(JsonNode codeSystem, String editionName) throws Exception {

        // Identify Edition's defaultLanguageCode - Per Kai, transform first language in set as defaultLangCode
        if (!codeSystem.has("languages")) {

            throw new Exception("All Code Systems must have lanaguages set filled in. " + editionName + " does not");
        }

        Iterator<String> languages = codeSystem.get("languages").fieldNames();

        return languages.next();
    }

    Set<String> identifyDefaultLanguageRefsets(JsonNode codeSystem, String shortName) {

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

    private Set<String> identifyModuleChildren(String branch) throws Exception {

        String url = SnowstormConnection.BASE_URL + "browser/" + branch + "/concepts/" + ANCESTOR_MODULE + "/children";
        Set<String> childrenSctIds = new HashSet<>();

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resultString.toString());

            Iterator<JsonNode> conceptIterator = root.iterator();

            while (conceptIterator.hasNext()) {

                JsonNode node = conceptIterator.next();
                childrenSctIds.add(node.get("conceptId").asText());
            }

        } catch (Exception e) {

            throw new Exception("Failed in getting code systems (first call to Snowstorm) with: " + e.getMessage(), e);
        }

        return childrenSctIds;
    }

    private Refset initializeWorkflowStatus(Refset refset) throws Exception {

        final String currentStatus = refset.getWorkflowStatus();

        try (final TerminologyService service = new TerminologyService()) {

            initializeService(service);

            // if the status is Published then create a new version of the refset that is ready to be edited
            refset = WorkflowService.setWorkflowStatusByAction(service, SyncDataInitializer.getSyncUser(), WorkflowService.FINISH_EDIT, refset, "");

            // if the status changed return the updated refset else return null
            if (!currentStatus.equals(refset.getWorkflowStatus())) {

                return refset;
            } else {

                return null;
            }

        }

    }

    void initializeService(TerminologyService service) {

        service.setModifiedBy("Sync");
        service.setModifiedFlag(true);

    }

    SimpleDateFormat getSdf() {

        return metadata.getSdf();
    }

    SyncPropertyFileReader getPropertyReader() {

        return propertyReader;
    }

    Set<String> getInternationalModules() {

        return internationalModules;
    }

    Map<String, Set<String>> getEditionModulesMap() {

        return editionModulesMap;
    }
}
