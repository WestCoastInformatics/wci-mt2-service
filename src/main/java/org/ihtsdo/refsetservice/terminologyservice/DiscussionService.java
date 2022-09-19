/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * Service class to handle getting and modifying discussion information.
 */
public class DiscussionService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(DiscussionService.class);

    /**
     * Returns a list of discussion threads based on the refset and possibly member ID.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param type Object type, e.g. 'REFSET, REFSET_MEMEBER'
     * @param refset the refset
     * @param conceptId The concept ID of the refset member if this is a member type
     * @return a list of matching discussion threads
     * @throws Exception the exception
     */
    public static ResultList<DiscussionThread> getDiscussions(final TerminologyService service, final User user, final DiscussionType type, final Refset refset, final String conceptId)
        throws Exception {

        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return new ResultList<DiscussionThread>();
        }

        boolean canViewPrivate = true;
        String query = "type:" + QueryParserBase.escape(type.name()) + " AND refsetInternalId:" + QueryParserBase.escape(refset.getId());
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("created");
        pfs.setAscending(false);

        if (type.equals(DiscussionType.REFSET_MEMBER) && conceptId != null) {
            query += " AND conceptId: " + QueryParserBase.escape(conceptId);
        }

        if (!canUserViewPrivateThread(user, refset)) {

            canViewPrivate = false;
            query += " AND privateThread: false";
        }

        final ResultList<DiscussionThread> results = service.find(query, pfs, DiscussionThread.class, null);

        // private posts need to be removed if the user does not have permission to view them
        for (int i = results.getItems().size() - 1; i >= 0; i--) {

            final DiscussionThread thread = results.getItems().get(i);
            final List<DiscussionPost> postList = ModelUtility.jsonCopy(thread.getPosts(), new TypeReference<List<DiscussionPost>>() {
            });
            thread.getPosts().clear();

            for (final DiscussionPost post : postList) {

                if (post.isPrivatePost() && !canViewPrivate) {
                    continue;
                }

                thread.getPosts().add(post);
            }

            thread.setLastPost(thread.getPosts().get(thread.getPosts().size() - 1).getCreated());

            if (thread.getPosts().size() > 1) {
                thread.setNumberReplies(thread.getPosts().size() - 1);
            }

        }

        results.setTotal(results.getItems().size());
        results.setTotalKnown(true);

        return results;
    }

    /**
     * Returns a single discussion thread.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param id the discussion thread ID
     * @return the discussion thread
     * @throws Exception the exception
     */
    public static DiscussionThread getDiscussion(final TerminologyService service, final User user, final String id) throws Exception {

        final DiscussionThread discussionThread = service.get(id, DiscussionThread.class);

        return discussionThread;
    }

    /**
     * Returns a single discussion post
     *
     * @param service the Terminology Service
     * @param user the user
     * @param id the discussion post ID
     * @return the discussion post
     * @throws Exception the exception
     */
    public static DiscussionPost getDiscussionPost(final TerminologyService service, final User user, final String id) throws Exception {

        final DiscussionPost discussionPost = service.get(id, DiscussionPost.class);

        return discussionPost;
    }

    /**
     * Adds discussion count to a refset
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refsets the refsets
     * @return the refset with discussion count included
     * @throws Exception the exception
     */
    public static List<Refset> attachRefsetDiscussionCounts(final TerminologyService service, final User user, final List<Refset> refsets) throws Exception {

        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return refsets;
        }

        for (final Refset refset : refsets) {
            attachRefsetDiscussionCount(service, user, refset);
        }

        return refsets;
    }

    /**
     * Adds discussion counts to a refset.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @return the refset with discussion counts included
     * @throws Exception the exception
     */
    public static Refset attachRefsetDiscussionCount(final TerminologyService service, final User user, final Refset refset) throws Exception {

        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return refset;
        }

        final String query = "type:" + DiscussionType.REFSET + " AND refsetInternalId:" + QueryParserBase.escape(refset.getId());

        final ResultList<DiscussionThread> results = service.find(query, null, DiscussionThread.class, null);

        if (results == null || results.getItems() == null || results.getItems().size() == 0) {
            return refset;
        } else {
            int openDiscussionCount = 0;
            int resolvedDiscussionCount = 0;
            boolean userIsThreadMember = false;

            for (final DiscussionThread thread : results.getItems()) {
                userIsThreadMember = thread.getPosts().stream().anyMatch(post -> post.getUser().equals(user));

                if (!thread.isPrivateThread() || (userIsThreadMember && thread.isPrivateThread())) {
                    if ("OPEN".equals(thread.getStatus())) {
                        openDiscussionCount++;
                    }
                    if ("RESOLVED".equals(thread.getStatus())) {
                        resolvedDiscussionCount++;
                    }
                }
            }

            refset.setOpenDiscussionCount(openDiscussionCount);
            refset.setResolvedDiscussionCount(resolvedDiscussionCount);
        }

        return refset;
    }

    /**
     * Adds discussion counts to a list of refset member concepts.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @param concepts a list of concepts to attach the discussion counts to
     * @return the list concepts with discussion counts included
     * @throws Exception the exception
     */
    public static List<Concept> attachMemberDiscussionCounts(final TerminologyService service, final User user, final Refset refset, final List<Concept> concepts) throws Exception {

        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return concepts;
        }

        String query = "type:" + DiscussionType.REFSET_MEMBER + " AND refsetInternalId:" + QueryParserBase.escape(refset.getId());
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("conceptId");

        final ResultList<DiscussionThread> results = service.find(query, null, DiscussionThread.class, null);

        if (results == null || results.getItems() == null || results.getItems().size() == 0) {
            return concepts;
        } else {
            for (final Concept concept : concepts) {
                int openDiscussionCount = 0;
                int resolvedDiscussionCount = 0;
                boolean userIsThreadMember = false;

                for (final DiscussionThread thread : results.getItems().stream().filter(c -> c.getConceptId().contentEquals(concept.getCode())).collect(Collectors.toList())) {

                    userIsThreadMember = thread.getPosts().stream().anyMatch(post -> post.getUser().equals(user));

                    if (!thread.isPrivateThread() || (userIsThreadMember && thread.isPrivateThread())) {
                        if ("OPEN".equals(thread.getStatus())) {
                            openDiscussionCount++;
                        }
                        if ("RESOLVED".equals(thread.getStatus())) {
                            resolvedDiscussionCount++;
                        }
                    }
                }

                concept.setOpenDiscussionCount(openDiscussionCount);
                concept.setResolvedDiscussionCount(resolvedDiscussionCount);
            }
        }

        return concepts;
    }

    /**
     * Check if the user can edit a discussion thread.
     *
     * @param user the user
     * @param refset the refset
     * @param thread the discussion thread
     * @return if the user can edit a thread
     * @throws Exception the exception
     */
    public static boolean canUserEditThread(final User user, final Refset refset, final DiscussionThread thread) throws Exception {

        final String threadUserName = thread.getPosts().get(0).getUser().getUserName();

        // if the user does not have the correct roles on the refset or they did not create the thread then they can't edit it
        if (Collections.disjoint(refset.getRoles(), Arrays.asList(User.ROLE_ADMIN)) && !threadUserName.equals(user.getUserName())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Check if the user can view a private discussion thread.
     *
     * @param user the user
     * @param refset the refset
     * @return if the user can view a private thread
     * @throws Exception the exception
     */
    public static boolean canUserViewPrivateThread(final User user, final Refset refset) throws Exception {

        // if the user does not have the correct roles on the refset or they did not create the thread then they can't edit it
        if (refset.getRoles().contains(User.ROLE_VIEWER) || refset.getRoles().contains(User.ROLE_ADMIN)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if the user can edit a discussion thread post.
     *
     * @param user the user
     * @param refset the refset
     * @param post the discussion thread post
     * @return if the user can edit a post
     * @throws Exception the exception
     */
    public static boolean canUserEditPost(final User user, final Refset refset, final DiscussionPost post) throws Exception {

        final String postUserName = post.getUser().getUserName();

        // if the user does not have the correct roles on the refset or they did not create the post then they can't edit it
        if (Collections.disjoint(refset.getRoles(), Arrays.asList(User.ROLE_ADMIN)) && !postUserName.equals(user.getUserName())) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Delete a discussion post by ID
     *
     * @param service the Terminology Service
     * @param user the user
     * @param threadId The ID of the thread
     * @param postId The ID of the post
     * @throws Exception the exception
     */
    public static void deletePost(final TerminologyService service, final User user, final String threadId, final String postId) throws Exception {

        final DiscussionThread thread = getDiscussion(service, user, threadId);

        if (thread == null) {

            logger.error("deletePost: Unable to retrieve discussion thread id: {}.", threadId);
            throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
        }

        final DiscussionPost post = getDiscussionPost(service, user, postId);
        final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());

        if (post == null) {

            logger.error("deletePost: Unable to retrieve discussion post id: {}.", postId);
            throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion post for " + postId + ".");
        }

        if (!DiscussionService.canUserEditPost(user, refset, post)) {

            logger.error("deletePost: User does not have permissions to perform this action: {}.", user.getUserName());
            throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to delete this discussion post.");
        }

        service.setTransactionPerOperation(false);
        service.beginTransaction();
        service.setModifiedBy(user.getUserName());
        service.setModifiedFlag(true);

        service.remove(post);

        for (int i = 0; i < thread.getPosts().size(); i++) {

            final DiscussionPost threadPost = thread.getPosts().get(i);

            if (threadPost.getId().equals(postId)) {

                thread.getPosts().remove(i);
                break;
            }
        }

        service.update(refset);
        service.update(thread);
        service.commit();
    }

    /**
     * Delete a discussion thread by ID
     *
     * @param service the Terminology Service
     * @param user the user
     * @param threadId The ID of the thread
     * @throws Exception the exception
     */
    public static void deleteThread(final TerminologyService service, final User user, final String threadId) throws Exception {

        final DiscussionThread thread = getDiscussion(service, user, threadId);

        if (thread == null) {

            logger.error("deleteThread: Unable to retrieve discussion thread id: {}.", threadId);
            throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", "Unable to find discussion thread for " + threadId + ".");
        }

        final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());

        if (!DiscussionService.canUserEditThread(user, refset, thread)) {

            logger.error("deleteThread: User does not have permissions to perform this action: {}.", user.getUserName());
            throw new RestException(false, HttpStatus.FORBIDDEN, "Forbidden", "User does not have permissions to delete this discussion thread.");
        }

        service.setTransactionPerOperation(false);
        service.beginTransaction();
        service.setModifiedBy(user.getUserName());
        service.setModifiedFlag(true);

        for (int i = thread.getPosts().size() - 1; i >= 0; i--) {

            final DiscussionPost post = thread.getPosts().get(i);
            service.remove(post);
        }

        service.update(refset);
        service.remove(thread);
        service.commit();
    }
}
