/*
 *    Copyright (c) 2024-2026 SOPTIM AG
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package org.rdfarchitect.api.controller;

import lombok.experimental.UtilityClass;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** The headers that suggest the name a download is saved under. */
@UtilityClass
public class DownloadHeaders {

    /**
     * Returns headers that suggest {@code fileName} for the response body.
     *
     * <p>The name goes into {@code filename*} encoded as UTF-8: a schema name may hold any
     * character, and Tomcat drops a header holding one outside Latin-1, which would leave the
     * download without its name. The header is exposed to the frontend, which saves the body
     * itself; without that the browser would hide it.
     *
     * @param fileName the suggested file name
     * @return new headers carrying the {@code Content-Disposition}
     */
    public static HttpHeaders attachment(String fileName) {
        var headers = new HttpHeaders();
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build());
        headers.setAccessControlExposeHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        return headers;
    }
}
