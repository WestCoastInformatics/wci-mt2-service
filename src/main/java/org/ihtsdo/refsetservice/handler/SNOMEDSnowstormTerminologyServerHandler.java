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
import java.util.Properties;
import java.util.Set;

import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormBranch;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormCodeSystem;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormConcept;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormDescription;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormExport;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMapping;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMultiSearch;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormRefset;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormRefsetMember;
import org.ihtsdo.refsetservice.model.Concept;
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
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * Implements a terminology handler that uses SNOMED's Snowstorm terminology server.
 */
public class SNOMEDSnowstormTerminologyServerHandler implements TerminologyServerHandler {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SNOMEDSnowstormTerminologyServerHandler.class);

    /** The handler properties. */
    private Properties handlerProperties = new Properties();

    /* see superclass */
    @Override
    public String createBranch(final String parentBranchPath, final String branchName) throws Exception {

        return SnowstormBranch.createBranch(parentBranchPath, branchName);
    }

    /* see superclass */
    @Override
    public boolean deleteBranch(final String branchPath) throws Exception {

        return SnowstormBranch.deleteBranch(branchPath);
    }

    /* see superclass */
    @Override
    public boolean doesBranchExist(final String branchPath) throws Exception {

        return SnowstormBranch.doesBranchExist(branchPath);

    }

    /* see superclass */
    @Override
    public List<String> getBranchChildren(final String branchPath) throws Exception {

        return SnowstormBranch.getBranchChildren(branchPath);
    }

    /* see superclass */
    @Override
    public void mergeBranch(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean rebase) throws Exception {

        SnowstormBranch.mergeBranch(sourceBranchPath, targetBranchPath, comment, rebase);

    }

    /* see superclass */
    @Override
    public String mergeRebaseReview(final String sourceBranchPath, final String targetBranchPath) throws Exception {

        return SnowstormBranch.mergeRebaseReview(sourceBranchPath, targetBranchPath);
    }

    /* see superclass */
    @Override
    public String getNewRefsetId(final String editionBranchPath) throws Exception {

        return SnowstormRefset.getNewRefsetId(editionBranchPath);
    }

    /* see superclass */
    @Override
    public List<String> getBranchVersions(final String editionPath) throws Exception {

        return SnowstormBranch.getBranchVersions(editionPath);
    }

    /* see superclass */
    @Override
    public String generateVersionFile(final String entityString) throws Exception {

        return SnowstormExport.generateVersionFile(entityString);

    }

    /* see superclass */
    @Override
    public void downloadGeneratedFile(final String snowVersionFileUrl, final String localSnowVersionPath) throws Exception {

        SnowstormExport.downloadGeneratedFile(snowVersionFileUrl, localSnowVersionPath);

    }

    /* see superclass */
    @Override
    public Map<String, String> getModuleNames(final Edition edition) throws Exception {

        return SnowstormConcept.getModuleNames(edition);

    }

    /* see superclass */
    @Override
    public List<Edition> getAffiliateEditionList() throws Exception {

        return SnowstormCodeSystem.getAffiliateEditionList();

    }

    /* see superclass */
    @Override
    public Object createRefset(final TerminologyService service, final User user, final Refset refsetEditParameters) throws Exception {

        return SnowstormRefset.createRefset(service, user, refsetEditParameters);

    }

    /* see superclass */
    @Override
    public ResultListConcept getRefsetConcepts(final TerminologyService service, final String branch, final boolean areParentConcepts) throws Exception {

        return SnowstormConcept.getRefsetConcepts(service, branch, areParentConcepts);

    }

    /* see superclass */
    @Override
    public void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId) throws Exception {

        SnowstormConcept.updateRefsetConcept(refset, active, moduleId);

    }

    /* Refset Member Service calls */

    /* see superclass */
    @Override
    public List<Concept> getAllRefsetMembers(final TerminologyService service, final String refsetInternalId, final String searchAfter,
        final List<Concept> concepts) throws Exception {

        return SnowstormRefsetMember.getAllRefsetMembers(service, refsetInternalId, searchAfter, concepts);

    }

    /* see superclass */
    @Override
    public Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception {

        return SnowstormMultiSearch.getDirectoryMembers(snowstormQuery);

    }

    /* see superclass */
    @Override
    public Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl, final Set<String> nonPublishedBranchPaths)
        throws Exception {

        return SnowstormMultiSearch.searchMultisearchDescriptions(searchParameters, ecl, nonPublishedBranchPaths);

    }

    /* see superclass */
    @Override
    public String getMemberSctids(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception {

        return SnowstormRefsetMember.getMemberSctids(refsetId, limit, searchAfter, branchPath);

    }

    /* see superclass */
    @Override
    public void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        SnowstormDescription.populateAllLanguageDescriptions(refset, conceptsToProcess);

    }

    /* see superclass */
    @Override
    public void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        SnowstormConcept.populateConceptLeafStatus(refset, conceptsToProcess);

    }

    /* see superclass */
    @Override
    public Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception {

        return SnowstormConcept.getConceptAncestors(refset, conceptId);

    }

    /* see superclass */
    @Override
    public ResultListConcept searchConcepts(final Refset refset, final SearchParameters searchParameters, final String searchMembersMode,
        final int limitReturnNumber) throws Exception {

        return SnowstormConcept.searchConcepts(refset, searchParameters, searchMembersMode, limitReturnNumber);

    }

    /* see superclass */
    @Override
    public int getMemberCount(final Refset refset) throws Exception {

        return SnowstormRefsetMember.getMemberCount(refset);

    }

    /* see superclass */
    @Override
    public ResultListConcept getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters)
        throws Exception {

        return SnowstormRefsetMember.getMemberList(refset, nonDefaultPreferredTerms, searchParameters);

    }

    /* see superclass */
    @Override
    public Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception {

        return SnowstormConcept.getConceptDetails(conceptId, refset);

    }

    /* see superclass */
    @Override
    public ResultListConcept getParents(final String conceptId, final Refset refset, final String language) throws Exception {

        return SnowstormConcept.getParents(conceptId, refset, language);

    }

    /* see superclass */
    @Override
    public ResultListConcept getChildren(final String conceptId, final Refset refset, final String language) throws Exception {

        return SnowstormConcept.getChildren(conceptId, refset, language);

    }

    /* see superclass */
    @Override
    public ResultListConcept getConceptsFromSnowstorm(final String url, final Refset refset, final ConceptLookupParameters lookupParameters,
        final String language) throws Exception {

        return SnowstormConcept.getConceptsFromSnowstorm(url, refset, lookupParameters, language);

    }

    /* see superclass */
    @Override
    public void populateMembershipInformation(final Refset refset, final List<Concept> concepts) throws Exception {

        SnowstormRefsetMember.populateMembershipInformation(refset, concepts);

    }

    /* see superclass */
    @Override
    public Long getLatestChangedVersionDate(final String branch, final String refsetId) throws Exception {

        return SnowstormRefset.getLatestChangedVersionDate(branch, refsetId);

    }

    /* see superclass */
    @Override
    public Long getRefsetConceptReleaseDate(final String refsetId, final String branch) throws Exception {

        return SnowstormRefset.getRefsetConceptReleaseDate(refsetId, branch);

    }

    /* see superclass */
    @Override
    public List<Map<String, String>> getMemberHistory(final TerminologyService service, final String referencedComponentId,
        final List<Map<String, String>> versions) throws Exception {

        return SnowstormRefsetMember.getMemberHistory(service, referencedComponentId, versions);

    }

    /* see superclass */
    @Override
    public boolean cacheMemberAncestors(final Refset refset) throws Exception {

        return SnowstormRefsetMember.cacheMemberAncestors(refset);

    }

    /* see superclass */
    @Override
    public List<String> addRefsetMembers(final TerminologyService service, final User user, final Refset refset, final List<String> conceptIds)
        throws Exception {

        return SnowstormRefsetMember.addRefsetMembers(service, user, refset, conceptIds);

    }

    /* see superclass */
    @Override
    public List<String> callAddMemberSingle(final String refsetId, final String url, final String conceptId, final String moduleId) throws Exception {

        return SnowstormRefsetMember.callAddMemberSingle(refsetId, url, conceptId, moduleId);

    }

    /* see superclass */
    @Override
    public List<String> callAddMembersBulk(final String refsetId, final String url, final List<String> conceptIds, final String moduleId) throws Exception {

        return SnowstormRefsetMember.callAddMembersBulk(refsetId, url, conceptIds, moduleId);

    }

    /* see superclass */
    @Override
    public List<String> removeRefsetMembers(final TerminologyService service, final User user, final Refset refset, final String conceptIds) throws Exception {

        return SnowstormRefsetMember.removeRefsetMembers(service, user, refset, conceptIds);

    }

    /* see superclass */
    @Override
    public List<String> callUpdateMemberSingle(final String refsetId, final String url, final JsonNode memberBody) throws Exception {

        return SnowstormRefsetMember.callUpdateMemberSingle(refsetId, url, memberBody);

    }

    /* see superclass */
    @Override
    public List<String> callUpdateMembersBulk(final String refsetId, final String url, final ArrayNode memberBodies) throws Exception {

        return SnowstormRefsetMember.callUpdateMembersBulk(refsetId, url, memberBodies);

    }

    /* see superclass */
    @Override
    public List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception {

        return SnowstormConcept.getConceptIdsFromEcl(branch, ecl);

    }

    /* see superclass */
    @Override
    public String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception {

        return RefsetMemberService.compileUpgradeData(service, user, refsetInternalId);

    }

    /* see superclass */
    @Override
    public String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset, final String inactiveConceptId,
        final String replacementConceptId, final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception {

        return RefsetMemberService.modifyUpgradeConcept(service, user, refset, inactiveConceptId, replacementConceptId, manualReplacementConcept, changed);

    }

    /*
     *
     * MAPPING FUNCTIONALITY
     *
     */

    /* see superclass */
    @Override
    public List<MapSet> getMapSets(final String branch) throws Exception {

        return SnowstormMapping.getMapSets(branch);
    }

    /* see superclass */
    @Override
    public MapSet getMapSet(final String branch, final String code) throws Exception {

        return SnowstormMapping.getMapSet(branch, code);

    }

    /* see superclass */
    @Override
    public ResultListMapping getMappings(final String branch, final String mapSetCode, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        return SnowstormMapping.getMappings(branch, mapSetCode, searchParameters, filter, showOverriddenEntries, conceptCodes);

    }

    /* see superclass */
    @Override
    public Mapping getMapping(final String branch, final String mapSetCode, final String conceptCode, final boolean showOverriddenEntries) throws Exception {

        return SnowstormMapping.getMapping(branch, mapSetCode, conceptCode, null, true, showOverriddenEntries, true);

    }

    /* see superclass */
    @Override
    public Concept getConcept(final String terminology, final String version, final String code) throws Exception {

        return SnowstormConcept.getConcept(terminology, version, code);

    }

    /* see superclass */
    @Override
    public ResultListConcept findConcepts(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        return SnowstormConcept.findConcepts(terminology, version, searchParameters);
    }

    /* see superclass */
    @Override
    public String getName() {

        return ModelUtility.getNameFromClass(SNOMEDSnowstormTerminologyServerHandler.class);
    }

    /* see superclass */
    @Override
    public void setProperties(final Properties properties) throws Exception {

        handlerProperties.putAll(properties);
    }

    /* see superclass */
    @Override
    public List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        return SnowstormMapping.createMappings(mapProject, branch, mapSetCode, mappings);
    }

    /* see superclass */
    @Override
    public List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        return SnowstormMapping.updateMappings(mapProject, branch, mapSetCode, mappings);
    }

    /* see superclass */
    @Override
    public File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest) throws Exception {

        return SnowstormMapping.exportMappings(branch, mapSetCode, mappingExportRequest);
    }

    /* see superclass */
	@Override
	public List<Mapping> importMappings(MapProject mapProject, String branch, MultipartFile mappingFile) throws Exception {
		
		 return SnowstormMapping.importMappings(mapProject, branch,  mappingFile);
	}
	
    
}
