/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.ResultListConceptRef;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.BranchService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Terminology handler for unit tests. Does not call Snowstorm or any other remote server.
 */
public abstract class OfflineTerminologyServerHandler implements TerminologyServerHandler {

    /** Handler properties from configuration. */
    private final Properties handlerProperties = new Properties();

    /**
     * Reject remote terminology-server use from unit tests.
     *
     * @param <T> unused
     * @return never
     */
    protected static <T> T unsupported() {

        throw new UnsupportedOperationException("Unit tests must not call a terminology server.");
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {

        return getClass().getSimpleName();
    }

    /**
     * Sets the properties.
     *
     * @param properties the new properties
     * @throws Exception the exception
     */
    @Override
    public void setProperties(final Properties properties) throws Exception {

        if (properties != null) {
            handlerProperties.putAll(properties);
        }
    }

    /**
     * Creates the branch.
     *
     * @param parentBranchPath the parent branch path
     * @param branchName the branch name
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String createBranch(final String parentBranchPath, final String branchName) throws Exception {

        return unsupported();
    }

    /**
     * Delete branch.
     *
     * @param branchPath the branch path
     * @return true, if successful
     * @throws Exception the exception
     */
    @Override
    public boolean deleteBranch(final String branchPath) throws Exception {

        return unsupported();
    }

    /**
     * Does branch exist.
     *
     * @param branchPath the branch path
     * @return true, if successful
     * @throws Exception the exception
     */
    @Override
    public boolean doesBranchExist(final String branchPath) throws Exception {

        return unsupported();
    }

    /**
     * Gets the branch children.
     *
     * @param branchPath the branch path
     * @return the branch children
     * @throws Exception the exception
     */
    @Override
    public List<String> getBranchChildren(final String branchPath) throws Exception {

        return unsupported();
    }

    /**
     * Merge branch.
     *
     * @param sourceBranchPath the source branch path
     * @param targetBranchPath the target branch path
     * @param comment the comment
     * @param rebase the rebase
     * @throws Exception the exception
     */
    @Override
    public void mergeBranch(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean rebase) throws Exception {

        unsupported();
    }

    /**
     * Merge rebase review.
     *
     * @param sourceBranchPath the source branch path
     * @param targetBranchPath the target branch path
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String mergeRebaseReview(final String sourceBranchPath, final String targetBranchPath) throws Exception {

        return unsupported();
    }

    /**
     * Gets the new refset id.
     *
     * @param editionBranchPath the edition branch path
     * @return the new refset id
     * @throws Exception the exception
     */
    @Override
    public String getNewRefsetId(final String editionBranchPath) throws Exception {

        return unsupported();
    }

    /**
     * Gets the branch versions.
     *
     * @param editionPath the edition path
     * @return the branch versions
     * @throws Exception the exception
     */
    @Override
    public List<String> getBranchVersions(final String editionPath) throws Exception {

        return Collections.singletonList("2025-01-01");
    }

    /**
     * Generate version file.
     *
     * @param entityString the entity string
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String generateVersionFile(final String entityString) throws Exception {

        return unsupported();
    }

    /**
     * Download generated file.
     *
     * @param snowVersionFileUrl the snow version file url
     * @param localSnowVersionPath the local snow version path
     * @throws Exception the exception
     */
    @Override
    public void downloadGeneratedFile(final String snowVersionFileUrl, final String localSnowVersionPath) throws Exception {

        unsupported();
    }

    /**
     * Gets the module names.
     *
     * @param edition the edition
     * @return the module names
     * @throws Exception the exception
     */
    @Override
    public Map<String, String> getModuleNames(final Edition edition) throws Exception {

        return unsupported();
    }

    /**
     * Gets the affiliate edition list.
     *
     * @return the affiliate edition list
     * @throws Exception the exception
     */
    @Override
    public List<Edition> getAffiliateEditionList() throws Exception {

        return unsupported();
    }

    /**
     * Creates the refset.
     *
     * @param service the service
     * @param user the user
     * @param refsetEditParameters the refset edit parameters
     * @return the object
     * @throws Exception the exception
     */
    @Override
    public Object createRefset(final TerminologyService service, final User user, final Refset refsetEditParameters) throws Exception {

        return unsupported();
    }

    /**
     * Gets the refset concepts.
     *
     * @param service the service
     * @param branch the branch
     * @param areParentConcepts the are parent concepts
     * @return the refset concepts
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept getRefsetConcepts(final TerminologyService service, final String branch, final boolean areParentConcepts) throws Exception {

        return unsupported();
    }

    /**
     * Update refset concept.
     *
     * @param refset the refset
     * @param active the active
     * @param moduleId the module id
     * @throws Exception the exception
     */
    @Override
    public void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId) throws Exception {

        unsupported();
    }

    /**
     * Gets the all refset members.
     *
     * @param service the service
     * @param refsetInternalId the refset internal id
     * @param searchAfter the search after
     * @param concepts the concepts
     * @return the all refset members
     * @throws Exception the exception
     */
    @Override
    public List<Concept> getAllRefsetMembers(final TerminologyService service, final String refsetInternalId, final String searchAfter,
        final List<Concept> concepts) throws Exception {

        return unsupported();
    }

    /**
     * Gets the member sct codes.
     *
     * @param refsetId the refset id
     * @param branchPath the branch path
     * @return the member sct codes
     * @throws Exception the exception
     */
    @Override
    public List<String> getMemberSctCodes(final String refsetId, final String branchPath) throws Exception {

        return unsupported();
    }

    /**
     * Gets the directory members.
     *
     * @param snowstormQuery the snowstorm query
     * @return the directory members
     * @throws Exception the exception
     */
    @Override
    public Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception {

        return unsupported();
    }

    /**
     * Search multisearch descriptions.
     *
     * @param searchParameters the search parameters
     * @param ecl the ecl
     * @param nonPublishedBranchPaths the non published branch paths
     * @return the sets the
     * @throws Exception the exception
     */
    @Override
    public Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl, final Set<String> nonPublishedBranchPaths)
        throws Exception {

        return unsupported();
    }

    /**
     * Gets the member sct ids.
     *
     * @param refsetId the refset id
     * @param limit the limit
     * @param searchAfter the search after
     * @param branchPath the branch path
     * @return the member sct ids
     * @throws Exception the exception
     */
    @Override
    public String getMemberSctIds(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception {

        return unsupported();
    }

    /**
     * Populate all language descriptions.
     *
     * @param refset the refset
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    @Override
    public void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        unsupported();
    }

    /**
     * Populate concept leaf status.
     *
     * @param refset the refset
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    @Override
    public void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        unsupported();
    }

    /**
     * Gets the concept ancestors.
     *
     * @param refset the refset
     * @param conceptId the concept id
     * @return the concept ancestors
     * @throws Exception the exception
     */
    @Override
    public Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception {

        return unsupported();
    }

    /**
     * Search concepts.
     *
     * @param refset the refset
     * @param searchParameters the search parameters
     * @param searchMembersMode the search members mode
     * @param limitReturnNumber the limit return number
     * @return the result list concept
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept searchConcepts(final Refset refset, final SearchParameters searchParameters, final String searchMembersMode,
        final int limitReturnNumber) throws Exception {

        return unsupported();
    }

    /**
     * Gets the member count.
     *
     * @param refset the refset
     * @return the member count
     * @throws Exception the exception
     */
    @Override
    public int getMemberCount(final Refset refset) throws Exception {

        return unsupported();
    }

    /**
     * Gets the member list.
     *
     * @param refset the refset
     * @param nonDefaultPreferredTerms the non default preferred terms
     * @param searchParameters the search parameters
     * @return the member list
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters)
        throws Exception {

        return unsupported();
    }

    /**
     * Gets the concept details.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @return the concept details
     * @throws Exception the exception
     */
    @Override
    public Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception {

        return unsupported();
    }

    /**
     * Gets the parents.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @param language the language
     * @return the parents
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept getParents(final String conceptId, final Refset refset, final String language) throws Exception {

        return unsupported();
    }

    /**
     * Gets the children.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @param language the language
     * @return the children
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept getChildren(final String conceptId, final Refset refset, final String language) throws Exception {

        return unsupported();
    }

    /**
     * Gets the concepts from snowstorm.
     *
     * @param url the url
     * @param refset the refset
     * @param lookupParameters the lookup parameters
     * @param language the language
     * @return the concepts from snowstorm
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept getConceptsFromSnowstorm(final String url, final Refset refset, final ConceptLookupParameters lookupParameters,
        final String language) throws Exception {

        return unsupported();
    }

    /**
     * Populate membership information.
     *
     * @param refset the refset
     * @param concepts the concepts
     * @throws Exception the exception
     */
    @Override
    public void populateMembershipInformation(final Refset refset, final List<Concept> concepts) throws Exception {

        unsupported();
    }

    /**
     * Gets the latest changed version date.
     *
     * @param branch the branch
     * @param refsetId the refset id
     * @return the latest changed version date
     * @throws Exception the exception
     */
    @Override
    public Long getLatestChangedVersionDate(final String branch, final String refsetId) throws Exception {

        return unsupported();
    }

    /**
     * Gets the refset concept release date.
     *
     * @param refsetId the refset id
     * @param branch the branch
     * @return the refset concept release date
     * @throws Exception the exception
     */
    @Override
    public Long getRefsetConceptReleaseDate(final String refsetId, final String branch) throws Exception {

        return unsupported();
    }

    /**
     * Gets the member history.
     *
     * @param service the service
     * @param referencedComponentId the referenced component id
     * @param versions the versions
     * @return the member history
     * @throws Exception the exception
     */
    @Override
    public List<Map<String, String>> getMemberHistory(final TerminologyService service, final String referencedComponentId,
        final List<Map<String, String>> versions) throws Exception {

        return unsupported();
    }

    /**
     * Cache member ancestors.
     *
     * @param refset the refset
     * @return true, if successful
     * @throws Exception the exception
     */
    @Override
    public boolean cacheMemberAncestors(final Refset refset) throws Exception {

        return unsupported();
    }

    /**
     * Adds the refset members.
     *
     * @param service the service
     * @param user the user
     * @param refset the refset
     * @param conceptIds the concept ids
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> addRefsetMembers(final TerminologyService service, final User user, final Refset refset, final List<String> conceptIds)
        throws Exception {

        return unsupported();
    }

    /**
     * Call add member single.
     *
     * @param refsetId the refset id
     * @param url the url
     * @param conceptId the concept id
     * @param moduleId the module id
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> callAddMemberSingle(final String refsetId, final String url, final String conceptId, final String moduleId) throws Exception {

        return unsupported();
    }

    /**
     * Call add members bulk.
     *
     * @param refsetId the refset id
     * @param url the url
     * @param conceptIds the concept ids
     * @param moduleId the module id
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> callAddMembersBulk(final String refsetId, final String url, final List<String> conceptIds, final String moduleId) throws Exception {

        return unsupported();
    }

    /**
     * Removes the refset members.
     *
     * @param service the service
     * @param user the user
     * @param refset the refset
     * @param conceptIds the concept ids
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> removeRefsetMembers(final TerminologyService service, final User user, final Refset refset, final String conceptIds) throws Exception {

        return unsupported();
    }

    /**
     * Call update member single.
     *
     * @param refsetId the refset id
     * @param url the url
     * @param memberBody the member body
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> callUpdateMemberSingle(final String refsetId, final String url, final JsonNode memberBody) throws Exception {

        return unsupported();
    }

    /**
     * Call update members bulk.
     *
     * @param refsetId the refset id
     * @param url the url
     * @param memberBodies the member bodies
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> callUpdateMembersBulk(final String refsetId, final String url, final ArrayNode memberBodies) throws Exception {

        return unsupported();
    }

    /**
     * Gets the concept ids from ecl.
     *
     * @param branch the branch
     * @param ecl the ecl
     * @return the concept ids from ecl
     * @throws Exception the exception
     */
    @Override
    public List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception {

        return unsupported();
    }

    /**
     * Compile upgrade data.
     *
     * @param service the service
     * @param user the user
     * @param refsetInternalId the refset internal id
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception {

        return unsupported();
    }

    /**
     * Modify upgrade concept.
     *
     * @param service the service
     * @param user the user
     * @param refset the refset
     * @param inactiveConceptId the inactive concept id
     * @param replacementConceptId the replacement concept id
     * @param manualReplacementConcept the manual replacement concept
     * @param changed the changed
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset, final String inactiveConceptId,
        final String replacementConceptId, final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception {

        return unsupported();
    }

    /**
     * Identify refset name.
     *
     * @param refset the refset
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String identifyRefsetName(final Refset refset) throws Exception {

        return unsupported();
    }

    /**
     * Gets the map sets.
     *
     * @param branch the branch
     * @return the map sets
     * @throws Exception the exception
     */
    @Override
    public List<MapSet> getMapSets(final String branch) throws Exception {

        return unsupported();
    }

    /**
     * Gets the map set.
     *
     * @param branch the branch
     * @param code the code
     * @return the map set
     * @throws Exception the exception
     */
    @Override
    public MapSet getMapSet(final String branch, final String code) throws Exception {

        return unsupported();
    }

    /**
     * Gets the mappings.
     *
     * @param branch the branch
     * @param mapSet the map set
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @return the mappings
     * @throws Exception the exception
     */
    @Override
    public ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        return unsupported();
    }

    /**
     * Gets the mappings.
     *
     * @param branch the branch
     * @param mapSet the map set
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @param restrictToConceptCodes the restrict to concept codes
     * @return the mappings
     * @throws Exception the exception
     */
    @Override
    public ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes, final Collection<String> restrictToConceptCodes) throws Exception {

        return unsupported();
    }

    /**
     * Gets the mapping.
     *
     * @param branch the branch
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @param mapSet the map set
     * @return the mapping
     * @throws Exception the exception
     */
    @Override
    public Mapping getMapping(final String branch, final String conceptCode, final boolean showOverriddenEntries, final MapSet mapSet) throws Exception {

        return unsupported();
    }

    /**
     * Gets the concept.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
     * @return the concept
     * @throws Exception the exception
     */
    @Override
    public Concept getConcept(final String terminology, final String version, final String code) throws Exception {

        return unsupported();
    }

    /**
     * Find concepts.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @return the result list concept
     * @throws Exception the exception
     */
    @Override
    public ResultListConcept findConcepts(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        return unsupported();
    }

    /**
     * Auto complete.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @return the result list concept ref
     * @throws Exception the exception
     */
    @Override
    public ResultListConceptRef autoComplete(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        return unsupported();
    }

    /**
     * Cache concepts.
     *
     * @param terminologyToVersion the terminology to version
     * @throws Exception the exception
     */
    @Override
    public void cacheConcepts(final Map<String, String> terminologyToVersion) throws Exception {

        return;
    }

    /**
     * Creates the mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        return unsupported();
    }

    /**
     * Update mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @param mapSet the map set
     * @param user the user
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings,
        final MapSet mapSet, final User user) throws Exception {

        return unsupported();
    }

    /**
     * Export mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappingExportRequest the mapping export request
     * @param mapSet the map set
     * @return the file
     * @throws Exception the exception
     */
    @Override
    public File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest, final MapSet mapSet)
        throws Exception {

        return unsupported();
    }

    /**
     * Import mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mappingFile the mapping file
     * @param mapSet the map set
     * @param user the user
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<Mapping> importMappings(final MapProject mapProject, final String branch, final MultipartFile mappingFile, final MapSet mapSet, final User user)
        throws Exception {

        return unsupported();
    }

    /**
     * Export map set.
     *
     * @param user the user
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @param mapSet the map set
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest, final MapSet mapSet)
        throws Exception {

        return unsupported();
    }

    /**
     * Sets the workflow status.
     *
     * @param service the service
     * @param user the user
     * @param mapSetInternalId the map set internal id
     * @param action the action
     * @param notes the notes
     * @return the map set
     * @throws Exception the exception
     */
    @Override
    public MapSet setWorkflowStatus(final TerminologyService service, final User user, final String mapSetInternalId, final WorkflowAction action,
        final String notes) throws Exception {

        return unsupported();
    }

    /**
     * Clear all refset caches.
     *
     * @param branch the branch
     */
    @Override
    public void clearAllRefsetCaches(final String branch) {

        return;
    }

    /**
     * Gets the branch path.
     *
     * @param mapSet the map set
     * @return the branch path
     * @throws Exception the exception
     */
    @Override
    public String getBranchPath(final MapSet mapSet) throws Exception {

        return BranchService.getMapSetBranchPath(mapSet);
    }

    /**
     * Gets the refset date from formatted string.
     *
     * @param publicationDateString the publication date string
     * @return the refset date from formatted string
     * @throws Exception the exception
     */
    @Override
    public Date getRefsetDateFromFormattedString(final String publicationDateString) throws Exception {

        return DateUtility.getDateWithNoTime(publicationDateString, DateUtility.DATE_FORMAT_REVERSE);
    }

    /**
     * Sets the roles.
     *
     * @param user the user
     * @param project the project
     * @param roles the roles
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public List<String> setRoles(final User user, final Project project, final List<String> roles) throws Exception {

        boolean giveViewerRole = false;
        if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {
            roles.add(User.ROLE_AUTHOR);
            giveViewerRole = true;
        }
        if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {
            roles.add(User.ROLE_REVIEWER);
            giveViewerRole = true;
        }
        if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
            roles.add(User.ROLE_ADMIN);
            giveViewerRole = true;
        }
        if (user.doesUserHavePermission(User.ROLE_VIEWER, project) || giveViewerRole) {
            roles.add(User.ROLE_VIEWER);
        }
        return roles;
    }

    /**
     * Removes the map set edit history.
     *
     * @param service the service
     * @param refsetCode the refset code
     * @throws Exception the exception
     */
    @Override
    public void removeMapSetEditHistory(final TerminologyService service, final String refsetCode) throws Exception {

        return;
    }

    /**
     * Sets the map set member count.
     *
     * @param service the service
     * @param mapSet the map set
     * @param force the force
     * @return true, if successful
     * @throws Exception the exception
     */
    @Override
    public boolean setMapSetMemberCount(final TerminologyService service, final MapSet mapSet, final boolean force) throws Exception {

        return false;
    }

    /**
     * Removes the upgrade data.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    @Override
    public void removeUpgradeData(final TerminologyService service, final MapSet mapSet) throws Exception {

        return;
    }

    /**
     * Replace map set with edit history.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    @Override
    public void replaceMapSetWithEditHistory(final TerminologyService service, final MapSet mapSet) throws Exception {

        return;
    }

    /**
     * Sets the map set permissions.
     *
     * @param user the user
     * @param mapSet the map set
     * @return the map set
     * @throws Exception the exception
     */
    @Override
    public MapSet setMapSetPermissions(final User user, final MapSet mapSet) throws Exception {

        return mapSet;
    }
}
