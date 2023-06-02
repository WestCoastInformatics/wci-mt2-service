
package org.ihtsdo.refsetservice.util;

import java.net.InetAddress;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Configuration for Rest listeners.
 *
 * @see EndpointsEvent
 */
@Component
public class EndpointsListener {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(EndpointsListener.class);

    /** The config properties. */
    private Properties properties = PropertyUtility.getProperties();

    /** The servlet context. */
    @Autowired
    private ServletContext servletContext;

    /**
     * Print out all REST endpoints.
     * 
     * @param event The event
     * @throws Exception The exception
     */
    @EventListener
    public void handleContextRefresh(final ContextRefreshedEvent event) throws Exception {

        // Local address
        final String localAdd = InetAddress.getLocalHost().getHostAddress();
        final String localname = InetAddress.getLocalHost().getHostName();

        // Remote address
        final String remoteAdd = InetAddress.getLoopbackAddress().getHostAddress();
        final String remoteName = InetAddress.getLoopbackAddress().getHostName();

        LOG.debug("******* localAdd: " + localAdd);
        LOG.debug("******* localname: " + localname);
        LOG.debug("******* remoteAdd: " + remoteAdd);
        LOG.debug("******* remoteName: " + remoteName);
        LOG.debug("******* ContextPath(): " + servletContext.getContextPath());
        LOG.debug("******* server.port: " + properties.getProperty("server.port"));

        final ApplicationContext applicationContext = event.getApplicationContext();
        applicationContext.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().forEach((key, value) -> LOG.debug("{} {}", key, value));
    }
}
