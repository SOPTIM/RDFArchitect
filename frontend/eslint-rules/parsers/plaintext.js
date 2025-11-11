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

const plainTextParser = {
    parseForESLint(code) {
        const ast = {
            type: "Program",
            sourceType: "module",
            body: [],
            range: [0, code.length],
            loc: computeLoc(code),
            tokens: [],
            comments: [],
        };

        return {
            ast,
            visitorKeys: { Program: [] },
        };
    },
};

function computeLoc(code) {
    const lines = code.split(/\r\n|\r|\n/);
    const lastLine = lines[lines.length - 1] ?? "";
    return {
        start: { line: 1, column: 0 },
        end: { line: lines.length, column: lastLine.length },
    };
}

export default plainTextParser;
