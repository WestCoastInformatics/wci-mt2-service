package org.ihtsdo.refsetservice.configuration;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Handle when spring sessions are destroyed.
 *
 */
@Component
public class SessionEndedListener implements ApplicationListener<SessionDestroyedEvent> {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SessionEndedListener.class);
    
    /**
     * Handle when spring sessions are destroyed.
     *
     * @param event the session destroyed event
     */
    @Override
    public void onApplicationEvent(SessionDestroyedEvent event) {
        
        try {
            
            for (SecurityContext securityContext : event.getSecurityContexts()) {
                Authentication authentication = securityContext.getAuthentication();
                logger.debug("************ SessionEndedListener Session expired!!");
                logger.debug("************ SessionEndedListener securityContext" + ModelUtility.toJson(securityContext));
                logger.debug("************ SessionEndedListener authentication.name" + authentication.getName());
                //UserPrincipal  user = (UserPrincipal) authentication.getPrincipal();
            }
        }
        catch(Exception e) {
            //na
        }
    }
    
    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<HttpSessionEventPublisher>(new HttpSessionEventPublisher());
    }

}
