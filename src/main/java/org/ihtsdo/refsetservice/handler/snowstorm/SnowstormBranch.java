/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class Branch.
 */
public final class SnowstormBranch extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormBranch.class);

  /**
   * Creates the branch.
   *
   * @param parentBranchPath the parent branch path
   * @param branchName the branch name
   * @return the string
   * @throws Exception the exception
   */
  public static String createBranch(final String parentBranchPath, final String branchName)
    throws Exception {

    final long start = System.currentTimeMillis();
    String refsetBranchPath = null;
    final String url = SnowstormConnection.getBaseUrl() + "branches";
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode body =
        mapper.createObjectNode().put("name", branchName).put("parent", parentBranchPath);

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

      LOG.info(
          "Created branch " + refsetBranchPath + ". Time: " + (System.currentTimeMillis() - start));
    }

    return refsetBranchPath;
  }

  /**
   * Delete branch.
   *
   * @param branchPath the branch path
   * @return true, if successful
   * @throws Exception the exception
   */
  public static boolean deleteBranch(final String branchPath) throws Exception {

    final long start = System.currentTimeMillis();
    final String url =
        SnowstormConnection.getBaseUrl() + "admin/" + branchPath + "/actions/hard-delete";

    LOG.debug("deleteBranch URL: " + url);

    try (final Response response = SnowstormConnection.deleteResponse(url, null)) {

      // Only process payload if Rest call is successful
      if (response.getStatus() == Response.Status.OK.getStatusCode()) {

        LOG.info(
            "Deleted branch " + branchPath + ". Time: " + (System.currentTimeMillis() - start));
        return true;
      } else {

        LOG.error("Could not delete branch " + branchPath);
        return false;
      }

    }
  }

  /**
   * Does branch exist.
   *
   * @param branchPath the branch path
   * @return true, if successful
   * @throws Exception the exception
   */
  public static boolean doesBranchExist(final String branchPath) throws Exception {

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

  /**
   * Gets the branch children.
   *
   * @param branchPath the branch path
   * @return the branch children
   * @throws Exception the exception
   */
  public static List<String> getBranchChildren(final String branchPath) throws Exception {

    final String url = SnowstormConnection.getBaseUrl() + "branches/" + branchPath
        + "children?immediateChildren=true&page=0&size=9000";
    final List<String> childBranchPaths = new ArrayList<>();

    LOG.debug("getBranchChildren URL: " + url);

    try (final Response response = SnowstormConnection.getResponse(url)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
        throw new Exception("call to url '" + url + "' wasn't successful. " + response.getStatus()
            + ": " + response.getStatusInfo().getReasonPhrase());
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

  /**
   * Merge branch.
   *
   * @param sourceBranchPath the source branch path
   * @param targetBranchPath the target branch path
   * @param comment the comment
   * @param rebase the rebase
   * @throws Exception the exception
   */
  public static void mergeBranch(final String sourceBranchPath, final String targetBranchPath,
    final String comment, final boolean rebase) throws Exception {

    final long start = System.currentTimeMillis();
    final String mergeUrl = SnowstormConnection.getBaseUrl() + "merges";
    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode body =
        mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);
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
      if (response.getStatus() != Response.Status.OK.getStatusCode()
          && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

        LOG.error("mergeBranch response status: " + response.getStatus());
        LOG.error(
            "mergeBranch response status reason: " + response.getStatusInfo().getReasonPhrase());
        final String error =
            "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath;
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

              final String error = "Could not merge branch " + sourceBranchPath + " into branch "
                  + targetBranchPath + ". Error: " + message;
              LOG.error(error);
              throw new Exception(error);

            } else {
              LOG.debug("Merge did not occurr. " + message);
            }

          } else if (status.equals("PENDING") || status.equals("IN_PROGRESS")
              || status.equals("SCHEDULED")) {

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
              final String stateUrl =
                  SnowstormConnection.getBaseUrl() + "branches/" + targetBranchPath;
              LOG.debug("Promoted branch state info at " + stateUrl);

              while (!stateGood) {

                try (final Response stateResponse = SnowstormConnection.getResponse(stateUrl)) {

                  final String stateResultString = stateResponse.readEntity(String.class);
                  final JsonNode stateRoot = mapper.readTree(stateResultString.toString());
                  final String state = stateRoot.get("state").asText();

                  LOG.info("Promoted branch state is: " + state);

                  if (state.equals("FORWARD") || state.equals("CURRENT")
                      || state.equals("UP_TO_DATE")) {
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

            LOG.info("Merged branch " + sourceBranchPath + " into branch " + targetBranchPath
                + ". Time: " + (System.currentTimeMillis() - start));
          }
        }
      }
    }
  }

  /**
   * Merge rebase review.
   *
   * @param sourceBranchPath the source branch path
   * @param targetBranchPath the target branch path
   * @return the string
   * @throws Exception the exception
   */
  public static String mergeRebaseReview(final String sourceBranchPath,
    final String targetBranchPath) throws Exception {

    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode body =
        mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);
    final String reviewUrl = SnowstormConnection.getBaseUrl() + "merge-reviews";
    String jobStatusUrl = null;
    boolean jobDone = false;
    String reviewId = "";
    LOG.debug("mergeRebaseReview review URL: " + reviewUrl + " ; body: " + body.toString());

    try (final Response response = SnowstormConnection.postResponse(reviewUrl, body.toString())) {

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()
          && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

        final String error = "Could not review branch rebase of " + sourceBranchPath
            + " into branch " + targetBranchPath;
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

        String error = "Could not review merge branch " + sourceBranchPath + " into branch "
            + targetBranchPath + ". ";

        // Only process payload if Rest call is successful
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
          LOG.error(error + " Status: " + Integer.toString(response.getStatus()) + ". Error: "
              + response.getStatusInfo().getReasonPhrase());
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

  /**
   * Gets the branch versions.
   *
   * @param editionPath the edition path
   * @return the branch versions
   * @throws Exception the exception
   */
  public static List<String> getBranchVersions(final String editionPath) throws Exception {

    final String url = SnowstormConnection.getBaseUrl() + "branches/" + editionPath
        + "/children?immediateChildren=true";
    final List<String> branchCache = RefsetService.getCacheForBranchVersions(editionPath);

    // check if the concept call has been cached
    if (branchCache.size() > 0) {
      LOG.debug("getBranchVersions USING CACHE");
      return branchCache;
    }

    try (final Response response = SnowstormConnection.getResponse(url)) {

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()) {
        throw new Exception(
            "Unable to get edition versions. Status: " + Integer.toString(response.getStatus())
                + ". Error: " + response.getStatusInfo().getReasonPhrase());
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
          final Date branchDate =
              DateUtility.getDate(childDate, DateUtility.DATE_FORMAT_REVERSE, null);
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
}
