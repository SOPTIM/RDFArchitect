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

package org.rdfarchitect.services.validation;

import lombok.RequiredArgsConstructor;

import org.apache.jena.graph.Graph;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.rdfarchitect.api.dto.validation.CGMESVersion;
import org.rdfarchitect.api.dto.validation.ValidationIssueDTO;
import org.rdfarchitect.api.dto.validation.ValidationReportDTO;
import org.rdfarchitect.api.dto.validation.ValidationSeverity;
import org.rdfarchitect.database.DatabasePort;
import org.rdfarchitect.database.GraphIdentifier;
import org.rdfarchitect.services.validation.rule.ValidationRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SchemaValidationService implements SchemaValidationUseCase {

    private final DatabasePort databasePort;
    private final List<ValidationRule> validationRules;

    @Override
    public ValidationReportDTO validateSchema(
            GraphIdentifier graphIdentifier, CGMESVersion cgmesVersion) {
        ValidationReportDTO report;
        try (var ctx = databasePort.getGraphWithContext(graphIdentifier).begin(ReadWrite.READ)) {
            var model = ModelFactory.createModelForGraph(ctx.getRdfGraph());
            model.setNsPrefixes(databasePort.getPrefixMapping(graphIdentifier.datasetName()));

            report = validateModel(model, cgmesVersion);
        }
        return report;
    }

    @Override
    public ValidationReportDTO validateSchema(Graph graph, CGMESVersion cgmesVersion) {
        return validateModel(ModelFactory.createModelForGraph(graph), cgmesVersion);
    }

    private ValidationReportDTO validateModel(Model model, CGMESVersion cgmesVersion) {
        var issues = new ArrayList<ValidationIssueDTO>();

        validationRules.forEach(rule -> rule.validate(model, issues, cgmesVersion));

        var hasErrors = issues.stream().anyMatch(i -> i.getSeverity() == ValidationSeverity.ERROR);

        return ValidationReportDTO.builder().valid(!hasErrors).issues(issues).build();
    }
}
