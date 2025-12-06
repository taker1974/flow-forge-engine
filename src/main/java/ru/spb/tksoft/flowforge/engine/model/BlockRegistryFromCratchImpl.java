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

package ru.spb.tksoft.flowforge.engine.model;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import ru.spb.tksoft.common.exceptions.ConfigurationMismatchException;
import ru.spb.tksoft.common.exceptions.NotImplementedException;
import ru.spb.tksoft.flowforge.engine.contract.BlockRegistry;
import ru.spb.tksoft.flowforge.sdk.contract.Block;
import ru.spb.tksoft.flowforge.sdk.contract.BlockPlugin;
import ru.spb.tksoft.utils.log.LogEx;

/**
 * Block registry implementation built from scratch.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
@RequiredArgsConstructor
public class BlockRegistryFromCratchImpl implements BlockRegistry {

    private static final Logger log = LoggerFactory.getLogger(BlockRegistryFromCratchImpl.class);

    /** Acceptable engine versions from {@link BlockPlugin#engineVersion}. */
    @NotNull
    private final Set<String> compatibleEngineVersions;

    /** The map of root module names and their actual versions. */
    @NotNull
    private final Map<String/* root module name */, String/* root module version */> rootModuleVersions;

    /**
     * Loaded block record.
     * 
     * @param type - the block type.
     * @param metadata - the block plugin metadata.
     */
    public record LoadedBlock(Class<? extends Block> type, BlockPlugin metadata) {
    }

    /**
     * Map of loaded blocks by block type id.
     */
    @NotNull
    private final Map<String/* block type id */, LoadedBlock> loadedBlocks =
            new ConcurrentHashMap<>();

    /**
     * Load blocks from each subdirectory of the top level modules directory.
     * 
     * @param topLevelModulesDirectoryPath - the path to the top level modules directory.
     */
    @Override
    public void loadBlocks(final Path topLevelModulesDirectoryPath) throws IOException {

        // Check if path is a directory.
        if (!Files.isDirectory(topLevelModulesDirectoryPath)) {
            throw new ConfigurationMismatchException(
                    "topLevelModulesDirectoryPath must be a directory");
        }

        // Traverse the directory. The structure of the directory is:
        // CHECKSTYLE:OFF
        // @formatter:off
        // - modules/ <-- top level modules directory
        //   - block-type-a/ <- should traverse subdirectories and load blocks from them
        //     - block-type-a-impl.ffb
        //     - block-type-a-dep-n.jar
        //   - block-type-b/
        //     - block-type-b-impl.ffb
        //     - block-type-b-dep-n.jar
        //   - ...
        // where:
        // - block-type-X-impl.ffb - module containing Block implementation class with attribute @BlockPlugin,
        //   and dependencies for the block implementation.
        // @formatter:on
        // CHECKSTYLE:ON

        // Iterate through all subdirectories of the top level modules directory.
        // For each subdirectory, load all blocks from the subdirectory.
        try (Stream<Path> paths = Files.list(topLevelModulesDirectoryPath)) {

            var moduleDirectoryPaths = paths.filter(Files::isDirectory).toList();
            for (Path moduleDirectoryPath : moduleDirectoryPaths) {

                // Finder for boot layer (main application).
                ModuleFinder bootFinder = ModuleFinder.ofSystem();

                // Finder for modules in the local directory.
                ModuleFinder localFinder = ModuleFinder.of(moduleDirectoryPath);

                // Combined finder: first search in boot layer, then in local directory.
                // This allows loading dependencies from the local directory, if they are not in the
                // boot layer.
                ModuleFinder combinedFinder = ModuleFinder.compose(bootFinder, localFinder);

                // Get all module names in the local directory.
                Set<String> rootModules = localFinder.findAll().stream()
                        .map(ModuleReference::descriptor)
                        .map(ModuleDescriptor::name)
                        .collect(Collectors.toSet());

                // Get parent layer (boot layer).
                ModuleLayer parentLayer = ModuleLayer.boot();

                // Resolve dependencies (load all modules that are found).
                // See Configuration.resolveAndBind() method documentation.
                Configuration config = parentLayer.configuration()
                        .resolveAndBind(/* before */localFinder, /* after */combinedFinder,
                                /* roots */rootModules);

                // Try to get the shared ClassLoader for all modules in this layer.
                // Since we use defineModulesWithOneLoader, all modules share the same ClassLoader.
                ClassLoader parentLoader = ClassLoader.getSystemClassLoader();
                ModuleLayer layer = parentLayer.defineModulesWithOneLoader(config, parentLoader);
                ClassLoader sharedClassLoader = null;
                if (!rootModules.isEmpty()) {
                    // Get ClassLoader from any module - they all share the same one
                    sharedClassLoader = layer.findLoader(rootModules.iterator().next());
                }
                // Fallback to system class loader if no modules found
                if (Objects.isNull(sharedClassLoader)) {
                    sharedClassLoader = ClassLoader.getSystemClassLoader();
                }

                loadedBlocks.clear();
                for (ModuleReference reference : localFinder.findAll()) {
                    processModule(reference, sharedClassLoader, loadedBlocks);
                }
            }
        }
    }

    /**
     * Process a module.
     * 
     * @param reference - the module reference.
     * @param classLoader - the shared class loader for all modules in this layer.
     * @param loadedBlocks - the map to add loaded blocks to.
     * @throws IOException - if an I/O error occurs.
     */
    private static void processModule(final ModuleReference reference,
            final ClassLoader classLoader,
            final Map<String, LoadedBlock> loadedBlocks) throws IOException {

        try (ModuleReader moduleReader = reference.open()) {
            moduleReader.list()
                    .filter(resource -> resource.endsWith(".class"))
                    .filter(resource -> !resource.equals("module-info.class"))
                    .forEach(resource -> {
                        try {
                            // Convert resource to class name.
                            String className = resource
                                    .substring(0, resource.length() - ".class".length())
                                    .replace('/', '.')
                                    .replace('\\', '.');

                            // Load class through module ClassLoader.
                            Class<?> clazz = classLoader.loadClass(className);
                            processClass(clazz, classLoader, loadedBlocks);

                        } catch (ClassNotFoundException | NoClassDefFoundError e) {
                            LogEx.warn(log, LogEx.me(), LogEx.EXCEPTION_THROWN,
                                    "Failed to load class from resource", resource, e);
                        }
                    });
        }

    }

    /**
     * Process a class.
     * 
     * @param clazz - the class to process.
     * @param loadedBlocks - the map to add loaded blocks to.
     */
    private static void processClass(final Class<?> clazz,
            final ClassLoader classLoader,
            final Map<String, LoadedBlock> loadedBlocks) {

        try {
            // Load Block interface through the same ClassLoader as the class.
            // This ensures isAssignableFrom works correctly in module system.
            Class<?> blockInterface = Class.forName(Block.class.getName(), false, classLoader);

            // Load BlockPlugin annotation class through the same ClassLoader.
            Class<? extends Annotation> blockPluginAnnotationClass =
                    (Class<? extends Annotation>) Class.forName(
                            BlockPlugin.class.getName(), false, classLoader);

            // Check if the class implements the Block interface.
            // The class implements Block (or itself is Block) but
            // we need to check that it is not an interface and not
            // an abstract class.
            if (blockInterface.isAssignableFrom(clazz) &&
                    !clazz.isInterface() &&
                    !Modifier.isAbstract(clazz.getModifiers())) {
                // This is a concrete class that implements Block.
                Class<? extends Block> blockClass = (Class<? extends Block>) clazz;

                // Get the BlockPlugin annotation using getAnnotation.
                // blockPluginClass is loaded through the same ClassLoader as clazz.
                Annotation annotation = clazz.getAnnotation(blockPluginAnnotationClass);

                if (Objects.nonNull(annotation)) {
                    // Access annotation values through reflection to avoid ClassLoader issues.
                    try {
                        String blockTypeId = (String) blockPluginAnnotationClass
                                .getMethod("blockTypeId")
                                .invoke(annotation);

                        String engineVersion = (String) blockPluginAnnotationClass
                                .getMethod("engineVersion")
                                .invoke(annotation);

                        String blockTypeDescription = (String) blockPluginAnnotationClass
                                .getMethod("blockTypeDescription")
                                .invoke(annotation);

                        // Create a BlockPlugin wrapper that implements the interface
                        // using values obtained through reflection.
                        BlockPlugin blockPlugin = createBlockPluginWrapper(
                                blockTypeId, engineVersion, blockTypeDescription);

                        // Save the block type and BlockPlugin metadata.
                        loadedBlocks.put(blockTypeId,
                                new LoadedBlock(blockClass, blockPlugin));

                    } catch (Exception e) {
                        LogEx.warn(log, LogEx.me(), LogEx.EXCEPTION_THROWN,
                                "Failed to get blockTypeId from BlockPlugin annotation",
                                clazz.getName(), e);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            LogEx.warn(log, LogEx.me(), LogEx.EXCEPTION_THROWN,
                    "Failed to load Block interface through module ClassLoader",
                    Block.class.getName(), e);
        } catch (ClassCastException e) {
            LogEx.warn(log, LogEx.me(), LogEx.EXCEPTION_THROWN,
                    "Class implements Block but cannot be cast", clazz.getName(), e);
        }
    }

    /**
     * Create a BlockPlugin wrapper from annotation values. This avoids ClassLoader issues when
     * storing annotations from different ClassLoaders.
     * 
     * @param blockTypeId - the block type id.
     * @param engineVersion - the engine version.
     * @param blockTypeDescription - the block type description.
     * @return a BlockPlugin implementation.
     */
    private static BlockPlugin createBlockPluginWrapper(final String blockTypeId,
            final String engineVersion, final String blockTypeDescription) {

        return new BlockPlugin() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return BlockPlugin.class;
            }

            @Override
            public String blockTypeId() {
                return blockTypeId;
            }

            @Override
            public String engineVersion() {
                return engineVersion;
            }

            @Override
            public String blockTypeDescription() {
                return blockTypeDescription;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) {
                    return true;
                }
                if (!(obj instanceof BlockPlugin)) {
                    return false;
                }
                BlockPlugin other = (BlockPlugin) obj;
                return Objects.equals(blockTypeId, other.blockTypeId())
                        && Objects.equals(engineVersion, other.engineVersion());
            }

            @Override
            public int hashCode() {
                return Objects.hash(blockTypeId, engineVersion);
            }

            @Override
            public String toString() {
                return "@" + BlockPlugin.class.getName() + "(blockTypeId=" + blockTypeId
                        + ", engineVersion=" + engineVersion + ")";
            }
        };
    }

    @Override
    public Block createBlock(final String blockTypeId, Object... ctorArgs) {
        throw new NotImplementedException("createBlock is not implemented");
    }
}
