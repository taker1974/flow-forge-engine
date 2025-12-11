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

import java.util.HashMap;
import java.util.Map;

public class Dependencies {

    public static Map<String, String> dependencies() {
        Map<String, String> deps = new HashMap<>();

        // Core dependencies - using Java module names, not Maven artifact names
        deps.put("org.slf4j", "2.0.17"); // slf4j-api is an automatic module
        deps.put("lombok", "1.18.42");
        deps.put("jakarta.validation", "3.1.1"); // jakarta.validation-api

        // TKSoft dependencies - using Java module names
        deps.put("ru.spb.tksoft.utils.log", "2.0.5"); // tk-log-utils
        deps.put("ru.spb.tksoft.flowforge.sdk", "2.0.5"); // flow-forge-sdk
        deps.put("ru.spb.tksoft.common.exceptions", "2.0.5"); // tk-common-exceptions

        // Test dependencies - using Java module names
        deps.put("org.assertj.core", "3.27.6"); // assertj-core
        deps.put("org.junit.jupiter.api", "5.13.4"); // junit-jupiter-api
        deps.put("org.mockito", "5.20.0"); // mockito-core
        deps.put("org.mockito.junit.jupiter", "5.20.0"); // mockito-junit-jupiter

        return deps;
    }
}

