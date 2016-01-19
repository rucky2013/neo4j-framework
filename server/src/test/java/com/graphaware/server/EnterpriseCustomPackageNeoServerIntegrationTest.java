/*
 * Copyright (c) 2013-2016 GraphAware
 *
 * This file is part of the GraphAware Framework.
 *
 * GraphAware Framework is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.server;

import com.graphaware.test.integration.EnterpriseNeoTestServer;
import com.graphaware.test.integration.NeoServerIntegrationTest;
import com.graphaware.test.integration.NeoTestServer;
import org.apache.http.HttpStatus;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Integration test for custom server that wires Spring components.
 */
@Ignore
public class EnterpriseCustomPackageNeoServerIntegrationTest extends NeoServerIntegrationTest {

    @Override
    protected NeoTestServer neoTestServer(String neo4jConfigFile, String neo4jServerConfigFile) {
        return new EnterpriseNeoTestServer(neo4jConfigFile, neo4jServerConfigFile);
    }

    @Override
    protected String neo4jServerConfigFile() {
        return "neo4j-server-custom-package.properties";
    }

    @Test
    public void componentsShouldBeWired() {
        httpClient.get(baseUrl() + "/graphaware/greet", HttpStatus.SC_OK);
    }
}
