/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Configurable;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Generically represents a handler for accessing terminology objects.
 */
public interface TerminologyServerHandler extends Configurable {

    /* Workflow Service calls */

    /**
     * Creates the branch.
     *
     * @param parentBranchPath the parent branch path
     * @param branchName the branch name
     * @return the string
     * @throws Exception the exception
     */
    public String createBranch(final String parentBranchPath, final String branchName) throws Exception;

    /**
     * Delete branch.
     *
     * @param branchPath the branch path
     * @return true, if successful
     * @throws Exception the exception
     */
    public boolean deleteBranch(final String branchPath) throws Exception;

    /**
     * Does branch exist.
     *
     * @param branchPath the branch path
     * @return true, if successful
     * @throws Exception the exception
     */
    public boolean doesBranchExist(final String branchPath) throws Exception;

    /**
     * Returns the branch children.
     *
     * @param branchPath the branch path
     * @return the branch children
     * @throws Exception the exception
     */
    public List<String> getBranchChildren(final String branchPath) throws Exception;

    /**
     * Merge branch.
     *
     * @param sourceBranchPath the source branch path
     * @param targetBranchPath the target branch path
     * @param comment the comment
     * @param rebase the rebase
     * @throws Exception the exception
     */
    public void mergeBranch(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean rebase) throws Exception;

    /**
     * Merge rebase review.
     *
     * @param sourceBranchPath the source branch path
     * @param targetBranchPath the target branch path
     * @return the string
     * @throws Exception the exception
     */
    public String mergeRebaseReview(final String sourceBranchPath, final String targetBranchPath) throws Exception;

    /**
     * Returns the new refset id.
     *
     * @param editionBranchPath the edition branch path
     * @return the new refset id
     * @throws Exception the exception
     */
    public String getNewRefsetId(final String editionBranchPath) throws Exception;

    /* Refset Service calls */

    /**
     * Returns the branch versions.
     *
     * @param editionPath the edition path
     * @return the branch versions
     * @throws Exception the exception
     */
    public List<String> getBranchVersions(final String editionPath) throws Exception;

    /**
     * Creates the refset.
     *
     * @param service the service
     * @param user the user
     * @param refsetEditParameters the refset edit parameters
     * @return the object
     * @throws Exception the exception
     */

    public Object createRefset(final TerminologyService service, final User user, final Refset refsetEditParameters) throws Exception;

    /**
     * Returns the refset concepts.
     *
     * @param service the service
     * @param branch the branch
     * @param areParentConcepts the are parent concepts
     * @return the refset concepts
     * @throws Exception the exception
     */
    public ResultListConcept getRefsetConcepts(final TerminologyService service, final String branch, final boolean areParentConcepts) throws Exception;

    /**
     * Update refset concept.
     *
     * @param refset the refset
     * @param active the active
     * @param moduleId the module id
     * @throws Exception the exception
     */
    public void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId) throws Exception;

    /* Export Handler calls */

    /**
     * Generate version file.
     *
     * @param entityString the entity string
     * @return the string
     * @throws Exception the exception
     */
    public String generateVersionFile(final String entityString) throws Exception;

    /**
     * Download generated file.
     *
     * @param versionFileUrl the version file url
     * @param localVersionPath the local version path
     * @throws Exception the exception
     */
    public void downloadGeneratedFile(final String versionFileUrl, final String localVersionPath) throws Exception;

    /* Project Service calls */

    /**
     * Gets the module names.
     *
     * @param edition the edition
     * @return the module names
     * @throws Exception the exception
     */
    public Map<String, String> getModuleNames(final Edition edition) throws Exception;

    /* Edition Service calls */

    /**
     * Returns the affiliate edition list.
     *
     * @return the affiliate edition list
     * @throws Exception the exception
     */
    public List<Edition> getAffiliateEditionList() throws Exception;

    /* Refset Member Service calls */

    /**
     * Returns the all refset members.
     *
     * @param service the service
     * @param refsetInternalId the refset internal id
     * @param searchAfter the search after
     * @param concepts the concepts
     * @return the all refset members
     * @throws Exception the exception
     */
    public List<Concept> getAllRefsetMembers(final TerminologyService service, final String refsetInternalId, final String searchAfter,
        final List<Concept> concepts) throws Exception;

    /**
     * Returns the directory members.
     *
     * @param snowstormQuery the snowstorm query
     * @return the directory members
     * @throws Exception the exception
     */
    public Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception;

    /**
     * Search multisearch descriptions.
     *
     * @param searchParameters the search parameters
     * @param ecl the ecl
     * @param nonPublishedBranchPaths the non published branch paths
     * @return the sets the
     * @throws Exception the exception
     */
    public Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl, final Set<String> nonPublishedBranchPaths)
        throws Exception;

    /**
     * Returns the member sctids.
     *
     * @param refsetId the refset id
     * @param limit the limit
     * @param searchAfter the search after
     * @param branchPath the branch path
     * @return the member sctids
     * @throws Exception the exception
     */
    public String getMemberSctids(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception;

    /**
     * Populate all language descriptions.
     *
     * @param refset the refset
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    public void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws Exception;

    /**
     * Populate concept leaf status.
     *
     * @param refset the refset
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    public void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess) throws Exception;

    /**
     * Returns the concept ancestors.
     *
     * @param refset the refset
     * @param conceptId the concept id
     * @return the concept ancestors
     * @throws Exception the exception
     */
    public Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception;

    /**
     * Search concepts.
     *
     * @param refset the refset
     * @param searchParameters the search parameters
     * @param searchMembersMode the search members mode
     * @param limitReturnNumber the limit return number
     * @return the concept result list
     * @throws Exception the exception
     */
    public ResultListConcept searchConcepts(final Refset refset, final SearchParameters searchParameters, final String searchMembersMode,
        final int limitReturnNumber) throws Exception;

    /**
     * Returns the concepts.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @return the concept
     * @throws Exception the exception
     */
    public ResultListConcept findConcepts(/* final String branch, */ final String terminology, final String version, final SearchParameters searchParameters)
        throws Exception;

    /**
     * Returns the member count.
     *
     * @param refset the refset
     * @return the member count
     * @throws Exception the exception
     */
    public int getMemberCount(final Refset refset) throws Exception;

    /**
     * Returns the member list.
     *
     * @param refset the refset
     * @param nonDefaultPreferredTerms the non default preferred terms
     * @param searchParameters the search parameters
     * @return the member list
     * @throws Exception the exception
     */
    public ResultListConcept getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters)
        throws Exception;

    /**
     * Returns the concept details.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @return the concept details
     * @throws Exception the exception
     */
    public Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception;

    /**
     * Returns the parents.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @param language the language
     * @return the parents
     * @throws Exception the exception
     */
    public ResultListConcept getParents(final String conceptId, final Refset refset, final String language) throws Exception;

    /**
     * Returns the children.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @param language the language
     * @return the children
     * @throws Exception the exception
     */
    public ResultListConcept getChildren(final String conceptId, final Refset refset, final String language) throws Exception;

    /**
     * Returns the concepts from snowstorm.
     *
     * @param url the url
     * @param refset the refset
     * @param lookupParameters the lookup parameters
     * @param language the language
     * @return the concepts from snowstorm
     * @throws Exception the exception
     */
    public ResultListConcept getConceptsFromSnowstorm(final String url, final Refset refset, final ConceptLookupParameters lookupParameters,
        final String language) throws Exception;

    /**
     * Populate membership information.
     *
     * @param refset the refset
     * @param concepts the concepts
     * @throws Exception the exception
     */
    public void populateMembershipInformation(final Refset refset, final List<Concept> concepts) throws Exception;

    /**
     * Returns the latest changed version date.
     *
     * @param branch the branch
     * @param refsetId the refset id
     * @return the latest changed version date
     * @throws Exception the exception
     */
    public Long getLatestChangedVersionDate(final String branch, final String refsetId) throws Exception;

    /**
     * Returns the refset concept release date.
     *
     * @param refsetId the refset id
     * @param branch the branch
     * @return the refset concept release date
     * @throws Exception the exception
     */
    public Long getRefsetConceptReleaseDate(final String refsetId, final String branch) throws Exception;

    /**
     * Returns the member history.
     *
     * @param service the service
     * @param referencedComponentId the referenced component id
     * @param versions the versions
     * @return the member history
     * @throws Exception the exception
     */
    public List<Map<String, String>> getMemberHistory(final TerminologyService service, final String referencedComponentId,
        final List<Map<String, String>> versions) throws Exception;

    /**
     * Cache member ancestors.
     *
     * @param refset the refset
     * @return true, if successful
     * @throws Exception the exception
     */
    public boolean cacheMemberAncestors(final Refset refset) throws Exception;

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
    public List<String> addRefsetMembers(final TerminologyService service, final User user, final Refset refset, final List<String> conceptIds)
        throws Exception;

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
    public List<String> callAddMemberSingle(final String refsetId, final String url, final String conceptId, final String moduleId) throws Exception;

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
    public List<String> callAddMembersBulk(final String refsetId, final String url, final List<String> conceptIds, final String moduleId) throws Exception;

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
    public List<String> removeRefsetMembers(final TerminologyService service, final User user, final Refset refset, final String conceptIds) throws Exception;

    /**
     * Call update member single.
     *
     * @param refsetId the refset id
     * @param url the url
     * @param memberBody the member body
     * @return the list
     * @throws Exception the exception
     */
    public List<String> callUpdateMemberSingle(final String refsetId, final String url, final JsonNode memberBody) throws Exception;

    /**
     * Call update members bulk.
     *
     * @param refsetId the refset id
     * @param url the url
     * @param memberBodies the member bodies
     * @return the list
     * @throws Exception the exception
     */
    public List<String> callUpdateMembersBulk(final String refsetId, final String url, final ArrayNode memberBodies) throws Exception;

    /**
     * Returns the concept ids from ecl.
     *
     * @param branch the branch
     * @param ecl the ecl
     * @return the concept ids from ecl
     * @throws Exception the exception
     */
    public List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception;

    /**
     * Compile upgrade data.
     *
     * @param service the service
     * @param user the user
     * @param refsetInternalId the refset internal id
     * @return the string
     * @throws Exception the exception
     */
    public String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception;

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
    public String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset, final String inactiveConceptId,
        final String replacementConceptId, final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception;

    /* Mapping Service calls */

    /**
     * Returns the map sets.
     *
     * @param branch the branch
     * @return the map sets
     * @throws Exception the exception
     */
    public List<MapSet> getMapSets(final String branch) throws Exception;

    /**
     * Returns the map set.
     *
     * @param branch the branch
     * @param code the code
     * @return the map set
     * @throws Exception the exception
     */
    public MapSet getMapSet(final String branch, final String code) throws Exception;

    /**
     * Returns the mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @return the mappings
     * @throws Exception the exception
     */
    public ResultListMapping getMappings(final String branch, final String mapSetCode, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception;

    /**
     * Returns the mapping.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @return the mapping
     * @throws Exception the exception
     */
    public Mapping getMapping(final String branch, final String mapSetCode, final String conceptCode, final boolean showOverriddenEntries) throws Exception;

    /**
     * Returns the concept.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
     * @return the concept
     * @throws Exception the exception
     */
    public Concept getConcept(/* final String branch, */ final String terminology, final String version, final String code) throws Exception;

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
    public List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception;

    /**
     * Update mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mapping the mapping
     * @return the list
     * @throws Exception the exception
     */
    public List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mapping)
        throws Exception;

    /**
     * Export mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappingExportRequest the mapping export request
     * @return the paths
     * @throws Exception the exception
     */
    public File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest) throws Exception;
	
    /**
     * Import mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mappingFile the mappingFile
     * @throws Exception the exception
     */
	public List<Mapping> importMappings(MapProject mapProject, String branch,  MultipartFile mappingFile) throws Exception;

	    
}
