/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Mariana Cedica
 */
package org.nuxeo.ecm.platform.routing.test;

import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.SimpleFeature;

/**
 *
 * @since 5.9.3
 *
 */
public class WorkflowFeature extends SimpleFeature {

    @Override
    public void beforeSetup(FeaturesRunner runner) throws Exception {
        // make sure tests are executed with an unrestricted user
        runner.getFeature(CoreFeature.class).getRepository().setUsername(
                "system");
    }
}
