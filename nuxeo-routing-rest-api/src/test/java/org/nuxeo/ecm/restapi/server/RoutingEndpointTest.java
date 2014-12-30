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

package org.nuxeo.ecm.restapi.server;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonProcessingException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.automation.io.services.codec.ObjectCodecService;
import org.nuxeo.ecm.automation.test.EmbeddedAutomationServerFeature;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.storage.sql.ra.PoolingRepositoryFactory;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.platform.routing.test.WorkflowFeature;
import org.nuxeo.ecm.restapi.server.jaxrs.routing.adapter.TaskAdapter;
import org.nuxeo.ecm.restapi.server.jaxrs.routing.adapter.WorkflowAdapter;
import org.nuxeo.ecm.restapi.server.jaxrs.routing.model.RoutingRequest;
import org.nuxeo.ecm.restapi.test.BaseTest;
import org.nuxeo.ecm.restapi.test.RestServerInit;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * @since 7.1
 */
@RunWith(FeaturesRunner.class)
@Features({ EmbeddedAutomationServerFeature.class, WorkflowFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD, repositoryFactoryClass = PoolingRepositoryFactory.class, init = RestServerInit.class)
@Jetty(port = 18090)
@Deploy({ "org.nuxeo.ecm.platform.restapi.server.routing", "org.nuxeo.ecm.automation.test",
        "org.nuxeo.ecm.automation.io", "org.nuxeo.ecm.platform.restapi.io", "org.nuxeo.ecm.platform.restapi.test",
        "org.nuxeo.ecm.platform.restapi.server", "org.nuxeo.ecm.platform.routing.default",
        "org.nuxeo.ecm.platform.filemanager.api", "org.nuxeo.ecm.platform.filemanager.core",
        "org.nuxeo.ecm.platform.mimetype.api", "org.nuxeo.ecm.platform.mimetype.core" })
public class RoutingEndpointTest extends BaseTest {

    @Inject
    ObjectCodecService objectCodecService;

    @Test
    public void testCreateGetAndCancelWorkflowEndpoint() throws IOException {
        // Check POST /workflow
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RoutingRequest routingRequest = new RoutingRequest();
        routingRequest.setRouteModelId("SerialDocumentReview");
        objectCodecService.write(out, routingRequest);
        ClientResponse response = getResponse(RequestType.POST, "/workflow", out.toString());
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        JsonNode node = mapper.readTree(response.getEntityInputStream());
        final String createdWorflowInstanceId = node.get("uid").getTextValue();

        // Check GET /workflow/{workflowInstanceId}
        response = getResponse(RequestType.GET, "/workflow/" + createdWorflowInstanceId);
        node = mapper.readTree(response.getEntityInputStream());
        String fetchedWorflowInstanceId = node.get("uid").getTextValue();
        assertEquals(createdWorflowInstanceId, fetchedWorflowInstanceId);

        // Check GET /workflow .i.e get running workflow initialized by currentUser
        response = getResponse(RequestType.GET, "/workflow");
        node = mapper.readTree(response.getEntityInputStream());
        // we expect to retrieve the one previously created
        assertEquals(1, node.get("entries").size());
        Iterator<JsonNode> elements = node.get("entries").getElements();
        fetchedWorflowInstanceId = elements.next().get("uid").getTextValue();
        assertEquals(createdWorflowInstanceId, fetchedWorflowInstanceId);

        // Check GET /task i.e. pending tasks for current user
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-NXDocumentProperties", "dublincore,task");
        response = getResponse(RequestType.GET, "/task", headers);
        assertActorIsAdministrator(response);

        // Check GET /task/Administrator i.e. pending tasks for Administrator
        MultivaluedMap<String, String> queryParams = new MultivaluedMapImpl();
        queryParams.put("userId", Arrays.asList(new String[] { "Administrator" }));
        response = getResponse(RequestType.GET, "/task", null, queryParams, null, headers);
        assertActorIsAdministrator(response);

        // Check GET /task/Administrator/workflow/{workflowInstanceId} i.e. pending tasks for Administrator
        queryParams.put("workflowInstanceId", Arrays.asList(new String[] { createdWorflowInstanceId }));
        response = getResponse(RequestType.GET, "/task", null, queryParams, null, headers);
        assertActorIsAdministrator(response);

        // TODO Check created RouteNode/Tasks

        // Check DELETE /workflow
        response = getResponse(RequestType.DELETE, "/workflow/" + createdWorflowInstanceId);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        // Check GET /workflow
        response = getResponse(RequestType.GET, "/workflow");
        node = mapper.readTree(response.getEntityInputStream());
        // we cancel running workflow, we expect 0 running workflow
        assertEquals(0, node.get("entries").size());

        // Check we have no opened tasks
        response = getResponse(RequestType.GET, "/task", headers);
        node = mapper.readTree(response.getEntityInputStream());
        assertEquals(0, node.get("entries").size());

    }

    protected void assertActorIsAdministrator(ClientResponse response) throws JsonProcessingException, IOException {
        JsonNode node = mapper.readTree(response.getEntityInputStream());
        assertEquals(1, node.get("entries").size());
        Iterator<JsonNode> elements = node.get("entries").getElements();
        JsonNode properties = elements.next().get("properties");
        JsonNode actors = properties.get("nt:actors");
        assertEquals(1, actors.size());
        String actor = actors.getElements().next().getTextValue();
        assertEquals("Administrator", actor);
    }

    @Test
    public void testGetAllWorkflowEndpoint() throws Exception {

        ClientResponse response = getResponse(RequestType.GET, "/workflow/models");

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

        JsonNode node = mapper.readTree(response.getEntityInputStream());
        assertEquals(2, node.get("entries").size());

        Iterator<JsonNode> elements = node.get("entries").getElements();

        List<String> expectedPaths = Arrays.asList(new String[] { "/document-route-models-root/SerialDocumentReview",
                "/document-route-models-root/ParallelDocumentReview" });
        Collections.sort(expectedPaths);
        List<String> realPaths = new ArrayList<String>();
        while (elements.hasNext()) {
            JsonNode element = elements.next();
            realPaths.add(element.get("path").getTextValue());
        }
        Collections.sort(realPaths);
        assertEquals(expectedPaths, realPaths);
    }

    @Test
    public void testAdapter() throws IOException {

        DocumentModel note = RestServerInit.getNote(0, session);
        // Check POST /api/id/{documentId}/@workflow/
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        RoutingRequest routingRequest = new RoutingRequest();
        routingRequest.setRouteModelId("SerialDocumentReview");
        objectCodecService.write(out, routingRequest);
        ClientResponse response = getResponse(RequestType.POST, "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME,
                out.toString());
        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());

        JsonNode node = mapper.readTree(response.getEntityInputStream());
        final String createdWorflowInstanceId = node.get("uid").getTextValue();

        // Check GET /api/id/{documentId}/@workflow/
        response = getResponse(RequestType.GET, "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME);
        node = mapper.readTree(response.getEntityInputStream());
        assertEquals(1, node.get("entries").size());
        assertEquals(createdWorflowInstanceId, node.get("entries").getElements().next().get("uid").getTextValue());

        // Check GET /api/id/{documentId}/@workflow/{workflowInstanceId}/task
        response = getResponse(RequestType.GET, "/id/" + note.getId() + "/@" + WorkflowAdapter.NAME + "/"
                + createdWorflowInstanceId + "/task");
        node = mapper.readTree(response.getEntityInputStream());
        assertEquals(1, node.get("entries").size());
        JsonNode taskNode = node.get("entries").getElements().next();
        assertEquals("RoutingTask", taskNode.get("type").getTextValue());
        String taskUid = taskNode.get("uid").getTextValue();

        // Check GET /api/id/{documentId}/@task/
        response = getResponse(RequestType.GET, "/id/" + note.getId() + "/@" + TaskAdapter.NAME);
        node = mapper.readTree(response.getEntityInputStream());
        assertEquals(1, node.get("entries").size());
        taskNode = node.get("entries").getElements().next();
        assertEquals(taskUid, taskNode.get("uid").getTextValue());
    }
}