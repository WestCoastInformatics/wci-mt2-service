/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.configuration;

import java.util.Set;

import javax.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.AbstractRequestLoggingFilter;

/**
 * The Class RequestLoggingFilter.
 */
@Component
public class RequestLoggingFilter extends AbstractRequestLoggingFilter {

    /** The excluded urls. */
    private Set<String> excludedUrls = Set.of("/version");

    /**
     * Instantiates a new request logging filter.
     */
    public RequestLoggingFilter() {

        setIncludeClientInfo(false);
        setIncludeQueryString(true);
        setIncludePayload(true);
        setMaxPayloadLength(50000);
        setIncludeHeaders(false);
    }

    /**
     * Before request.
     *
     * @param request the request
     * @param message the message
     */
    @Override
    protected void beforeRequest(final HttpServletRequest request, final String message) {

        // Do nothing
    }

    /**
     * After request.
     *
     * @param request the request
     * @param message the message
     */
    @Override
    protected void afterRequest(final HttpServletRequest request, final String message) {

        logger.info(message);
    }

    /**
     * Should log.
     *
     * @param request the request
     * @return true, if successful
     */
    @Override
    protected boolean shouldLog(final HttpServletRequest request) {

        final String requestURIWithoutContextPath = request.getRequestURI().substring(request.getContextPath().length());

        // Exclude swagger from logging
        if (requestURIWithoutContextPath.contains("swagger")) {
            return false;
        }

        return excludedUrls.stream().noneMatch(url -> requestURIWithoutContextPath.endsWith(url)) && super.shouldLog(request);

    }

}
