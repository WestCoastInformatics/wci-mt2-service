
package org.ihtsdo.refsetservice.handler;

/**
 * Represents an example of a handler. To use this in another class, do this
 * 
 * <pre>
 * 
 * &#64;Autowired
 * List<ExampleHandler> handlers;
 * </pre>
 * 
 * Also, consider having a mechanism in application.properties to allow handlers to be disabled (e.g. check against a property to see if you're disabled).
 */
public interface ExampleHandler {

    /**
     * Do something.
     *
     * @return the string
     * @throws Exception the exception
     */
    public String doSomething() throws Exception;
}
