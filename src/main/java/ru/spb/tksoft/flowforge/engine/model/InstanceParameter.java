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

import lombok.Value;
import ru.spb.tksoft.common.exception.NullArgumentException;

/**
 * InstanceParameter class.
 * 
 * Subclassing is not allowed.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
@Value
public final class InstanceParameter {

    private final String internalBlockId;

    /**
     * All parameters must be stored in the parameter value (as a string or as a JSON object etc.).
     */
    private final String parameterValue;

    /**
     * Constructor.
     * 
     * @param internalBlockId - the internal block id.
     * @param parameterValue - the parameter value. All parameters must be stored in the parameter
     *        value (as a string or as a JSON object etc.).
     * @throws NullArgumentException - if internalBlockId or parameterValue is null or blank.
     */
    public InstanceParameter(final String internalBlockId, final String parameterValue) {

        if (internalBlockId == null || internalBlockId.isBlank()) {
            throw new NullArgumentException("internalBlockId must not be null or blank");
        }

        if (parameterValue == null || parameterValue.isBlank()) {
            throw new NullArgumentException("parameterValue must not be null or blank");
        }

        this.internalBlockId = internalBlockId;
        this.parameterValue = parameterValue;
    }
}
