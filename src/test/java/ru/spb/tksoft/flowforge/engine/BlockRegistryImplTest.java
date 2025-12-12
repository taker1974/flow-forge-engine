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
import ru.spb.tksoft.flowforge.engine.model.BlockRegistryImpl;
import ru.spb.tksoft.flowforge.sdk.contract.Block;

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
     * Load block builder services from the specified path.
     * 
     * NOTE: This test depends on the presence of the blocks subdirectory in the data home directory
     * ~/.local/share/flow-forge-backend-instance/blocks with subfolders like "example-block-01"
     * with jar files inside.
     *
     * @param path - the path to load blocks from.
     */
    @Test
    void loadBlockBuilderServices() {

        // Load blocks from the specified path.
        String dataHome = System.getenv("XDG_DATA_HOME");
        if (Objects.isNull(dataHome) || dataHome.isEmpty()) {
            dataHome = System.getProperty("user.home") + "/.local/share";
        }

        BlockRegistry blockRegistry = new BlockRegistryImpl(Set.of("2.0.5"));

        final Path blocksPath = Paths.get(dataHome,
                applicationName, blocksSubdirectory);

        try {
            blockRegistry.loadBlockBuilderServices(blocksPath, true);
            // If we get here, blocks were loaded successfully
            assertTrue(true, "Blocks loaded successfully");
        } catch (Exception e) {
            Assertions.fail("Failed to load blocks from path: " + blocksPath, e);
        }

        // Create a block.
        // Note: first argument is the block type id!
        String blockTypeId1 = "example-block-01";
        Block block1 = blockRegistry.createBlock(blockTypeId1,
                "block-1", "Some input text for block 1...");

        String blockTypeId2 = "example-block-02";
        Block block2 = blockRegistry.createBlock(blockTypeId2,
                "block-2", "Some input text for block 2...");

        Assertions.assertNotNull(block1);
        Assertions.assertNotNull(block2);

        blockRegistry.close();
    }
}

