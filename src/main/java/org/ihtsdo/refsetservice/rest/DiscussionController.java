/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.DiscussionService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Controller for /discussion endpoints.
 * 
 */
@RestController
@OpenAPIDefinition(info = @Info(title = "Discussion Controller", version = "1.0.0", description = "Endpoints for adding, updating, and removing discussions and posts."), tags = {
		@Tag(name = "discussion", description = "Discussion service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class DiscussionController extends BaseController {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(DiscussionController.class);

	/**
	 * Returns a list of discussion threads based on the refset and possibly member
	 * ID.
	 *
	 * @param type             Object type, e.g. 'REFSET, REFSET_MEMEBER'
	 * @param refsetInternalId The internal ID of the Refset
	 * @param conceptId        The concept ID of the refset member if this is a
	 *                         member type
	 * @return a list of matching discussion threads
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/discussion/{type}/{refsetInternalId}")
	@Operation(summary = "Get discussions for the specified parameters. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested discussion"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden") })
	@Parameters({
			@Parameter(name = "type", description = "Object type, e.g. 'REFSET, REFSET_MEMEBER'", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "refsetInternalId", description = "The internal ID of the Refset", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "conceptId", description = "The concept ID of the refset member if this is a member type", required = false, schema = @Schema(implementation = String.class)) })
	@RecordMetric
	public @ResponseBody ResponseEntity<ResultList<DiscussionThread>> getDiscussions(
			@PathVariable(value = "type") final DiscussionType type,
			@PathVariable(value = "refsetInternalId") final String refsetInternalId,
			@RequestParam(required = false) final String conceptId) throws Exception {

		final User authUser = authorizeUser();
		try {

			try (final TerminologyService service = new TerminologyService()) {

				final Refset refset = RefsetService.getRefset(service, authUser, refsetInternalId);

				final ResultList<DiscussionThread> results = DiscussionService.getDiscussions(service, authUser, type,
						refset, conceptId);
				return new ResponseEntity<>(results, HttpStatus.OK);
			}

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Returns a single discussion thread.
	 *
	 * @param id the discussion ID
	 * @return the discussion thread
	 * @throws Exception the exception
	 */
	@RequestMapping(method = RequestMethod.GET, value = "/discussion/{id}")
	@Operation(summary = "Returns discussion thread. To see certain results this call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved the requested discussion"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden") })
	@Parameters({
			@Parameter(name = "id", description = "Discussion id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)), })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> getDiscussion(@PathVariable(value = "id") final String id)
			throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread discussionThread = DiscussionService.getDiscussion(service, authUser, id);

			if (discussionThread == null) {
				return null;
			}

			return new ResponseEntity<>(discussionThread, new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Create a new discussion thread.
	 *
	 * @param thread the discussion thread
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PostMapping("/discussion")
	@Operation(summary = "Add discussion. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Added discussion"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden") })
	@Parameters({ @Parameter(name = "thread", description = "Discussion thread object", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> createDiscussionThread(
			@RequestBody final DiscussionThread thread) throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());
			final boolean permittedRole = refset.getRoles().contains(User.ROLE_VIEWER);

			// If the user does not have the correct permissions then return an error
			if ((refset.isPrivateRefset() || thread.isPrivateThread()) && !permittedRole) {

				LOG.error("createDiscussionThread: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			final DiscussionPost post = thread.getPosts().get(0);
			post.setUser(authUser);
			service.add(post);

			service.update(refset);
			service.add(thread);

			service.commit();

			return new ResponseEntity<>(thread, new HttpHeaders(), HttpStatus.CREATED);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Add a post to a discussion thread.
	 *
	 * @param threadId the discussion thread ID
	 * @param post     the post to add
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PostMapping("/discussion/{threadId}/post")
	@Operation(summary = "Add post to existing discussion thread. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "201", description = "Added discussion post to discussion thread."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "post", description = "Post object.", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionPost> createPost(
			@PathVariable(value = "threadId") final String threadId, @RequestBody final DiscussionPost post)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

			if (thread == null) {
				throw new RestException(false, 404, "Not found", "Unable to find discussion thread for " + threadId);
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());
			final boolean permittedRole = refset.getRoles().contains(User.ROLE_VIEWER);

			// If the user does not have the correct permissions then return an error
			if ((refset.isPrivateRefset() || thread.isPrivateThread()) && !permittedRole) {
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			post.setUser(authUser);
			service.add(post);

			thread.getPosts().add(post);
			service.update(thread);

			service.update(refset);

			service.commit();

			return new ResponseEntity<>(post, new HttpHeaders(), HttpStatus.CREATED);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update the discussion thread.
	 *
	 * @param threadId the discussion thread ID
	 * @param thread   the discussion thread
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PutMapping("/discussion/{threadId}")
	@Operation(summary = "Update discussion thread. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Updated discussion thread"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "thread", description = "Discussion thread object", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThread(
			@PathVariable(value = "threadId") final String threadId, @RequestBody final DiscussionThread thread)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread originalThread = service.get(threadId, DiscussionThread.class);

			if (thread == null) {

				LOG.error("updateDiscussionThread: Unable to retrieve discussion thread id: {}.", threadId);
				throw new RestException(false, 404, "Not found",
						"Unable to find discussion thread for " + threadId + ".");
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());

			// If the user does not have the correct permissions then return an error
			if (!DiscussionService.canUserEditThread(authUser, refset, originalThread)) {

				LOG.error("updateDiscussionThread: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			originalThread.setSubject(thread.getSubject());
			originalThread.setStatus(thread.getStatus());
			originalThread.setVisibility(thread.getVisibility());
			originalThread.setPrivateThread(thread.isPrivateThread());
			service.update(originalThread);

			final DiscussionPost post = originalThread.getPosts().get(0);
			post.setMessage(thread.getPosts().get(0).getMessage());
			post.setPrivatePost(thread.isPrivateThread());
			service.update(post);

			service.update(refset);

			service.commit();

			return new ResponseEntity<>(originalThread, new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update the discussion thread's status.
	 *
	 * @param threadId the discussion thread ID
	 * @param status   the discussion thread's status
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PutMapping("/discussion/{threadId}/status")
	@Operation(summary = "Set discussion thread status. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Updated discussion thread status"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "status", description = "the discussion thread's status", required = true, schema = @Schema(implementation = String.class)) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadStatus(
			@PathVariable(value = "threadId") final String threadId, @RequestParam final String status)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

			if (thread == null) {

				LOG.error("updateDiscussionThreadStatus: Unable to retrieve discussion thread id: {}.", threadId);
				throw new RestException(false, 404, "Not found",
						"Unable to find discussion thread for " + threadId + ".");
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());

			// If the user does not have the correct permissions then return an error
			if (!DiscussionService.canUserEditThread(authUser, refset, thread)) {

				LOG.error("updateDiscussionThreadStatus: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			thread.setStatus(status);

			// Update
			service.update(thread);
			service.update(refset);

			service.commit();

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update the discussion thread's privacy.
	 *
	 * @param threadId  the discussion thread ID
	 * @param isPrivate is the discussion thread private
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PutMapping("/discussion/{threadId}/privacy")
	@Operation(summary = "Set the privacy of a discussion thread. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully updated discussion thread privacy"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "isPrivate", description = "Is the discussion thread private", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadPrivacy(
			@PathVariable(value = "threadId") final String threadId, @RequestParam final boolean isPrivate)
			throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

			if (thread == null) {

				LOG.error("updateDiscussionThreadPrivacy: Unable to retrieve discussion thread id: {}.", threadId);
				throw new RestException(false, 404, "Not found",
						"Unable to find discussion thread for " + threadId + ".");
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());

			// If the user does not have the correct permissions then return an error
			if (!DiscussionService.canUserEditThread(authUser, refset, thread)) {

				LOG.error("updateDiscussionThreadPrivacy: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			thread.setPrivateThread(isPrivate);
			service.update(thread);

			final DiscussionPost post = thread.getPosts().get(0);
			post.setPrivatePost(isPrivate);
			service.update(post);

			service.update(refset);

			service.commit();

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update the discussion thread's visibility.
	 *
	 * @param threadId   the discussion thread ID
	 * @param visibility the discussion thread's visibility
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PutMapping("/discussion/{threadId}/visibility")
	@Operation(summary = "Set discussion thread visibility. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Updated discussion thread visibility"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "visibility", description = "The discussion thread's visibility", required = true, schema = @Schema(implementation = String.class)) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadVisibility(
			@PathVariable(value = "threadId") final String threadId, @RequestParam final String visibility)
			throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

			if (thread == null) {

				LOG.error("updateDiscussionThreadVisibility: Unable to retrieve discussion thread id: {}.", threadId);
				throw new RestException(false, 404, "Not found",
						"Unable to find discussion thread for " + threadId + ".");
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());

			// If the user does not have the correct permissions then return an error
			if (!DiscussionService.canUserEditThread(authUser, refset, thread)) {

				LOG.error(
						"updateDiscussionThreadVisibility: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			thread.setVisibility(visibility);

			// Update
			service.update(thread);
			service.update(refset);

			service.commit();

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update the discussion post's privacy.
	 *
	 * @param threadId  the discussion thread ID
	 * @param postId    the discussion post ID
	 * @param isPrivate is the discussion post private
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PutMapping("/discussion/{threadId}/post/{postId}/privacy")
	@Operation(summary = "Set discussion post privacy. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Updated the discussion post privacy"),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "postId", description = "Discussion post id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "isPrivate", description = "Is the discussion post private", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionPostPrivacy(
			@PathVariable(value = "threadId") final String threadId,
			@PathVariable(value = "postId") final String postId, @RequestParam final boolean isPrivate)
			throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

			if (thread == null) {

				LOG.error("updateDiscussionPostPrivacy: Unable to retrieve discussion thread id: {}.", threadId);
				throw new RestException(false, 404, "Not found",
						"Unable to find discussion thread for " + threadId + ".");
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());
			final DiscussionPost post = service.get(postId, DiscussionPost.class);

			if (post == null) {

				LOG.error("updateDiscussionPostPrivacy: Unable to retrieve discussion post id: {}.", postId);
				throw new RestException(false, 404, "Not found", "Unable to find discussion post for " + postId + ".");
			}

			// If the user does not have the correct permissions then return an error
			if (!DiscussionService.canUserEditPost(authUser, refset, post)) {

				LOG.error("updateDiscussionPostPrivacy: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			post.setPrivatePost(isPrivate);
			service.update(post);

			for (final DiscussionPost threadPost : thread.getPosts()) {

				if (threadPost.getId().equals(post.getId())) {

					threadPost.setPrivatePost(isPrivate);
					break;
				}
			}

			service.update(thread);
			service.update(refset);

			service.commit();

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Update a discussion post.
	 *
	 * @param threadId    the discussion thread ID
	 * @param postId      the discussion post ID
	 * @param updatedPost the post to update
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@PutMapping("/discussion/{threadId}/post/{postId}")
	@Operation(summary = "Updates a discussion post. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully updated the discussion post."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found"),
			@ApiResponse(responseCode = "417", description = "Expectation failed") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "postId", description = "Discussion post id, e.g. &l5;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "updatedPost", description = "Discussion post object", required = true) })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionPost> updateDiscussionPost(
			@PathVariable(value = "threadId") final String threadId,
			@PathVariable(value = "postId") final String postId, @RequestBody final DiscussionPost updatedPost)
			throws Exception {

		final User authUser = authorizeUser();

		try (final TerminologyService service = new TerminologyService()) {

			final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

			if (!postId.equals(updatedPost.getId())) {

				final String message = "The postId parameter " + postId
						+ " does not match the id property of the updatedPost parameter " + updatedPost.getId() + ".";
				LOG.error("updateDiscussionPost: " + message);
				throw new RestException(false, 417, "Expectation failed", message);
			}

			if (thread == null) {

				LOG.error("updateDiscussionPost: Unable to retrieve discussion thread id: {}.", threadId);
				throw new RestException(false, 404, "Not found",
						"Unable to find discussion thread for " + threadId + ".");
			}

			final Refset refset = RefsetService.getRefset(service, authUser, thread.getRefsetInternalId());
			final DiscussionPost existingPost = service.get(postId, DiscussionPost.class);

			if (existingPost == null) {

				LOG.error("updateDiscussionPost: Unable to retrieve discussion post id: {}.", postId);
				throw new RestException(false, 404, "Not found", "Unable to find discussion post for " + postId + ".");
			}

			// If the user does not have the correct permissions then return an error
			if (!DiscussionService.canUserEditPost(authUser, refset, existingPost)) {

				LOG.error("updateDiscussionPost: User does not have permissions to perform this action: {}.",
						authUser.getUserName());
				throw new RestException(false, 403, "Forbidden",
						"User does not have permissions to perform this action.");
			}

			service.setTransactionPerOperation(false);
			service.beginTransaction();
			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			existingPost.setMessage(updatedPost.getMessage());
			existingPost.setPrivatePost(updatedPost.isPrivatePost());
			service.update(existingPost);

			for (final DiscussionPost threadPost : thread.getPosts()) {

				if (threadPost.getId().equals(existingPost.getId())) {

					threadPost.populateFrom(existingPost);
					break;
				}
			}

			service.update(thread);
			service.update(refset);

			service.commit();

			return new ResponseEntity<>(existingPost, new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Delete a discussion post.
	 *
	 * @param threadId the discussion thread ID
	 * @param postId   the discussion post ID
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@DeleteMapping("/discussion/{threadId}/post/{postId}")
	@Operation(summary = "Delete discussion post. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully deleted discussion thread."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)),
			@Parameter(name = "postId", description = "Discussion post id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)), })
	@RecordMetric
	public @ResponseBody ResponseEntity<DiscussionThread> deleteDiscussionPost(
			@PathVariable(value = "threadId") final String threadId,
			@PathVariable(value = "postId") final String postId) throws Exception {

		final User user = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(user.getUserName());
			service.setModifiedFlag(true);

			DiscussionService.deletePost(service, user, threadId, postId);

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

	/**
	 * Delete a discussion thread.
	 *
	 * @param threadId the discussion thread ID
	 * @return the response entity
	 * @throws Exception the exception
	 */
	@Operation(summary = "Deletes a discussion thread. This call requires authentication with the correct role.", responses = {
			@ApiResponse(responseCode = "200", description = "Successfully deleted the provided discussion thread."),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "403", description = "Forbidden"),
			@ApiResponse(responseCode = "404", description = "Not found") })
	@Parameters({
			@Parameter(name = "threadId", description = "Discussion thread id, e.g. &lt;uuid&gt;", required = true, schema = @Schema(implementation = String.class)), })
	@RecordMetric
	@DeleteMapping("/discussion/{threadId}")
	public @ResponseBody ResponseEntity<String> deleteDiscussionThread(
			@PathVariable(value = "threadId") final String threadId) throws Exception {

		final User authUser = authorizeUser();
		try (final TerminologyService service = new TerminologyService()) {

			service.setModifiedBy(authUser.getUserName());
			service.setModifiedFlag(true);

			DiscussionService.deleteThread(service, authUser, threadId);

			return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

		} catch (final Exception e) {

			handleException(e);
			return null;
		}
	}

}
