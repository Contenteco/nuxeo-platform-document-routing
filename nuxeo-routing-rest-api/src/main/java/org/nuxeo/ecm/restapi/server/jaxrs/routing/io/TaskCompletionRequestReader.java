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

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
import org.nuxeo.ecm.platform.routing.core.impl.GraphRoute;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.restapi.server.jaxrs.routing.io.util.JsonEncodeDecodeUtils;
import org.nuxeo.ecm.restapi.server.jaxrs.routing.model.TaskCompletionRequest;
import org.nuxeo.ecm.webengine.WebException;
import org.nuxeo.ecm.webengine.jaxrs.session.SessionFactory;

/**
 * @since 7.2
 */
@Provider
public class TaskCompletionRequestReader implements MessageBodyReader<TaskCompletionRequest> {

    protected static final Log log = LogFactory.getLog(TaskCompletionRequestReader.class);

    @Context
    private JsonFactory factory;

    @Context
    HttpServletRequest request;

    @Override
    public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return TaskCompletionRequest.class.isAssignableFrom(type);
    }

    @Override
    public TaskCompletionRequest readFrom(Class<TaskCompletionRequest> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> httpHeaders,
            InputStream entityStream) throws IOException, WebApplicationException {
        String content = IOUtils.toString(entityStream);
        if (content.isEmpty()) {
            if (content.isEmpty()) {
                throw new WebException("No content in request body", Response.Status.BAD_REQUEST.getStatusCode());
            }

        }

        try {
            return readRequest(content, httpHeaders);
        } catch (IOException | ClientException | ClassNotFoundException e) {
            throw WebException.wrap(e);
        }
    }

    private TaskCompletionRequest readRequest(String content, MultivaluedMap<String, String> httpHeaders)
            throws IOException, ClientException, ClassNotFoundException {
        JsonParser jp = factory.createJsonParser(content);
        return readJson(jp, httpHeaders);
    }

    private TaskCompletionRequest readJson(JsonParser jp, MultivaluedMap<String, String> httpHeaders)
            throws JsonParseException, IOException, ClassNotFoundException {
        CoreSession session = SessionFactory.getSession(request);
        JsonToken tok = jp.nextToken();

        // skip {
        if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
            tok = jp.nextToken();
        }
        String id = null;
        String comment = null;
        JsonNode variableNode = null;
        Map<String, Serializable> variables = null;
        while (tok != JsonToken.END_OBJECT) {
            String key = jp.getCurrentName();
            jp.nextToken();
            if ("id".equals(key)) {
                id = jp.readValueAs(String.class);
            } else if ("comment".equals(key)) {
                comment = jp.readValueAs(String.class);
            } else if ("variables".equals(key)) {
                variableNode = jp.readValueAsTree();
            } else if ("entity-type".equals(key)) {
                String entityType = jp.readValueAs(String.class);
                if (!TaskWriter.ENTITY_TYPE.equals(entityType)) {
                    throw new WebApplicationException(Response.Status.BAD_REQUEST);
                }
            } else {
                log.debug("Unknown key: " + key);
                jp.skipChildren();
            }
            tok = jp.nextToken();

        }

        if (id == null) {
            throw new WebException("No id found in request body", Response.Status.BAD_REQUEST.getStatusCode());
        }

        TaskCompletionRequest result = new TaskCompletionRequest();
        Task originalTask = session.getDocument(new IdRef(id)).getAdapter(Task.class);
        GraphNode node = null;
        GraphRoute workflowInstance = null;
        final String nodeId = originalTask.getVariable(DocumentRoutingConstants.TASK_NODE_ID_KEY);
        String workflowInstanceId = originalTask.getProcessId();
        DocumentModel workflowInstanceDoc = session.getDocument(new IdRef(workflowInstanceId));
        workflowInstance = workflowInstanceDoc.getAdapter(GraphRoute.class);
        node = workflowInstance.getNode(nodeId);
        if (variableNode != null) {
            variables = JsonEncodeDecodeUtils.decodeVariables(variableNode, node.getVariables(), session);
        }
        result.setVariables(variables);
        result.setComment(comment);

        return result;
    }

}
