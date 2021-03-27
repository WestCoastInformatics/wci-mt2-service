
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

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(EndpointsListener.class);

    /** The config properties. */
    Properties properties = PropertyUtility.getProperties();

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
    public void handleContextRefresh(ContextRefreshedEvent event) throws Exception {

        // Local address
        String localAdd = InetAddress.getLocalHost().getHostAddress();
        String localname = InetAddress.getLocalHost().getHostName();

        // Remote address
        String remoteAdd = InetAddress.getLoopbackAddress().getHostAddress();
        String remoteName = InetAddress.getLoopbackAddress().getHostName();

        logger.debug("******* localAdd: " + localAdd);
        logger.debug("******* localname: " + localname);
        logger.debug("******* remoteAdd: " + remoteAdd);
        logger.debug("******* remoteName: " + remoteName);
        logger.debug("******* ContextPath(): " + servletContext.getContextPath());
        logger.debug("******* server.port: " + properties.getProperty("server.port"));

        ApplicationContext applicationContext = event.getApplicationContext();
        applicationContext.getBean(RequestMappingHandlerMapping.class).getHandlerMethods()
                .forEach((key, value) -> logger.debug("{} {}", key, value));
    }
}
