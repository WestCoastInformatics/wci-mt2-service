/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.test;

import java.lang.reflect.Method;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Automates JUnit testing XML/JSON Serialization.
 */
public class SerializationTester extends ProxyTester {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SerializationTester.class);

    /**
     * Constructs a new getter/setter tester to test objects of a particular class.
     * 
     * @param obj Object to test.
     */
    public SerializationTester(final Object obj) {

        super(obj);
    }

    /**
     * Tests XML and JSON serialization for equality,.
     *
     * @return true, if successful
     * @throws Exception the exception
     */
    public boolean testJsonSerialization() throws Exception {

        LOG.debug("Test json serialization - " + getClazz().getName());
        final Object obj = createObject(1);
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        LOG.debug(" " + obj);
        final String json = mapper.writeValueAsString(obj);
        LOG.info("json = " + json);
        final Object obj3 = mapper.readValue(json, obj.getClass());

        LOG.debug(" " + obj3);

        // If obj has an "id" field, compare the ids
        try {
            final Method method = obj.getClass().getMethod("getId", new Class<?>[] {});
            if (method != null && method.getReturnType() == Long.class) {

                final Long id1 = (Long) method.invoke(obj, new Object[] {});
                final Long id3 = (Long) method.invoke(obj3, new Object[] {});
                if (!id1.equals(id3)) {
                    LOG.error("  id fields do not match " + id1 + ", " + id3);
                    return false;
                }
            }
        } catch (final NoSuchMethodException e) {
            // this is OK
        }
        if (obj.equals(obj3)) {
            return true;
        } else {
            LOG.info("obj and obj3 are not equal");
            LOG.info("obj = " + obj);
            LOG.debug("json = " + json);
            LOG.info("obj3 = " + obj3);
            return false;
        }
    }

}
