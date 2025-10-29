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

package org.rdfarchitect.config;

import org.rdfarchitect.cim.rendering.RenderCIMCollectionUseCase;
import org.rdfarchitect.cim.rendering.mermaid.RenderCIMCollectionMermaidService;
import org.rdfarchitect.cim.rendering.svelteflow.RenderCIMCollectionSvelteFlowService;
import org.rdfarchitect.services.dl.select.FetchRenderingLayoutDataUseCase;
import org.rdfarchitect.services.dl.update.EnsureDiagramLayoutForCIMCollectionUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RenderingConfig {

    @Value("${rendering.renderer:mermaid}")
    private String rendererType;

    @Bean
    public RenderCIMCollectionUseCase renderingPort(FetchRenderingLayoutDataUseCase fetchRenderingLayoutDataUseCase,
                                                    EnsureDiagramLayoutForCIMCollectionUseCase ensureDiagramLayoutForCIMCollectionUseCase) {
        return switch (rendererType) {
            case "svelteflow" -> new RenderCIMCollectionSvelteFlowService(fetchRenderingLayoutDataUseCase, ensureDiagramLayoutForCIMCollectionUseCase);
            case "mermaid" -> new RenderCIMCollectionMermaidService();
            default -> new RenderCIMCollectionMermaidService();
        };
    }
}
