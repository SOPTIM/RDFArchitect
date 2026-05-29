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

package org.rdfarchitect.database;

import org.apache.jena.query.ReadWrite;

/**
 * Transaction lifecycle contract. Implementations must be usable in try-with-resources: {@link
 * #begin(ReadWrite)} returns {@code this}, and {@link #close()} calls {@link #end()}.
 */
public interface Transactional extends AutoCloseable {

    Transactional begin(ReadWrite mode);

    void commit();

    void commit(String message);

    void abort();

    void end();

    boolean isInTransaction();

    ReadWrite transactionMode();

    @Override
    void close();
}
