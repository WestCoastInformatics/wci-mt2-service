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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.DiscussionService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * Controller for /discussion endpoints.
 * 
 */
@RestController
@Api(tags = "Discussion endpoints")
@SuppressWarnings("javadoc")
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class DiscussionController extends BaseController {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(DiscussionController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Returns a list of discussion threads based on the refset and possibly member ID.
     *
     * @param type Object type, e.g. 'REFSET, REFSET_MEMEBER'
     * @param refsetInternalId The internal ID of the Refset
     * @param conceptId The concept ID of the refset member if this is a member type
     * @return a list of matching discussion threads
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get all discussions for the specified object type and object key", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested discussion"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "type", value = "Object type, e.g. 'REFSET, REFSET_MEMEBER'", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "objectKey", value = "The internal ID of the Refset", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "conceptId", value = "The concept ID of the refset member if this is a member type", required = false, dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/discussion/{type}/{refsetInternalId}")
    public @ResponseBody ResponseEntity<ResultList<DiscussionThread>> getDiscussions(@PathVariable(value = "type") final DiscussionType type, @PathVariable(value = "refsetInternalId") final String refsetInternalId,
        @RequestParam(required = false) final String conceptId) throws Exception {

        try {

            logger.debug("Get discussions for type: " + type.name() + "; refsetInternalId: " + refsetInternalId + "; conceptId: " + conceptId);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

                final ResultList<DiscussionThread> results = DiscussionService.getDiscussions(service, user, type, refset, conceptId);

                logger.debug("getDiscussionThreads: results: " + ModelUtility.toJson(results));

                return new ResponseEntity<>(results, HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }
    }

    /**
     * Returns a single discussion thread.
     *
     * @param id the discussion ID
     * @return the discussion thread
     * @throws Exception the exception
     */
    @ApiOperation(value = "Returns a single discussion thread", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested discussion"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "id", value = "The ID of the discussion", required = true, dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/discussion/{id}")
    public @ResponseBody ResponseEntity<DiscussionThread> getDiscussion(@PathVariable(value = "id") final String id) throws Exception {

        try {

            logger.debug("getDiscussion: Get discussion for id: " + id);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread discussionThread = DiscussionService.getDiscussion(service, user, id);

                if (discussionThread == null) {

                    final String message = "Unable to retrieve discussion thread: " + id;
                    logger.info("getDiscussion: " + message);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", message);
                }

                logger.debug("getDiscussion: discussionThread: " + ModelUtility.toJson(discussionThread));

                return new ResponseEntity<>(discussionThread, new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            return handleException(e);
        }
    }

    /**
     * Create a new discussion thread.
     *
     * @param thread the discussion thread
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Create a new discussion", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Created"), @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "thread", value = "The discussion thread to create", required = true, dataTypeClass = DiscussionThread.class, paramType = "body"),
    })
    @RecordMetric
    @PostMapping("/discussion")
    public @ResponseBody ResponseEntity<DiscussionThread> createDiscussionThread(@RequestBody final DiscussionThread thread) throws Exception {

        try {

            logger.debug("createDiscussionThread thread: " + thread);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());
                final boolean permittedRole = refset.getRoles().contains(User.ROLE_VIEWER);

                // If the user does not have the correct permissions then return an error
                if ((refset.isPrivateRefset() || thread.isPrivateThread()) && !permittedRole) {

                    logger.error("createDiscussionThread: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
                service.setModifiedFlag(true);

                final DiscussionPost post = thread.getPosts().get(0);
                post.setUser(user);
                service.add(post);

                service.add(thread);

                return new ResponseEntity<>(thread, new HttpHeaders(), HttpStatus.CREATED);
            }

        } catch (final Exception e) {

            return handleException(e);
        }
    }

    /**
     * Add a post to a discussion thread.
     *
     * @param threadId the discussion thread ID
     * @param post the post to add
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Add a new discussion post to a discussion thread", response = DiscussionPost.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Added discussion post to discussion thread."), @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "Id of the discussion thread to add the post to.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "post", value = "The post to be added to the discussion thread.", required = true, dataTypeClass = DiscussionPost.class, paramType = "body"),
    })
    @RecordMetric
    @PostMapping("/discussion/{threadId}/post")
    public @ResponseBody ResponseEntity<DiscussionPost> createPost(@PathVariable(value = "threadId") final String threadId, @RequestBody final DiscussionPost post) throws Exception {

        try {

            logger.debug("createPost threadId: " + threadId + "; post: " + post);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

                if (thread == null) {

                    logger.error("createPost: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());
                final boolean permittedRole = refset.getRoles().contains(User.ROLE_VIEWER);

                // If the user does not have the correct permissions then return an error
                if ((refset.isPrivateRefset() || thread.isPrivateThread()) && !permittedRole) {

                    logger.error("createPost: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
                service.setModifiedFlag(true);
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                post.setUser(user);
                service.add(post);

                thread.getPosts().add(post);
                service.update(thread);

                service.commit();
            }

            return new ResponseEntity<>(post, new HttpHeaders(), HttpStatus.CREATED);
        }
        
        catch (final Exception e) {

            logger.error("Error adding post: {} to discussionThreadId: {}", post, threadId);
            return handleException(e);
        }
    }

    /**
     * Update the discussion thread.
     *
     * @param threadId the discussion thread ID
     * @param thread the discussion thread
     * @return the response entity
     * @throws Exception the exception
     */
    @SuppressWarnings("rawtypes")
    // @ApiOperation(value = "Update a discussion for the specified ID", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion"), @ApiResponse(code = 400, message = "Bad request"), @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "Id of the discussion thread update.", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "thread", value = "The updated discussion thread", required = true, dataTypeClass = DiscussionThread.class, paramType = "body")
    })
    @RecordMetric
    @PutMapping("/discussion/{threadId}")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThread(@PathVariable(value = "threadId") final String threadId, @RequestBody final DiscussionThread thread) throws Exception {

        try {

            logger.debug("updateDiscussionThread threadId: " + threadId + "; thread: " + thread);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread originalThread = service.get(threadId, DiscussionThread.class);

                if (thread == null) {

                    logger.error("updateDiscussionThread: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());

                // If the user does not have the correct permissions then return an error
                if (!DiscussionService.canUserEditThread(user, refset, originalThread)) {

                    logger.error("updateDiscussionThread: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
                service.setModifiedFlag(true);
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                originalThread.setSubject(thread.getSubject());
                originalThread.setStatus(thread.getStatus());
                originalThread.setVisibility(thread.getVisibility());
                originalThread.setPrivateThread(thread.isPrivateThread());
                service.update(originalThread);

                final DiscussionPost post = originalThread.getPosts().get(0);
                post.setMessage(thread.getPosts().get(0).getMessage());
                post.setPrivatePost(thread.isPrivateThread());
                service.update(post);

                service.commit();

                return new ResponseEntity<>(originalThread, new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("updateDiscussionThread Error updating thread for discussionThread: {}", thread.toString());
            return handleException(e);
        }
    }

    /**
     * Update the discussion thread's status.
     *
     * @param threadId the discussion thread ID
     * @param status the discussion thread's status
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Set the status a discussion thread.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion thread's status."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"), @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "status", value = "", required = true, dataTypeClass = String.class, paramType = "query")
    })
    @RecordMetric
    @PutMapping("/discussion/{threadId}/status")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadStatus(@PathVariable(value = "threadId") final String threadId, @RequestParam final String status) throws Exception {

        try {

            logger.debug("updateDiscussionThreadStatus threadId: " + threadId + "; status: " + status);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

                if (thread == null) {

                    logger.error("updateDiscussionThreadStatus: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());

                // If the user does not have the correct permissions then return an error
                if (!DiscussionService.canUserEditThread(user, refset, thread)) {

                    logger.error("updateDiscussionThreadStatus: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
                service.setModifiedFlag(true);

                thread.setStatus(status);

                // Update
                service.update(thread);

                return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("updateDiscussionThreadStatus Error updating thread for discussionThread: {}", threadId);
            return handleException(e);
        }
    }

    /**
     * Update the discussion thread's privacy.
     *
     * @param threadId the discussion thread ID
     * @param isPrivate is the discussion thread private
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Set the privacy of a discussion thread.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion thread's privacy."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"), @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "isPrivate", value = "true", required = true, dataTypeClass = Boolean.class, paramType = "query")
    })
    @RecordMetric
    @PutMapping("/discussion/{threadId}/privacy")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadPrivacy(@PathVariable(value = "threadId") final String threadId, @RequestParam final boolean isPrivate)
        throws Exception {

        try {

            logger.debug("updateDiscussionThreadPrivacy threadId: " + threadId + "; isPrivate: " + isPrivate);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

                if (thread == null) {

                    logger.error("updateDiscussionThreadPrivacy: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());

                // If the user does not have the correct permissions then return an error
                if (!DiscussionService.canUserEditThread(user, refset, thread)) {

                    logger.error("updateDiscussionThreadPrivacy: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
                service.setModifiedFlag(true);
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                thread.setPrivateThread(isPrivate);
                service.update(thread);

                final DiscussionPost post = thread.getPosts().get(0);
                post.setPrivatePost(isPrivate);
                service.update(post);

                service.commit();

                return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("updateDiscussionThreadPrivacy Error updating thread for discussionThread: {}", threadId);
            return handleException(e);
        }
    }

    /**
     * Update the discussion thread's visibility.
     *
     * @param threadId the discussion thread ID
     * @param visibility the discussion thread's visibility
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Set the visibility of a discussion thread.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion thread's visibility."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"), @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "visibility", value = "", required = true, dataTypeClass = String.class, paramType = "query")
    })
    @RecordMetric
    @PutMapping("/discussion/{threadId}/visibility")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadVisibility(@PathVariable(value = "threadId") final String threadId, @RequestParam final String visibility)
        throws Exception {

        try {

            logger.debug("updateDiscussionThreadVisibility threadId: " + threadId + "; visibility: " + visibility);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

                if (thread == null) {

                    logger.error("updateDiscussionThreadVisibility: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());

                // If the user does not have the correct permissions then return an error
                if (!DiscussionService.canUserEditThread(user, refset, thread)) {

                    logger.error("updateDiscussionThreadVisibility: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
                service.setModifiedFlag(true);

                thread.setVisibility(visibility);

                // Update
                service.update(thread);

                return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("updateDiscussionThreadVisibility Error updating thread for discussionThread: {}", threadId);
            return handleException(e);
        }
    }

    /**
     * Update the discussion post's privacy.
     *
     * @param threadId the discussion thread ID
     * @param postId the discussion post ID
     * @param isPrivate is the discussion post private
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Set the privacy of a discussion post.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion post's privacy."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"), @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "postId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "isPrivate", value = "", required = true, dataTypeClass = Boolean.class, paramType = "query")
    })
    @RecordMetric
    @PutMapping("/discussion/{threadId}/post/{postId}/privacy")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionPostPrivacy(@PathVariable(value = "threadId") final String threadId, @PathVariable(value = "postId") final String postId,
        @RequestParam final boolean isPrivate) throws Exception {

        try {

            logger.debug("updateDiscussionPostPrivacy threadId: " + threadId + "; postId: " + postId + "; isPrivate: " + isPrivate);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

                if (thread == null) {

                    logger.error("updateDiscussionPostPrivacy: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());
                final DiscussionPost post = service.get(postId, DiscussionPost.class);

                if (post == null) {

                    logger.error("updateDiscussionPostPrivacy: Unable to retrieve discussion post id: {}.", postId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion post for " + postId + ".");
                }

                // If the user does not have the correct permissions then return an error
                if (!DiscussionService.canUserEditPost(user, refset, post)) {

                    logger.error("updateDiscussionPostPrivacy: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
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

                return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("updateDiscussionPostPrivacy: Error updating thread for discussionThread: {}", threadId);
            return handleException(e);
        }
    }

    /**
     * Update a discussion post.
     *
     * @param threadId the discussion thread ID
     * @param postId the discussion post ID
     * @param updatedPost the post to update
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Updates a discussion post.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the discussion post."), 
        @ApiResponse(code = 400, message = "Bad request"), 
        @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "postId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "updatedPost", value = "", required = true, dataTypeClass = DiscussionPost.class, paramType = "query")
    })
    @RecordMetric
    @PutMapping("/discussion/{threadId}/post/{postId}")
    public @ResponseBody ResponseEntity<DiscussionPost> updateDiscussionPost(
        @PathVariable(value = "threadId") final String threadId, @PathVariable(value = "postId") final String postId, @RequestBody final DiscussionPost updatedPost) throws Exception 
    {

        try {

            logger.debug("updateDiscussionPost threadId: " + threadId + "; postId: " + postId + "; post: " + updatedPost);

            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread thread = service.get(threadId, DiscussionThread.class);

                if (!postId.equals(updatedPost.getId())) {
                    
                    final String message = "The postId parameter " + postId + " does not match the id property of the updatedPost parameter " + updatedPost.getId() + ".";
                    logger.error("updateDiscussionPost: " + message);
                    throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message);
                }

                if (thread == null) {

                    logger.error("updateDiscussionPost: Unable to retrieve discussion thread id: {}.", threadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
                }

                final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());
                final DiscussionPost existingPost = service.get(postId, DiscussionPost.class);

                if (existingPost == null) {

                    logger.error("updateDiscussionPost: Unable to retrieve discussion post id: {}.", postId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion post for " + postId + ".");
                }

                // If the user does not have the correct permissions then return an error
                if (!DiscussionService.canUserEditPost(user, refset, existingPost)) {

                    logger.error("updateDiscussionPost: User does not have permissions to perform this action: {}.", user.getUserName());
                    throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to perform this action.");
                }

                service.setModifiedBy(user.getUserName());
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

                return new ResponseEntity<>(existingPost, new HttpHeaders(), HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("Error updating discussion post: {}; for thread: {}", postId, threadId);
            return handleException(e);
        }
    }
    
    /**
     * Delete a discussion post.
     *
     * @param threadId the discussion thread ID
     * @param postId the discussion post ID
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Deletes a discussion post.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the discussion post."), 
        @ApiResponse(code = 400, message = "Bad request"), 
        @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
        @ApiImplicitParam(name = "postId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @DeleteMapping("/discussion/{threadId}/post/{postId}")
    public @ResponseBody ResponseEntity<DiscussionThread> deleteDiscussionPost(
        @PathVariable(value = "threadId") final String threadId, @PathVariable(value = "postId") final String postId) throws Exception 
    {

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("deleteDiscussionPost threadId: " + threadId + "; postId: " + postId);

            final User user = SecurityService.getUserFromSession();
            
            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            DiscussionService.deletePost(service, user, threadId, postId);

            return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

        } catch (final Exception e) {

            logger.error("Error deleting discussion post: {}; for thread: {}", postId, threadId);
            return handleException(e);
        }
    }

    /**
     * Delete a discussion thread.
     *
     * @param threadId the discussion thread ID
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Deletes a discussion thread.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully deleted the provided discussion thread."), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"), @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "threadId", value = "", required = true, dataTypeClass = String.class, paramType = "path"),
    })
    @RecordMetric
    @DeleteMapping("/discussion/{threadId}")
    public @ResponseBody ResponseEntity<String> deleteDiscussionThread(@PathVariable(value = "threadId") final String threadId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            logger.debug("deleteDiscussionThread threadId: " + threadId);

            final User user = SecurityService.getUserFromSession();

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            DiscussionService.deleteThread(service, user, threadId);

            return new ResponseEntity<>(new HttpHeaders(), HttpStatus.OK);

        } catch (final Exception e) {

            logger.error("deleteDiscussionThread Error deleting thread for discussionThread: {}", threadId);
            return handleException(e);
        }
    }
}
