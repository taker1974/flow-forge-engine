/*
 * Copyright 2025 Konstantin Terskikh
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package ru.spb.tksoft.flowforge.engine.contract;

import java.io.IOException;
import java.nio.file.Path;
import ru.spb.tksoft.flowforge.sdk.contract.Block;

/**
 * Block loader interface.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
public interface BlockRegistry extends AutoCloseable {

    /**
     * Load block builder services from the top level modules directory.
     * 
     * @param topLevelModulesDirectoryPath - the top level modules directory path.
     * @param removeDuplicateDependencies - true if duplicate dependencies should be removed from
     *        the modules directories.
     * @throws IOException - if an I/O error occurs.
     */
    void loadBlockBuilderServices(Path topLevelModulesDirectoryPath,
            boolean removeDuplicateDependencies) throws IOException;

    /**
     * Create a block.
     * 
     * @param blockTypeId - the block type id.
     * @param ctorArgs - the constructor arguments.
     * @return the block.
     */
    Block createBlock(String blockTypeId, Object... ctorArgs);

    /**
     * Close the registry and release all resources including module ClassLoaders.
     */
    @Override
    void close();
}
