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

package org.rdfarchitect.models.cim.data.dto.relations.uri;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

public class URIDeserializer extends JsonDeserializer<URI> {

    @Override
    public URI deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        if (jsonParser.getCurrentToken().isScalarValue()) {
            String value = jsonParser.getValueAsString();
            return new URI(value);
        }
        ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
        var map = mapper.readValue(jsonParser, new TypeReference<Map<String, String>>() {
        });

        var suffix = map.getOrDefault("suffix", null);
        var prefix = map.getOrDefault("prefix", null);

        if (suffix == null) {
            throw new JsonParseException("Failed to deserialize URI, because no suffix ist set.");
        }
        if (prefix == null) {
            throw new JsonParseException("Failed to deserialize URI, because no prefix ist set.");
        }
        return new URI(prefix + suffix);
    }
}
