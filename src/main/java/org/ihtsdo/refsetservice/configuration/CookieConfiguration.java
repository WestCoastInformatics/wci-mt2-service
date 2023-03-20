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

    @Bean
    public TomcatContextCustomizer cookieProcessorCustomizer() {
      return (context) -> context.setCookieProcessor(new LegacyCookieProcessor());
    }
}
