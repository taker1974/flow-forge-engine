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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import ru.spb.tksoft.common.exception.ObjectAlreadyExistsException;
import ru.spb.tksoft.common.exception.NullArgumentException;

/**
 * Instance parameters.
 * 
 * Subclassing is not allowed.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
public final class InstanceParameters {

    /** List of instance parameters. */
    @NotNull
    private final List<InstanceParameter> parameters;

    /**
     * Map of instance parameters by internal block id. Just for speed up access to parameters by
     * internal block id.
     */
    @NotNull
    private final Map<String/* internal block id */, InstanceParameter> parametersMap;

    /**
     * Constructor.
     * 
     * @param parameters - parameters.
     * @throws NullArgumentException - if parameters is null.
     * @throws ObjectAlreadyExistsException - if parameters contain duplicate internal block ids.
     */
    public InstanceParameters(final List<InstanceParameter> parameters) {

        if (parameters == null) {
            throw new NullArgumentException("parameters must not be null");
        }

        parametersMap = new HashMap<>();

        // Check parameters: all good or throw exception.
        for (final InstanceParameter parameter : parameters) {
            if (parameter == null) {
                throw new NullArgumentException("parameter must not be null");
            }

            if (parametersMap.containsKey(parameter.getInternalBlockId())) {
                throw new ObjectAlreadyExistsException(
                        "parameters must not contain duplicate internal block ids");
            }

            parametersMap.put(parameter.getInternalBlockId(), parameter);
        }

        this.parameters = parameters;
    }

    /**
     * Get the parameter by internal block id.
     * 
     * @param internalBlockId - the internal block id.
     * @return the parameter.
     * @throws NullArgumentException - if internalBlockId is null or blank.
     */
    public InstanceParameter getParameter(final String internalBlockId) {

        if (internalBlockId == null || internalBlockId.isBlank()) {
            throw new NullArgumentException("internalBlockId must not be null or blank");
        }

        return parametersMap.get(internalBlockId);
    }
}
