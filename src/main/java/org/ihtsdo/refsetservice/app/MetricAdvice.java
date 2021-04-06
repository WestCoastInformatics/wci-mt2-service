
package org.ihtsdo.refsetservice.app;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.ihtsdo.refsetservice.model.Metric;
import org.ihtsdo.refsetservice.properties.ElasticServerProperties;
import org.ihtsdo.refsetservice.service.ElasticOperationsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Handle record metric annotations via AOP.
 */
@Component
@Aspect
@ConditionalOnProperty(name = "app.metrics.enabled")
public class MetricAdvice {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(MetricAdvice.class);

    /** The elastic server properties. */
    @Autowired
    private ElasticServerProperties elasticServerProperties;

    /** The operations service. */
    @Autowired
    private ElasticOperationsService operationsService;

    /**
     * Record metric.
     *
     * @param pjp the pjp
     * @param recordMetric the record metric
     * @return the object
     * @throws Throwable the throwable
     */

    @Around("execution(* org.ihtsdo.refsetservice.rest.*.*(..)) && @annotation(recordMetric)")
    private Object recordMetric(final ProceedingJoinPoint pjp, final RecordMetric recordMetric)
        throws Throwable {

        // get the request
        final HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                        .getRequest();
        return recordMetricHelper(pjp, request, request.getParameterMap());

    }

    /**
     * Record metric helper.
     *
     * @param pjp the pjp
     * @param request the request
     * @param params the params
     * @return the object
     * @throws Throwable the throwable
     */
    public Object recordMetricHelper(final ProceedingJoinPoint pjp,
        final HttpServletRequest request, final Map<String, String[]> params) throws Throwable {

        // get the start time
        final Date startDate = new Date();
        final Object retval = pjp.proceed();

        final Date endDate = new Date();
        final long duration = endDate.getTime() - startDate.getTime();
        final Metric metric = new Metric();

        metric.setDuration(duration);

        // get the ip address of the remote user
        final ServletRequestAttributes attr =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();

        final String userIpAddress = attr.getRequest().getRemoteAddr();
        metric.setRemoteIpAddress(userIpAddress);

        final String hostName = attr.getRequest().getRemoteHost();
        metric.setHostName(hostName);

        final String url = request.getRequestURL().toString();
        metric.setEndPoint(url);

        metric.setQueryParams(params);
        metric.setStartTime(startDate);
        metric.setEndTime(endDate);

        // get the parameters
        operationsService.loadMetric(metric,
                "metrics-" + String.valueOf(Calendar.getInstance().get(Calendar.YEAR)) + "-"
                        + String.valueOf(Calendar.getInstance().get(Calendar.MONTH)));

        logger.debug("metric = " + metric);
        return retval;
    }

}
