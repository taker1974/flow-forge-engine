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
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Constructor;
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
import ru.spb.tksoft.common.exceptions.ConfigurationMismatchException;
import ru.spb.tksoft.flowforge.engine.exception.InstantiationException;
import ru.spb.tksoft.flowforge.engine.contract.BlockRegistry;
import ru.spb.tksoft.flowforge.sdk.contract.Block;
import ru.spb.tksoft.flowforge.sdk.contract.BlockPlugin;
import ru.spb.tksoft.utils.log.LogEx;

/**
 * Block registry implementation.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
public class BlockRegistryImpl implements BlockRegistry {

    private static final Logger log = LoggerFactory.getLogger(BlockRegistryImpl.class);

    // <editor-fold desc="Versions">

    /** Acceptable engine versions from {@link BlockPlugin#engineVersion}. */
    @NotNull
    private final Set<String> compatibleEngineVersions;

    /** The map of root module names and their actual versions. */
    @NotNull
    private final Map<String/* root module name */, String/* root module version */> rootModuleVersions;

    // </editor-fold>

    /**
     * Constructor.
     * 
     * @param compatibleEngineVersions - the set of compatible engine versions from
     *        {@link BlockPlugin#engineVersion}.
     * @param rootModuleVersions - the map of root module names and their actual versions.
     *        (optional). If not specified, then all module versions are compatible by default.
     */
    public BlockRegistryImpl(final Set<String> compatibleEngineVersions,
            final Map<String, String> rootModuleVersions) {

        this.compatibleEngineVersions = compatibleEngineVersions;
        this.rootModuleVersions = rootModuleVersions;
    }

    // <editor-fold desc="Load classes">

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
        // - modules/
        //   - block-type-a/
        //     - block-type-a-impl.jar
        //     - block-type-a-dep-n.jar
        //   - block-type-b/
        //     - block-type-b-impl.jar
        //     - block-type-b-dep-n.jar
        //   - ...
        // where:
        // - block-type-X-impl.jar - module containing Block implementation class with attribute @BlockPlugin,
        //   and dependencies for the block implementation.
        // CHECKSTYLE:ON
        // @formatter:on
        try (Stream<Path> paths = Files.walk(topLevelModulesDirectoryPath)) {
            var moduleDirectoryPaths = paths.filter(Files::isDirectory).toList();

            for (Path moduleDirectoryPath : moduleDirectoryPaths) {
                loadedBlocks.clear();
                Map<String, LoadedBlock> blocks = loadBlocksFromDirectory(moduleDirectoryPath,
                        compatibleEngineVersions, rootModuleVersions);
                loadedBlocks.putAll(blocks);
            }
        }
    }

    /**
     * Load blocks from a single module directory.
     * 
     * @param moduleDirectoryPath - the path to the module directory.
     * @return the map of loaded blocks by block type id.
     * @throws IOException - if an I/O error occurs.
     */
    protected static Map<String, LoadedBlock> loadBlocksFromDirectory(
            final Path moduleDirectoryPath, final Set<String> compatibleEngineVersions,
            final Map<String, String> rootModuleVersions)
            throws IOException {

        // Finder for modules in the local directory.
        ModuleFinder localFinder = ModuleFinder.of(moduleDirectoryPath);
        ModuleLayer parentLayer = ModuleLayer.boot();

        Set<String> rootModules = localFinder.findAll().stream()
                .map(ref -> ref.descriptor().name())
                .collect(Collectors.toSet());

        // Finder for boot layer (main application).
        ModuleFinder bootFinder = ModuleFinder.ofSystem();

        // Combined finder: first search in boot layer, then in local directory.
        // This allows loading dependencies from the local directory, if they are not in the boot
        // layer.
        ModuleFinder combinedFinder = ModuleFinder.compose(bootFinder, localFinder);

        Configuration configuration = parentLayer.configuration()
                .resolveAndBind(localFinder, combinedFinder, rootModules);

        ModuleLayer layer = parentLayer.defineModulesWithOneLoader(configuration,
                ClassLoader.getSystemClassLoader());

        Map<String/* block type id */, LoadedBlock> blocks = new ConcurrentHashMap<>();

        for (ModuleReference reference : localFinder.findAll()) {
            String moduleName = reference.descriptor().name();
            ClassLoader loader = layer.findLoader(moduleName);

            if (!isModuleCompatible(reference, rootModuleVersions)) {
                throw new ConfigurationMismatchException(
                        "Module " + moduleName
                                + " is not compatible with the given root module versions");
            }

            try (ModuleReader reader = reference.open()) {
                reader.list()
                        .filter(entry -> entry.endsWith(".class"))
                        .filter(entry -> !entry.equals("module-info.class"))
                        .forEach(entry -> processClassEntry(entry, loader, blocks,
                                compatibleEngineVersions));
            }
        }

        return blocks;
    }

    /**
     * Check if the module is compatible with the given root module versions.
     * 
     * @param reference - the module reference.
     * @param rootModuleVersions - the map of root module names and their actual versions.
     * @return true if the module is compatible with the given root module versions.
     */
    private static boolean isModuleCompatible(final ModuleReference reference,
            final Map<String, String> rootModuleVersions) {

        Map<String, String> moduleDependencies = getModuleDependencies(reference);
        for (Map.Entry<String, String> moduleDependencyEntry : moduleDependencies.entrySet()) {
            if (!isModuleDependencyVersionCompatible(moduleDependencyEntry, rootModuleVersions)) {
                return false;
            }
        }
        return true;
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
     * Check if the module dependency version is compatible with the given root module versions.
     * 
     * @param moduleDependencyEntry - the module dependency entry (module name and version).
     * @param rootModuleVersions - the map of root module names and their actual versions
     *        (optional).
     * @return true if root module versions are not specified, then all module versions are
     *         compatible by default, or the module version is compatible with the given root module
     *         versions.
     */
    private static boolean isModuleDependencyVersionCompatible(
            final Map.Entry<String, String> moduleDependencyEntry,
            final Map<String, String> rootModuleVersions) {

        // If root module versions are not specified, then all module versions are compatible by
        // default.
        if (Objects.isNull(rootModuleVersions)) {
            return true;
        }

        if (Objects.isNull(moduleDependencyEntry)) {
            return false;
        }

        final String moduleName = moduleDependencyEntry.getKey();
        if (Objects.isNull(moduleName) || moduleName.isBlank()) {
            return false;
        }

        final String moduleVersion = moduleDependencyEntry.getValue();
        if (Objects.isNull(moduleVersion) || moduleVersion.isBlank()) {
            return false;
        }

        return rootModuleVersions.containsKey(moduleName)
                && rootModuleVersions.get(moduleName).equals(moduleVersion);
    }

    /**
     * Get module dependencies.
     * 
     * @param reference - the module reference.
     * @return the map of dependency module names and their versions.
     */
    private static Map<String, String> getModuleDependencies(final ModuleReference reference) {

        return reference.descriptor().requires().stream()
                .collect(Collectors.toMap(
                        ModuleDescriptor.Requires::name,
                        requires -> requires.compiledVersion()
                                .map(ModuleDescriptor.Version::toString)
                                .orElse("")));
    }

    /**
     * Process a single class entry from module reader.
     * 
     * @param entry - the class entry path.
     * @param loader - the class loader to use.
     * @param blocks - the map to add loaded blocks to.
     */
    private static void processClassEntry(final String entry, final ClassLoader loader,
            final Map<String, LoadedBlock> blocks, final Set<String> compatibleEngineVersions) {

        String className = entry
                .replace('/', '.')
                .replace('\\', '.')
                .replaceAll("\\.class$", "");
        try {
            Class<?> candidate = Class.forName(className, false, loader);
            if (isValidBlockClass(candidate)) {
                BlockPlugin annotation = candidate.getAnnotation(BlockPlugin.class);

                String engineVersion = annotation.engineVersion();
                if (!isCompatibleEngineVersion(engineVersion, compatibleEngineVersions)) {
                    throw new ConfigurationMismatchException(
                            "engineVersion in @BlockPlugin annotation is not compatible with the engine version");
                }

                String blockTypeId = annotation.blockTypeId();
                if (blockTypeId == null || blockTypeId.isBlank()) {
                    throw new ConfigurationMismatchException(
                            "blockTypeId in @BlockPlugin annotation must not be null or blank");
                }

                Class<? extends Block> blockType = candidate.asSubclass(Block.class);
                blocks.put(blockTypeId, new LoadedBlock(blockType, annotation));
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            LogEx.trace(log, LogEx.me(), "Class not found: {}", className);
        } catch (ConfigurationMismatchException e) {
            LogEx.error(log, LogEx.me(), "Configuration mismatch: {}",
                    e.getMessage(), e);
        }
    }

    /**
     * Check if a class is a valid block class.
     * 
     * @param candidate - the class to check.
     * @return true if the class is a valid block class.
     */
    private static boolean isValidBlockClass(Class<?> candidate) {

        return Block.class.isAssignableFrom(candidate)
                && candidate.isAnnotationPresent(BlockPlugin.class)
                && Modifier.isPublic(candidate.getModifiers());
    }

    // </editor-fold>

    // <editor-fold desc="Create instances">

    /**
     * {@inheritDoc}
     */
    @Override
    public Block createBlock(final String blockTypeId, Object... ctorArgs) {

        Class<? extends Block> blockType = loadedBlocks.get(blockTypeId).type();
        for (Constructor<?> ctor : blockType.getConstructors()) {

            if (!areCompatible(ctor, ctorArgs)) {
                continue;
            }

            try {
                return (Block) ctor.newInstance(ctorArgs);
            } catch (Exception e) {
                throw new InstantiationException("Failed to instantiate " + blockType.getName(), e);
            }
        }
        throw new IllegalArgumentException(
                "No matching public constructor found for " + blockType.getName());
    }

    /**
     * Check if the arguments are compatible with the parameter types.
     * 
     * @param ctor - the constructor to check.
     * @param tryingArgs - the arguments to check.
     * @return true if the arguments are compatible with the parameter types.
     */
    private static boolean areCompatible(
            final Constructor<?> ctor, final Object[] tryingArgs) {

        Class<?>[] parameterTypes = ctor.getParameterTypes();

        // First check: parameter count must match
        if (parameterTypes.length != tryingArgs.length) {
            return false;
        }

        // Second check: each argument must be compatible with its parameter type
        for (int i = 0; i < parameterTypes.length; i++) {
            Class<?> paramType = parameterTypes[i];
            Object arg = tryingArgs[i];

            if (!isCompatibleType(paramType, arg)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Check if an argument is compatible with a parameter type. Supports: - Primitive types and
     * their wrappers (int/Integer, boolean/Boolean, etc.) - Interfaces (if argument implements the
     * interface) - Inheritance (if argument is a subclass of parameter type) - Null values (if
     * parameter is not primitive)
     * 
     * @param paramType - the parameter type.
     * @param arg - the argument value.
     * @return true if the argument is compatible with the parameter type.
     */
    private static boolean isCompatibleType(final Class<?> paramType, final Object arg) {

        // Handle null arguments
        if (arg == null) {
            // Null is compatible only with non-primitive types
            return !paramType.isPrimitive();
        }

        Class<?> argType = arg.getClass();

        // Exact type match
        if (paramType.equals(argType)) {
            return true;
        }

        // Handle primitive types and their wrappers
        if (paramType.isPrimitive()) {
            return isPrimitiveCompatible(paramType, argType);
        }

        if (argType.isPrimitive()) {
            // If argument is primitive but parameter is wrapper
            return isPrimitiveCompatible(argType, paramType);
        }

        // Handle inheritance and interfaces: if parameter is superclass/superinterface of argument
        return paramType.isAssignableFrom(argType);
    }

    /**
     * Check if primitive type and wrapper type are compatible.
     * 
     * @param primitiveType - the primitive type.
     * @param wrapperType - the wrapper type (or vice versa).
     * @return true if types are compatible.
     */
    private static boolean isPrimitiveCompatible(
            final Class<?> primitiveType, final Class<?> wrapperType) {

        // Map of primitive types to their wrapper classes
        if (primitiveType == boolean.class) {
            return wrapperType == Boolean.class;
        }
        if (primitiveType == byte.class) {
            return wrapperType == Byte.class;
        }
        if (primitiveType == char.class) {
            return wrapperType == Character.class;
        }
        if (primitiveType == short.class) {
            return wrapperType == Short.class;
        }
        if (primitiveType == int.class) {
            return wrapperType == Integer.class;
        }
        if (primitiveType == long.class) {
            return wrapperType == Long.class;
        }
        if (primitiveType == float.class) {
            return wrapperType == Float.class;
        }
        if (primitiveType == double.class) {
            return wrapperType == Double.class;
        }
        if (primitiveType == void.class) {
            return wrapperType == Void.class;
        }

        return false;
    }

    // </editor-fold>
}
