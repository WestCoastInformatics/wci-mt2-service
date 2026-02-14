/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.config.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.refsetservice.Application;
import org.ihtsdo.refsetservice.model.ApplicationMetadata;
import org.ihtsdo.refsetservice.model.ModuleInfo;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The Class ApplicationMetadataUnitTest.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
public class ApplicationMetadataUnitTest {

    /** The logger. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationMetadataUnitTest.class);

    /** The model object to test. */
    private ApplicationMetadata object;

    /** The l 1. */
    private List<String> l1;

    /** The l 2. */
    private List<String> l2;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        object = new ApplicationMetadata();
        l1 = new ArrayList<>();
        l1.add("1");
        l2 = new ArrayList<>();
        l2.add("2");
        l2.add("3");
    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.proxy(List.class, 1, l1);
        tester.proxy(List.class, 2, l2);
        tester.test();
    }

    /**
     * Test copy constructor.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelCopy() throws Exception {

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        tester.proxy(List.class, 1, l1);
        assertTrue(tester.testCopyConstructor(ApplicationMetadata.class));
    }

    /**
     * Test json data.
     *
     * @throws Exception the exception
     */
    @Test
    public void testJsonData() throws Exception {

        final ApplicationMetadata am = ModelUtility
            .fromJson(IOUtils.toString(getClass().getClassLoader().getResourceAsStream("ApplicationMetadata.json"), "UTF-8"), ApplicationMetadata.class);

        assertTrue(am.getModule().size() > 0);

    }

    /**
     * Test load file.
     *
     * @throws Exception the exception
     */
    @Test
    public void testLoadFile() throws Exception {

        try (final InputStream is = Application.class.getClassLoader().getResourceAsStream("ApplicationMetadata.json");) {

            final ApplicationMetadata allMetadata = ModelUtility.fromJson(IOUtils.toString(is, "UTF-8"), ApplicationMetadata.class);

            final ModuleInfo module = allMetadata.getModule().get(0);
            assertEquals("51000202101", module.getId());
            assertEquals("Norway", module.getName());
            assertEquals("no", module.getCountryCode());

        }
    }
}
