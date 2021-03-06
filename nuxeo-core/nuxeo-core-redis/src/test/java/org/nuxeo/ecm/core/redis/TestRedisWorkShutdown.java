package org.nuxeo.ecm.core.redis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.work.AbstractWork;
import org.nuxeo.ecm.core.work.api.Work;
import org.nuxeo.ecm.core.work.api.WorkManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import redis.clients.jedis.Jedis;

@Features({ RedisFeature.class, CoreFeature.class })
@RunWith(FeaturesRunner.class)
public class TestRedisWorkShutdown {

    static Log log = LogFactory.getLog(TestRedisWorkShutdown.class);

    static CountDownLatch canShutdown = new CountDownLatch(2);

    static CountDownLatch canProceed = new CountDownLatch(1);

    public static class MyWork extends AbstractWork {

        private static final long serialVersionUID = 1L;

        MyWork(String id) {
            super(id);
        }

        @Override
        public String getTitle() {
            return "waiting work";
        }

        @Override
        public void work() {
            try {
                log.debug(id + " waiting for shutdown");
                canShutdown.countDown();
                canProceed.await(1, TimeUnit.MINUTES);
                Assert.assertThat(isSuspending(), Matchers.is(true));
            } catch (InterruptedException cause) {
                Thread.currentThread()
                        .interrupt();
            }
        }

        @Override
        public String toString() {
            return id;
        }
    }

    @Inject
    WorkManager works;

    @Inject
    RedisFeature redis;

    @Test
    public void worksArePersisted() throws InterruptedException {
        try {
            works.schedule(new MyWork("first"));
            works.schedule(new MyWork("second"));
            canShutdown.await(10, TimeUnit.SECONDS);
            works.shutdown(0, TimeUnit.SECONDS);
        } finally {
            canProceed.countDown();
        }
        List<Work> scheduled = new ScheduledRetriever().listScheduled();
        Assert.assertThat(scheduled.size(), Matchers.is(2));
    }

    class ScheduledRetriever {
        String namespace = Framework.getService(RedisAdmin.class)
                .namespace("work");

        byte[] keyBytes(String value) {
            try {
                return namespace.concat(value)
                        .getBytes("UTF-8");
            } catch (UnsupportedEncodingException cause) {
                throw new UnsupportedOperationException("Cannot encode " + value, cause);
            }
        }

        byte[] queueBytes() {
            return keyBytes("sched:default");
        }

        byte[] dataKey() {
            return keyBytes("data");
        }

        List<Work> listScheduled() {
            return Framework.getService(RedisExecutor.class)
                    .execute(new RedisCallable<List<Work>>() {
                        @Override
                        public List<Work> call(Jedis jedis) {
                            Set<byte[]> keys = jedis.smembers(queueBytes());
                            List<Work> list = new ArrayList<Work>(keys.size());
                            for (byte[] workIdBytes : keys) {
                                // get data
                                byte[] workBytes = jedis.hget(dataKey(), workIdBytes);
                                Work work = deserializeWork(workBytes);
                                list.add(work);
                            }
                            return list;
                        }
                    });
        }

        Work deserializeWork(byte[] bytes) {
            if (bytes == null) {
                return null;
            }
            InputStream bain = new ByteArrayInputStream(bytes);
            try (ObjectInputStream in = new ObjectInputStream(bain)) {
                return (Work) in.readObject();
            } catch (RuntimeException cause) {
                throw cause;
            } catch (IOException | ClassNotFoundException cause) {
                throw new RuntimeException("Cannot deserialize work", cause);
            }
        }
    }
}
