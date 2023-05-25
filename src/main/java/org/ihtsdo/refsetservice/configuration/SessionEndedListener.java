/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.configuration;

import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Handle when spring sessions are destroyed.
 *
 * @see SessionEndedEvent
 */
@Component
public class SessionEndedListener implements ApplicationListener<SessionDestroyedEvent> {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SessionEndedListener.class);

    /**
     * Handle when spring sessions are destroyed.
     *
     * @param event the session destroyed event
     */
    @Override
    public void onApplicationEvent(final SessionDestroyedEvent event) {

        try {

            for (final SecurityContext securityContext : event.getSecurityContexts()) {
                final Authentication authentication = securityContext.getAuthentication();
                LOG.debug("************ SessionEndedListener Session expired!!");
                LOG.debug("************ SessionEndedListener securityContext" + ModelUtility.toJson(securityContext));
                LOG.debug("************ SessionEndedListener authentication.name" + authentication.getName());
                // UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
            }
        } catch (final Exception e) {
            // na
        }
    }

    /**
     * Http session event publisher.
     *
     * @return the servlet listener registration bean
     */
    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {

        return new ServletListenerRegistrationBean<HttpSessionEventPublisher>(new HttpSessionEventPublisher());
    }

}
