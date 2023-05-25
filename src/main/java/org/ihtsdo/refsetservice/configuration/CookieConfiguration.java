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

import org.apache.tomcat.util.http.LegacyCookieProcessor;
import org.springframework.boot.web.embedded.tomcat.TomcatContextCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Customize the cookie processor.
 */
@Configuration
public class CookieConfiguration {

    /**
     * Cookie processor customizer.
     *
     * @return the tomcat context customizer
     */
    @Bean
    public TomcatContextCustomizer cookieProcessorCustomizer() {

        return (context) -> context.setCookieProcessor(new LegacyCookieProcessor());
    }
}
