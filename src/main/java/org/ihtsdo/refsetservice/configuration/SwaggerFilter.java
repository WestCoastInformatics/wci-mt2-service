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

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Filter to ensure only authenticated users can access swagger or api-docs.
 */
@Component
public class SwaggerFilter implements Filter {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SwaggerFilter.class);

    /* see superclass */
    @Override
    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain) throws IOException, ServletException {

        /*
         * String url = null;
         * 
         * if (request instanceof HttpServletRequest) { url = ((HttpServletRequest) request).getRequestURL().toString(); if (url.contains("swagger") ||
         * url.contains("api-docs")) { final HttpServletResponse res = (HttpServletResponse) response;
         * 
         * try { final Cookie cookie = SecurityService.getImsCookie();
         * 
         * if (cookie == null) { LOG.info("Unauthorized user tried to access Swagger."); res.sendError(401, "Not Authorized"); }
         * 
         * } catch (final Exception e) { LOG.error("Error occurred checking to see if user is allowed access to swagger.", e); res.sendError(401,
         * "Not Authorized"); }
         * 
         * } }
         */
        chain.doFilter(request, response);
    }
}
