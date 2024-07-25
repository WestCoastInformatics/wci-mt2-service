/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.net.InetAddress;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
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
    final RequestMappingHandlerMapping requestMappingHandlerMapping = applicationContext
        .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
    final Map<RequestMappingInfo, HandlerMethod> map =
        requestMappingHandlerMapping.getHandlerMethods();
    map.forEach((key, value) -> LOG.info("{} {}", key, value));

  }
}
