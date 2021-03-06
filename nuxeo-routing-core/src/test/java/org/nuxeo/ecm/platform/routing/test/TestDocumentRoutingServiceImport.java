/*
 * (C) Copyright 2009-2015 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Alexandre Russel
 *     Florent Guillaume
 */
package org.nuxeo.ecm.platform.routing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Test;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.ecm.core.test.annotations.RepositoryInit;
import org.nuxeo.ecm.platform.routing.api.DocumentRoute;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingConstants;
import org.nuxeo.ecm.platform.routing.api.DocumentRoutingService;
import org.nuxeo.ecm.platform.routing.api.RouteModelResourceType;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;

@Deploy({ "org.nuxeo.ecm.platform.filemanager.core", //
        "org.nuxeo.ecm.platform.query.api", //
        "org.nuxeo.ecm.platform.task.core", //
        "org.nuxeo.ecm.platform.routing.core.test", //
})
@RepositoryConfig(init = TestDocumentRoutingServiceImport.ImportRouteRepositoryInit.class, cleanup = Granularity.METHOD)
public class TestDocumentRoutingServiceImport extends DocumentRoutingTestCase {

    protected static File tmp;

    @After
    public void tearDown() throws Exception {
        if (tmp != null) {
            tmp.delete();
            tmp = null;
        }
    }

    public static class ImportRouteRepositoryInit implements RepositoryInit {

        @Override
        public void populate(CoreSession session) throws ClientException {
            // content-template already populates the default domain
            try {
                populate0(session);
            } catch (IOException | URISyntaxException e) {
                throw new ClientException(e);
            }
        }

        public void populate0(CoreSession session) throws ClientException, IOException, URISyntaxException {

            // create an initial route to test that is override at import
            DocumentModel root = createDocumentModel(session, "document-route-models-root", "DocumentRouteModelsRoot",
                    "/");
            assertNotNull(root);
            DocumentModel route = createDocumentModel(session, "myRoute", "DocumentRoute",
                    "/document-route-models-root/");
            route.setPropertyValue("dc:coverage", "test");
            route = session.saveDocument(route);
            // set ACL to test that the ACLs are kept
            ACP acp = route.getACP();
            ACL acl = acp.getOrCreateACL("testrouting");
            acl.add(new ACE("testusername", "Write", true));
            acp.addACL(acl);
            route.setACP(acp, true);
            route = session.saveDocument(route);

            assertNotNull(route);
            assertEquals("test", route.getPropertyValue("dc:coverage"));

            DocumentModel node = createDocumentModel(session, "myNode", "RouteNode",
                    "/document-route-models-root/myRoute");

            assertNotNull(node);

            // create a ZIP for the contrib
            tmp = File.createTempFile("nuxeoRoutingTest", ".zip");
            ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(tmp));
            URL url = getClass().getResource("/routes/myRoute");
            File dir = new File(url.toURI().getPath());
            zipTree("", dir, false, zout);
            zout.finish();
            zout.close();

            RouteModelResourceType resource = new RouteModelResourceType();
            resource.setId("test");
            resource.setPath(tmp.getPath());
            resource.setUrl(tmp.toURI().toURL());

            DocumentRoutingService service = Framework.getService(DocumentRoutingService.class);
            service.registerRouteResource(resource, null);
        }

        protected DocumentModel createDocumentModel(CoreSession session, String name, String type, String path)
                throws ClientException {
            DocumentModel doc = session.createDocumentModel(path, name, type);
            doc.setPropertyValue(DocumentRoutingConstants.TITLE_PROPERTY_NAME, name);
            return session.createDocument(doc);
        }

        protected void zipTree(String prefix, File root, boolean includeRoot, ZipOutputStream zout) throws IOException {
            if (includeRoot) {
                prefix += root.getName() + '/';
                zipDirectory(prefix, zout);
            }
            for (String name : root.list()) {
                File file = new File(root, name);
                if (file.isDirectory()) {
                    zipTree(prefix, file, true, zout);
                } else {
                    if (name.endsWith("~") || name.endsWith("#") || name.endsWith(".bak")) {
                        continue;
                    }
                    name = prefix + name;
                    zipFile(name, file, zout);
                }
            }
        }

        protected void zipDirectory(String entryName, ZipOutputStream zout) throws IOException {
            ZipEntry zentry = new ZipEntry(entryName);
            zout.putNextEntry(zentry);
            zout.closeEntry();
        }

        protected void zipFile(String entryName, File file, ZipOutputStream zout) throws IOException {
            ZipEntry zentry = new ZipEntry(entryName);
            zentry.setTime(file.lastModified());
            zout.putNextEntry(zentry);
            FileInputStream in = new FileInputStream(file);
            try {
                IOUtils.copy(in, zout);
            } finally {
                in.close();
            }
            zout.closeEntry();
        }
    }

    @Test
    public void testImportRouteModel() throws Exception {
        // re-import routes created by test repository init after initial import
        service.importAllRouteModels(session);

        DocumentModel modelsRoot = session.getDocument(new PathRef("/document-route-models-root/"));
        assertNotNull(modelsRoot);
        DocumentModel route = session.getDocument(new PathRef("/document-route-models-root/myRoute"));
        assertNotNull(route);

        String routeDocId = service.getRouteModelDocIdWithId(session, "myRoute");
        DocumentModel doc = session.getDocument(new IdRef(routeDocId));
        DocumentRoute model = doc.getAdapter(DocumentRoute.class);

        assertEquals(route.getId(), model.getDocument().getId());
        // test that document was overriden but the ACLs were kept
        ACL newAcl = route.getACP().getACL("testrouting");
        assertNotNull(newAcl);
        assertEquals(1, newAcl.getACEs().length);
        assertEquals("testusername", newAcl.getACEs()[0].getUsername());

        // Oracle makes no difference between null and blank
        assertTrue(StringUtils.isBlank((String) route.getPropertyValue("dc:coverage")));
        DocumentModel node;
        try {
            node = session.getDocument(new PathRef("/document-route-models-root/myRoute/myNode"));
        } catch (ClientException e) {
            node = null;
        }
        assertNull(node);
        assertEquals("DocumentRoute", route.getType());
        DocumentModel step1 = session.getDocument(new PathRef("/document-route-models-root/myRoute/Step1"));
        assertNotNull(step1);
        assertEquals("RouteNode", step1.getType());
        DocumentModel step2 = session.getDocument(new PathRef("/document-route-models-root/myRoute/Step2"));
        assertNotNull(step2);
        assertEquals("RouteNode", step2.getType());
    }

}
