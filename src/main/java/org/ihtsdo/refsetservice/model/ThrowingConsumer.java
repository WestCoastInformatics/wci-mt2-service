package org.ihtsdo.refsetservice.model;


/**
 * Interface for wrapping Consumer Lambdas that throw exceptions
 */
@FunctionalInterface
public interface ThrowingConsumer<T, E extends Exception> {
    
    /**
     * The exception type to except.
     * 
     * @param t the exception type 
     */
    void accept(T t) throws E;
}
