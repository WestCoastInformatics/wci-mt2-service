package org.ihtsdo.refsetservice.configuration;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ihtsdo.refsetservice.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 
 */
@Component
public class SwaggerFilter implements Filter {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SwaggerFilter.class);

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        String url = null;

        if (request instanceof HttpServletRequest) {
            url = ((HttpServletRequest) request).getRequestURL().toString();
            if (url.contains("swagger") || url.contains("api-docs")) {
                final HttpServletResponse res = (HttpServletResponse) response;

                try {
                    final Cookie cookie = SecurityService.getImsCookie();

                    if (cookie == null) {
                        logger.info("Unauthorized user tried to access Swagger.");
                        res.sendError(401, "Not Authorized");
                    }

                } catch (Exception e) {
                    logger.error("Error occurred checking to see if user is allowed access to swagger.", e);
                    res.sendError(401, "Not Authorized");
                }

            }
        }

        chain.doFilter(request, response);
    }
}
