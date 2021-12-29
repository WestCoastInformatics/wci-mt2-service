
package org.ihtsdo.refsetservice.util;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * Utility for interacting with domain objects. TODO: clean this up and
 * reconcile with NormUtility (push all logic here).
 */
public final class ThreadUtility {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(ThreadUtility.class);

    

    static {
        //NA
    }

    /**
     * Instantiates an empty {@link ConfigUtility}.
     */
    private ThreadUtility() {
        // n/a
    }

    /**
     * Both or neither null.
     *
     * @param a the a
     * @param b the b
     * @return true, if successful
     */
//    static <T> Consumer<T> throwingConsumerWrapper(ThrowingConsumer<T, Exception> throwingConsumer) {
//       
//          return i -> {
//              try {
//                  throwingConsumer.accept(i);
//              } catch (Exception ex) {
//                  throw new RuntimeException(ex);
//              }
//          };
//      }

}
