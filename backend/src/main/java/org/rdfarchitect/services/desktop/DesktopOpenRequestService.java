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

package org.rdfarchitect.services.desktop;

import org.rdfarchitect.api.dto.desktop.OpenRequestDTO;
import org.rdfarchitect.desktop.OpenRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Workspace files the operating system asked the desktop app to open. */
@Service
@Profile("desktop")
public class DesktopOpenRequestService
        implements RequestOpenFileUseCase, GetOpenRequestFileUseCase {

    private static final Logger logger = LoggerFactory.getLogger(DesktopOpenRequestService.class);

    private final OpenRequests openRequests;

    public DesktopOpenRequestService(OpenRequests openRequests) {
        this.openRequests = openRequests;
    }

    @Override
    public OpenRequestDTO requestOpenFile(String path) {
        var request = openRequests.add(Path.of(path));
        return new OpenRequestDTO(request.id(), request.fileName(), request.path().toString());
    }

    @Override
    public Optional<OpenRequestFile> getOpenRequestFile(String requestId) {
        var request = openRequests.get(requestId);
        if (request.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    new OpenRequestFile(
                            request.get().fileName(), Files.readAllBytes(request.get().path())));
        } catch (IOException e) {
            logger.warn("Could not read {}.", request.get().path(), e);
            return Optional.empty();
        }
    }
}
