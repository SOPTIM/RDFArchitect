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

package org.rdfarchitect.database.implementations.http;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.rdfarchitect.database.implementations.DatabaseAdminProtocol;
import org.rdfarchitect.exception.database.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class FusekiHttpAdminProtocol implements DatabaseAdminProtocol {

    private static final Logger logger = LoggerFactory.getLogger(FusekiHttpAdminProtocol.class);

    private final String url;

    public FusekiHttpAdminProtocol(String url) {
        this.url = url;
    }

    @Override
    public boolean ping() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri(URI.create(url + "/$/ping"))
                                             .GET()
                                             .build();
            HttpResponse<String> response = null;
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException | InterruptedException e) {
            logger.warn("Ping failed against endpoint: {}", url);
            return false;
        }
    }

    @Override
    public List<String> listDatasets() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri(URI.create(url + "/$/datasets"))
                                             .GET()
                                             .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String jsonResponse = response.body();
                JSONObject jsonObject = new JSONObject(jsonResponse);
                JSONArray datasets = jsonObject.getJSONArray("datasets");

                List<String> datasetNames = new ArrayList<>();
                for (int i = 0; i < datasets.length(); i++) {
                    JSONObject dataset = datasets.getJSONObject(i);
                    String datasetId = dataset.getString("ds.name").replace("/", "");
                    datasetNames.add(datasetId);
                }
                return datasetNames;
            }
        } catch (IOException | InterruptedException | JSONException e) {
            throw new DataAccessException("Failed to list datasets", e);
        }
        throw new DataAccessException("Failed to list datasets");
    }

    @Override
    public void createDataset(String datasetName) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            String requestBody = "dbName=" + datasetName + "&dbType=tdb2";
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri(URI.create(this.url + "/$/datasets"))
                                             .header("Content-Type", "application/x-www-form-urlencoded")
                                             .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                             .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                logger.info("Successfully created dataset: \"{}\" at endpoint: \"{}\"", datasetName, this.url);
            } else {
                throw new DataAccessException("Failed to create dataset \"" + datasetName + "\" at endpoint: \"" + this.url + "\"");
            }
        } catch (IOException | InterruptedException e) {
            throw new DataAccessException("Failed to create dataset \"" + datasetName + "\" at endpoint: \"" + this.url + "\"", e);
        }
    }

    @Override
    public void deleteDataset(String datasetName) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                                             .uri(URI.create(this.url + "/$/datasets/" + datasetName))
                                             .DELETE()
                                             .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                logger.info("Successfully deleted dataset: \"{}\" at endpoint: \"{}\"", datasetName, this.url);
            } else {
                throw new DataAccessException("Failed to delete dataset \"" + datasetName + "\" at endpoint: \"" + this.url + "\"");
            }
        } catch (IOException | InterruptedException e) {
            throw new DataAccessException("Failed to delete dataset \"" + datasetName + "\" at endpoint: \"" + this.url + "\"", e);
        }
    }
}
