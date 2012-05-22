/*
 * (C) Copyright 2012 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.routing.core.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.PropertyException;
import org.nuxeo.ecm.platform.routing.api.DocumentRouteElement;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.exception.DocumentRouteException;

/**
 * @since 5.6
 */
public class GraphRouteImpl implements GraphRoute {

    public static final String PROP_VARIABLES = "docri:variables";

    public static final String PROP_VAR_NAME = "name";

    public static final String PROP_VAR_VALUE = "value";

    protected final DocumentModel doc;

    protected final List<GraphNode> nodes;

    public GraphRouteImpl(DocumentRouteElement element)
            throws DocumentRouteException {
        this.doc = element.getDocument();
        this.nodes = computeNodes();
    }

    protected List<GraphNode> computeNodes() throws DocumentRouteException {
        try {
            CoreSession session = doc.getCoreSession();
            DocumentModelList children = session.getChildren(doc.getRef());
            List<GraphNode> nodes = new ArrayList<GraphNode>(children.size());
            for (DocumentModel doc : children) {
                // TODO use adapters
                if (doc.getType().equals("RouteNode")) {
                    nodes.add(new GraphNodeImpl(doc, this));
                }
            }
            return nodes;
        } catch (ClientException e) {
            throw new DocumentRouteException(e);
        }
    }

    @Override
    public String getName() {
        try {
            return doc.getTitle();
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @Override
    public GraphNode getStartNode() throws DocumentRouteException {
        for (GraphNode node : nodes) {
            if (node.isStart()) {
                return node;
            }
        }
        throw new DocumentRouteException("No start node for graph: "
                + getName());
    }

    public Collection<GraphNode> getNodes() {
        return nodes;
    }

    @Override
    public Map<String, Serializable> getVariables() {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Serializable>> vars = (List<Map<String, Serializable>>) doc.getPropertyValue(PROP_VARIABLES);
            Map<String, Serializable> map = new LinkedHashMap<String, Serializable>();
            for (Map<String, Serializable> var : vars) {
                String name = (String) var.get(PROP_VAR_NAME);
                Serializable value = var.get(PROP_VAR_VALUE);
                map.put(name, value);
            }
            return map;
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @Override
    public void setVariables(Map<String, Serializable> map) {
        try {
            List<Map<String, Serializable>> vars = new LinkedList<Map<String, Serializable>>();
            for (Entry<String, Serializable> es : map.entrySet()) {
                Map<String, Serializable> m = new HashMap<String, Serializable>();
                m.put(PROP_VAR_NAME, es.getKey());
                m.put(PROP_VAR_VALUE, es.getValue());
                vars.add(m);
            }
            doc.setPropertyValue(PROP_VARIABLES, (Serializable) vars);
            CoreSession session = doc.getCoreSession();
            session.saveDocument(doc);
            session.save();
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

    @Override
    public List<String> getAttachedDocumentIds() {
        try {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) doc.getPropertyValue(DocumentRoutingConstants.ATTACHED_DOCUMENTS_PROPERTY_NAME);
            return ids;
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }
    @Override
    public DocumentModelList getAttachedDocuments() {
        try {
            @SuppressWarnings("unchecked")
            List<String> ids = (List<String>) doc.getPropertyValue(DocumentRoutingConstants.ATTACHED_DOCUMENTS_PROPERTY_NAME);
            ArrayList<DocumentRef> docRefs = new ArrayList<DocumentRef>();
            for (String id : ids) {
                docRefs.add(new IdRef(id));
            }
            return doc.getCoreSession().getDocuments(
                    docRefs.toArray(new DocumentRef[0]));
        } catch (ClientException e) {
            throw new ClientRuntimeException(e);
        }
    }

}