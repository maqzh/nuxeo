/*
 * (C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     tiry
 */
package org.nuxeo.ecm.core.event.bus;

import java.util.List;
import java.util.Map;

import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventServiceAdmin;
import org.nuxeo.ecm.core.event.impl.AsyncEventExecutor;
import org.nuxeo.ecm.core.event.impl.EventListenerDescriptor;
import org.nuxeo.ecm.core.event.impl.EventListenerList;
import org.nuxeo.runtime.api.Framework;

/**
 *
 * @since TODO
 */
public abstract class AbstractEventBundlePipeConsumer<T> {

    protected List<EventListenerDescriptor> postCommitAsync;

    protected String name;

    protected Map<String, String> params;

    protected volatile AsyncEventExecutor asyncExec;

    public void initConsumer(String name, Map<String, String> params) {
        EventServiceAdmin eventService = Framework.getService(EventServiceAdmin.class);
        EventListenerList listeners = eventService.getListenerList();
        postCommitAsync = listeners.getEnabledAsyncPostCommitListenersDescriptors();
        asyncExec = new AsyncEventExecutor();
    }

    protected String getName() {
        return name;
    }

    protected Map<String, String> getParameters() {
        return params;
    }


    public void receiveMessage(List<T> messages) {
        List<EventBundle> bundles = unmarshallEventBundle(messages);
        processEventBundles(bundles);
    }

    protected abstract List<EventBundle> unmarshallEventBundle(List<T> messages);

    protected void processEventBundles(List<EventBundle> bundles) {

        // could introduce bulk mode for EventListeners
        for (EventBundle eventBundle : bundles) {
            asyncExec.run(postCommitAsync, eventBundle);
        }

    }

}
