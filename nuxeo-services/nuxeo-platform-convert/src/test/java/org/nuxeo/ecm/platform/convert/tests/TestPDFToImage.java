/*
 * (C) Copyright 2006-2015 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 */

package org.nuxeo.ecm.platform.convert.tests;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.api.blobholder.SimpleBlobHolder;
import org.nuxeo.ecm.core.api.impl.blob.FileBlob;
import org.nuxeo.ecm.core.convert.api.ConversionService;
import org.nuxeo.ecm.core.convert.api.ConverterCheckResult;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandAvailability;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandLineExecutorService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.NXRuntimeTestCase;

public class TestPDFToImage extends NXRuntimeTestCase {

    private static final Log log = LogFactory.getLog(TestPDFToImage.class);

    protected ConversionService cs;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        deployBundle("org.nuxeo.ecm.core.api");
        deployBundle("org.nuxeo.ecm.core.convert.api");
        deployBundle("org.nuxeo.ecm.core.convert");
        deployBundle("org.nuxeo.ecm.platform.commandline.executor");
        deployBundle("org.nuxeo.ecm.platform.convert");

        cs = Framework.getLocalService(ConversionService.class);
        assertNotNull(cs);
    }

    protected static BlobHolder getBlobFromPath(String path) {
        File file = FileUtils.getResourceFileFromContext(path);
        assertTrue(file.length() > 0);
        return new SimpleBlobHolder(new FileBlob(file));
    }

    @Test
    public void testConverter() throws Exception {

        String converterName = cs.getConverterName("application/pdf", "image/jpeg");
        assertEquals("pdf2image", converterName);

        CommandLineExecutorService cles = Framework.getLocalService(CommandLineExecutorService.class);
        assertNotNull(cles);

        ConverterCheckResult check = cs.isConverterAvailable(converterName);
        assertNotNull(check);
        if (!check.isAvailable()) {
            log.warn("Skipping PDF2Image tests since commandLine is not installed");
            log.warn(" converter check output : " + check.getInstallationMessage());
            log.warn(" converter check output : " + check.getErrorMessage());
            return;
        }

        CommandAvailability ca = cles.getCommandAvailability("pdftoimage");
        if (!ca.isAvailable()) {
            log.warn("pdftoimage command is not available, skipping test");
            return;
        }

        BlobHolder pdfBH = getBlobFromPath("test-docs/hello.pdf");
        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put("targetFilePath", "hello.png");

        BlobHolder result = cs.convert(converterName, pdfBH, parameters);
        assertNotNull(result);

        List<Blob> blobs = result.getBlobs();
        assertNotNull(blobs);
        assertEquals(1, blobs.size());

        Blob mainBlob = result.getBlob();
        assertEquals("hello.png", mainBlob.getFilename());
    }

}
