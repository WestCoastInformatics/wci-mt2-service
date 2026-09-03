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

    /* see superclass */
    @Override
    public String getName() {

        return getClass().getSimpleName();
    }

    /* see superclass */
    @Override
    public void setProperties(final Properties properties) throws Exception {

        if (properties != null) {
            handlerProperties.putAll(properties);
        }
    }

    /* see superclass */
    @Override
    public String createBranch(final String parentBranchPath, final String branchName) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public boolean deleteBranch(final String branchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public boolean doesBranchExist(final String branchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> getBranchChildren(final String branchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void mergeBranch(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean rebase) throws Exception {

        unsupported();
    }

    /* see superclass */
    @Override
    public String mergeRebaseReview(final String sourceBranchPath, final String targetBranchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public String getNewRefsetId(final String editionBranchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> getBranchVersions(final String editionPath) throws Exception {

        return Collections.singletonList("2025-01-01");
    }

    /* see superclass */
    @Override
    public String generateVersionFile(final String entityString) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void downloadGeneratedFile(final String snowVersionFileUrl, final String localSnowVersionPath) throws Exception {

        unsupported();
    }

    /* see superclass */
    @Override
    public Map<String, String> getModuleNames(final Edition edition) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<Edition> getAffiliateEditionList() throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Object createRefset(final TerminologyService service, final User user, final Refset refsetEditParameters) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept getRefsetConcepts(final TerminologyService service, final String branch, final boolean areParentConcepts) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId) throws Exception {

        unsupported();
    }

    /* see superclass */
    @Override
    public List<Concept> getAllRefsetMembers(final TerminologyService service, final String refsetInternalId, final String searchAfter, final List<Concept> concepts) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> getMemberSctCodes(final String refsetId, final String branchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl, final Set<String> nonPublishedBranchPaths) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public String getMemberSctIds(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        unsupported();
    }

    /* see superclass */
    @Override
    public void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        unsupported();
    }

    /* see superclass */
    @Override
    public Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept searchConcepts(final Refset refset, final SearchParameters searchParameters, final String searchMembersMode, final int limitReturnNumber) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public int getMemberCount(final Refset refset) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept getParents(final String conceptId, final Refset refset, final String language) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept getChildren(final String conceptId, final Refset refset, final String language) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept getConceptsFromSnowstorm(final String url, final Refset refset, final ConceptLookupParameters lookupParameters, final String language) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void populateMembershipInformation(final Refset refset, final List<Concept> concepts) throws Exception {

        unsupported();
    }

    /* see superclass */
    @Override
    public Long getLatestChangedVersionDate(final String branch, final String refsetId) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Long getRefsetConceptReleaseDate(final String refsetId, final String branch) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<Map<String, String>> getMemberHistory(final TerminologyService service, final String referencedComponentId, final List<Map<String, String>> versions) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public boolean cacheMemberAncestors(final Refset refset) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> addRefsetMembers(final TerminologyService service, final User user, final Refset refset, final List<String> conceptIds) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> callAddMemberSingle(final String refsetId, final String url, final String conceptId, final String moduleId) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> callAddMembersBulk(final String refsetId, final String url, final List<String> conceptIds, final String moduleId) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> removeRefsetMembers(final TerminologyService service, final User user, final Refset refset, final String conceptIds) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> callUpdateMemberSingle(final String refsetId, final String url, final JsonNode memberBody) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> callUpdateMembersBulk(final String refsetId, final String url, final ArrayNode memberBodies) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset, final String inactiveConceptId, final String replacementConceptId, final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public String identifyRefsetName(final Refset refset) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<MapSet> getMapSets(final String branch) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public MapSet getMapSet(final String branch, final String code) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter, final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter, final boolean showOverriddenEntries, final List<String> conceptCodes, final Collection<String> restrictToConceptCodes) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Mapping getMapping(final String branch, final String conceptCode, final boolean showOverriddenEntries, final MapSet mapSet) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public Concept getConcept(final String terminology, final String version, final String code) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConcept findConcepts(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public ResultListConceptRef autoComplete(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void cacheConcepts(final Map<String, String> terminologyToVersion) throws Exception {

        return;
    }

    /* see superclass */
    @Override
    public List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings, final MapSet mapSet, final User user) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest, final MapSet mapSet) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public List<Mapping> importMappings(final MapProject mapProject, final String branch, final MultipartFile mappingFile, final MapSet mapSet, final User user) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest, final MapSet mapSet) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public MapSet setWorkflowStatus(final TerminologyService service, final User user, final String mapSetInternalId, final WorkflowAction action, final String notes) throws Exception {

        return unsupported();
    }

    /* see superclass */
    @Override
    public void clearAllRefsetCaches(final String branch) {

        return;
    }

    /* see superclass */
    @Override
    public String getBranchPath(final MapSet mapSet) throws Exception {

        return BranchService.getMapSetBranchPath(mapSet);
    }

    /* see superclass */
    @Override
    public Date getRefsetDateFromFormattedString(final String publicationDateString) throws Exception {

        return DateUtility.getDateWithNoTime(publicationDateString, DateUtility.DATE_FORMAT_REVERSE);
    }

    /* see superclass */
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

    /* see superclass */
    @Override
    public void removeMapSetEditHistory(final TerminologyService service, final String refsetCode) throws Exception {

        return;
    }

    /* see superclass */
    @Override
    public boolean setMapSetMemberCount(final TerminologyService service, final MapSet mapSet, final boolean force) throws Exception {

        return false;
    }

    /* see superclass */
    @Override
    public void removeUpgradeData(final TerminologyService service, final MapSet mapSet) throws Exception {

        return;
    }

    /* see superclass */
    @Override
    public void replaceMapSetWithEditHistory(final TerminologyService service, final MapSet mapSet) throws Exception {

        return;
    }

    /* see superclass */
    @Override
    public MapSet setMapSetPermissions(final User user, final MapSet mapSet) throws Exception {

        return mapSet;
    }
}
