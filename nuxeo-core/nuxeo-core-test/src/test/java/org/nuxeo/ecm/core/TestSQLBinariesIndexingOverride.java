/*
 * Copyright (c) 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.util.Map;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.blobholder.BlobHolder;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.StorageConfiguration;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.reload.ReloadService;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeHarness;
import org.nuxeo.runtime.transaction.TransactionHelper;

@RunWith(FeaturesRunner.class)
@Features(CoreFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.core.convert", //
        "org.nuxeo.ecm.core.convert.plugins", //
        "org.nuxeo.runtime.reload", //
})
public class TestSQLBinariesIndexingOverride {

    @Inject
    protected RuntimeHarness runtimeHarness;

    @Inject
    protected EventService eventService;

    @Inject
    protected CoreFeature coreFeature;

    @Inject
    protected CoreSession session;

    @Inject
    protected ReloadService reloadService;

    protected boolean deployed;

    @Before
    public void setUp() throws Exception {
        assumeTrue(coreFeature.getStorageConfiguration().isVCS());
        // SQL Server fulltext indexes can't easily be updated by Nuxeo
        assumeTrue(!coreFeature.getStorageConfiguration().isVCSSQLServer());

        // cannot be done through @LocalDeploy, because the framework variables
        // about repository configuration aren't ready yet
        runtimeHarness.deployContrib("org.nuxeo.ecm.core.test.tests", "OSGI-INF/test-override-indexing-contrib.xml");
        deployed = true;
        newRepository(); // fully reread repo and its indexing config
    }

    @After
    public void tearDown() throws Exception {
        if (deployed) {
            runtimeHarness.undeployContrib("org.nuxeo.ecm.core.test.tests",
                    "OSGI-INF/test-override-indexing-contrib.xml");
            deployed = false;
        }
    }

    protected void newRepository() {
        waitForAsyncCompletion();
        coreFeature.releaseCoreSession();
        // reload repo with new config
        reloadService.reloadRepository();
        session = coreFeature.createCoreSession();
    }

    protected void waitForAsyncCompletion() {
        nextTransaction();
        eventService.waitForAsyncCompletion();
    }

    protected void waitForFulltextIndexing() {
        nextTransaction();
        coreFeature.getStorageConfiguration().waitForFulltextIndexing();
    }

    protected void nextTransaction() {
        if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
            TransactionHelper.commitOrRollbackTransaction();
            TransactionHelper.startTransaction();
        }
    }

    @Test
    public void testTwoBinaryIndexes() throws Exception {
        DocumentModelList res;
        DocumentModel doc = session.createDocumentModel("/", "source", "File");
        BlobHolder holder = doc.getAdapter(BlobHolder.class);
        holder.setBlob(Blobs.createBlob("test"));
        doc = session.createDocument(doc);
        session.save();

        waitForFulltextIndexing();

        // main index
        res = session.query("SELECT * FROM Document WHERE ecm:fulltext = 'test'");
        assertEquals(1, res.size());

        // other index
        res = session.query("SELECT * FROM Document WHERE ecm:fulltext_binaries = 'test'");
        assertEquals(1, res.size());
    }

    @Test
    public void testGetBinaryFulltext() throws Exception {
        DocumentModelList res;
        DocumentModel doc = session.createDocumentModel("/", "source", "File");
        BlobHolder holder = doc.getAdapter(BlobHolder.class);
        holder.setBlob(Blobs.createBlob("test"));
        doc = session.createDocument(doc);
        session.save();

        waitForFulltextIndexing();

        // main index
        res = session.query("SELECT * FROM Document WHERE ecm:fulltext = 'test'");
        assertEquals(1, res.size());
        Map<String, String> map = session.getBinaryFulltext(res.get(0).getRef());
        assertTrue(map.containsValue("test"));
        StorageConfiguration database = coreFeature.getStorageConfiguration();
        if (!(database.isVCSMySQL() || database.isVCSSQLServer())) {
            // we have 2 binaries field
            assertTrue(map.containsKey("binarytext"));
            assertTrue(map.containsKey("binarytext_binaries"));
            assertEquals("test", map.get("binarytext"));
            assertEquals("test", map.get("binarytext_binaries"));
        }
    }

}
