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

import java.util.Date;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
    HttpServletRequest request;

    /**
     * Returns the discussion thread based on object (ex. refset, refset member) and the object 's id
     *
     * @param type the object
     * @param key the object id
     * @return the discussion thread
     * @throws Exception the exception
     */
    @ApiOperation(value = "Get the discussion for the specified object type and object key", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully retrieved the requested discussion"), @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "type", value = "Object type, e.g. 'REFSET, REFSET_MEMEBER'", required = true, dataType = "string", paramType = "path"),
        @ApiImplicitParam(name = "objectKey", value = "Unique id of the Refset or Refset Memeber", required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @RequestMapping(method = RequestMethod.GET, value = "/discussion/{type}/{objectKey}")
    public @ResponseBody ResponseEntity<DiscussionThread> getDiscussion(@PathVariable(value = "type", required = true) final DiscussionType type,
        @PathVariable(value = "objectKey", required = true) final String objectKey) throws Exception {

        try {

            logger.info("Get discussion for type: {}, objectKey:", type, objectKey);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (TerminologyService service = new TerminologyService()) {

                final String query = "type:" + QueryParserBase.escape(type.name()) + " AND objectKey:" + QueryParserBase.escape(objectKey) + "";

                final DiscussionThread discussionThread = service.findSingle(query, DiscussionThread.class, null);

                if (discussionThread == null) {
                    logger.info("getDiscussion: Unable to retrieve discussion Thread type: {}, objectKey: {}.", type.name(),  objectKey);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to retrieve discussion Thread type: " + type.name() + " objectKey: " + objectKey + ".");
                }

                logger.debug("getDiscussionThread: discussionThread: " + ModelUtility.toJson(discussionThread));

                return new ResponseEntity<>(discussionThread, HttpStatus.OK);
            }

        } catch (final RestException re) {
            throw re;

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Create a new discussion thread.
     *
     * @param discussionThread the discussion thread
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Create a new discussion for the specified object type and object key", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Created"), 
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "type", value = "Object type, e.g. 'REFSET, REFSET_MEMEBER'", required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @PostMapping("/discussion/{type}/")
    public @ResponseBody ResponseEntity<DiscussionThread> createDiscussionThread(@PathVariable(value = "type", required = true) final DiscussionType type,
        @RequestBody final DiscussionThread discussionThread) throws Exception {

        try {

            logger.info("Add discussion thread for type: {}, discussionThread", type, discussionThread.toString());
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread dt = (DiscussionThread) discussionThread;

                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                service.add(dt);
                service.commit();

                final HttpHeaders headers = new HttpHeaders();
                return new ResponseEntity<>(dt, headers, HttpStatus.CREATED);
            }

        } catch (final Exception e) {

            handleException(e);
            return null;
        }
    }

    /**
     * Update the discussion thread.
     *
     * @param objectKey the object key
     * @param discussionThread the discussion thread
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Update a new discussion for the specified object type and object key", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion"), 
        @ApiResponse(code = 400, message = "Bad request"), 
        @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "discussionThreadId", value = "", required = true, dataType = "string", paramType = "path")
    })
    @RecordMetric
    @PutMapping("/discussion/{discussionThreadId}")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThread(
        @PathVariable(value = "discussionThreadId") final String discussionThreadId,
        @RequestBody final DiscussionThread discussionThread) throws Exception {

        try {

            logger.info("Update discussionThread: {}", discussionThread);

            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                // Find the discussion thread
                final DiscussionThread original = service.get(discussionThread.getId(), DiscussionThread.class);

                if (original == null) {
                    logger.info("updateDiscussionThread: Unable to retrieve discussion thread id: {}.", discussionThread.getId());
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + discussionThread.getId() + ".");
                }

                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                // Apply changes
                original.patchFrom(discussionThread);

                // Update
                service.update(original);
                service.commit();

                return new ResponseEntity<>(original, HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("Error updating thread for discussionThread: {}", discussionThread.toString());
            handleException(e);
            return null;
        }
    }
    
    /**
     * Update the discussion thread's status.
     *
     * @param objectKey the object key
     * @param discussionThread the discussion thread
     * @return the response entity
     * @throws Exception the exception
     */
    @ApiOperation(value = "Resolve/Unresolve a discussion thread.", response = DiscussionThread.class)
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Successfully updated the provided discussion thread's resolve status."), 
        @ApiResponse(code = 400, message = "Bad request"), 
        @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "discussionThreadId", value = "", required = true, dataType = "string", paramType = "path")
    })
    @RecordMetric
    @PutMapping("/discussion/{discussionThreadId}/status")
    public @ResponseBody ResponseEntity<DiscussionThread> updateDiscussionThreadStatus(
        @PathVariable(value = "discussionThreadId") final String discussionThreadId
        ) throws Exception {

        try {

            logger.info("Update discussionThreadId: {}", discussionThreadId);

            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                // Find the discussion thread
                final DiscussionThread original = service.get(discussionThreadId, DiscussionThread.class);

                if (original == null) {
                    logger.info("updateDiscussionThread: Unable to retrieve discussion thread id: {}.", discussionThreadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + discussionThreadId + ".");
                }
                
                // flip status
                original.setResolve(!original.isResolve());
                original.setResolvedBy(user.getId());
                
                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                // Update
                service.update(original);
                service.commit();

                return new ResponseEntity<>(original, HttpStatus.OK);
            }

        } catch (final Exception e) {

            logger.error("Error updating thread for discussionThread: {}", discussionThreadId);
            handleException(e);
            return null;
        }
    }

    @ApiOperation(value = "Add a new discussion post to a discussion thread", response = DiscussionPost.class)
    @ApiResponses(value = {
        @ApiResponse(code = 201, message = "Added discussion post to discussion thread."), 
        @ApiResponse(code = 400, message = "Bad request"),
        @ApiResponse(code = 404, message = "Resource not found"),
        @ApiResponse(code = 500, message = "Server error")
    })
    @ApiImplicitParams({
        @ApiImplicitParam(name = "discussionThreadId", value = "Id of the discussion thread to add the post.", required = true, dataType = "string", paramType = "path"),
    })
    @RecordMetric
    @PostMapping("/discussion/{discussionThreadId}/post")
    public @ResponseBody ResponseEntity<Void> addPost(@PathVariable(value = "discussionThreadId") final String discussionThreadId, @RequestBody final DiscussionPost discussionPost) throws Exception {

        try {
            logger.info("Add post to discussionThreadId: {}", discussionThreadId);
            // TODO check permissions, fail if not authorized.
            // final AuthContext context = authorize(request);
            final User user = SecurityService.getUserFromSession();

            try (final TerminologyService service = new TerminologyService()) {

                final DiscussionThread discussionThread = service.get(discussionThreadId, DiscussionThread.class);

                if (discussionThread == null) {
                    logger.info("updateDiscussionThread: Unable to retrieve discussion thread id: {}.", discussionThreadId);
                    throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + discussionThreadId + ".");
                }
                
                discussionThread.getPosts().add(discussionPost);            
                
                // add required attributes
                discussionPost.setCreated(new Date());
                discussionPost.setModifiedBy(user.getId());
                discussionPost.setModified(new Date());
                
                service.setModifiedBy(user.getId());
                service.setTransactionPerOperation(false);
                service.beginTransaction();

                service.update(discussionThread);
                service.commit();
            }

            final HttpHeaders headers = new HttpHeaders();
            return new ResponseEntity<>(headers, HttpStatus.CREATED);

        } catch (final RestException re) {
            throw re;
        }
        
        catch (final Exception e) {
            logger.error("Error adding post:{} to discussionThreadId: {}", discussionPost, discussionThreadId);
            handleException(e);
            return null;
        }
    }

}