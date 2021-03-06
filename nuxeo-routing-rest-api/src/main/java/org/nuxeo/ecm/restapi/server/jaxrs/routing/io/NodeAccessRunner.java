/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     <a href="mailto:grenard@nuxeo.com">Guillaume Renard</a>
 *
 */

package org.nuxeo.ecm.restapi.server.jaxrs.routing.io;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.UnrestrictedSessionRunner;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;

/**
 * @since 7.2
 */
public class NodeAccessRunner extends UnrestrictedSessionRunner {

    GraphNode node;

    GraphRoute workflowInstance;

    String workflowInstanceId;

    String nodeId;

    public NodeAccessRunner(CoreSession session, String workflowInstanceId, String nodeId) {
        super(session);
        this.workflowInstanceId = workflowInstanceId;
        this.nodeId = nodeId;
    }

    @Override
    public void run() throws ClientException {
        DocumentModel workflowInstanceDoc = session.getDocument(new IdRef(workflowInstanceId));
        workflowInstance = workflowInstanceDoc.getAdapter(GraphRoute.class);
        node = workflowInstance.getNode(nodeId);
        workflowInstanceDoc.detach(true);
        node.getDocument().detach(true);
    }

}
