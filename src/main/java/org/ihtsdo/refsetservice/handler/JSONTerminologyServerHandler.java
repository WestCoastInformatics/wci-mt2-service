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
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMapping;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.sync.util.SyncStatistics;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Implements a terminology handler that reads terminology information from pregenerated .json files
 */
public class JSONTerminologyServerHandler implements TerminologyServerHandler {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(JSONTerminologyServerHandler.class);

    /** The handler properties. */
    private final Properties handlerProperties = new Properties();

    /*
     *
     * REFSET FUNCTIONALITY
     *
     */

    /* see superclass */
    @Override
    public String createBranch(final String parentBranchPath, final String branchName) throws Exception {

        final long start = System.currentTimeMillis();
        String refsetBranchPath = null;
        final String url = SnowstormConnection.getBaseUrl() + "branches";
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("name", branchName).put("parent", parentBranchPath);

        LOG.debug("createBranch URL: " + url + " ; body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                final String error = "Could not create branch " + parentBranchPath + "/" + branchName;
                LOG.error(error);
                throw new Exception(error);
            }

            final String resultString = response.readEntity(String.class);

            final JsonNode root = mapper.readTree(resultString.toString());
            final JsonNode rootNode = root;

            if (rootNode.has("path")) {

                refsetBranchPath = rootNode.get("path").asText();
            }

            LOG.info("Created branch " + refsetBranchPath + ". Time: " + (System.currentTimeMillis() - start));
        }

        return refsetBranchPath;
    }

    /* see superclass */
    @Override
    public boolean deleteBranch(final String branchPath) throws Exception {

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getBaseUrl() + "admin/" + branchPath + "/actions/hard-delete";

        LOG.debug("deleteBranch URL: " + url);

        try (final Response response = SnowstormConnection.deleteResponse(url, null)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                LOG.info("Deleted branch " + branchPath + ". Time: " + (System.currentTimeMillis() - start));
                return true;
            } else {

                LOG.error("Could not delete branch " + branchPath);
                return false;
            }

        }
    }

    /* see superclass */
    @Override
    public boolean doesBranchExist(final String branchPath) throws Exception {

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getBaseUrl() + "branches/" + branchPath;

        LOG.debug("doesBranchExist URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // If Rest call is successful then branch exists
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                LOG.debug("doesBranchExist: true. Time: " + (System.currentTimeMillis() - start));
                return true;
            } else {

                LOG.debug("doesBranchExist: false. Time: " + (System.currentTimeMillis() - start));
                return false;
            }

        }
    }

    /* see superclass */
    @Override
    public List<String> getBranchChildren(final String branchPath) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + "branches/" + branchPath + "children?immediateChildren=true&page=0&size=9000";
        final List<String> childBranchPaths = new ArrayList<>();

        LOG.debug("getBranchChildren URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + url + "' wasn't successful. " + response.getStatus() + ": " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.iterator();

            if (iterator.hasNext()) {

                final JsonNode childNode = iterator.next();
                childBranchPaths.add(childNode.get("path").asText());
            }
        }

        return childBranchPaths;
    }

    /* see superclass */
    @Override
    public void mergeBranch(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean rebase) throws Exception {

        final long start = System.currentTimeMillis();
        final String mergeUrl = SnowstormConnection.getBaseUrl() + "merges";
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);
        boolean jobDone = false;

        if (comment != null) {

            body.put("commitComment", comment);
        }

        if (rebase) {

            final String reviewId = mergeRebaseReview(sourceBranchPath, targetBranchPath);
            body.put("reviewId", reviewId);
        }

        LOG.debug("mergeBranch URL: " + mergeUrl + " ; body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(mergeUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                LOG.error("mergeBranch response status: " + response.getStatus());
                LOG.error("mergeBranch response status reason: " + response.getStatusInfo().getReasonPhrase());
                final String error = "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath;
                LOG.error(error);
                throw new Exception(error);
            }

            final String jobStatusUrl = response.getHeaderString("Location");

            LOG.debug("Merge status info at " + jobStatusUrl);

            while (!jobDone) {

                try (final Response mergeInfoResponse = SnowstormConnection.getResponse(jobStatusUrl)) {

                    final String resultString = mergeInfoResponse.readEntity(String.class);
                    final JsonNode root = mapper.readTree(resultString.toString());
                    final String status = root.get("status").asText();

                    LOG.info("Merge status is: " + status);

                    if (status.equals("FAILED")) {

                        final String message = root.get("message").asText();
                        jobDone = true;

                        if (!message.contains("is not meaningful")) {

                            final String error = "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Error: " + message;
                            LOG.error(error);
                            throw new Exception(error);

                        } else {
                            LOG.debug("Merge did not occurr. " + message);
                        }

                    } else if (status.equals("PENDING") || status.equals("IN_PROGRESS") || status.equals("SCHEDULED")) {

                        LOG.debug("Merge hasn't finished yet...");

                        try {
                            Thread.sleep(300);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }

                    } else {

                        jobDone = true;

                        if (rebase) {

                            // in a rebase that has changes clear all the caches for the
                            // target branch
                            RefsetService.clearAllRefsetCaches(targetBranchPath);
                            RefsetMemberService.clearAllMemberCaches(targetBranchPath);
                        } else {

                            try {

                                LOG.debug("Merge promotion sleep 1000ms to let snowstorm caches update.");
                                Thread.sleep(1000);
                            } catch (final InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }

                            // final check that the promotion has finished.
                            boolean stateGood = false;
                            final String stateUrl = SnowstormConnection.getBaseUrl() + "branches/" + targetBranchPath;
                            LOG.debug("Promoted branch state info at " + stateUrl);

                            while (!stateGood) {

                                try (final Response stateResponse = SnowstormConnection.getResponse(stateUrl)) {

                                    final String stateResultString = stateResponse.readEntity(String.class);
                                    final JsonNode stateRoot = mapper.readTree(stateResultString.toString());
                                    final String state = stateRoot.get("state").asText();

                                    LOG.info("Promoted branch state is: " + state);

                                    if (state.equals("FORWARD") || state.equals("CURRENT") || state.equals("UP_TO_DATE")) {
                                        stateGood = true;

                                    } else {

                                        try {

                                            LOG.debug("Merge promotion sleep 300ms to let snowstorm caches update.");
                                            Thread.sleep(300);
                                        } catch (final InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                }
                            }
                        }

                        LOG.info("Merged branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Time: " + (System.currentTimeMillis() - start));
                    }
                }
            }
        }
    }

    /* see superclass */
    @Override
    public String mergeRebaseReview(final String sourceBranchPath, final String targetBranchPath) throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);
        final String reviewUrl = SnowstormConnection.getBaseUrl() + "merge-reviews";
        String jobStatusUrl = null;
        boolean jobDone = false;
        String reviewId = "";
        LOG.debug("mergeRebaseReview review URL: " + reviewUrl + " ; body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(reviewUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                final String error = "Could not review branch rebase of " + sourceBranchPath + " into branch " + targetBranchPath;
                LOG.error(error);
                throw new Exception(error);
            }

            jobStatusUrl = response.getHeaderString("Location");
            final String[] location = jobStatusUrl.split("/");
            reviewId = location[location.length - 1];
        }

        LOG.debug("mergeRebaseReview review job status URL: " + jobStatusUrl);

        while (!jobDone) {

            try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

                String error = "Could not review merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". ";

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    LOG.error(error + " Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final String status = root.get("status").asText();
                LOG.debug("merge review status: " + status);

                if (status.equalsIgnoreCase("PENDING")) {

                    LOG.debug("Merge review hasn't finished yet...");

                    try {
                        Thread.sleep(300);
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                } else if (status.equalsIgnoreCase("failed")) {

                    error += "Job failed with: " + root.get("message").asText();
                    LOG.error(error);
                    throw new Exception(error);

                } else if (status.equalsIgnoreCase("stale")) {

                    reviewId = mergeRebaseReview(sourceBranchPath, targetBranchPath);
                    jobDone = true;

                } else {
                    jobDone = true;
                }
            }
        }

        return reviewId;
    }

    /* see superclass */
    @Override
    public String getNewRefsetId(final String editionBranchPath) throws Exception {

        String refsetConceptId = null;
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode();
        final String projectBranchPath = WorkflowService.getProjectBranchPath(editionBranchPath);
        String tempBranchPath = null;

        if (!doesBranchExist(projectBranchPath)) {
            createBranch(editionBranchPath, WorkflowService.getProjectBranchName(editionBranchPath));
        }

        if (doesBranchExist(projectBranchPath + "/" + WorkflowService.TEMP_BRANCH_NAME)) {
            tempBranchPath = projectBranchPath + "/" + WorkflowService.TEMP_BRANCH_NAME;
        } else {
            tempBranchPath = createBranch(projectBranchPath, WorkflowService.TEMP_BRANCH_NAME);
        }

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getBaseUrl() + "browser/" + tempBranchPath + "/" + "concepts/";

        LOG.debug("getNewRefsetId URL: " + url);
        LOG.debug("getNewRefsetId URL Body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + url + "' wasn't successful. " + response.readEntity(String.class));
            }

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final String resultString = response.readEntity(String.class);

            final JsonNode root = mapper.readTree(resultString.toString());
            final JsonNode conceptNode = root;

            if (conceptNode.has("conceptId")) {

                refsetConceptId = conceptNode.get("conceptId").asText();
            } else {

                throw new Exception("Unable to create new refset concept.");
            }

        }

        LOG.debug("New Refset ID " + refsetConceptId + ". Time: " + (System.currentTimeMillis() - start));
        return refsetConceptId;
    }

    /* see superclass */
    @Override
    public List<String> getBranchVersions(final String editionPath) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + "branches/" + editionPath + "/children?immediateChildren=true";
        final List<String> branchCache = RefsetService.getCacheForBranchVersions(editionPath);

        // check if the concept call has been cached
        if (branchCache.size() > 0) {

            LOG.debug("getBranchVersions USING CACHE");
            return branchCache;
        }

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("Unable to get edition versions. Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> branchIterator = root.iterator();

            // get versions from edition as long as active & within edition's module
            while (branchIterator.hasNext()) {

                final JsonNode child = branchIterator.next();
                final String childBranch = child.get("path").asText();
                String childDate = childBranch.replace(editionPath, "");

                if (childDate.startsWith("/")) {

                    childDate = childDate.substring(1);
                }

                // Only get pure date branches
                if (childDate.matches("^\\d{4}-\\d{2}-\\d{2}$")) {

                    final Date branchDate = DateUtility.getDate(childDate, DateUtility.DATE_FORMAT_REVERSE, null);

                    if (branchDate.before(new Date())) {

                        branchCache.add(childDate);
                    }

                }

                // stop when branch does not start with a date
                else if (!childDate.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {

                    break;
                }

            }

            // sort the results in reverse order since that is the usual way they are
            // consumed
            Collections.sort(branchCache, (o1, o2) -> (o2.compareTo(o1)));
        }

        RefsetService.BRANCH_VERSION_CACHE.put(editionPath, branchCache);
        return branchCache;
    }

    /* see superclass */
    @Override
    public String generateVersionFile(final String entityString) throws Exception {
        /*-
         * Example of entity
         {
        "branchPath": "MAIN/SNOMEDCT-BE/2020-03-15",
        "conceptsAndRelationshipsOnly": false,
        "filenameEffectiveDate": "20210315",
        "legacyZipNaming": false,
        "refsetIds": [
            "741000172102"
        ],
        "startEffectiveTime": "20210315",
        "transientEffectiveTime": "20210315",
        "type": "SNAPSHOT",
        "unpromotedChangesOnly": false
        } */

        LOG.debug(entityString);

        // Call Snowstorm to create RF2 file
        final String snowstormExportApiUrl = SnowstormConnection.getBaseUrl() + "exports";
        LOG.debug("Snowstorm Export API URL: " + snowstormExportApiUrl + entityString);

        String snowVersionFileUrl = "";

        try (final Response response = SnowstormConnection.postResponse(snowstormExportApiUrl, entityString);) {

            snowVersionFileUrl = response.getLocation().toString() + "/archive";
            LOG.debug("Snowstorm File URL: " + snowVersionFileUrl);

        } catch (final Exception ex) {
            throw new Exception("Could not generate the Rf2 file by snowstorm with : " + entityString, ex);

        }

        return snowVersionFileUrl;
    }

    /* see superclass */
    @Override
    public void downloadGeneratedFile(final String snowVersionFileUrl, final String localSnowVersionPath) throws Exception {

        // Download generated file from Snowstorm
        LOG.debug("Local snow version file path is: " + localSnowVersionPath);

        // Download the Snowstorm file
        try (final InputStream inputStream = SnowstormConnection.getFileDownload(snowVersionFileUrl);
            final ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
            final FileOutputStream fileOutputStream = new FileOutputStream(localSnowVersionPath);
            final FileChannel fileChannel = fileOutputStream.getChannel()) {

            fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
        } catch (final Exception ex) {
            throw new Exception("Failed to download the Snowstorm generated RF2 file: " + ex.getMessage(), ex);
        }
    }

    /* see superclass */
    @Override
    public Map<String, String> getModuleNames(final Edition edition) throws Exception {

        // Create Snowstorm URL
        final String conceptSearchUrl = SnowstormConnection.getBaseUrl() + edition.getBranch() + "/concepts/search";
        final String bodyBase = "{\"limit\": 1000, ";
        String bodyConceptIds = "\"conceptIds\":[";
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, String> moduleNames = new HashMap<>();

        for (final String moduleId : edition.getModules()) {
            bodyConceptIds += "\"" + moduleId + "\",";
        }

        bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",") + "]";

        final String searchBody = bodyBase + bodyConceptIds + "}";
        LOG.debug("getModuleNames URL: " + conceptSearchUrl);
        LOG.debug("getModuleNames BODY: " + searchBody);

        try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, searchBody)) {

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("call to url '" + conceptSearchUrl + "' for module name lookup wasn't successful. Status: " + response.getStatus()
                    + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.get("items").iterator();

            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final String conceptId = conceptNode.get("conceptId").asText();
                String name = "";

                if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {
                    name = conceptNode.get("fsn").get("term").asText();
                }

                moduleNames.put(conceptId, name);
            }
        }

        return moduleNames;
    }

    /* see superclass */
    @Override
    public List<Edition> getAffiliateEditionList() throws Exception {

        final List<Edition> editionList = new ArrayList<>();
        final String url = SnowstormConnection.getBaseUrl() + "codesystems";
        LOG.info("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
            final SyncUtilities syncUtilities = new SyncUtilities(new SyncDatabaseHandler(null, new SyncStatistics()));

            while (organizationIterator.hasNext()) {

                final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

                while (codeSystems.hasNext()) {

                    final JsonNode codeSystem = codeSystems.next();

                    // Check for invalid or ignored code systems
                    if (!codeSystem.has("shortName")) {
                        continue;
                    }

                    final String editionShortName = codeSystem.get("shortName").asText();
                    final String maintainerType = syncUtilities.identifyMaintainerType(codeSystem, editionShortName);

                    // Skip inactive code systems
                    if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                        continue;
                    }

                    // Code System has been defined as to-be-ignored (either by specifying
                    // name or
                    // shortname).
                    else if (syncUtilities.getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {
                        continue;
                    }

                    // deal only with Type-3 (non-Managed Service)
                    // else if (!maintainerType.equalsIgnoreCase("Managed Service")) {
                    // continue;
                    // }

                    // deal only with official affiliate code systems
                    else if (!editionShortName.toLowerCase().contains("-affiliate")) {
                        continue;
                    }

                    final String editionName = codeSystem.get("name").asText();
                    final String branch = codeSystem.get("branchPath").asText();
                    final String defaultLanguageCode = syncUtilities.identifyDefaultLanguageCode(codeSystem, editionName);
                    final Set<String> defaultLanguageRefsets = syncUtilities.identifyDefaultLanguageRefsets(codeSystem, editionShortName, branch);

                    // Affiliates (on any extension) should not have the ability to choose
                    // modules.
                    // Fix to 1201891009 |SNOMED CT Community content module (core
                    // metadata
                    // concept)| for all affiliate refsets
                    final Set<String> editionModules = new HashSet<>(Arrays.asList("1201891009"));

                    final Edition edition = new Edition();
                    edition.setShortName(editionShortName);
                    edition.setName(editionName);
                    edition.setBranch(branch);
                    edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
                    edition.setModules(editionModules);
                    edition.setDefaultLanguageCode(defaultLanguageCode);
                    edition.setMaintainerType(maintainerType);
                    edition.setActive(true);

                    editionList.add(edition);
                }
            }
        }

        return editionList;
    }

    /* see superclass */
    @Override
    public Object createRefset(final TerminologyService service, final User user, final Refset refsetEditParameters) throws Exception {

        String newInternalRefsetId = null;
        Refset refset = null;
        String refsetConceptId = refsetEditParameters.getRefsetId();
        String parentConceptId = refsetEditParameters.getParentConceptId();
        Edition edition = null;
        Project project = null;
        List<String> conceptIdList = new ArrayList<>();
        final String moduleId = refsetEditParameters.getModuleId();

        // get the edition and project for the new refset
        if (refsetConceptId != null && RefsetService.doesRefsetExist(refsetConceptId, null)) {

            return "Error - Concept Id '" + refsetConceptId + "' is already used as a reference set.";
        }

        project = service.get(refsetEditParameters.getProjectId(), Project.class);

        if (project == null) {

            throw new Exception("Project Id: " + refsetEditParameters.getProjectId() + " does not exist in the RT2 database");
        }

        edition = project.getEdition();

        // if a new refset concept needs to be created get the ID to use
        if (refsetConceptId == null) {

            refsetConceptId = WorkflowService.getNewRefsetId(edition.getBranch());
        }

        // create a refset branch for the new refset
        final String refsetBranchId = WorkflowService.generateBranchId();
        final String refsetBranch = WorkflowService.createRefsetBranch(edition.getBranch(), refsetConceptId, refsetBranchId, refsetEditParameters.isLocalSet());

        // if a new refset concept needs to be created
        if (refsetEditParameters.getRefsetId() == null) {

            // if null set the parent to "Simple Type Reference Set"
            if (parentConceptId == null) {

                parentConceptId = RefsetService.SIMPLE_TYPE_REFERENCE_SET;
            }

            final ObjectMapper mapper = new ObjectMapper();

            final ObjectNode descriptions = mapper.createObjectNode().set("descriptions",
                mapper.createArrayNode()
                    .add(mapper.createObjectNode().put("moduleId", moduleId).put("term", refsetEditParameters.getName()).put("typeId", "900000000000013009")
                        .put("caseSignificance", "CASE_INSENSITIVE").put("lang", "en")
                        .set("acceptabilityMap", mapper.createObjectNode().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED")))
                    .add(mapper.createObjectNode().put("moduleId", moduleId).put("term", refsetEditParameters.getName() + " (foundation metadata concept)")
                        .put("typeId", "900000000000003001").put("caseSignificance", "CASE_INSENSITIVE").put("lang", "en")
                        .set("acceptabilityMap", mapper.createObjectNode().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED"))));

            final ObjectNode relationships = mapper.createObjectNode().set("relationships",
                mapper.createArrayNode()
                    .add(mapper.createObjectNode().put("moduleId", moduleId).put("destinationId", parentConceptId).put("typeId", "116680003").put("groupId", 0)
                        .put("lang", "en")
                        .set("acceptabilityMap", mapper.createObjectNode().put("900000000000509007", "PREFERRED").put("900000000000508004", "PREFERRED")))
                    .add(mapper.createObjectNode().put("destinationId", "446609009").put("typeId", "116680003").put("groupId", 0)));

            final ObjectNode classAxioms = mapper.createObjectNode().set("classAxioms",
                mapper.createArrayNode().add(mapper.createObjectNode().put("moduleId", moduleId).put("definitionStatusId", "900000000000074008")
                    .set("relationships", mapper.createArrayNode().add(
                        mapper.createObjectNode().put("moduleId", moduleId).put("destinationId", "446609009").put("typeId", "116680003").put("groupId", 0)))));

            final long start = System.currentTimeMillis();
            final ObjectNode body = mapper.createObjectNode().put("conceptId", refsetConceptId).put("moduleId", moduleId);
            body.setAll(relationships);
            body.setAll(classAxioms);
            body.setAll(descriptions);

            final String url = SnowstormConnection.getBaseUrl() + "browser/" + refsetBranch + "/" + "concepts/";

            LOG.debug("createRefset URL: " + url);
            LOG.debug("createRefset URL body: " + body.toString());

            try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    throw new Exception(
                        "call to url '" + url + "' wasn't successful. " + response.getStatus() + ": " + response.getStatusInfo().getReasonPhrase());
                }

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception(Integer.toString(response.getStatus()));
                }

                final String resultString = response.readEntity(String.class);

                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode conceptNode = root;

                if (conceptNode.has("conceptId")) {

                    refsetConceptId = conceptNode.get("conceptId").asText();
                } else {

                    throw new Exception("Unable to create new reference set concept.");
                }

            }

            LOG.debug("Create Refset: newly created refset concept ID: " + refsetConceptId + ". Time: " + (System.currentTimeMillis() - start));
        }

        final String editBranchId = WorkflowService.generateBranchId();

        final long start = System.currentTimeMillis();

        // add the new refset to the database
        refset = new Refset(refsetEditParameters);
        refset.setRefsetId(refsetConceptId);
        refset.setVersionStatus(Refset.IN_DEVELOPMENT);
        refset.setWorkflowStatus(WorkflowService.READY_FOR_EDIT);
        refset.setProject(project);
        refset.setVersionDate(null);
        refset.setEditBranchId(editBranchId);
        refset.setRefsetBranchId(refsetBranchId);

        if (refset.getType().equals(Refset.INTENSIONAL)) {

            // Add definition clauses to the DB
            for (final DefinitionClause clause : refset.getDefinitionClauses()) {

                service.add(clause);
            }

        }

        // Add an object
        service.add(refset);
        newInternalRefsetId = refset.getId();

        WorkflowService.createEditBranch(service, user, refset, editBranchId);

        // Add a workflow history entry for CREATE and then update the workflow to
        // IN_EDIT
        WorkflowService.addWorkflowHistory(service, user, WorkflowService.CREATE, refset, "");
        refset = WorkflowService.setWorkflowStatus(service, user, WorkflowService.EDIT, refset, "", WorkflowService.IN_EDIT, user.getUserName());

        // if cloning a refset this is where extensional members are copied over
        // String originBranchPath = edition.getBranch();

        // if (refsetEditParameters.getVersionDate() != null) {
        // originBranchPath += "/" +
        // getFormattedRefsetDate(refsetEditParameters.getVersionDate());
        // }

        RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(newInternalRefsetId, new HashMap<>());

        if (refset.getType().equals(Refset.INTENSIONAL)) {

            refset.setBranchPath(RefsetService.getBranchPath(refset));

            try {

                final String ecl = RefsetService.getEclFromDefinition(refsetEditParameters.getDefinitionClauses());

                // get the list of concepts from the ECL
                conceptIdList = RefsetMemberService.getConceptIdsFromEcl(refset.getBranchPath(), ecl);

                // if there are no concepts in the definition then stop the creation
                if (conceptIdList.size() == 0) {

                    return "Error - Definition returns no concepts.";
                }

            } catch (final Exception e) {

                return "Error - Invalid ECL Definition";
            }

            // add the list of concepts as members to the refset
            RefsetMemberService.addRefsetMembers(service, user, refset, conceptIdList);
            WorkflowService.mergeEditIntoRefsetBranch(refset.getEditionBranch(), refset.getRefsetId(), editBranchId, refsetBranchId,
                "Initial intensional refset creation.", refset.isLocalSet());
        }

        RefsetService.clearAllRefsetCaches(refset.getEditionBranch());

        LOG.info("Create Refset: Refset " + refset.getRefsetId() + " successfully added. Time: " + (System.currentTimeMillis() - start));
        LOG.debug("Create Refset: Refset: " + ModelUtility.toJson(refset));

        return refset;
    }

    /* see superclass */
    @Override
    public ResultListConcept getRefsetConcepts(final TerminologyService service, final String branch, final boolean areParentConcepts) throws Exception {

        final ResultListConcept results = new ResultListConcept();
        final Set<String> existingRefsetIds = new HashSet<>();
        String ecl = StringUtility.encodeValue(QueryParserBase.escape("<<" + RefsetService.SIMPLE_TYPE_REFERENCE_SET));

        if (!areParentConcepts) {

            ecl = StringUtility.encodeValue(QueryParserBase.escape("<" + RefsetService.SIMPLE_TYPE_REFERENCE_SET));
        }

        final List<Edition> editions = RefsetService.getEditionForBranch(branch);

        String modules = "";

        if (editions.size() > 0) {

            for (final Edition edition : editions) {
                modules += edition.getModules().stream().collect(Collectors.joining(",")) + ", ";
            }
        }

        modules += RefsetService.SNOMED_CORE_MODULE_ID;

        final String url = SnowstormConnection.getBaseUrl() + branch + "/" + "concepts?ecl=" + ecl + "&limit=1000&module=" + modules;

        LOG.debug("getRefsetConcepts URL: " + url);

        // If we are looking for concepts to represent a refset, then we are
        // filtering
        // out those concept that are currently refsets
        if (!areParentConcepts) {

            // get all the existing refsets for latest branch version
            final String query = "(latestPublishedVersion: true AND hasVersionInDevelopment: false) OR versionStatus: (" + Refset.IN_DEVELOPMENT + ")";
            final ResultList<Refset> refsets = service.find(query, null, Refset.class, null);

            for (final Refset refset : refsets.getItems()) {

                if (!existingRefsetIds.contains(refset.getRefsetId())) {

                    existingRefsetIds.add(refset.getRefsetId());
                }

            }

            LOG.debug("getRefsetConcepts existingRefsetIds: " + existingRefsetIds);
        }

        final Set<String> excludeList = RefsetService.conceptsToRemove(!areParentConcepts);

        // update the concept with the new data
        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("Unable to get reference set concepts. Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.get("items").iterator();

            // loop thru the returned member details and inactivate it or add it to
            // the list
            // to delete
            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final Concept concept = new Concept();
                final String conceptId = conceptNode.get("conceptId").asText();

                // if this isn't for a parent concept and the refset already exists then
                // skip it
                if ((!areParentConcepts && existingRefsetIds.contains(conceptId)) || excludeList.contains(conceptId)) {

                    continue;
                }

                concept.setCode(conceptId);
                concept.setName(conceptNode.get("pt").get("term").asText());
                concept.setTerminology("SNOMEDCT");

                results.getItems().add(concept);
            }

            // sort the results
            Collections.sort(results.getItems(), (o1, o2) -> (o1.getName().compareTo(o2.getName())));
        }

        return results;
    }

    /* see superclass */
    @Override
    public void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId) throws Exception {

        // first retrieve the concept so all fields will be present for the update
        final String refsetId = refset.getRefsetId();
        final String branch = refset.getBranchPath();
        final String url = SnowstormConnection.getBaseUrl() + "browser/" + branch + "/" + "concepts/" + refsetId;
        final ObjectMapper mapper = new ObjectMapper();
        ObjectNode memberBody = null;

        LOG.debug("updateRefsetConcept URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("Unable to retrieve reference set concept: " + refsetId + " Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            // create the body entity for the update call from the retrieved concept
            final String resultString = response.readEntity(String.class);
            memberBody = (ObjectNode) mapper.readTree(resultString.toString()).deepCopy();
        }

        if (active != refset.isActive()) {

            LOG.info("Changing refset concept active status to: " + active);

            // set the concept status
            memberBody.put("active", active);

            // set the concept inactivation indicator
            if (!active) {
                memberBody.put("inactivationIndicator", "OUTDATED");
            } else {
                memberBody.put("inactivationIndicator", "");
            }

            // loop thru the class axioms and set the status
            final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();

            while (axiomIterator.hasNext()) {

                final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
                axiomNode.put("active", active);
            }

            // loop thru the relationships and set the status
            final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();

            while (relationshipsIterator.hasNext()) {

                final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
                relationshipsNode.put("active", active);
            }
        }

        if (!moduleId.equals(refset.getModuleId())) {

            LOG.info("Changing Refset Concept Module ID from: " + refset.getModuleId() + " to: " + moduleId);
            memberBody.put("moduleId", moduleId);

            // loop thru the descriptions and set the moduleId
            final Iterator<JsonNode> descriptionsIterator = memberBody.get("descriptions").iterator();

            while (descriptionsIterator.hasNext()) {

                final ObjectNode descriptionNode = (ObjectNode) descriptionsIterator.next();
                descriptionNode.put("moduleId", moduleId);
            }

            // loop thru the class axioms and set the moduleId
            final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();

            while (axiomIterator.hasNext()) {

                final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
                axiomNode.put("moduleId", moduleId);

                // loop thru the axiom relationships and set the moduleId
                final Iterator<JsonNode> relationshipsIterator = axiomNode.get("relationships").iterator();

                while (relationshipsIterator.hasNext()) {

                    final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
                    relationshipsNode.put("moduleId", moduleId);
                }
            }

            // loop thru the relationships and set the moduleId
            final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();

            while (relationshipsIterator.hasNext()) {

                final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
                relationshipsNode.put("moduleId", moduleId);
            }
        }

        LOG.debug("updateRefsetConcept update concept URL body: " + memberBody.toString());

        // update the concept with the new data
        try (final Response response = SnowstormConnection.putResponse(url, memberBody.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to update reference set concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            LOG.info("updateRefsetConcept refset concept: " + refsetId);
        }
    }

    /* Refset Member Service calls */

    /* see superclass */
    @Override
    public List<Concept> getAllRefsetMembers(final TerminologyService service, final String refsetInternalId, final String searchAfter,
        final List<Concept> concepts) throws Exception {

        final Refset refset = service.get(refsetInternalId, Refset.class);

        if (refset == null) {

            throw new Exception("Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
        }

        final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/concepts?ecl=%5E%20" + refset.getRefsetId()
            + "&offset=0&limit=10000" + (searchAfter.contentEquals("") ? "" : "&searchAfter=" + searchAfter);

        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setGetMembershipInformation(true);
        lookupParameters.setGetDescriptions(true);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final ResultListConcept conceptList = RefsetMemberService.populateConcepts(root, refset, lookupParameters);
            concepts.addAll(conceptList.getItems());

            final String newSearchAfter = (root.get("searchAfter") != null ? root.get("searchAfter").asText() : "");
            if (StringUtils.isNoneBlank(newSearchAfter)) {

                getAllRefsetMembers(service, refsetInternalId, newSearchAfter, concepts);
            }

        }

        LOG.debug("Refset has " + concepts.size() + " members");

        return concepts;
    }

    /* see superclass */
    @Override
    public Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception {

        // refsetId: "12345" AND privateRefset:false AND term:"blood" AND
        // name:"work"
        final Set<String> refsetIds = new HashSet<>();

        // if there are no query terms just exit the method
        if (snowstormQuery.equals("")) {

            return refsetIds;
        }

        final String url = SnowstormConnection.getBaseUrl() + "multisearch/descriptions/referencesets?active=true&offset=0&limit=1&term="
            + StringUtility.encodeValue(snowstormQuery);

        LOG.debug("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            if (root.get("buckets") != null) {

                final Iterator<String> membershipIterator = root.get("buckets").get("membership").fieldNames();

                while (membershipIterator.hasNext()) {

                    refsetIds.add(membershipIterator.next());
                }

            }

        } catch (final Exception ex) {

            throw new Exception("Could not retrieve Reference Set members from snowstorm: " + ex.getMessage(), ex);
        }

        LOG.debug("searchDirectoryMembers refsetQuery: {}", refsetIds);
        return refsetIds;
    }

    /* see superclass */
    @Override
    public Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl, final Set<String> nonPublishedBranchPaths)
        throws Exception {

        final String query = searchParameters.getQuery();
        final List<String> directoryColumns =
            Arrays.asList("id", "refsetId", "name", "editionName", "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
        String snowstormQuery = "";
        final String[] queryParts = query.split(" AND ");
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode();
        final ArrayNode bodyPaths = mapper.createArrayNode();

        for (final String queryPart : queryParts) {

            final String[] keyValue = queryPart.split(":");

            if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {
                continue;
            } else {
                snowstormQuery += queryPart + " AND ";
            }
        }

        snowstormQuery = StringUtils.removeEnd(snowstormQuery, " AND ");

        for (final String branchPath : nonPublishedBranchPaths) {
            bodyPaths.add(branchPath);
        }

        body.set("branches", bodyPaths);

        final String url = SnowstormConnection.getBaseUrl() + "multisearch/descriptions?active=true&offset=0&limit=10000" + "&ecl="
            + StringUtility.encodeValue(ecl) + "&term=" + StringUtility.encodeValue(snowstormQuery);

        LOG.debug("searchMultisearchDescriptions: Search Refset Concepts descriptions URL: " + url);
        LOG.debug("!!!!! searchMultisearchDescriptions: Search Refset Concepts descriptions BODY: " + body);

        try (Response response = SnowstormConnection.postResponse(url, body.toString())) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);
            final JsonNode root = mapper.readTree(resultString.toString());

            final Set<String> conceptIds = new HashSet<>();

            if (root.get("items") != null) {

                final JsonNode itemsNode = root.get("items");

                if (itemsNode.isArray()) {

                    for (final JsonNode itemNode : itemsNode) {
                        conceptIds.add(itemNode.get("concept").get("conceptId").asText());
                    }

                }

            }

            return conceptIds;
        }
    }

    /* see superclass */
    @Override
    public String getMemberSctids(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception {

        final String pagingParams = "&limit=" + limit + "&searchAfter=" + searchAfter;

        final String url = SnowstormConnection.getBaseUrl() + "" + branchPath + "/members?referenceSet=" + refsetId + "&" + pagingParams;

        LOG.debug("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);
            return resultString;

        } catch (final Exception ex) {

            throw new Exception("Could not retrieve Reference Set members from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /* see superclass */
    @Override
    public void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        if (conceptsToProcess == null || conceptsToProcess.isEmpty()) {
            return;
        }

        // Create Snowstorm URL
        final String fullSnowstormUrl = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/descriptions?limit="
            + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + "&conceptIds="
            + conceptsToProcess.stream().map(Concept::getCode).collect(Collectors.joining(","));

        // Call Snowstorm
        try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode allDescriptionNodes = root.get("items");
            final Iterator<JsonNode> descriptionIterator = allDescriptionNodes.iterator();
            final HashMap<String, Set<JsonNode>> conceptDescriptionNodes = new HashMap<>();

            // Assign descriptions to proper concept
            while (descriptionIterator.hasNext()) {

                final JsonNode descriptionNode = descriptionIterator.next();

                if (descriptionNode.get("active").asBoolean()) {

                    final String conceptId = descriptionNode.get("conceptId").asText();

                    if (!conceptDescriptionNodes.containsKey(conceptId)) {

                        conceptDescriptionNodes.put(conceptId, new HashSet<JsonNode>());
                    }

                    conceptDescriptionNodes.get(conceptId).add(descriptionNode);
                }

            }

            // Process and sort each concept's descriptions
            final Map<String, List<Map<String, String>>> conceptDescriptionMap = new HashMap<>();

            final List<String> nonDefaultPreferredTerms = RefsetMemberService.identifyNonDefaultPreferredTerms(refset.getEdition());

            for (final String conceptId : conceptDescriptionNodes.keySet()) {

                final Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
                Set<Map<String, String>> descriptions = new HashSet<>();

                descriptions =
                    RefsetMemberService.processDescriptionNodes(descriptionNodes, refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

                final List<Map<String, String>> sortedDescriptions =
                    RefsetMemberService.sortConceptDescriptions(conceptId, descriptions, refset, nonDefaultPreferredTerms);
                conceptDescriptionMap.put(conceptId, sortedDescriptions);
            }

            // Populate concept with description-based data
            for (final Concept concept : conceptsToProcess) {

                final List<Map<String, String>> descriptions = conceptDescriptionMap.get(concept.getCode());

                if (descriptions == null || descriptions.size() == 0) {

                    LOG.debug("Description not retrieved for concept " + concept.getCode());
                    continue;
                }

                concept.setDescriptions(descriptions);

                if (descriptions.get(0) != null) {

                    concept.setName(descriptions.get(0).get(RefsetMemberService.DESCRIPTION_TERM));
                } else {

                    for (final Map<String, String> description : descriptions) {

                        if (description == null) {

                            continue;
                        }

                        if (description.get(RefsetMemberService.LANGUAGE_ID).equals(RefsetMemberService.PREFERRED_TERM_EN)) {

                            concept.setName(description.get(RefsetMemberService.DESCRIPTION_TERM));
                            break;
                        }

                    }

                }

            }

        } catch (final Exception ex) {

            LOG.error("Could not retrieve descriptions " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /* see superclass */
    @Override
    public void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        final StringBuffer conceptIds = new StringBuffer();

        // Create Snowstorm URL
        final String url =
            SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/concepts?limit=3000&includeLeafFlag=true&form=inferred";

        boolean firstTime = true;

        for (final Concept concept : conceptsToProcess) {

            if (firstTime) {

                firstTime = false;
            } else {

                conceptIds.append(",");
            }

            conceptIds.append(concept.getCode());
        }

        // Call Snowstorm
        // LOG.debug("Get Concept Leaf Status URL: " + url + "&conceptIds=" +
        // conceptIds);
        final String fullSnowstormUrl = url + "&conceptIds=" + conceptIds;
        try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode allConceptNodes = root.get("items");
            final Iterator<JsonNode> conceptIterator = allConceptNodes.iterator();
            final HashMap<String, JsonNode> conceptNodes = new HashMap<>();

            // Map concept info
            while (conceptIterator.hasNext()) {

                final JsonNode conceptNode = conceptIterator.next();

                final String conceptId = conceptNode.get("conceptId").asText();
                conceptNodes.put(conceptId, conceptNode);
            }

            // Populate concept with child info
            for (final Concept concept : conceptsToProcess) {

                final JsonNode conceptNode = conceptNodes.get(concept.getCode());

                if (conceptNode == null || conceptNode.size() == 0) {

                    LOG.debug("Concept info not retrieved for concept " + concept.getCode());
                    continue;
                }

                if (conceptNode.has("isLeafInferred")) {

                    concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                }

            }

        } catch (final Exception ex) {

            LOG.error("Could not retrieve concept leaf info " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /* see superclass */
    @Override
    public Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception {

        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setNonDefaultPreferredTerms(RefsetMemberService.identifyNonDefaultPreferredTerms(refset.getEdition()));
        lookupParameters.setGetDescriptions(true);
        final String branchPath = RefsetMemberService.getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + "-" + conceptId;
        final Map<String, Concept> branchCache = RefsetMemberService.getCacheForTaxonomySearchAncestors(branchPath);
        final Concept concept = new Concept();
        concept.setCode(conceptId);

        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {

            LOG.debug("getConceptAncestors USING CACHE");
            return branchCache.get(cacheString);
        }

        // Create Snowstorm URL
        final String url = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/concepts/ancestor-paths?conceptIds=" + conceptId;

        // Call Snowstorm
        LOG.debug("Get Concept Ancestors URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            // read the results of the call for ancestors for many concepts
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.iterator();

            // loop thru each concept to get the ancestor path for it
            while (iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                // final String nodeConceptId = conceptNode.get("conceptId").asText();
                final JsonNode ancestorPathNode = conceptNode.get("ancestorPath");

                // get the ancestor list and reverse the order so the taxonomy
                // root is first
                final ResultListConcept ancestorList = RefsetMemberService.populateConcepts(ancestorPathNode, refset, lookupParameters);
                final List<Concept> parents = ancestorList.getItems();
                Collections.reverse(parents);

                concept.setParents(parents);
            }
        }

        branchCache.put(cacheString, concept);
        RefsetMemberService.TAXONOMY_SEARCH_ANCESTORS_CACHE.put(branchPath, branchCache);

        return concept;
    }

    /* see superclass */
    @Override
    public ResultListConcept searchConcepts(final Refset refset, final SearchParameters searchParameters, final String searchMembersMode,
        final int limitReturnNumber) throws Exception {

        final ResultListConcept returnConcepts = new ResultListConcept();
        final ObjectMapper mapper = new ObjectMapper();
        final String encodedCaret = "%5E";
        final String encodedSpace = "%20";
        // final String encodedLeftBrace = "%7B";
        // final String encodedRightBrace = "%7D";
        boolean searchOnlyRefsetMembers = false;
        boolean limitToNonMembers = false;
        int limit = RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH - 1;

        if (limitReturnNumber > 0) {

            limit = limitReturnNumber;
        }

        if (searchMembersMode.equals("members")) {

            searchOnlyRefsetMembers = true;

        } else if (searchMembersMode.equals("non members")) {

            limitToNonMembers = true;
        }

        // Create Snowstorm URL
        String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/concepts?&offset=0&limit=" + limit;

        // if this search is for editing then get the concept leaf information
        if (searchParameters.getEditing()) {

            url += "&includeLeafFlag=true&form=inferred";
        }

        boolean searchEcl = false;

        // if the query is not an ID then see if it passes ECL syntax
        if (!limitToNonMembers && searchParameters.getQuery() != null && !searchParameters.getQuery().matches("\\d*")) {

            final String eclUrl = SnowstormConnection.getBaseUrl() + "util/ecl-string-to-model";
            final String body = StringUtility.encodeValue(searchParameters.getQuery());

            LOG.debug("searchConcepts ECL Parse URL: " + eclUrl + "; body: " + body);

            try (final Response response = SnowstormConnection.postResponse(eclUrl, body)) {

                // if the query parses as ECL then search by ecl
                if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {

                    searchEcl = true;
                }

            }

        }

        final String membersEcl = encodedCaret + refset.getRefsetId();
        // final String activeConceptsEcl = encodedLeftBrace + encodedLeftBrace +
        // "C" +
        // encodedSpace + "active=1" + encodedRightBrace + encodedRightBrace;
        // final String inactiveConceptsEcl = encodedLeftBrace + encodedLeftBrace +
        // "C"
        // + encodedSpace + "active=0" + encodedRightBrace + encodedRightBrace;
        // final String activeAndInactiveMembersEcl =
        // "(" + membersEcl + encodedSpace + activeConceptsEcl + encodedSpace + "OR"
        // +
        // encodedSpace + membersEcl + encodedSpace + inactiveConceptsEcl + ")";

        // set the appropriate way to search
        if (!searchEcl) {

            url += "&term=" + StringUtility.encodeValue(searchParameters.getQuery());

            if (searchOnlyRefsetMembers) {

                url += "&ecl=" + membersEcl;
            }

        } else {

            url += "&ecl=" + StringUtility.encodeValue(searchParameters.getQuery());

            if (searchOnlyRefsetMembers) {

                url += encodedSpace + "AND" + encodedSpace + "(" + membersEcl + ")";
            }

        }

        if (searchParameters.getActiveOnly() != null && searchParameters.getActiveOnly()) {

            url += "&activeFilter=true";
        }

        String searchAfter = "";
        boolean hasMorePages = true;
        final long start = System.currentTimeMillis();

        while (hasMorePages) {

            // Call Snowstorm
            final String fullSnowstormUrl = url + searchAfter;
            LOG.debug("searchConcepts URL: " + fullSnowstormUrl);

            try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    hasMorePages = false;
                    throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode conceptNodeBatch = root.get("items");

                if (limitReturnNumber < 0 && root.get("searchAfter") != null) {

                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }

                // if the search returned results set the total
                if (!returnConcepts.isTotalKnown()) {

                    returnConcepts.setTotal(root.get("total").asInt());
                    returnConcepts.setTotalKnown(true);
                }

                if (limitReturnNumber > 0 || conceptNodeBatch.size() == 0
                    || conceptNodeBatch.size() + returnConcepts.getItems().size() >= returnConcepts.getTotal()) {

                    hasMorePages = false;
                }

                if (System.currentTimeMillis() - start > RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD) {

                    hasMorePages = false;
                }

                if (conceptNodeBatch.size() != 0 && !conceptNodeBatch.get(0).has("error")) {

                    final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();
                    final ArrayList<Concept> conceptBatch = new ArrayList<>();

                    // parse items to retrieve matching concepts
                    while (itemIterator.hasNext()) {

                        final JsonNode conceptNode = itemIterator.next();

                        final Concept concept = new Concept();
                        concept.setActive(conceptNode.get("active").asBoolean());
                        concept.setId(conceptNode.get("id").asText());
                        concept.setCode(conceptNode.get("id").asText());

                        if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {

                            concept.setDefined(true);
                        } else {

                            concept.setDefined(false);
                        }

                        if (conceptNode.get("pt") != null) {

                            concept.setName(conceptNode.get("pt").get("term").asText());
                        }

                        if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {

                            concept.setFsn(conceptNode.get("fsn").get("term").asText());
                        }

                        if (conceptNode.has("isLeafInferred")) {

                            concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                        }

                        RefsetMemberService.setConceptPermissions(concept);
                        concept.setMemberOfRefset(searchOnlyRefsetMembers);
                        RefsetMemberService.processIntensionalDefinitionException(refset, concept);
                        conceptBatch.add(concept);
                    }

                    if (searchParameters.getEditing() || limitToNonMembers) {

                        populateMembershipInformation(refset, conceptBatch);
                    }

                    returnConcepts.getItems().addAll(conceptBatch);
                }

            } catch (final Exception ex) {

                LOG.error("searchConcepts Could not retrieve concepts matching term: " + ex.getMessage());
                ex.printStackTrace();
                break;
            }

        }

        if (limitToNonMembers) {

            final List<Concept> conceptsToInclude = new ArrayList<>();

            for (final Concept concept : returnConcepts.getItems()) {

                if (concept.isMemberOfRefset()) {

                    continue;
                }

                conceptsToInclude.add(concept);

                if (conceptsToInclude.size() == searchParameters.getLimit()) {

                    break;
                }

            }

            returnConcepts.setItems(conceptsToInclude);
            returnConcepts.setTotal(conceptsToInclude.size());
        }

        return returnConcepts;
    }

    /* see superclass */
    @Override
    public int getMemberCount(final Refset refset) throws Exception {

        int count = 0;

        // when searching for members we only want concepts whose membership is
        // active
        // (though the concept itself can be inactive)
        final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/members?referenceSet=" + refset.getRefsetId()
            + "&active=true&offset=0&limit=1";

        LOG.debug("Get Refset Member Count URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            count = root.get("total").asInt();
        }

        return count;
    }

    /* see superclass */
    @Override
    public ResultListConcept getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters)
        throws Exception {

        // 2 Snowstorm calls: 1) Memberlist and 2) Descriptions
        final ResultListConcept members = new ResultListConcept();
        final String branchPath = RefsetMemberService.getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + searchParameters.toString() + "true";
        final Map<String, ResultListConcept> branchCache = RefsetMemberService.getCacheForConceptsCall(branchPath);
        final String refsetId = refset.getRefsetId();

        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {

            LOG.debug("getMemberList USING CACHE");
            return branchCache.get(cacheString);
        }

        try {

            boolean notSearching = true;
            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetMembershipInformation(true);

            final List<Concept> conceptsToProcess = new ArrayList<>();

            if (searchParameters.getQuery() == null) {

                searchParameters.setQuery("");
            }

            ResultListConcept currentList = new ResultListConcept();

            // if search term is indicated, find members that match search term
            if (searchParameters.getQuery() != null && !searchParameters.getQuery().isEmpty()) {

                notSearching = false;
                currentList = searchConcepts(refset, searchParameters, "members", -1);
            } else {

                String searchAfter = "";
                final long start = System.currentTimeMillis();
                boolean hasMorePages = true;
                final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;

                // when searching for members we only want concepts whose membership is
                // active
                // (though the concept itself can be inactive)
                final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/members?referenceSet=" + refsetId
                    + "&active=true&offset=0&limit=" + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;

                while (hasMorePages) {

                    final String fullSnowstormUrl = url + searchAfter;
                    LOG.debug("Get Member List URL: " + fullSnowstormUrl);

                    try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl, acceptLanguage)) {

                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                            hasMorePages = false;
                            throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                                + response.getStatusInfo().getReasonPhrase());
                        }

                        final String resultString = response.readEntity(String.class);

                        // Only process payload if Rest call is successful
                        if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                            throw new Exception(Integer.toString(response.getStatus()));
                        }

                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode root = mapper.readTree(resultString.toString());
                        final JsonNode conceptNodeBatch = root.get("items");

                        // if the search returned results set the total
                        if (!currentList.isTotalKnown()) {

                            currentList.setTotal(root.get("total").asInt());
                            currentList.setTotalKnown(true);
                        }

                        if (root.get("searchAfter") != null) {

                            searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                        }

                        if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() + currentList.getItems().size() >= currentList.getTotal()) {

                            hasMorePages = false;
                        }

                        if (System.currentTimeMillis() - start > RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD) {

                            hasMorePages = false;
                        }

                        final ResultListConcept currentMemberBatch = RefsetMemberService.populateConcepts(root, refset, lookupParameters);
                        currentList.getItems().addAll(currentMemberBatch.getItems());
                    }

                }

            }

            int snowstormCallCount = 0;
            final boolean doNotSearch = notSearching;

            // Change the numbers to '1's to avoid threading
            final ExecutorService executor =
                new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

            // add the descriptions to the children concepts in batches
            for (int i = 0; i < currentList.getItems().size(); i++) {

                final Concept concept = currentList.getItems().get(i);
                conceptsToProcess.add(concept);

                if (conceptsToProcess.size() == RefsetMemberService.CONCEPT_DESCRIPTIONS_PER_CALL || i == currentList.getItems().size() - 1) {

                    snowstormCallCount++;
                    final List<Concept> threadConcepts = new ArrayList<Concept>();
                    threadConcepts.addAll(conceptsToProcess);

                    executor.submit(new Runnable() {

                        /* see superclass */
                        @Override
                        public void run() {

                            try {

                                // LOG.debug("getMemberList IN THREAD ID: " +
                                // Thread.currentThread().getId());
                                populateAllLanguageDescriptions(refset, threadConcepts);

                                // if not searching and editing then get the concept leaf
                                // information
                                if (doNotSearch && searchParameters.getEditing()) {

                                    populateConceptLeafStatus(refset, threadConcepts);
                                }

                            } catch (final Exception e) {

                                throw new RuntimeException(e);
                            }

                        }

                    });

                    conceptsToProcess.clear();
                }

            }

            executor.shutdown();
            executor.awaitTermination(600, TimeUnit.SECONDS);

            LOG.debug("getMemberList snowstormCallCount: " + snowstormCallCount);

            members.getItems().addAll(currentList.getItems());
            members.setTotal(currentList.getTotal());
            members.setTotalKnown(true);

            branchCache.put(cacheString, members);
            RefsetMemberService.CONCEPTS_CALL_CACHE.put(branchPath, branchCache);

        } catch (final Exception ex) {

            throw new Exception("Could not get Reference Set member list for Reference Set " + refsetId + " from snowstorm: " + ex.getMessage(), ex);
        }

        return members;
    }

    /* see superclass */
    @Override
    public Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception {

        final String branchPath = RefsetMemberService.getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + conceptId;
        final Map<String, Concept> branchCache = RefsetMemberService.getCacheForConceptDetails(branchPath);

        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {

            LOG.debug("getConceptDetails USING CACHE");
            return branchCache.get(cacheString);
        }

        // 3 Snowstorm calls: 1) on concept, 2) parents, and 3) children
        try {

            final String url = SnowstormConnection.getBaseUrl() + "browser/" + RefsetMemberService.getBranchPath(refset) + "/" + "concepts/" + conceptId;

            LOG.debug("Get Concept Details URL: " + url);

            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetDescriptions(true);
            lookupParameters.setGetRoleGroups(true);
            lookupParameters.setSingleConceptRequest(true);

            final ResultListConcept conceptResultList = getConceptsFromSnowstorm(url, refset, lookupParameters, null);

            if (conceptResultList.size() != 1) {

                throw new Exception("Unexpected number of concepts found (" + conceptResultList.size() + ") in getConceptDetails");
            }

            final Concept concept = conceptResultList.getItems().iterator().next();
            branchCache.put(cacheString, concept);
            RefsetMemberService.CONCEPT_DETAILS_CACHE.put(branchPath, branchCache);

            return concept;

        } catch (final RestException ex) {

            // throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found",
            // message);
            throw new RestException(false, ex.getError().getStatus(), ex.getMessage(), "Could not get Reference Set children for concept " + conceptId + ".");

        } catch (final Exception ex) {

            throw new Exception("Could not get Reference Set children for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);

        }
    }

    /* see superclass */
    @Override
    public ResultListConcept getParents(final String conceptId, final Refset refset, final String language) throws Exception {

        try {

            final String url = SnowstormConnection.getBaseUrl() + "browser/" + RefsetMemberService.getBranchPath(refset) + "/" + "concepts/" + conceptId
                + "/parents?form=inferred";

            LOG.debug("Get Parents URL: " + url);
            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetFsn(true);

            final ResultListConcept results = getConceptsFromSnowstorm(url, refset, lookupParameters, language);

            // sort the results
            Collections.sort(results.getItems(), (object1, object2) -> (object1.compareTo(object2)));

            return results;

        } catch (final Exception ex) {

            if ("400".equals(ex.getMessage())) {

                throw ex;
            }

            throw new Exception("Could not get Reference Set parents for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /* see superclass */
    @Override
    public ResultListConcept getChildren(final String conceptId, final Refset refset, final String language) throws Exception {

        try {

            final String branchPath = RefsetMemberService.getBranchPath(refset);
            final String url = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/" + "concepts/" + conceptId + "/children?form=inferred";

            LOG.debug("Get Children URL: " + url);
            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetFsn(true);

            return getConceptsFromSnowstorm(url, refset, lookupParameters, language);

        } catch (final Exception ex) {

            if ("400".equals(ex.getMessage())) {

                throw ex;
            }

            throw new Exception("Could not get Reference Set children for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /* see superclass */
    @Override
    public ResultListConcept getConceptsFromSnowstorm(final String url, final Refset refset, final ConceptLookupParameters lookupParameters,
        final String language) throws Exception {

        String acceptLanguage = language;

        if (acceptLanguage == null) {

            acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        }

        try (final Response response = SnowstormConnection.getResponse(url, acceptLanguage)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                LOG.error(
                    "Call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                throw new RestException(false, response.getStatusInfo().getStatusCode(), "Message: " + response.getStatusInfo().getReasonPhrase(),
                    "Error looking up concept(s).");
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            return RefsetMemberService.populateConcepts(root, refset, lookupParameters);
        }
    }

    /* see superclass */
    @Override
    public void populateMembershipInformation(final Refset refset, final List<Concept> concepts) throws Exception {

        final String baseUrl = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/members?referenceSet=" + refset.getRefsetId()
            + "&active=true&limit=" + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + "&offset=0&referencedComponentId=";
        final List<Concept> conceptsToProcess = new ArrayList<>();
        String conceptIds = "";
        int snowstormCallCount = 0;

        // Change the '30's to '1's to avoid threading
        final ExecutorService executor =
            new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

        // add the descriptions to the children concepts in batches
        for (int i = 0; i < concepts.size(); i++) {

            final Concept concept = concepts.get(i);
            conceptsToProcess.add(concept);
            conceptIds += concept.getCode() + ",";

            if (conceptsToProcess.size() == RefsetMemberService.CONCEPT_DESCRIPTIONS_PER_CALL || i == concepts.size() - 1) {

                final String memberUrl = baseUrl + conceptIds;
                conceptIds = "";
                snowstormCallCount++;
                final List<Concept> threadConcepts = new ArrayList<Concept>();
                threadConcepts.addAll(conceptsToProcess);

                executor.submit(new Runnable() {

                    /* see superclass */
                    @Override
                    public void run() {

                        try {

                            // LOG.debug("populateMembershipInformation IN THREAD ID: " +
                            // Thread.currentThread().getId());
                            LOG.debug("Get Membership URL for Populate: " + memberUrl);

                            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
                            lookupParameters.setGetMembershipInformation(true);
                            final ResultListConcept resultList = getConceptsFromSnowstorm(memberUrl, refset, lookupParameters, null);

                            for (final Concept resultConcept : resultList.getItems()) {

                                for (final Concept conceptToProcess : threadConcepts) {

                                    if (resultConcept.getCode().equals(conceptToProcess.getCode())) {

                                        conceptToProcess.setMemberOfRefset(resultConcept.isMemberOfRefset());
                                        conceptToProcess.setMemberEffectiveTime(resultConcept.getMemberEffectiveTime());
                                        conceptToProcess.setReleased(resultConcept.isReleased());
                                        break;
                                    }

                                }

                            }

                        } catch (final Exception e) {

                            throw new RuntimeException(e);
                        }

                    }

                });

                conceptsToProcess.clear();
            }

        }

        executor.shutdown();
        executor.awaitTermination(600, TimeUnit.SECONDS);

        LOG.debug("populateMembershipInformation snowstormCallCount: " + snowstormCallCount);
    }

    /* see superclass */
    @Override
    public Long getLatestChangedVersionDate(final String branch, final String refsetId) throws Exception {
        // Get all members
        // EG:
        // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/browser/SNOMEDCT-BE/members?referenceSet=1235&offset=0&limit=10
        // EG:
        // https://dev-integration-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/SNOMEDCT-BE/members?referenceSet=1235&offset=0&limit=10

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(RefsetMemberService.DATE_FORMAT);
        final int limit = RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;
        String searchAfter = "";

        long refsetLatestVersion = -1;
        final long start = System.currentTimeMillis();
        boolean hasMorePages = true;
        final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;

        while (hasMorePages) {

            final String url = SnowstormConnection.getBaseUrl() + branch + "/members?referenceSet=" + refsetId + searchAfter + "&limit=" + limit;
            LOG.debug("getRefsetMembers URL: " + url);

            try (final Response response = SnowstormConnection.getResponse(url, acceptLanguage)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    hasMorePages = false;
                    throw new Exception("call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception(Integer.toString(response.getStatus()));
                }

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode conceptNodeBatch = root.get("items");

                searchAfter = (root.get("searchAfter") != null ? "&searchAfter=" + root.get("searchAfter").asText() : "");

                if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() < limit) {

                    hasMorePages = false;
                }

                if (System.currentTimeMillis() - start > RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD) {

                    hasMorePages = false;
                }

                final Iterator<JsonNode> iterator = conceptNodeBatch.iterator();

                JsonNode memberNode = null;

                long versionLatestTime = -1;

                while (iterator.hasNext()) {

                    memberNode = iterator.next();

                    if (memberNode.has("releasedEffectiveTime")) {

                        final long memberEffectiveTime = simpleDateFormat.parse(memberNode.get("releasedEffectiveTime").asText()).getTime();

                        if (versionLatestTime < memberEffectiveTime) {

                            versionLatestTime = memberEffectiveTime;
                        }

                    }

                }

                if (refsetLatestVersion < versionLatestTime) {

                    refsetLatestVersion = versionLatestTime;
                }

            } catch (final Exception e) {

                LOG.error("Caught during defining Reference Set version on: " + refsetId + " --- " + branch + "\n", e);
                throw e;
            }

        }

        // No members with release dates, so use release date of refset concept
        // itself.
        if (refsetLatestVersion < 0) {

            refsetLatestVersion = getRefsetConceptReleaseDate(refsetId, branch);
        }

        // See if version already exists.
        if (!RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.containsKey(refsetId)) {

            RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.put(refsetId, new ArrayList<Long>());
        }

        if (RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.get(refsetId).contains(refsetLatestVersion)) {

            return null;
        } else {

            RefsetMemberService.REFSET_TO_PUBLISHED_VERSION_MAP.get(refsetId).add(refsetLatestVersion);
            return refsetLatestVersion;
        }
    }

    /* see superclass */
    @Override
    public Long getRefsetConceptReleaseDate(final String refsetId, final String branch) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + "browser/" + branch + "/" + "concepts/" + refsetId;
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(RefsetMemberService.DATE_FORMAT);

        LOG.debug("Get Concept Details URL: " + url);

        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setGetDescriptions(false);
        lookupParameters.setGetRoleGroups(false);
        lookupParameters.setSingleConceptRequest(true);

        try (final Response response = SnowstormConnection.getResponse(url, SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode conceptNode = mapper.readTree(resultString.toString());

            if (conceptNode.has("releasedEffectiveTime")) {

                return simpleDateFormat.parse(conceptNode.get("releasedEffectiveTime").asText()).getTime();
            }

            return null;

        } catch (final Exception ex) {

            throw new Exception("Could not get Reference Set children for concept " + refsetId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /* see superclass */
    @Override
    public List<Map<String, String>> getMemberHistory(final TerminologyService service, final String referencedComponentId,
        final List<Map<String, String>> versions) throws Exception {

        // Note, the system expects that versions are ordered from oldest first
        // to newest last. Failure to adhere to this convention will break the
        // algorithm.
        final List<Map<String, String>> memberHistory = new ArrayList<>();
        String previousStatus = null;

        for (final Map<String, String> version : versions) {

            final String refsetInternalId = version.get("refsetInternalId");
            final String branchDate = version.get("date");

            LOG.debug("Processing history on: " + branchDate + " using internalRefsetId: " + refsetInternalId);

            final Refset refset = service.get(refsetInternalId, Refset.class);

            if (refset == null) {

                throw new Exception("Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
            }

            final String url = SnowstormConnection.getBaseUrl() + RefsetService.getBranchPath(refset) + "/members?referenceSet=" + refset.getRefsetId()
                + "&referencedComponentId=" + referencedComponentId;

            LOG.debug("Get Membership History URL: " + url);

            try (final Response response = SnowstormConnection.getResponse(url)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    throw new Exception("call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode node = root.get("items");
                String currentVersionDate = null;
                final Iterator<JsonNode> iterator = node.iterator();
                String currentStatus = null;

                if (iterator.hasNext()) {

                    final JsonNode memberNode = iterator.next();

                    // Calculate the version date and ensure it has
                    // proper format yyyy-mm-dd
                    currentVersionDate = memberNode.get("releasedEffectiveTime").asText();
                    currentVersionDate = currentVersionDate.substring(0, 4) + "-" + currentVersionDate.substring(4, 6) + "-" + currentVersionDate.substring(6);

                    if (memberNode.has("active")) {

                        if (memberNode.get("active").asBoolean()) {

                            currentStatus = "Active";
                        } else {

                            currentStatus = "Inactive";
                        }

                    }

                }

                // have reached to the point prior to the concept
                // becoming a member, so can cancel searching further
                // versions
                if (currentStatus == null) {

                    continue;
                }

                if (previousStatus == null) {
                    // First time encountering a membership status, thus
                    // first time added

                    final Map<String, String> historyEntry = new HashMap<>();

                    historyEntry.put("version", currentVersionDate);

                    if ("active".equals(currentStatus.toLowerCase())) {

                        historyEntry.put("change", "Added");
                    } else {

                        historyEntry.put("change", "Added as Inactive");
                    }

                    memberHistory.add(historyEntry);

                } else if (!currentStatus.equals(previousStatus)) {
                    // if the previous status wasn't null and the
                    // current
                    // status doesn't match it, then set the last status

                    final Map<String, String> historyEntry = new HashMap<>();
                    historyEntry.put("version", currentVersionDate);

                    if (currentStatus.equals("Active")) {

                        historyEntry.put("change", "Activated");
                    } else {

                        historyEntry.put("change", "Inactivated");
                    }

                    memberHistory.add(historyEntry);

                }

                previousStatus = currentStatus;

            } catch (final Exception ex) {

                LOG.error("Could not grab Reference Set members for Reference Set " + refset.getRefsetId() + " from snowstorm: " + ex.getMessage(), ex);
                continue;
            }

        }

        return memberHistory;
    }

    /* see superclass */
    @Override
    public boolean cacheMemberAncestors(final Refset refset) throws Exception {

        final long start = System.currentTimeMillis();
        final String branchPath = refset.getBranchPath();
        final String cacheString = refset.getRefsetId();
        final Map<String, Set<String>> branchCache = RefsetMemberService.getCacheForMemberAncestors(branchPath);

        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {

            LOG.debug("cacheMemberAncestors USING CACHE");
            return true;
        }

        final String memberCountUrl = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/members?referenceSet="
            + refset.getRefsetId() + "&active=true&limit=1";

        // See how many members the refset has - if it is more than 100k we can not
        // cache the ancestors
        try (final Response response = SnowstormConnection.getResponse(memberCountUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "Call to url '" + memberCountUrl + "' wasn't successful. " + response.getStatus() + ": " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final int memberTotal = root.get("total").asInt();

            if (memberTotal > 100000) {

                LOG.warn("Could not cache the ancestors of Reference Set " + refset.getRefsetId() + " because it has too many members: " + memberTotal);
                branchCache.put(cacheString, new HashSet<String>());
                RefsetMemberService.ANCESTORS_CACHE.put(branchPath, branchCache);
                return true;
            }

        } catch (final Exception e) {

            LOG.error("Could not cache the ancestors of Reference Set " + refset.getRefsetId() + " from snowstorm: " + e.getMessage(), e);
        }

        // Get ancestors of all members via ecl e.g. >(^723264001)
        final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/concepts?ecl=%3E(%5E" + refset.getRefsetId()
            + ")&limit=1000&offset=";
        final List<Set<String>> ancestorsSetBatches = new ArrayList<>(Collections.nCopies(10, new HashSet<>()));

        // Change the '10's to '1's to avoid threading
        final ExecutorService executor =
            new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10), new ThreadPoolExecutor.CallerRunsPolicy());

        // add the descriptions to the children concepts in batches
        for (int i = 0; i < ancestorsSetBatches.size(); i++) {

            final Set<String> ancestorsSet = ancestorsSetBatches.get(i);
            final int offset = 1000 * i;
            LOG.debug("cacheMemberAncestors URL: " + url + offset);

            executor.submit(new Runnable() {

                /* see superclass */
                @Override
                public void run() {

                    final String fullSnowstormUrl = url + offset;
                    try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                            throw new Exception("Call to url '" + fullSnowstormUrl + "' wasn't successful. " + response.getStatus() + ": "
                                + response.getStatusInfo().getReasonPhrase());
                        }

                        final String resultString = response.readEntity(String.class);

                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode root = mapper.readTree(resultString.toString());
                        final JsonNode allResultNodes = root.get("items");
                        final Iterator<JsonNode> resultsIterator = allResultNodes.iterator();

                        while (resultsIterator.hasNext()) {

                            final JsonNode resultNode = resultsIterator.next();

                            if (!resultNode.has("conceptId")) {

                                throw new Exception("Result wasn't as expected with resultNode: " + resultNode);
                            }

                            ancestorsSet.add(resultNode.get("conceptId").asText());
                        }

                    } catch (final Exception e) {

                        LOG.error("Could not cache the ancestors of Reference Set " + refset.getRefsetId() + " from snowstorm: " + e.getMessage(), e);
                    }

                }

            });
        }

        executor.shutdown();
        executor.awaitTermination(120, TimeUnit.SECONDS);

        final Set<String> ancestorsSet = new HashSet<>();

        for (final Set<String> ancestorsBatch : ancestorsSetBatches) {

            ancestorsSet.addAll(ancestorsBatch);
        }

        branchCache.put(cacheString, ancestorsSet);
        RefsetMemberService.ANCESTORS_CACHE.put(branchPath, branchCache);

        LOG.debug("cacheMemberAncestors Time Taken: " + (System.currentTimeMillis() - start));
        LOG.debug("cacheMemberAncestors Number of Ancestors: " + ancestorsSet.size());

        return true;
    }

    /* see superclass */
    @Override
    public List<String> addRefsetMembers(final TerminologyService service, final User user, final Refset refset, final List<String> conceptIds)
        throws Exception {

        if (refset == null) {
            throw new Exception("Reference Set Internal Id: is NULL does not exist in the RT2 database");
        }

        final List<String> unaddedConcepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();

        final String branchPath = RefsetService.getBranchPath(refset);
        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/" + "members";

        if (conceptIds.size() == 0) {

            return unaddedConcepts;
        }

        final String refsetId = refset.getRefsetId();

        // when searching for members we only want concepts whose membership is
        // active
        // (though the concept itself can be inactive)
        final String conceptSearchUrl = SnowstormConnection.getBaseUrl() + branchPath + "/concepts/search";
        final String memberSearchUrl =
            SnowstormConnection.getBaseUrl() + branchPath + "/members/search?limit=" + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;
        final String bodyBase = "{\"limit\": " + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + ", ";
        final String memberSearchBodyBase = "{\"referenceSet\":\"" + refsetId + "\", \"referencedComponentIds\":[";
        final List<String> permanentFullConceptList = new ArrayList<>(conceptIds);
        final Map<String, Map<String, String>> conceptsStatus = RefsetMemberService.REFSETS_UPDATED_MEMBERS.get(refset.getId());
        final List<String> validatedConcepts = new ArrayList<>();
        final ArrayNode memberUpdateArray = mapper.createArrayNode();
        boolean searchAgain = true;
        int searchIndex = 0;
        int loopNumber = 1;
        LOG.debug("addRefsetMembers concept search URL: " + conceptSearchUrl);
        LOG.debug("addRefsetMembers concept member verification URL: " + memberSearchUrl);

        while (searchAgain) {

            searchAgain = false;
            String bodyConceptIds = "\"conceptIds\":[";
            Iterator<JsonNode> iterator = null;
            final List<String> conceptBatch = new ArrayList<>();

            for (; searchIndex < permanentFullConceptList.size(); searchIndex++) {

                // LOG.debug("addRefsetMembers searchIndex: " + searchIndex + " ::
                // permanentFullConceptList.size(): " + permanentFullConceptList.size()
                // + "
                // ::
                // permanentFullConceptList.get(searchIndex): " +
                // permanentFullConceptList.get(searchIndex));
                final String conceptId = permanentFullConceptList.get(searchIndex);
                final Map<String, String> status = new HashMap<>();
                status.put("operation", "Added");
                status.put("status", "Failed");
                conceptsStatus.put(conceptId, status);
                conceptBatch.add(conceptId);
                bodyConceptIds += "\"" + conceptId + "\",";

                if (searchIndex / loopNumber >= RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH) {

                    loopNumber++;
                    searchAgain = true;
                    break;
                }

            }

            LOG.debug("addRefsetMembers searchIndex :: loopNumber :: batch size: " + searchIndex + " :: " + loopNumber + " :: " + (searchIndex / loopNumber));
            bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",") + "]";

            // verify the concept IDs if a bulk add is going to be used
            if (conceptIds.size() > 0) {

                final String conceptVerificationBody = bodyBase + bodyConceptIds + "}";
                // LOG.debug("addRefsetMembers bulk verification body: " +
                // conceptVerificationBody);

                try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, conceptVerificationBody)) {

                    final String resultString = response.readEntity(String.class);

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                        throw new Exception("call to url '" + conceptSearchUrl + "' for concept verification wasn't successful. Status: " + response.getStatus()
                            + " Message: " + response.getStatusInfo().getReasonPhrase());
                    }

                    final JsonNode root = mapper.readTree(resultString.toString());
                    iterator = root.get("items").iterator();

                    while (iterator != null && iterator.hasNext()) {

                        final JsonNode conceptNode = iterator.next();
                        final String conceptId = conceptNode.get("conceptId").asText();
                        String name = "";
                        validatedConcepts.add(conceptId);

                        if (conceptNode.get("pt") != null && conceptNode.get("pt").get("term") != null) {

                            name = conceptNode.get("pt").get("term").asText();
                        }

                        final Map<String, String> status = new HashMap<>();
                        status.put("operation", "Added");
                        status.put("status", "Success");
                        status.put("name", name);
                        status.put("active", conceptNode.get("active").asText());
                        conceptsStatus.put(conceptId, status);
                    }

                }

            }

            // gather any input concept not in the validated list
            final List<String> invalidConcepts = conceptBatch.stream().filter(inputConceptId -> {

                final boolean found = validatedConcepts.contains(inputConceptId);

                if (found) {

                    return false;
                } else {

                    LOG.debug("The ID " + inputConceptId + " is not a valid concept.");
                    return true;
                }

            }).collect(Collectors.toList());

            // remove invalid concepts from the concept lists
            LOG.debug("invalidConcepts: " + invalidConcepts);
            unaddedConcepts.addAll(invalidConcepts);
            conceptIds.removeAll(invalidConcepts);
            conceptBatch.removeAll(invalidConcepts);

            if (conceptBatch.size() > 0) {

                bodyConceptIds = "";

                // generate the body list for the check for concepts that are already
                // members
                for (final String conceptId : conceptBatch) {

                    bodyConceptIds += conceptId + ",";
                }

                bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");
                final String memberSearchBody = memberSearchBodyBase + bodyConceptIds + "]}";
                // LOG.debug("addRefsetMembers member search body: " +
                // memberSearchBody);

                try (final Response response = SnowstormConnection.postResponse(memberSearchUrl, memberSearchBody)) {

                    final String resultString = response.readEntity(String.class);

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                        throw new Exception("call to url '" + conceptSearchUrl + "' for member search wasn't successful. Status: " + response.getStatus()
                            + " Message: " + response.getStatusInfo().getReasonPhrase());
                    }

                    final JsonNode root = mapper.readTree(resultString.toString());
                    iterator = root.get("items").iterator();

                    // loop thru the returned member details
                    while (iterator != null && iterator.hasNext()) {

                        final JsonNode conceptNode = iterator.next();
                        final String conceptId = conceptNode.get("referencedComponentId").asText();
                        final boolean active = conceptNode.get("active").asBoolean();

                        // if active remove from the list to add, else update the membership
                        // if the
                        // concept used to be a member
                        if (active) {

                            LOG.debug("addRefsetMembers removing already member conceptId: " + conceptId);
                            conceptIds.remove(conceptId);

                            final Map<String, String> status = new HashMap<>();
                            status.put("operation", "Added");
                            status.put("status", "Already Member");
                            conceptsStatus.put(conceptId, status);

                        } else {

                            conceptIds.remove(conceptId);

                            final ObjectNode memberBody = mapper.createObjectNode().put("active", true).put("memberId", conceptNode.get("memberId").asText())
                                .put("moduleId", conceptNode.get("moduleId").asText())
                                .put("referencedComponentId", conceptNode.get("referencedComponentId").asText())
                                .put("refsetId", conceptNode.get("refsetId").asText()).put("released", conceptNode.get("released").asBoolean())
                                .put("releasedEffectiveTime", conceptNode.get("releasedEffectiveTime").asInt())
                                .set("additionalFields", conceptNode.get("additionalFields"));

                            if (conceptNode.get("effectiveTime") != null) {
                                memberBody.put("effectiveTime", conceptNode.get("effectiveTime").asText());
                            }

                            memberUpdateArray.add(memberBody);
                        }
                    }
                }
            }
        }

        LOG.debug("addRefsetMembers about to add concept size: " + conceptIds.size());

        final String moduleId = refset.getModuleId();

        if (conceptIds.size() == 1) {
            unaddedConcepts.addAll(callAddMemberSingle(refsetId, url, conceptIds.get(0), moduleId));

        } else if (conceptIds.size() > 1) {
            unaddedConcepts.addAll(callAddMembersBulk(refsetId, url, conceptIds, moduleId));
        }

        // re-add any concepts that used to be members
        if (!memberUpdateArray.isEmpty()) {
            callUpdateMembersBulk(refsetId, SnowstormConnection.getBaseUrl() + branchPath + "/members/bulk", memberUpdateArray);
        }

        conceptIds.removeAll(unaddedConcepts);

        // clear the caches for this refset
        RefsetMemberService.clearAllMemberCaches(branchPath);

        // update the member count and save the refset
        if (refset.getMemberCount() == -1) {

            refset.setMemberCount(0);
        }

        refset.setMemberCount(refset.getMemberCount() + conceptIds.size() + memberUpdateArray.size());
        service.update(refset);

        for (final String conceptId : unaddedConcepts) {

            final Map<String, String> status = new HashMap<>();
            status.put("operation", "Added");
            status.put("status", "Failed");
            conceptsStatus.put(conceptId, status);
        }

        return unaddedConcepts;
    }

    /* see superclass */
    @Override
    public List<String> callAddMemberSingle(final String refsetId, final String url, final String conceptId, final String moduleId) throws Exception {

        final List<String> unaddedConcepts = new ArrayList<>();

        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("refsetId", refsetId).put("moduleId", moduleId).put("referencedComponentId", conceptId);

        LOG.debug("callAddMemberSingle URL: " + url);
        // LOG.debug("callAddMemberSingle URL body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                LOG.error("Add Reference Set Member call to url '" + url + "' for Reference Set '" + refsetId + "' and concept '" + conceptId
                    + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                unaddedConcepts.add(conceptId);
            }

        }

        return unaddedConcepts;
    }

    /* see superclass */
    @Override
    public List<String> callAddMembersBulk(final String refsetId, final String url, final List<String> conceptIds, final String moduleId) throws Exception {

        final List<String> unaddedConcepts = new ArrayList<>();
        final String bulkUrl = url + "/bulk";
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode body = mapper.createArrayNode();

        for (final String conceptId : conceptIds) {

            final ObjectNode memberBody = mapper.createObjectNode().put("refsetId", refsetId).put("moduleId", moduleId).put("referencedComponentId", conceptId);

            body.add(memberBody);
        }

        LOG.debug("callAddMembersBulk URL: " + bulkUrl);
        // LOG.debug("callAddMembersBulk URL body: " + body.toString());

        String jobStatusUrl = null;
        boolean jobDone = false;
        final String errorMessage = "Add Reference Set Member bulk call to url '" + bulkUrl + "' for Reference Set '" + refsetId + " wasn't successful. ";

        try (final Response response = SnowstormConnection.postResponse(bulkUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            jobStatusUrl = response.getHeaderString("Location");
        }

        if (jobStatusUrl == null) {

            LOG.error(errorMessage);

        } else {

            try {

                Thread.sleep(800);
            } catch (final InterruptedException ex) {

                Thread.currentThread().interrupt();
            }

            LOG.debug("callAddMembersBulk job status URL: " + jobStatusUrl);

            while (!jobDone) {

                try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                        jobDone = true;
                        LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                    }

                    final String resultString = response.readEntity(String.class);
                    final JsonNode root = mapper.readTree(resultString.toString());

                    // LOG.debug("addRefsetMembers job status response: " + root);
                    final String status = root.get("status").asText();

                    if (status.equalsIgnoreCase("COMPLETED")) {

                        jobDone = true;

                    } else if (status.equalsIgnoreCase("failed")) {

                        jobDone = true;
                        LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage + root.get("message").asText());
                    } else {

                        LOG.debug("Bulk member add hasn't finished yet...");

                        try {

                            Thread.sleep(800);
                        } catch (final InterruptedException ex) {

                            Thread.currentThread().interrupt();
                        }

                    }

                }

            }

        }

        return unaddedConcepts;
    }

    /* see superclass */
    @Override
    public List<String> removeRefsetMembers(final TerminologyService service, final User user, final Refset refset, final String conceptIds) throws Exception {

        List<String> unremovedConcepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Map<String, String>> conceptsStatus = RefsetMemberService.REFSETS_UPDATED_MEMBERS.get(refset.getId());

        if (conceptIds.isEmpty()) {

            return unremovedConcepts;
        }

        final String refsetId = refset.getRefsetId();
        final String branchPath = RefsetService.getBranchPath(refset);
        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/" + "members";

        // when searching for members we only want concepts whose membership is
        // active
        // (though the concept itself can be inactive)
        final String memberSearchUrlBase = SnowstormConnection.getBaseUrl() + branchPath + "/members?referenceSet=" + refset.getRefsetId()
            + "&offset=0&active=true" + "&limit=" + RefsetMemberService.URL_MAX_CHAR_LENGTH + "&referencedComponentId=";
        final ArrayNode memberDeleteArray = mapper.createArrayNode();
        final ArrayNode memberUpdateArray = mapper.createArrayNode();
        final List<String> permanentFullConceptList = Arrays.asList(conceptIds.split(","));
        final List<String> members = new ArrayList<>();
        boolean searchAgain = true;
        int searchIndex = 0;

        while (searchAgain) {

            searchAgain = false;
            String bodyConceptIds = "";

            for (; searchIndex < permanentFullConceptList.size(); searchIndex++) {

                final String conceptId = permanentFullConceptList.get(searchIndex);
                final Map<String, String> status = new HashMap<>();
                status.put("operation", "Removed");
                status.put("status", "Failed");
                conceptsStatus.put(conceptId, status);
                bodyConceptIds += conceptId + ",";

                if (memberSearchUrlBase.length() + bodyConceptIds.length() >= RefsetMemberService.URL_MAX_CHAR_LENGTH) {

                    searchAgain = true;
                    break;
                }

            }

            bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");

            final String memberSearchUrl = memberSearchUrlBase + bodyConceptIds;
            Iterator<JsonNode> iterator = null;

            LOG.debug("removeRefsetMembers search URL: " + memberSearchUrl);

            try (final Response response = SnowstormConnection.getResponse(memberSearchUrl)) {

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    searchAgain = false;
                    throw new Exception("call to url '" + memberSearchUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                iterator = root.get("items").iterator();
            }

            // loop thru the returned member details and add it to the list to delete
            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final boolean released = conceptNode.get("released").asBoolean();
                final String membershipId = conceptNode.get("memberId").asText();
                String name = "";
                members.add(conceptNode.get("referencedComponentId").asText());

                // if the member has not been released then remove the membership
                if (!released) {

                    memberDeleteArray.add(membershipId);
                }

                // if the member has been released add the information to the update
                // array
                else {

                    final ObjectNode memberBody = mapper.createObjectNode().put("active", false).put("effectiveTime", conceptNode.get("effectiveTime").asText())
                        .put("memberId", membershipId).put("moduleId", conceptNode.get("moduleId").asText())
                        .put("referencedComponentId", conceptNode.get("referencedComponentId").asText()).put("refsetId", conceptNode.get("refsetId").asText())
                        .put("released", released).put("releasedEffectiveTime", conceptNode.get("releasedEffectiveTime").asInt())
                        .set("additionalFields", conceptNode.get("additionalFields"));

                    memberUpdateArray.add(memberBody);
                }

                final JsonNode referencedComponent = conceptNode.get("referencedComponent");

                if (referencedComponent.get("pt") != null && referencedComponent.get("pt").get("term") != null) {

                    name = referencedComponent.get("pt").get("term").asText();
                }

                final Map<String, String> status = new HashMap<>();
                status.put("operation", "Removed");
                status.put("status", "Success");
                status.put("name", name);
                status.put("active", referencedComponent.get("active").asText());
                conceptsStatus.put(conceptNode.get("referencedComponentId").asText(), status);
            }

        }

        // gather any input concept that was not a member and adjust its status
        // permanentFullConceptList.stream().filter((inputConceptId) -> {
        //
        // boolean isMember = members.contains(inputConceptId);
        //
        // if (isMember) {
        // return false;
        // } else {
        //
        // final Map<String, String> status = new HashMap<>();
        // status.put("operation", "Removed");
        // status.put("status", "Was Not a Member");
        // conceptsStatus.put(inputConceptId, status);
        // return true;
        // }
        // });

        try {

            // delete any members that haven't been released
            if (memberDeleteArray.size() > 0) {

                final String deleteBody = mapper.createObjectNode().set("memberIds", memberDeleteArray).toString();
                final String deleteUrl = url + "?force";
                final String errorMessage =
                    "Remove Reference Set Member bulk call to url '" + deleteUrl + "' for Reference Set '" + refsetId + " wasn't successful. ";

                LOG.debug("removeRefsetMembers URL: " + deleteUrl);
                // LOG.debug("removeRefsetMembers URL Body: " + deleteBody);

                try (final Response response = SnowstormConnection.deleteResponse(deleteUrl, deleteBody)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {

                        LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                    }

                }

            }

            // If there is one member to inactivate call the single update method,
            // otherwise
            // call the batch update
            if (memberUpdateArray.size() == 1) {

                final JsonNode memberBody = memberUpdateArray.get(0);
                unremovedConcepts = callUpdateMemberSingle(refsetId, url + "/" + memberBody.get("memberId").asText(), memberBody);

            } else if (memberUpdateArray.size() > 1) {

                unremovedConcepts = callUpdateMembersBulk(refsetId, url + "/bulk", memberUpdateArray);
            }

        } finally {

            // clear the caches for this refset
            RefsetMemberService.clearAllMemberCaches(branchPath);
        }

        // update the member count and save the refset
        refset.setMemberCount(getMemberCount(refset));
        service.update(refset);

        for (final String conceptId : unremovedConcepts) {

            final Map<String, String> status = new HashMap<>();
            status.put("operation", "Removed");
            status.put("status", "Failed");
            conceptsStatus.put(conceptId, status);
        }

        return unremovedConcepts;
    }

    /* see superclass */
    @Override
    public List<String> callUpdateMemberSingle(final String refsetId, final String url, final JsonNode memberBody) throws Exception {

        final List<String> unchangedConcepts = new ArrayList<>();

        LOG.debug("callUpdateMemberSingle URL: " + url);
        // LOG.debug("callUpdateMemberSingle URL body: " + memberBody.toString());

        try (final Response response = SnowstormConnection.putResponse(url, memberBody.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                final String memberId = memberBody.get("memberId").asText();
                final String conceptId = memberBody.get("referencedComponentId").asText();

                LOG.error("Inactivate Reference Set Member call to url '" + url + "' for Reference Set '" + refsetId + "' and concept '" + conceptId
                    + "' and member '" + memberId + "' wasn't successful. " + " Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
                unchangedConcepts.add(memberId);
            }

        }

        return unchangedConcepts;
    }

    /* see superclass */
    @Override
    public List<String> callUpdateMembersBulk(final String refsetId, final String url, final ArrayNode memberBodies) throws Exception {

        final List<String> unchangedConcepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();

        LOG.debug("callUpdateMembersBulk URL: " + url);
        LOG.debug("callUpdateMembersBulk URL body: " + memberBodies.toString());

        String jobStatusUrl = null;
        boolean jobDone = false;
        final String errorMessage = "Reference Set Member bulk update call to url '" + url + "' for Reference Set '" + refsetId + " wasn't successful. ";

        try (final Response response = SnowstormConnection.postResponse(url, memberBodies.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            jobStatusUrl = response.getHeaderString("Location");
        }

        if (jobStatusUrl == null) {

            LOG.error(errorMessage);

        } else {

            try {

                Thread.sleep(800);
            } catch (final InterruptedException ex) {

                Thread.currentThread().interrupt();
            }

            LOG.debug("callUpdateMembersBulk job status URL: " + jobStatusUrl);

            while (!jobDone) {

                try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                        jobDone = true;
                        LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            errorMessage + " Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                    }

                    final String resultString = response.readEntity(String.class);
                    final JsonNode root = mapper.readTree(resultString.toString());

                    // LOG.debug("addRefsetMembers job status response: " + root);
                    final String status = root.get("status").asText();

                    if (status.equalsIgnoreCase("COMPLETED")) {

                        jobDone = true;

                    } else if (status.equalsIgnoreCase("failed")) {

                        jobDone = true;
                        LOG.error(errorMessage + root.get("message").asText());
                        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, errorMessage + root.get("message").asText());

                    } else {

                        LOG.debug("Bulk member update hasn't finished yet...");

                        try {

                            Thread.sleep(800);
                        } catch (final InterruptedException ex) {

                            Thread.currentThread().interrupt();
                        }

                    }

                }

            }

        }

        return unchangedConcepts;
    }

    /* see superclass */
    @Override
    public List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception {

        final List<String> concepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        final String url = SnowstormConnection.getBaseUrl() + branch + "/" + "concepts?ecl=" + StringUtility.encodeValue(ecl) + "&limit="
            + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;
        boolean keepSearching = true;
        int total = 0;
        int totalReturned = 0;
        String searchAfter = "";

        while (keepSearching) {

            LOG.debug("getConceptIdsFromEcl URL: " + url + searchAfter);

            // update the concept with the new data
            try (final Response response = SnowstormConnection.getResponse(url + searchAfter)) {

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception("Unable to get Reference Set concepts. Status: " + Integer.toString(response.getStatus()) + ". Error: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode items = root.get("items");
                final Iterator<JsonNode> iterator = items.iterator();
                totalReturned += items.size();

                if (total == 0) {

                    total = root.get("total").asInt();
                }

                LOG.debug("getConceptIdsFromEcl total: " + total);
                LOG.debug("getConceptIdsFromEcl totalReturned: " + totalReturned);

                if (totalReturned == 0 || totalReturned >= total) {

                    keepSearching = false;
                } else {

                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }

                LOG.debug("getConceptIdsFromEcl totalReturned so far: " + totalReturned);

                // loop thru the returned concepts add them to the list
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final String conceptId = conceptNode.get("conceptId").asText();

                    if (concepts.contains(conceptId)) {

                        continue;
                    }

                    concepts.add(conceptId);
                }

                LOG.debug("getConceptIdsFromEcl concepts.size(): " + concepts.size());
            }

        }

        return concepts;
    }

    /* see superclass */
    @Override
    public String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception {

        final String status = "Upgrade data compiled";
        Refset tempRefset = RefsetService.getRefset(service, user, refsetInternalId);

        WorkflowService.canUserPerformWorkflowAction(user, tempRefset, WorkflowService.UPGRADE);

        if (tempRefset.getWorkflowStatus().equals(WorkflowService.PUBLISHED) && tempRefset.getAvailableActions().contains(WorkflowService.UPGRADE)) {

            final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, user, tempRefset.getId(), false);
            tempRefset = service.get(newRefsetInternalId, Refset.class);
        }

        if (!tempRefset.getWorkflowStatus().equals(WorkflowService.READY_FOR_EDIT)) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reference Set is in the wrong status to be Upgraded");
        }

        final Refset upgradeRefset = tempRefset;

        // set the refset into IN_UPGRADE status
        WorkflowService.setWorkflowStatusByAction(service, user, WorkflowService.UPGRADE, upgradeRefset, "");

        final List<Concept> inactiveMemberList = new ArrayList<>();
        final List<String> activeMemberList = new ArrayList<>();
        final LinkedHashMap<String, UpgradeInactiveConcept> inactiveData = new LinkedHashMap<>();
        final String branchPath = RefsetMemberService.getBranchPath(upgradeRefset);
        final String refsetId = upgradeRefset.getRefsetId();
        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setGetMembershipInformation(true);
        String searchAfter = "";
        boolean hasMorePages = true;
        final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        int memberTotal = 0;
        boolean memberTotalKnown = false;
        String inactiveConceptIds = "";
        final ObjectMapper mapper = new ObjectMapper();
        final List<String> nonDefaultPreferredTerms = RefsetMemberService.identifyNonDefaultPreferredTerms(upgradeRefset.getEdition());
        @SuppressWarnings("unused")
        int replacementCount = 0;
        final String conceptSearchUrl = SnowstormConnection.getBaseUrl() + branchPath + "/concepts/search";
        final String bodyBase =
            "{\"limit\": " + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + ", \"eclFilter\": \"^" + upgradeRefset.getRefsetId() + "\", ";

        // when searching for members we only want concepts whose membership is
        // active
        // (though the concept itself can be inactive)
        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/members?referenceSet=" + refsetId + "&active=true&offset=0&limit="
            + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;

        // use members call to get members
        while (hasMorePages) {

            final String fullSnowstormUrl = url + searchAfter;
            LOG.debug("compileUpgradeData Member list URL: " + fullSnowstormUrl);

            try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl, acceptLanguage)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    hasMorePages = false;
                    throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception(Integer.toString(response.getStatus()));
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode conceptNodeBatch = root.get("items");

                // if the search returned results set the total
                if (!memberTotalKnown) {

                    memberTotal = root.get("total").asInt();
                    memberTotalKnown = true;
                }

                if (root.get("searchAfter") != null) {

                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }

                if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() + inactiveMemberList.size() >= memberTotal) {

                    hasMorePages = false;
                }

                final ResultListConcept currentMemberBatch = RefsetMemberService.populateConcepts(root, upgradeRefset, lookupParameters);

                // filter for inactive concepts
                for (final Concept concept : currentMemberBatch.getItems()) {

                    if (concept.isActive()) {

                        activeMemberList.add(concept.getCode());
                    } else {

                        inactiveMemberList.add(concept);
                        inactiveConceptIds += "\"" + concept.getCode() + "\", ";
                    }

                }

            }

        }

        inactiveConceptIds = StringUtils.removeEnd(inactiveConceptIds, ", ");
        final String conceptDetailsBaseUrl = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/concepts?conceptIds=";
        boolean searchAgain = true;
        int searchIndex = 0;

        if (inactiveMemberList.size() == 0) {

            return status;
        }

        // Change the numbers to '1's to avoid threading
        final ExecutorService executor =
            new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

        // use /browser/MAIN/concepts/bulk-load POST call to get the inactive
        // concept
        // details including descriptions and reasons (root.associationTargets)
        while (searchAgain) {

            searchAgain = false;
            String bodyConceptIds = "";

            for (; searchIndex < inactiveMemberList.size(); searchIndex++) {

                final Concept member = inactiveMemberList.get(searchIndex);
                final String conceptId = member.getCode();
                final UpgradeInactiveConcept inactiveConcept = new UpgradeInactiveConcept();
                inactiveConcept.setRefsetId(refsetId);
                inactiveConcept.setCode(member.getCode());
                inactiveConcept.setMemberId(member.getMemberId());
                inactiveConcept.setStillMember(true);
                inactiveData.put(conceptId, inactiveConcept);
                bodyConceptIds += conceptId + ",";

                if (conceptDetailsBaseUrl.length() + bodyConceptIds.length() >= RefsetMemberService.URL_MAX_CHAR_LENGTH) {

                    searchAgain = true;
                    break;
                }

            }

            bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");

            final String memberDetailsUrl = conceptDetailsBaseUrl + bodyConceptIds;
            Iterator<JsonNode> iterator = null;

            LOG.debug("compileUpgradeData member details URL: " + memberDetailsUrl);

            try (final Response response = SnowstormConnection.getResponse(memberDetailsUrl)) {

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    searchAgain = false;
                    throw new Exception("call to url '" + memberDetailsUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                iterator = root.get("items").iterator();

                // loop thru the returned member details and process the descriptions
                // and
                // replacement concepts
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final UpgradeInactiveConcept inactiveConcept = inactiveData.get(conceptNode.get("conceptId").asText());

                    if (conceptNode.get("descriptions") != null) {

                        final List<Map<String, String>> descriptions = RefsetMemberService.populateDescriptions(inactiveConcept.getCode(),
                            conceptNode.get("descriptions"), upgradeRefset, nonDefaultPreferredTerms);
                        inactiveConcept.setDescriptions(ModelUtility.toJson(descriptions));
                    }

                    final JsonNode inactivationIndicatorNode = conceptNode.get("inactivationIndicator");

                    if (inactivationIndicatorNode != null) {

                        inactiveConcept.setInactivationReason(inactivationIndicatorNode.asText());
                    } else {

                        inactiveConcept.setInactivationReason("NOT SPECIFIED");
                    }

                    final JsonNode associationTargets = conceptNode.get("associationTargets");

                    if (associationTargets != null && associationTargets.size() != 0 && associationTargets.fields() != null) {

                        LOG.debug("compileUpgradeData associationTargets: " + ModelUtility.toJson(associationTargets));
                        final Map<String, String> reasonMap = new HashMap<>();
                        final Entry<String, JsonNode> entry = associationTargets.fields().next();
                        final List<Concept> replacementConceptsToLookup = new ArrayList<>();
                        final String reason = entry.getKey();
                        String replacementConceptIds = entry.getValue().toString();
                        String replacementBodyConceptIds = "\"conceptIds\":[";

                        if (replacementConceptIds.contains("[")) {

                            replacementConceptIds = replacementConceptIds.substring(1, replacementConceptIds.length() - 1);
                        }

                        replacementConceptIds = replacementConceptIds.replaceAll("\"", "");

                        for (final String replacementConceptId : replacementConceptIds.split(",")) {

                            replacementCount++;
                            reasonMap.put(replacementConceptId, reason);
                            replacementConceptsToLookup.add(new Concept(replacementConceptId));
                            replacementBodyConceptIds += "\"" + replacementConceptId + "\",";
                        }

                        final String threadReplacementBodyConceptIds = StringUtils.removeEnd(replacementBodyConceptIds, ",") + "]";

                        LOG.debug("compileUpgradeData replacementConceptsToLookup: " + replacementConceptsToLookup);

                        // process descriptions of any replacement concepts
                        if (replacementConceptsToLookup.size() > 0) {

                            executor.submit(new Runnable() {

                                /* see superclass */
                                @Override
                                public void run() {

                                    try (final TerminologyService threadService = new TerminologyService()) {

                                        threadService.setModifiedBy(user.getUserName());
                                        threadService.setModifiedFlag(true);

                                        final List<String> replacementThatAreMembers = new ArrayList<>();
                                        final String memberSearchBody = bodyBase + threadReplacementBodyConceptIds + "}";
                                        LOG.debug("compileUpgradeData replacement member search url: " + conceptSearchUrl);
                                        LOG.debug("compileUpgradeData replacement member search body: " + memberSearchBody);

                                        try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, memberSearchBody)) {

                                            final String resultString = response.readEntity(String.class);

                                            // Only process payload if Rest call is successful
                                            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                                                throw new Exception("call to url '" + conceptSearchUrl + "' for member search wasn't successful. Status: "
                                                    + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                                            }

                                            final JsonNode root = mapper.readTree(resultString.toString());
                                            final Iterator<JsonNode> iterator = root.get("items").iterator();

                                            // loop thru the returned member details to mark
                                            // replacements that are
                                            // already members
                                            while (iterator != null && iterator.hasNext()) {

                                                final JsonNode conceptNode = iterator.next();
                                                replacementThatAreMembers.add(conceptNode.get("conceptId").asText());
                                            }

                                        }

                                        final List<Concept> conceptsToProcess = new ArrayList<>();
                                        int i = 0;

                                        for (final Concept concept : replacementConceptsToLookup) {

                                            conceptsToProcess.add(concept);
                                            i++;

                                            if (conceptsToProcess.size() == RefsetMemberService.CONCEPT_DESCRIPTIONS_PER_CALL
                                                || i == replacementConceptsToLookup.size()) {

                                                populateAllLanguageDescriptions(upgradeRefset, conceptsToProcess);
                                                conceptsToProcess.clear();
                                            }

                                        }

                                        for (final Concept replacementConcept : replacementConceptsToLookup) {

                                            final UpgradeReplacementConcept upgradeReplacementConcept = new UpgradeReplacementConcept();
                                            upgradeReplacementConcept.setCode(replacementConcept.getCode());
                                            upgradeReplacementConcept.setReason(reasonMap.get(replacementConcept.getCode()));
                                            upgradeReplacementConcept.setActive(replacementConcept.isActive());

                                            if (replacementThatAreMembers.contains(replacementConcept.getCode())) {

                                                upgradeReplacementConcept.setExistingMember(true);
                                            }

                                            if (conceptNode.get("descriptions") != null) {

                                                upgradeReplacementConcept.setDescriptions(ModelUtility.toJson(replacementConcept.getDescriptions()));
                                            }

                                            threadService.add(upgradeReplacementConcept);
                                            LOG.debug("compileUpgradeData added Replacement Concept: " + upgradeReplacementConcept);
                                            inactiveConcept.getReplacementConcepts().add(upgradeReplacementConcept);
                                        }

                                        threadService.add(inactiveConcept);
                                        LOG.debug("compileUpgradeData added inactive Concept: " + inactiveConcept);
                                    } catch (final Exception e) {

                                        throw new RuntimeException(e);
                                    }

                                }
                            });

                        } else {

                            service.add(inactiveConcept);
                            LOG.debug("compileUpgradeData added inactive Concept with no replacement: " + inactiveConcept);
                        }

                    }

                }

            }

        }

        executor.shutdown();
        executor.awaitTermination(600, TimeUnit.SECONDS);

        return status;
    }

    /* see superclass */
    @Override
    public String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset, final String inactiveConceptId,
        final String replacementConceptId, final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception {

        try {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refset.getId());

            List<String> unchangedConcepts = new ArrayList<>();
            final List<String> replacementChangeStatuses =
                Arrays.asList(RefsetMemberService.REPLACEMENT_REMOVED, RefsetMemberService.REPLACEMENT_ADDED, RefsetMemberService.REMOVED_MANUAL_REPLACEMENT);
            boolean add = true;
            String changeText = "added";
            boolean memberChange = true;
            boolean removeInactiveAlso = false;
            final UpgradeInactiveConcept upgradeInactiveConcept = RefsetMemberService.getUpgradeConcept(service, user, refset, inactiveConceptId);
            UpgradeReplacementConcept upgradeReplacementConcept = null;
            String conceptIdToChange = inactiveConceptId;

            if (changed.contains("REMOVED")) {

                add = false;
                changeText = "removed";
            }

            // if the operation needs it get the stored replacement concept
            if (replacementChangeStatuses.contains(changed)) {

                for (final UpgradeReplacementConcept replacementConcept : upgradeInactiveConcept.getReplacementConcepts()) {

                    if (replacementConcept.getCode().equals(replacementConceptId)) {

                        upgradeReplacementConcept = replacementConcept;
                        conceptIdToChange = replacementConceptId;

                        if (upgradeInactiveConcept.isStillMember() && changed.equals(RefsetMemberService.REPLACEMENT_ADDED)) {

                            removeInactiveAlso = true;

                        } else if (changed.equals(RefsetMemberService.REMOVED_MANUAL_REPLACEMENT) && !upgradeReplacementConcept.isAdded()) {

                            memberChange = false;
                        }

                    }

                }

            } else if (changed.equals(RefsetMemberService.NEW_MANUAL_REPLACEMENT)) {

                upgradeReplacementConcept = manualReplacementConcept;
                memberChange = false;
                conceptIdToChange = upgradeReplacementConcept.getCode();

                // save the replacement concept and add it to the inactive concept
                service.add(upgradeReplacementConcept);
                upgradeInactiveConcept.getReplacementConcepts().add(upgradeReplacementConcept);
            }

            LOG.debug("modifyUpgradeConcept: member change: " + conceptIdToChange);

            // add or remove the concept as a member to the refset
            if (memberChange) {

                RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refset.getId(), new HashMap<>());

                if (add) {

                    unchangedConcepts = RefsetMemberService.addRefsetMembers(service, user, refset, new ArrayList<>(Arrays.asList(conceptIdToChange)));

                    if (removeInactiveAlso && !unchangedConcepts.isEmpty()) {

                        removeInactiveAlso = false;

                    } else if (removeInactiveAlso && unchangedConcepts.isEmpty()) {

                        unchangedConcepts.addAll(RefsetMemberService.removeRefsetMembers(service, user, refset, inactiveConceptId));
                    }

                } else {

                    unchangedConcepts = RefsetMemberService.removeRefsetMembers(service, user, refset, conceptIdToChange);
                }

                // see if there the concept was unable to be changed and craft the error
                // message
                if (unchangedConcepts.size() > 0) {

                    return "The concept " + unchangedConcepts.get(0) + " was unable to be " + changeText;
                }

            }

            if (changed.equals(RefsetMemberService.INACTIVE_REMOVED)) {

                upgradeInactiveConcept.setStillMember(false);

            } else if (replacementChangeStatuses.contains(changed)) {

                if (upgradeReplacementConcept == null) {

                    throw new Exception("Unable to find replacement concept code");
                }

                if (memberChange) {

                    if (add) {

                        upgradeInactiveConcept.setReplaced(add);
                        LOG.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                    } else {

                        boolean isStillReplaced = false;

                        for (final UpgradeReplacementConcept replacementData : upgradeInactiveConcept.getReplacementConcepts()) {

                            if (upgradeReplacementConcept.getId().equals(replacementData.getId())) {

                                continue;
                            }

                            if (replacementData.isAdded() || replacementData.isExistingMember()) {

                                isStillReplaced = true;
                            }

                        }

                        if (!isStillReplaced) {

                            upgradeInactiveConcept.setReplaced(add);
                            LOG.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                        }

                    }

                    if (removeInactiveAlso && !unchangedConcepts.contains(inactiveConceptId)) {

                        upgradeInactiveConcept.setStillMember(false);
                    }

                    // If a replacement was successfully added for the first time then
                    // populate the
                    // member ID
                    if (changed.equals(RefsetMemberService.REPLACEMENT_ADDED) && unchangedConcepts.size() == 0) {

                        // when searching for members we only want concepts whose membership
                        // is active
                        // (though the concept itself can be inactive)
                        final String url = SnowstormConnection.getBaseUrl() + refset.getBranchPath() + "/members?referenceSet=" + refset.getRefsetId()
                            + "&active=true&referencedComponentId=" + conceptIdToChange;

                        LOG.debug("modifyUpgradeConcept Member list URL: " + url);

                        try (final Response response = SnowstormConnection.getResponse(url, SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES)) {

                            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                                throw new Exception("call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                                    + response.getStatusInfo().getReasonPhrase());
                            }

                            final ObjectMapper mapper = new ObjectMapper();
                            final String resultString = response.readEntity(String.class);

                            // Only process payload if Rest call is successful
                            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                                throw new Exception(Integer.toString(response.getStatus()));
                            }

                            final JsonNode root = mapper.readTree(resultString.toString());
                            final Iterator<JsonNode> iterator = root.get("items").iterator();

                            if (iterator.hasNext()) {

                                final JsonNode conceptNode = iterator.next();

                                if (conceptNode.get("referencedComponentId").asText().equals(conceptIdToChange)) {

                                    upgradeReplacementConcept.setMemberId(conceptNode.get("memberId").asText());
                                } else {

                                    throw new Exception("There was a problem getting the member ID");
                                }

                            }

                        }

                    }

                }

                // save or remove the replacement concept
                if (changed.equals(RefsetMemberService.REMOVED_MANUAL_REPLACEMENT)) {

                    upgradeInactiveConcept.getReplacementConcepts().remove(upgradeReplacementConcept);
                    service.remove(upgradeReplacementConcept);
                    LOG.debug("modifyUpgradeConcept: removed the replacement concept: " + conceptIdToChange);
                } else {

                    upgradeReplacementConcept.setAdded(add);
                    service.update(upgradeReplacementConcept);
                    LOG.debug("modifyUpgradeConcept: updated the replacement concept: " + conceptIdToChange);
                }

            } else if (changed.equals(RefsetMemberService.INACTIVE_ADDED)) {

                upgradeInactiveConcept.setStillMember(true);
            }

            // save the inactive concept
            service.update(upgradeInactiveConcept);

            // if this is adding or removing a replacement concept make the same
            // changes to
            // any duplicate concepts in the upgrade data
            if (Arrays.asList(RefsetMemberService.REPLACEMENT_REMOVED, RefsetMemberService.REPLACEMENT_ADDED).contains(changed)) {

                final ResultList<UpgradeInactiveConcept> upgradeData = RefsetMemberService.getUpgradeData(service, user, refset);

                for (final UpgradeInactiveConcept inactiveData : upgradeData.getItems()) {

                    if (upgradeInactiveConcept.getId().equals(inactiveData.getId())) {

                        continue;
                    }

                    boolean isStillReplaced = false;
                    boolean isReplacementChanging = false;

                    for (final UpgradeReplacementConcept replacementData : inactiveData.getReplacementConcepts()) {

                        // if this is already in the state it would be changed to then skip
                        // to the next
                        // inactive concept
                        if (add == replacementData.isAdded()) {

                            break;
                        }

                        // if this is the same concept as the main replacement being worked
                        // on change it
                        // as well
                        if (upgradeReplacementConcept != null && upgradeReplacementConcept.getCode().equals(replacementData.getCode())) {

                            isReplacementChanging = true;

                            replacementData.setAdded(upgradeReplacementConcept.isAdded());
                            replacementData.setMemberId(upgradeReplacementConcept.getMemberId());
                            service.update(replacementData);
                            LOG.debug("modifyUpgradeConcept: duplicate replacement marked as added = " + add);

                            continue;
                        }

                        if (replacementData.isAdded() || replacementData.isExistingMember()) {

                            isStillReplaced = true;
                        }

                    }

                    // if a replacement is being added or the replacement is being removed
                    // and there
                    // are no other added replacements
                    if ((add && isReplacementChanging) || (!add && isReplacementChanging && !isStillReplaced)) {

                        inactiveData.setReplaced(add);
                        inactiveData.setReplaced(inactiveData.isReplaced());
                        service.update(inactiveData);
                        LOG.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                    }

                }

            }

            return "All changes made successfully";

        } catch (final Exception e) {

            throw new Exception(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refset.getId());
        }
    }

    /*
     *
     * MAPPING FUNCTIONALITY
     *
     */

    /* see superclass */
    @Override
    public List<MapSet> getMapSets(final String branch) throws Exception {

        final File f = new File(handlerProperties.getProperty("dir") + "/MapSets.json");
        if (!f.exists()) {
            LOG.error("MapSet file doesn't exist: " + f.getPath());
            return null;
        }

        final ArrayList<MapSet> mapSets = new ArrayList<>();

        final ObjectMapper mapper = new ObjectMapper();

        final JsonNode root = mapper.readTree(f);
        final JsonNode mapSetsBatch = root.get("items");

        final Iterator<JsonNode> itemIterator = mapSetsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mapSetNode = itemIterator.next();

            final MapSet mapSet = new MapSet();
            mapSet.setRefSetCode(mapSetNode.get("id").asText());
            mapSet.setModuleId(mapSetNode.get("moduleId").asText());

            // Set refset name to FSN if it exists, defaulting to PT if not.
            if (mapSetNode.get("pt") != null) {

                mapSet.setRefSetName(mapSetNode.get("pt").get("term").asText());
            }
            if (mapSetNode.get("fsn") != null && mapSetNode.get("fsn").get("term") != null) {

                mapSet.setRefSetName(mapSetNode.get("fsn").get("term").asText());
            }

            final JsonNode additionalFields = mapSetNode.get("additionalFields");

            mapSet.setVersionStatus(additionalFields.get("versionStatus").asText());
            mapSet.setVersion(additionalFields.get("version").asText());
            mapSet.setModified(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(additionalFields.get("lastModified").asText()));
            mapSet.setFromTerminology(additionalFields.get("fromTerminology").asText());
            mapSet.setToTerminology(additionalFields.get("toTerminology").asText());

            mapSets.add(mapSet);
        }

        return mapSets;
    }

    /* see superclass */
    @Override
    public MapSet getMapSet(final String branch, final String code) throws Exception {

        final File f = new File(handlerProperties.getProperty("dir") + "/MapSets.json");
        if (!f.exists()) {
            LOG.error("MapSet file doesn't exist: " + f.getPath());
            return null;
        }

        final ObjectMapper mapper = new ObjectMapper();

        final JsonNode root = mapper.readTree(f);
        final JsonNode mapSetsBatch = root.get("items");

        final Iterator<JsonNode> itemIterator = mapSetsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mapSetNode = itemIterator.next();

            // Pull the requested mapSet
            if (!(mapSetNode.get("conceptId").asText().equals(code))) {
                continue;
            }

            final MapSet mapSet = new MapSet();
            mapSet.setRefSetCode(mapSetNode.get("id").asText());
            mapSet.setModuleId(mapSetNode.get("moduleId").asText());

            // Set refset name to FSN if it exists, defaulting to PT if not.
            if (mapSetNode.get("pt") != null) {

                mapSet.setRefSetName(mapSetNode.get("pt").get("term").asText());
            }
            if (mapSetNode.get("fsn") != null && mapSetNode.get("fsn").get("term") != null) {

                mapSet.setRefSetName(mapSetNode.get("fsn").get("term").asText());
            }

            final JsonNode additionalFields = mapSetNode.get("additionalFields");

            mapSet.setVersionStatus(additionalFields.get("versionStatus").asText());
            mapSet.setVersion(additionalFields.get("version").asText());
            mapSet.setModified(new SimpleDateFormat("yyyy-MM-dd hh:mm:ss").parse(additionalFields.get("lastModified").asText()));
            mapSet.setFromTerminology(additionalFields.get("fromTerminology").asText());
            mapSet.setToTerminology(additionalFields.get("toTerminology").asText());

            return mapSet;
        }

        return null;
    }

    /* see superclass */
    @Override
    public ResultListMapping getMappings(final String branch, final String mapSetCode, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        final File f = new File(handlerProperties.getProperty("dir") + "/Mappings.json");
        if (!f.exists()) {
            LOG.error("Mappings file doesn't exist: " + f.getPath());
            return null;
        }

        // Grab the specified mapSet
        final MapSet mapSet = getMapSet(branch, mapSetCode);

        final Map<String, Mapping> conceptIdToMappingMap = new HashMap<>();

        final ObjectMapper mapper = new ObjectMapper();

        final JsonNode root = mapper.readTree(f);
        final JsonNode mappingsBatch = root.get("items");

        final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mappingNode = itemIterator.next();

            // Only process active mappings from the correct mapset
            if (!(mappingNode.get("refsetId").asText().equals(mapSet.getRefSetCode()) && mappingNode.get("active").asText().equals("true"))) {
                continue;
            }

            // If this is the first time a fromConcept is encountered, set up the
            // mapping and add it to the tracker
            if (!conceptIdToMappingMap.containsKey(mappingNode.get("referencedComponentId").asText())) {
                final Mapping mapping = new Mapping();
                mapping.setCode(mappingNode.get("referencedComponentId").asText());
                mapping.setName(getConcept(/* branch, */ mapSet.getFromTerminology(), "", mapping.getCode()).getName());
                mapping.setMapSetId(mapSet.getId());
                mapping.setMapEntries(new ArrayList<>());

                conceptIdToMappingMap.put(mappingNode.get("referencedComponentId").asText(), mapping);
            }

            // Add an entry to the mapping
            final Mapping mapping = conceptIdToMappingMap.get(mappingNode.get("referencedComponentId").asText());
            final MapEntry mapEntry = new MapEntry();

            mapEntry.setModified(new SimpleDateFormat("yyyyMMdd").parse(mappingNode.get("effectiveTime").asText()));

            final JsonNode additionalFields = mappingNode.get("additionalFields");

            mapEntry.setRule(additionalFields.get("mapRule").asText());
            mapEntry.setPriority(additionalFields.get("mapPriority").asInt());
            mapEntry.setGroup(additionalFields.get("mapGroup").asInt());

            final Set<String> advices = new HashSet<>();
            String mapAdviceString = additionalFields.get("mapAdvice").asText();
            // Store each pipe-delimited section of the map advice string as a
            // separate map advice
            for (String mapAdvice : mapAdviceString.split("\\|")) {
                advices.add(mapAdvice.trim());
            }
            mapEntry.setAdvices(advices);

            final Concept relationConcept = getConcept(/* branch, */ mapSet.getFromTerminology(), "", additionalFields.get("mapCategoryId").asText());
            if (relationConcept != null) {
                mapEntry.setRelation(relationConcept.getName());
            } else {
                mapEntry.setRelation(mapEntry.getToCode() + " DOES NOT EXIST");
            }

            mapEntry.setToCode(additionalFields.get("mapTarget").asText());

            final Concept toConcept = getConcept(/* branch, */ mapSet.getToTerminology(), "", additionalFields.get("mapTarget").asText());

            if (toConcept != null) {
                mapEntry.setToName(toConcept.getName());
            } else {
                mapEntry.setToName(mapEntry.getToCode() + " DOES NOT EXIST");
            }

            final List<MapEntry> mapEntries = mapping.getMapEntries();
            mapEntries.add(mapEntry);
            mapping.setMapEntries(mapEntries);

        }

        // Once the file is completed parsed, return mappings as list
        final ResultListMapping mappings = new ResultListMapping();
        mappings.getItems().addAll(conceptIdToMappingMap.values());
        mappings.setTotal(conceptIdToMappingMap.size());
        mappings.setLimit(searchParameters.getLimit());

        return mappings;
    }

    /* see superclass */
    @Override
    public Mapping getMapping(final String branch, final String mapSetCode, final String conceptCode, final boolean showOverriddenEntries) throws Exception {

        final File f = new File(handlerProperties.getProperty("dir") + "/Mappings.json");
        if (!f.exists()) {
            LOG.error("Mappings file doesn't exist: " + f.getPath());
            return null;
        }

        // Grab the specified mapSet
        final MapSet mapSet = getMapSet(branch, mapSetCode);

        final Mapping mapping = new Mapping();

        final ObjectMapper mapper = new ObjectMapper();

        final JsonNode root = mapper.readTree(f);
        final JsonNode mappingsBatch = root.get("items");

        final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mappingNode = itemIterator.next();

            // Only process active mappings from the correct mapset
            if (!(mappingNode.get("refsetId").asText().equals(mapSet.getRefSetCode()) && mappingNode.get("active").asText().equals("true"))) {
                continue;
            }

            // Only process the requested concept
            if (!mappingNode.get("referencedComponentId").asText().equals(conceptCode)) {
                continue;
            }

            // If this is the first time the fromConcept is encountered, set up the
            // mapping
            if (mapping.getCode() == null || mapping.getCode().isEmpty()) {
                mapping.setCode(mappingNode.get("referencedComponentId").asText());
                mapping.setName(getConcept(/* branch, */ mapSet.getFromTerminology(), "", mapping.getCode()).getName());
                mapping.setMapSetId(mapSet.getId());
                mapping.setMapEntries(new ArrayList<>());
            }

            // Add an entry to the mapping
            final MapEntry mapEntry = new MapEntry();

            mapEntry.setModified(new SimpleDateFormat("yyyyMMdd").parse(mappingNode.get("effectiveTime").asText()));

            final JsonNode additionalFields = mappingNode.get("additionalFields");

            mapEntry.setRule(additionalFields.get("mapRule").asText());
            mapEntry.setPriority(additionalFields.get("mapPriority").asInt());
            mapEntry.setGroup(additionalFields.get("mapGroup").asInt());

            final Set<String> advices = new HashSet<>();
            String mapAdviceString = additionalFields.get("mapAdvice").asText();
            // Store each pipe-delimited section of the map advice string as a
            // separate map advice
            for (String mapAdvice : mapAdviceString.split("\\|")) {
                advices.add(mapAdvice.trim());
            }
            mapEntry.setAdvices(advices);

            final Concept relationConcept = getConcept(/* branch, */ mapSet.getFromTerminology(), "", additionalFields.get("mapCategoryId").asText());
            if (relationConcept != null) {
                mapEntry.setRelation(relationConcept.getName());
            } else {
                mapEntry.setRelation(mapEntry.getToCode() + " DOES NOT EXIST");
            }

            mapEntry.setToCode(additionalFields.get("mapTarget").asText());

            final Concept toConcept = getConcept(/* branch, */ mapSet.getToTerminology(), "", additionalFields.get("mapTarget").asText());

            if (toConcept != null) {
                mapEntry.setToName(toConcept.getName());
            } else {
                mapEntry.setToName(mapEntry.getToCode() + " DOES NOT EXIST");
            }

            final List<MapEntry> mapEntries = mapping.getMapEntries();
            mapEntries.add(mapEntry);
            mapping.setMapEntries(mapEntries);
        }

        // Once the file is completed parsing, return the mapping
        return mapping;
    }

    /* see superclass */
    @Override
    public Concept getConcept(/* final String branch, */ final String terminology, final String version, final String code) throws Exception {

        final File f = new File(handlerProperties.getProperty("dir") + "/Concepts" + terminology + ".json");
        if (!f.exists()) {
            LOG.error("File for specified terminology doesn't exist: " + f.getPath());
            return null;
        }

        final ObjectMapper mapper = new ObjectMapper();

        final JsonNode root = mapper.readTree(f);
        final JsonNode conceptNodeBatch = root.get("items");

        final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode conceptNode = itemIterator.next();

            // Pull the requested, active concept
            if (!(conceptNode.get("conceptId").asText().equals(code) && conceptNode.get("active").asText().equals("true"))) {
                continue;
            }

            final Concept concept = new Concept();
            concept.setActive(conceptNode.get("active").asBoolean());
            concept.setId(conceptNode.get("id").asText());
            concept.setCode(conceptNode.get("id").asText());

            if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {

                concept.setDefined(true);
            } else {

                concept.setDefined(false);
            }

            if (conceptNode.get("pt") != null) {

                concept.setName(conceptNode.get("pt").get("term").asText());
            }

            if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {

                concept.setFsn(conceptNode.get("fsn").get("term").asText());
            }

            return concept;
        }

        // If no concept with the specified terminology and code found, return null
        return null;
    }

    /* see superclass */
    @Override
    public ResultListConcept findConcepts(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        // TODO implement with Snowstorm
        throw new UnsupportedOperationException("Method not implemented");
    }

    /* see superclass */
    @Override
    public String getName() {

        return ModelUtility.getNameFromClass(JSONTerminologyServerHandler.class);
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

        // TODO implement with Snowstorm
        throw new UnsupportedOperationException("Method not implemented");
    }

    /* see superclass */
    @Override
    public List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        // TODO implement with Snowstorm
        throw new UnsupportedOperationException("Method not implemented");
    }

    /* see superclass */
    @Override
    public File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest) throws Exception {

        // TODO implement with Snowstorm
        throw new UnsupportedOperationException("Method not implemented");
    }

    /* see superclass */
	@Override
	public List<Mapping> importMappings(MapProject mapProject, String branch, MultipartFile mappingFile) throws Exception {
		
	    // TODO implement with Snowstorm
		throw new UnsupportedOperationException("Method not implemented");
	}


}
