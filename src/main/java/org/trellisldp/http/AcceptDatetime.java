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

import static java.time.ZonedDateTime.parse;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Objects.requireNonNull;

import java.time.Instant;

/**
 * @author acoburn
 */
public class AcceptDatetime {

    private final Instant datetime;

    /**
     * Create an Accept-Datetime header object
     * @param datetime the date time in RFC 1123 format
     */
    public AcceptDatetime(final String datetime) {
        requireNonNull(datetime, "Datetime may not be null");
        this.datetime = parse(datetime.trim(), RFC_1123_DATE_TIME).toInstant();
    }

    /**
     * Retrieve the corresponding instant
     * @return the instant
     */
    public Instant getInstant() {
        return datetime;
    }

    @Override
    public String toString() {
        return datetime.toString();
    }
}
