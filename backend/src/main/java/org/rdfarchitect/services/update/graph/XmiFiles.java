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

package org.rdfarchitect.services.update.graph;

import lombok.experimental.UtilityClass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Tells an Enterprise Architect XMI export apart from the RDF files the import otherwise takes. */
@UtilityClass
public class XmiFiles {

    private final Logger logger = LoggerFactory.getLogger(XmiFiles.class);

    /**
     * How much of a file is read to recognise it. The marker sits in the root element, which
     * follows the XML declaration directly, but the declaration of an export is long.
     */
    private final int PROBE_BYTES = 8192;

    private final String XMI_NAMESPACE = "http://www.omg.org/spec/XMI";

    /**
     * Whether the file opens like an export. The content decides rather than the extension, because
     * an export is named {@code .xml} like an RDF/XML file, which must keep importing as one.
     */
    public boolean isXmiFile(MultipartFile file) {
        try (var content = file.getInputStream()) {
            var probe = content.readNBytes(PROBE_BYTES);
            // The marker is ASCII and sits in the root element, so reading the probe as
            // ISO-8859-1 finds it whatever the file declares as its encoding.
            return new String(probe, StandardCharsets.ISO_8859_1).contains(XMI_NAMESPACE);
        } catch (IOException exception) {
            logger.warn(
                    "Unable to look into '{}': {}",
                    file.getOriginalFilename(),
                    exception.getMessage());
            return false;
        }
    }
}
