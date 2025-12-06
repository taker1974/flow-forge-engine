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

import org.assertj.core.api.Assertions;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import ru.spb.tksoft.common.exceptions.NullArgumentException;
import ru.spb.tksoft.flowforge.engine.model.InstanceParameter;

/**
 * Tests for InstanceParameter.
 *
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
class InstanceParameterTest {

    @Test
    void testConstructor() {
        InstanceParameter parameter = new InstanceParameter("block1", "value1");

        Assertions.assertThat(parameter.getInternalBlockId()).isEqualTo("block1");
        Assertions.assertThat(parameter.getParameterValue()).isEqualTo("value1");
    }

    @Test
    void testConstructorWithNullInternalBlockId() {
        assertThatThrownBy(() -> new InstanceParameter(null, "value1"))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("internalBlockId must not be null or blank");
    }

    @Test
    void testConstructorWithBlankInternalBlockId() {
        assertThatThrownBy(() -> new InstanceParameter("", "value1"))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("internalBlockId must not be null or blank");

        assertThatThrownBy(() -> new InstanceParameter("   ", "value1"))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("internalBlockId must not be null or blank");
    }

    @Test
    void testConstructorWithNullParameterValue() {
        assertThatThrownBy(() -> new InstanceParameter("block1", null))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("parameterValue must not be null or blank");
    }

    @Test
    void testConstructorWithBlankParameterValue() {
        assertThatThrownBy(() -> new InstanceParameter("block1", ""))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("parameterValue must not be null or blank");

        assertThatThrownBy(() -> new InstanceParameter("block1", "   "))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("parameterValue must not be null or blank");
    }

    @Test
    void testEqualsAndHashCode() {
        InstanceParameter param1 = new InstanceParameter("block1", "value1");
        InstanceParameter param2 = new InstanceParameter("block1", "value1");
        InstanceParameter param3 = new InstanceParameter("block2", "value1");

        Assertions.assertThat(param1).isEqualTo(param2)
                .hasSameHashCodeAs(param2)
                .isNotEqualTo(param3);
    }
}

