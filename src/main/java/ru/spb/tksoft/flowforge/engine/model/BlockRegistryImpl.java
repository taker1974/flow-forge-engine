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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.validation.constraints.NotNull;
import ru.spb.tksoft.common.exceptions.ConfigurationMismatchException;
import ru.spb.tksoft.flowforge.engine.contract.BlockRegistry;
import ru.spb.tksoft.flowforge.sdk.contract.Block;
import ru.spb.tksoft.flowforge.sdk.contract.BlockBuilderService;
import ru.spb.tksoft.utils.log.LogEx;

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
    @NotNull
    private final Set<String> acceptableEngineVersions;

    /**
     * Constructor.
     *
     * @param acceptableEngineVersions - the set of acceptable engine versions from
     *        {@link BlockBuilderService#getExpectedEngineVersion()}.
     */
    public BlockRegistryImpl(final Set<String> acceptableEngineVersions) {

        this.acceptableEngineVersions = acceptableEngineVersions;
    }

    /**
     * Map of block builder services by block type id.
     */
    @NotNull
    private final Map<String/* block type id */, BlockBuilderService> blockBuilderServices =
            new ConcurrentHashMap<>();

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
        if (!Files.exists(topLevelModulesDirectoryPath)
                || !Files.isDirectory(topLevelModulesDirectoryPath)) {
            throw new ConfigurationMismatchException(
                    "topLevelModulesDirectoryPath must exist and be a directory: "
                            + topLevelModulesDirectoryPath);
        }

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

        // Anyway, clear the map of block builder services.
        blockBuilderServices.clear();

        try (Stream<Path> paths = Files.list(topLevelModulesDirectoryPath)) {
            var moduleDirectoryPaths = paths.filter(Files::isDirectory).toList();

            for (Path moduleDirectoryPath : moduleDirectoryPaths) {
                Map<String, BlockBuilderService> loadedBlockBuilderServices =
                        loadBlockBuilderServicesFromModuleDirectory(moduleDirectoryPath,
                                acceptableEngineVersions,
                                removeDuplicateDependencies, getClass());
                blockBuilderServices.putAll(loadedBlockBuilderServices);
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
     * @return the map of loaded block builder services by block type id.
     * @throws IOException - if an I/O error occurs.
     * @throws ConfigurationMismatchException - if the module is not compatible with the given root
     */
    protected static Map<String, BlockBuilderService> loadBlockBuilderServicesFromModuleDirectory(
            final Path moduleDirectoryPath,
            final Set<String> acceptableEngineVersions,
            final boolean removeDuplicateDependencies,
            final Class<? extends BlockRegistryImpl> clazz)
            throws IOException {

        if (!Files.exists(moduleDirectoryPath) || !Files.isDirectory(moduleDirectoryPath)) {
            throw new ConfigurationMismatchException(
                    "Module directory must exist and be a directory: "
                            + moduleDirectoryPath);
        }

        // Collect all JAR files from the module directory.
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
                    .filter(Objects::nonNull)
                    .toArray(URL[]::new);
        }

        if (jarUrls.length <= 0) {
            throw new ConfigurationMismatchException(
                    "No JAR files found in the module directory: "
                            + moduleDirectoryPath);
        }

        LogEx.info(log, LogEx.me(),
                "Found " + jarUrls.length + " JAR file(s) in " + moduleDirectoryPath);

        dealWithDuplicateDependencies(moduleDirectoryPath, removeDuplicateDependencies);

        // Create URLClassLoader with parent = ClassLoader of the application.
        // This allows plugins to use classes from the application classpath.
        URLClassLoader moduleClassLoader =
                new URLClassLoader(jarUrls, clazz.getClassLoader());

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
                LogEx.info(log, LogEx.me(), "Loaded BlockBuilderService", blockTypeId);
            }

        }
        return loadedServices;
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
            LogEx.warn(log, LogEx.me(), "Failed to list module directory", moduleDirectoryPath);
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
            LogEx.warn(log, LogEx.me(),
                    "DUPLICATE DEPENDENCIES DETECTED! The following artifacts exist both in " +
                            "module directory and application classpath: " + duplicates + ". " +
                            "This may cause ClassLoader conflicts. Consider removing them from module directory");

            if (removeDuplicateDependencies) {
                LogEx.info(log, LogEx.me(),
                        "Removing duplicate artifact IDs from module directory");

                // Remove duplicate artifact IDs from module directory.
                for (String duplicate : duplicates) {
                    Path duplicatePath = moduleDirectoryPath.resolve(duplicate);
                    try {
                        Files.deleteIfExists(duplicatePath);
                    } catch (IOException e) {
                        LogEx.warn(log, LogEx.me(), "Failed to remove duplicate JAR file",
                                duplicatePath);
                        throw new ConfigurationMismatchException(
                                "Failed to remove duplicate JAR file: " + duplicatePath);
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
     * {@inheritDoc}
     */
    @Override
    public Block createBlock(final String blockTypeId, Object... ctorArgs) {

        BlockBuilderService service = blockBuilderServices.get(blockTypeId);
        if (Objects.isNull(service)) {
            throw new IllegalArgumentException(
                    "BlockBuilderService for block type id " + blockTypeId + " not found");
        }

        return service.buildBlock(blockTypeId, ctorArgs);
    }
}
