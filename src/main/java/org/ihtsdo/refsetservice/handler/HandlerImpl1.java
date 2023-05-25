
package org.ihtsdo.refsetservice.handler;

import java.util.Properties;

import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.springframework.stereotype.Component;

/**
 * Sample implementation of the handler.
 */
@Component
public class HandlerImpl1 implements ExampleHandler {

    /** The config properties. */
    private Properties properties = PropertyUtility.getProperties();

    /**
     * Do something.
     *
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String doSomething() throws Exception {

        return properties.getProperty("spring.application.name");
    }
}
