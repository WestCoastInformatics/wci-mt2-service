/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.configuration;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * The Class AppContext.
 */
@Component
public class AppContext implements ApplicationContextAware {

    /** The context. */
    private static ApplicationContext context;

    /**
     * Sets the application context.
     *
     * @param applicationContext the new application context
     * @throws BeansException the beans exception
     */
    @Override
    public void setApplicationContext(final ApplicationContext applicationContext) throws BeansException {

        context = applicationContext;
    }

    /**
     * Gets the bean.
     *
     * @param <T> the generic type
     * @param beanClass the bean class
     * @return the bean
     */
    public static <T extends Object> T getBean(final Class<T> beanClass) {

        return context.getBean(beanClass);
    }
}
