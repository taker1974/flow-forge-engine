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

package ru.spb.tksoft.flowforge.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spb.tksoft.flowforge.engine.contract.BlockRegistry;
import ru.spb.tksoft.flowforge.engine.model.BlockRegistryFromCratchImpl;

/**
 * Tests for BlockRegistryImpl.
 *
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
class BlockRegistryImplTest {

    static final Logger log = LoggerFactory.getLogger(BlockRegistryImplTest.class);

    private final String applicationName = "flow-forge-backend-instance";
    private final String blocksSubdirectory = "blocks";

    /**
     * Load blocks from the specified path.
     *
     * @param path - the path to load blocks from.
     */
    @Test
    void loadBlocks() {

        // Load blocks from the specified path.
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (Objects.isNull(dataHome) || dataHome.isEmpty()) {
            dataHome = System.getProperty("user.home") + "/.local/share";
        }

        BlockRegistry blockRegistry = new BlockRegistryFromCratchImpl(
                Set.of("2.0.5"),
                Dependencies.dependencies());

        final Path blocksPath = Paths.get(dataHome,
                applicationName, blocksSubdirectory);

        try {
            blockRegistry.loadBlocks(blocksPath);
            // If we get here, blocks were loaded successfully
            assertTrue(true, "Blocks loaded successfully");
        } catch (Exception e) {
            Assertions.fail("Failed to load blocks from path: " + blocksPath, e);
        }

        // // Create a block.
        // // Note: first argument is the block type id!
        // Block block = blockRegistry.createBlock(/* block type id */ "example-block-01",
        // /* constructor arguments */ "block-start", "Some input text...");

        // assertNotNull(block);
    }
}

