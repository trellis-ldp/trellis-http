/*
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
 */
package org.trellisldp.http;

import static java.util.Objects.isNull;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.SESSION_PROPERTY;
import static org.trellisldp.spi.RDFUtils.getInstance;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.spi.Session;

/**
 * @author acoburn
 */
class BaseLdpResource {

    protected static final Logger LOGGER = getLogger(BaseLdpResource.class);

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected static final RDF rdf = getInstance();

    protected final Map<String, String> partitions;

    protected BaseLdpResource(final Map<String, String> partitions) {
        this.partitions = partitions;
    }

    protected Session getSession(final LdpBaseRequest req) {
        final Session session = (Session) req.ctx.getProperty(SESSION_PROPERTY);
        if (isNull(session)) {
            return new HttpSession();
        }
        return session;
    }

    protected String getPartition(final String path) {
        return path.split("/", 2)[0];
    }

    protected String getBaseUrl(final LdpBaseRequest req) {
        return partitions.getOrDefault(getPartition(req.path), req.uriInfo.getBaseUri().toString());
    }
}
