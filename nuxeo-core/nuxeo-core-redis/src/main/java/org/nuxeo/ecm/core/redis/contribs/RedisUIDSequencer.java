/*
 * (C) Copyright 2014-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thierry Delprat
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.redis.contribs;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.redis.RedisAdmin;
import org.nuxeo.ecm.core.redis.RedisCallable;
import org.nuxeo.ecm.core.redis.RedisExecutor;
import org.nuxeo.ecm.core.uidgen.AbstractUIDSequencer;
import org.nuxeo.runtime.api.Framework;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Redis-based UID generator.
 *
 * @since 7.4
 */
public class RedisUIDSequencer extends AbstractUIDSequencer {

    protected static final Log log = LogFactory.getLog(RedisUIDSequencer.class);

    protected String namespace;

    @Override
    public void init() {
        RedisAdmin redisAdmin = Framework.getService(RedisAdmin.class);
        namespace = redisAdmin.namespace("counters");
    }

    @Override
    public void dispose() {
    }

    @Override
    public int getNext(String key) {
        RedisExecutor executor = Framework.getService(RedisExecutor.class);
        try {
            return executor.execute(new RedisCallable<Long>() {
                @Override
                public Long call(Jedis jedis) {
                    return jedis.incr(namespace + key);
                }
            }).intValue();
        } catch (JedisException e) {
            throw new NuxeoException(e);
        }
    }

}
