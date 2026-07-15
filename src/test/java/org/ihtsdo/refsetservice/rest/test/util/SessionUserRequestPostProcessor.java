package org.ihtsdo.refsetservice.rest.test.util;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MockMvc helper that puts a user into the HTTP session.
 */
public final class SessionUserRequestPostProcessor implements RequestPostProcessor {

    private final User user;

    /**
     * Instantiates a post processor for the given user.
     *
     * @param user the session user
     */
    public SessionUserRequestPostProcessor(final User user) {

        this.user = user;
    }

    @Override
    public MockHttpServletRequest postProcessRequest(final MockHttpServletRequest request) {

        final MockHttpSession session = new MockHttpSession();
        session.setAttribute(SecurityService.SESSION_USER_OBJECT_KEY, user);
        request.setAttribute(SecurityService.TEST_SESSION_USER_ATTRIBUTE, user);
        request.setSession(session);
        return request;
    }
}
