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

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DiscussionPost;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.DiscussionType;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to handle getting and modifying discussion information.
 */
public class DiscussionService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(DiscussionService.class);

    /**
     * Returns a list of discussion threads based on the refset and possibly member ID
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @param type Object type, e.g. 'REFSET, REFSET_MEMEBER'
     * @param conceptId The concept ID of the refset member if this is a member type
     * @return a list of matching discussion threads
     * @throws Exception the exception
     */
    public static ResultList<DiscussionThread> getDiscussions(final TerminologyService service, final User user, final DiscussionType type,
        final Refset refset, final String conceptId) throws Exception 
    {

        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return new ResultList<DiscussionThread>();
        }
        
        String query = "type:" + QueryParserBase.escape(type.name()) + " AND refsetInternalId:" + QueryParserBase.escape(refset.getId());
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("created");
        pfs.setAscending(false);
        
        if (type.equals(DiscussionType.REFSET_MEMBER) && conceptId != null) {
            query += " AND conceptId: " + QueryParserBase.escape(conceptId);
        }
        
        if (!canUserViewPrivateThread(user, refset)) {
            query += " AND privateThread: false";
        }

        final ResultList<DiscussionThread> results = service.find(query, pfs, DiscussionThread.class, null);
        
        for (int i = results.getItems().size() - 1; i >= 0; i--) {
            
            final DiscussionThread thread = results.getItems().get(i);
            thread.setLastPost(thread.getPosts().get(thread.getPosts().size() - 1).getCreated());
            thread.setNumberReplies(thread.getPosts().size() - 1);
        }
        
        results.setTotal(results.getItems().size());
        results.setTotalKnown(true);
        
        return results;
    }
    
    /**
     * Returns a single discussion thread
     *
     * @param service the Terminology Service
     * @param user the user
     * @param id the discussion ID
     * @return the discussion thread
     * @throws Exception the exception
     */
    public static DiscussionThread getDiscussion(final TerminologyService service, final User user, final String id) throws Exception {
        
        final DiscussionThread discussionThread = service.get(id, DiscussionThread.class);
        
        return discussionThread;
    }
    
    /**
     * Adds discussion count to a refset
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @return the refset with discussion count included
     * @throws Exception the exception
     */
    public static List<Refset> attachRefsetDiscussionCounts(final TerminologyService service, final User user, final List<Refset> refsets) throws Exception {
        
        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return refsets;
        }

        for (Refset refset : refsets) {
            attachRefsetDiscussionCount(service, user, refset);
        }

        return refsets;
    }
    
    /**
     * Adds discussion count to a refset
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @return the refset with discussion count included
     * @throws Exception the exception
     */
    public static Refset attachRefsetDiscussionCount(final TerminologyService service, final User user, final Refset refset) throws Exception {
        
        if (user.getUserName().equals(SecurityService.GUEST_USERNAME)) {
            return refset;
        }
        
        String query = "type:" + DiscussionType.REFSET + " AND refsetInternalId:" + QueryParserBase.escape(refset.getId());
        
        if (!canUserViewPrivateThread(user, refset)) {
            query += " AND privateThread: false";
        }
        
        final ResultList<DiscussionThread> results = service.find(query, null, DiscussionThread.class, null);
        int count = results.getItems().size();
        
        refset.setDiscussionCount(count);
        
        return refset;
    }
    
    /**
     * Adds discussion counts to a list of refset member concepts
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
        
        if (!canUserViewPrivateThread(user, refset)) {
            query += " AND privateThread: false";
        }
        
        final ResultList<DiscussionThread> results = service.find(query, pfs, DiscussionThread.class, null);
        
        if (results.getItems().size() == 0) {
            return concepts;
        }
        
        for (final Concept concept : concepts) {
            
            int count = 0;
            
            for (final DiscussionThread thread : results.getItems()) {
                
                if (!thread.getConceptId().equals(concept.getCode()) && count == 0) {
                    continue;
                    
                } else if (thread.getConceptId().equals(concept.getCode())) {
                    count++;
                    
                } else {
                    break;
                }
            }
            
            concept.setDiscussionCount(count);
        }
        
        return concepts;
    }
    
    /**
     * Check if the user can edit a discussion thread
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
        if (Collections.disjoint(refset.getRoles(), Arrays.asList(User.ROLE_ADMIN, User.ROLE_AUTHOR, User.ROLE_REVIEWER)) && !threadUserName.equals(user.getUserName())) {
            return false;
        } else {
            return true;
        }
    }
    
    /**
     * Check if the user can view a private discussion thread
     *
     * @param user the user
     * @param refset the refset
     * @return if the user can view a private thread
     * @throws Exception the exception
     */
    public static boolean canUserViewPrivateThread(final User user, final Refset refset) throws Exception {
        
        // if the user does not have the correct roles on the refset or they did not create the thread then they can't edit it
        if (refset.getRoles().contains(User.ROLE_VIEWER)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if the user can edit a discussion thread post
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
        if (Collections.disjoint(refset.getRoles(), Arrays.asList(User.ROLE_ADMIN, User.ROLE_AUTHOR, User.ROLE_REVIEWER)) && !postUserName.equals(user.getUserName())) {
            return false;
        } else {
            return true;
        }
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
        final Refset refset = RefsetService.getRefset(service, user, thread.getRefsetInternalId());
        final String threadUserName = thread.getPosts().get(0).getUser().getUserName();
        
        if (user.getUserName().equals(SecurityService.GUEST_USERNAME) || !(refset.getRoles().contains(User.ROLE_ADMIN) || threadUserName.equals(user.getUserName()))) {
            
            logger.error("deleteThread: User does not have permissions to perform this action: {}.", user.getUserName());
            throw new Exception("User does not have permission to delete this discussion thread.");
        }
        
        service.setTransactionPerOperation(false);
        service.beginTransaction();
        
        for (int i = thread.getPosts().size() - 1; i >= 0; i--) {
            
            final DiscussionPost post = thread.getPosts().get(i);
            service.remove(post);
        }
        
        service.remove(thread);
        service.commit();
    }
}
