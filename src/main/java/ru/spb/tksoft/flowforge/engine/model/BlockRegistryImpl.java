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

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.constraints.NotNull;
import ru.spb.tksoft.common.exceptions.ConfigurationMismatchException;
import ru.spb.tksoft.common.exceptions.NullArgumentException;
import ru.spb.tksoft.flowforge.engine.contract.BlockRegistry;
import ru.spb.tksoft.flowforge.sdk.contract.Block;
import ru.spb.tksoft.flowforge.sdk.contract.BlockBuilderService;
import ru.spb.tksoft.utils.log.LogEx;
import ru.spb.tksoft.utils.log.LogFx;

/**
 * Block registry implementation.
 * 
 * This implementation expects that main application runs in a classpath mode - classic Java Spring
 * Boot application, not in a module mode, which is not existing in Spring Boot yet: Spring Boot
 * 3.x.x. or 4.x.x.
 *
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
public class BlockRegistryImpl implements BlockRegistry {

    private static final Logger log = LoggerFactory.getLogger(BlockRegistryImpl.class);

    private static final String JAR_FILE_EXTENSION = ".jar";

    /**
     * Acceptable engine versions from {@link BlockBuilderService#getExpectedEngineVersion()}.
     */
    private final Set<String> acceptableEngineVersions;

    /**
     * Constructor.
     *
     * @param acceptableEngineVersions - the set of acceptable engine versions from
     *        {@link BlockBuilderService#getExpectedEngineVersion()}.
     */
    public BlockRegistryImpl(final Set<String> acceptableEngineVersions) {

        if (Objects.isNull(acceptableEngineVersions) || acceptableEngineVersions.isEmpty()) {
            throw new ConfigurationMismatchException(
                    "acceptableEngineVersions must be non-null and non-empty");
        }

        this.acceptableEngineVersions = Set.copyOf(acceptableEngineVersions);
    }

    /**
     * Immutable holder for registry state. Used for atomic state replacement.
     *
     * @param services - the map of block builder services by block type id.
     * @param classLoaders - the list of module ClassLoaders.
     */
    private record RegistryState(
            Map<String, BlockBuilderService> services,
            List<URLClassLoader> classLoaders) {

        static RegistryState empty() {
            return new RegistryState(Map.of(), List.of());
        }
    }

    /**
     * Current registry state. AtomicReference ensures thread-safe atomic replacement of the entire
     * state during reload operations.
     */
    private final AtomicReference<RegistryState> state =
            new AtomicReference<>(RegistryState.empty());

    /**
     * Result of loading block builder services from a module directory.
     *
     * @param services - the map of loaded block builder services by block type id.
     * @param classLoader - the URLClassLoader used to load the services.
     */
    protected record ModuleLoadResult(
            Map<String, BlockBuilderService> services,
            URLClassLoader classLoader) {
    }

    /**
     * Load block builder services from each subdirectory of the top level modules directory.
     *
     * @param topLevelModulesDirectoryPath - the path to the top level modules directory.
     * @param removeDuplicateDependencies - true if duplicate dependencies should be removed from
     *        the modules directories.
     */
    @Override
    public void loadBlockBuilderServices(final Path topLevelModulesDirectoryPath,
            final boolean removeDuplicateDependencies)
            throws IOException {

        // Check if path is exists and is a directory.
        validateDirectory(topLevelModulesDirectoryPath);

        // Traverse the directory. The structure of the directory is:
        // CHECKSTYLE:OFF
        // @formatter:off
        // - modules/
        //   - block-type-a/
        //     - block-type-a-impl.jar
        //     - block-type-a-dep-n.jar
        //   - block-type-b/
        //     - block-type-b-impl.jar
        //     - block-type-b-dep-n.jar
        //   - ...
        // where:
        // - block-type-X-impl.jar - module containing Block implementation(s) class(es), which also provides
        //   BlockBuilderService implementation(s) for the block type.
        // - block-type-X-dep-n.jar - dependencies for the block implementation(s).
        // @formatter:on
        // CHECKSTYLE:ON

        // Load all services into new collections first.
        Map<String, BlockBuilderService> newServices = new ConcurrentHashMap<>();
        List<URLClassLoader> newClassLoaders = new ArrayList<>();

        try (Stream<Path> paths = Files.list(topLevelModulesDirectoryPath)) {
            var moduleDirectoryPaths = paths.filter(Files::isDirectory).toList();

            for (Path moduleDirectoryPath : moduleDirectoryPaths) {
                ModuleLoadResult result =
                        loadBlockBuilderServicesFromModuleDirectory(moduleDirectoryPath,
                                acceptableEngineVersions,
                                removeDuplicateDependencies);
                newServices.putAll(result.services());
                newClassLoaders.add(result.classLoader());
            }
        }

        // Atomic state replacement - getAndSet returns old state for cleanup.
        RegistryState oldState = state.getAndSet(
                new RegistryState(Map.copyOf(newServices), List.copyOf(newClassLoaders)));

        // Close old ClassLoaders after replacement to prevent resource leaks.
        closeClassLoaders(oldState.classLoaders());

        LogFx.info(log, "{}: Loaded {} BlockBuilderService(s) total",
                LogEx.me(), newServices.size());
    }

    /**
     * Close the given ClassLoaders.
     *
     * @param classLoaders - the list of ClassLoaders to close.
     */
    private static void closeClassLoaders(final List<URLClassLoader> classLoaders) {

        for (URLClassLoader classLoader : classLoaders) {
            try {
                classLoader.close();
            } catch (IOException e) {
                LogFx.warn(log, "{}: Failed to close module ClassLoader: {}",
                        LogEx.me(), e);
            }
        }
    }

    /**
     * Load block builder services from a single module directory.
     *
     * @param moduleDirectoryPath - the path to the module directory.
     * @param acceptableEngineVersions - the set of acceptable engine versions from
     *        {@link BlockBuilderService#getExpectedEngineVersion()}.
     * @param removeDuplicateDependencies - true if duplicate dependencies should be removed from
     *        the module directory.
     * @return the ModuleLoadResult containing loaded services and the ClassLoader.
     * @throws IOException - if an I/O error occurs.
     * @throws ConfigurationMismatchException - if the module is not compatible with the given root
     */
    protected static ModuleLoadResult loadBlockBuilderServicesFromModuleDirectory(
            final Path moduleDirectoryPath,
            final Set<String> acceptableEngineVersions,
            final boolean removeDuplicateDependencies)
            throws IOException {

        validateDirectory(moduleDirectoryPath);

        // Deal with duplicate dependencies between module directory and application classpath.
        // Do it before collecting JAR files.
        dealWithDuplicateDependencies(moduleDirectoryPath, removeDuplicateDependencies);

        // Collect all JAR files from the module directory.
        // Do it after dealing with duplicate dependencies to avoid duplicate JAR files.
        URL[] jarUrls;
        try (Stream<Path> pathStream = Files.list(moduleDirectoryPath)) {
            jarUrls = pathStream
                    .filter(p -> p.toString().endsWith(JAR_FILE_EXTENSION))
                    .map(p -> {
                        try {
                            return p.toUri().toURL();
                        } catch (MalformedURLException e) {
                            throw new ConfigurationMismatchException(
                                    "Failed to convert path to URL: " + p, e);
                        }
                    })
                    .toArray(URL[]::new);
        }

        if (jarUrls.length == 0) {
            throw new ConfigurationMismatchException(
                    "No JAR files found in the module directory: "
                            + moduleDirectoryPath);
        }

        LogFx.info(log, "{}: Found {} JAR file(s) in {}",
                LogEx.me(), jarUrls.length, moduleDirectoryPath);


        // Create URLClassLoader with parent = ClassLoader of the application.
        // This allows plugins to use classes from the application classpath.
        URLClassLoader moduleClassLoader =
                new URLClassLoader(jarUrls, BlockRegistryImpl.class.getClassLoader());

        // Load block builder services through ServiceLoader with specified ClassLoader.
        ServiceLoader<BlockBuilderService> loader =
                ServiceLoader.load(BlockBuilderService.class, moduleClassLoader);

        Map<String, BlockBuilderService> loadedServices = new ConcurrentHashMap<>();
        for (BlockBuilderService service : loader) {
            if (!isCompatibleEngineVersion(service.getExpectedEngineVersion(),
                    acceptableEngineVersions)) {
                throw new ConfigurationMismatchException(
                        "BlockBuilderService " + service.getClass().getName()
                                + " is not compatible with the acceptable engine versions: " +
                                acceptableEngineVersions);
            }

            List<String> supportedBlockTypeIds = service.getSupportedBlockTypeIds();
            for (String blockTypeId : supportedBlockTypeIds) {
                loadedServices.put(blockTypeId, service);
                LogFx.info(log, "{}: Loaded BlockBuilderService: {}",
                        LogEx.me(), blockTypeId);
            }

        }
        return new ModuleLoadResult(loadedServices, moduleClassLoader);
    }

    /**
     * Deal with duplicate dependencies between module directory and application classpath. Logs
     * warnings for any duplicates found.
     * 
     * @param moduleDirectoryPath - the path to the module directory.
     * @param removeDuplicateDependencies - true if duplicate dependencies should be removed from
     *        module directory.
     */
    private static void dealWithDuplicateDependencies(final Path moduleDirectoryPath,
            final boolean removeDuplicateDependencies) {

        // Get artifact IDs from module directory.
        List<String> moduleJars;
        try (Stream<Path> pathStream = Files.list(moduleDirectoryPath)) {
            moduleJars = pathStream
                    .filter(p -> p.toString().endsWith(JAR_FILE_EXTENSION))
                    .map(p -> p.getFileName().toString())
                    .filter(jarFileName -> !jarFileName.isBlank())
                    .toList();
        } catch (IOException e) {
            LogFx.warn(log, "{}: Failed to list module directory: {}",
                    LogEx.me(), moduleDirectoryPath);
            throw new ConfigurationMismatchException(
                    "Failed to list module directory: " + moduleDirectoryPath);
        }

        // Get artifact IDs from application classpath.
        Set<String> classpathJars = getClasspathJars();

        // Find duplicate artifact IDs.
        List<String> duplicates = moduleJars.stream()
                .filter(classpathJars::contains)
                .toList();

        if (!duplicates.isEmpty()) {
            LogFx.warn(log, "{}: DUPLICATE DEPENDENCIES DETECTED: {}",
                    LogEx.me(), duplicates);

            if (removeDuplicateDependencies) {
                LogFx.info(log, "{}: Removing duplicate artifact IDs from module directory",
                        LogEx.me());

                // Remove duplicate artifact IDs from module directory.
                for (String duplicate : duplicates) {
                    Path duplicatePath = moduleDirectoryPath.resolve(duplicate);
                    try {
                        Files.deleteIfExists(duplicatePath);
                    } catch (IOException e) {
                        LogFx.warn(log, "{}: Failed to remove duplicate JAR file: {}",
                                LogEx.me(), duplicatePath);
                        throw new ConfigurationMismatchException(
                                "Failed to remove duplicate JAR file: " + duplicatePath, e);
                    }
                }
            }
        }
    }

    /**
     * Get set of JAR files from application classpath.
     * 
     * @return set of JAR files.
     */
    @NotNull
    private static Set<String> getClasspathJars() {

        Set<String> jars = new HashSet<>();

        String classpath = System.getProperty("java.class.path");
        if (Objects.isNull(classpath) || classpath.isBlank()) {
            return jars;
        }

        for (String entry : classpath.split(File.pathSeparator)) {
            Path path = Path.of(entry);
            String fileName = path.getFileName().toString();
            if (Objects.nonNull(fileName) && fileName.endsWith(JAR_FILE_EXTENSION)
                    && !fileName.isBlank()) {
                jars.add(fileName);
            }
        }

        return jars;
    }

    /**
     * Check if the engine version is compatible.
     *
     * @param engineVersion - the engine version.
     * @param compatibleEngineVersions - the set of compatible engine versions.
     * @return true if the engine version is compatible.
     */
    private static boolean isCompatibleEngineVersion(final String engineVersion,
            final Set<String> compatibleEngineVersions) {

        return Objects.nonNull(engineVersion) && !engineVersion.isBlank() &&
                Objects.nonNull(compatibleEngineVersions) && !compatibleEngineVersions.isEmpty()
                && compatibleEngineVersions.contains(engineVersion);
    }

    /**
     * Validate that the path is not null, exists and is a directory.
     *
     * @param path - the path to validate.
     * @param name - the name of the path.
     */
    private static void validateDirectory(final Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new ConfigurationMismatchException(
                    path + " must exist and be a directory");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Block createBlock(final String blockTypeId, Object... ctorArgs) {

        if (Objects.isNull(blockTypeId) || blockTypeId.isBlank()) {
            throw new NullArgumentException(
                    "blockTypeId must not be null or blank");
        }

        BlockBuilderService service = state.get().services().get(blockTypeId);
        if (Objects.isNull(service)) {
            throw new IllegalArgumentException(
                    "BlockBuilderService for block type id " + blockTypeId + " not found");
        }

        return service.buildBlock(blockTypeId, ctorArgs);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {

        // Atomic state replacement with empty state, returns old state for cleanup.
        RegistryState oldState = state.getAndSet(RegistryState.empty());

        // Close old ClassLoaders after replacement.
        closeClassLoaders(oldState.classLoaders());

        LogFx.info(log, "{}: BlockRegistry closed, all resources released", LogEx.me());
    }
}
