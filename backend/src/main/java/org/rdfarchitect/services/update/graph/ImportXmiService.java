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

import org.rdfarchitect.services.update.graph.ImportProgressListener.Outcome;
import org.rdfarchitect.services.update.graph.ImportProgressListener.PlannedImport;
import org.rdfarchitect.xmi.read.XmiModelScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImportXmiService implements ImportXmiUseCase {

    private static final Logger logger = LoggerFactory.getLogger(ImportXmiService.class);

    @Override
    public List<ScannedProfile> scanProfiles(
            List<MultipartFile> files, ImportProgressListener listener) {
        listener.planned(plan(files));

        var profiles = new ArrayList<ScannedProfile>();
        for (var index = 0; index < files.size(); index++) {
            var file = files.get(index);
            if (listener.isCancelled()) {
                listener.finished(index, Outcome.SKIPPED, null);
                continue;
            }
            listener.started(index);
            scanFile(file, index, profiles, listener);
        }
        return profiles;
    }

    private void scanFile(
            MultipartFile file,
            int index,
            List<ScannedProfile> profiles,
            ImportProgressListener listener) {
        var fileName = file.getOriginalFilename();
        if (!XmiFiles.isXmiFile(file)) {
            logger.warn("'{}' is not an Enterprise Architect export.", fileName);
            listener.finished(index, Outcome.FAILED, null);
            return;
        }
        try (var xmi = file.getInputStream()) {
            for (var profile : XmiModelScanner.scan(xmi)) {
                profiles.add(
                        new ScannedProfile(
                                fileName,
                                profile.id(),
                                profile.name(),
                                profile.namespaceCandidates()));
            }
            logger.info("Export '{}' holds {} profile(s).", fileName, profiles.size());
            listener.finished(index, Outcome.SCANNED, null);
        } catch (IOException | RuntimeException exception) {
            logger.warn("Unable to read the export '{}': {}", fileName, exception.getMessage());
            listener.finished(index, Outcome.FAILED, null);
        }
    }

    private List<PlannedImport> plan(List<MultipartFile> files) {
        var planned = new ArrayList<PlannedImport>(files.size());
        for (var index = 0; index < files.size(); index++) {
            var file = files.get(index);
            planned.add(new PlannedImport(index, file.getOriginalFilename(), file.getSize()));
        }
        return planned;
    }
}
