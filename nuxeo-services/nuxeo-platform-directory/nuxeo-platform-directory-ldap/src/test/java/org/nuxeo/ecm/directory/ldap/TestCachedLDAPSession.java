/*
 * (C) Copyright 2006-2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.directory.ldap;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.internal.core.Assert;
import org.junit.Before;
import org.junit.Test;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.redis.RedisFeature;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.ecm.directory.DirectoryCache;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * Test class for LDAP directory that use cache
 */
@Deploy("org.nuxeo.ecm.core.cache")
@LocalDeploy("org.nuxeo.ecm.directory.ldap.tests:ldap-directory-cache-config.xml")
public class TestCachedLDAPSession extends TestLDAPSession {

    protected final static String CACHE_CONFIG = "ldap-directory-cache-config.xml";

    protected final static String REDIS_CACHE_CONFIG = "ldap-directory-redis-cache-config.xml";

    protected final static String ENTRY_CACHE_NAME = "ldap-entry-cache";

    protected final static String ENTRY_CACHE_WITHOUT_REFERENCES_NAME = "ldap-entry-cache-without-references";

    @Before
    public void setUpCache() throws Exception {
        if (RedisFeature.setup(runtimeHarness)) {
            runtimeHarness.deployContrib("org.nuxeo.ecm.directory.ldap.tests", "ldap-directory-redis-cache-config.xml");
            Framework.getService(WorkManager.class).init();
        }

        List<String> directories = Arrays.asList("userDirectory", "groupDirectory");
        for (String directoryName : directories) {
            LDAPDirectory dir = getLDAPDirectory(directoryName);
            DirectoryCache cache = dir.getCache();
            cache.setEntryCacheName(ENTRY_CACHE_NAME);
            cache.setEntryCacheWithoutReferencesName(ENTRY_CACHE_WITHOUT_REFERENCES_NAME);
        }
    }

    @Test
    public void testGetFromCache() {
        Session ldapSession = getLDAPDirectory("userDirectory").getSession();

        // First call will update cache
        DocumentModel entry = ldapSession.getEntry("user1");
        Assert.isNotNull(entry);

        // Second call will use the cache
        entry = ldapSession.getEntry("user1");
        Assert.isNotNull(entry);
    }

}
