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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import ru.spb.tksoft.common.exception.NullArgumentException;
import ru.spb.tksoft.common.exception.ObjectAlreadyExistsException;
import ru.spb.tksoft.flowforge.engine.model.InstanceParameters;
import ru.spb.tksoft.flowforge.engine.model.InstanceParameter;

/**
 * Tests for InstanceParameters.
 *
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
class InstanceParametersTest {

    @Test
    void testConstructorWithEmptyList() {
        InstanceParameters parameters = new InstanceParameters(Collections.emptyList());

        assertThat(parameters.getParameter("block1")).isNull();
    }

    @Test
    void testConstructorWithNullList() {
        assertThatThrownBy(() -> new InstanceParameters(null))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("parameters must not be null");
    }

    @Test
    void testConstructorWithNullParameter() {
        List<InstanceParameter> params = Arrays.asList(
                new InstanceParameter("block1", "value1"),
                null,
                new InstanceParameter("block2", "value2"));

        assertThatThrownBy(() -> new InstanceParameters(params))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("parameter must not be null");
    }

    @Test
    void testConstructorWithDuplicateInternalBlockIds() {
        List<InstanceParameter> params = Arrays.asList(
                new InstanceParameter("block1", "value1"),
                new InstanceParameter("block1", "value2"));

        assertThatThrownBy(() -> new InstanceParameters(params))
                .isInstanceOf(ObjectAlreadyExistsException.class)
                .hasMessageContaining("parameters must not contain duplicate internal block ids");
    }

    @Test
    void testGetParameter() {
        InstanceParameter param1 = new InstanceParameter("block1", "value1");
        InstanceParameter param2 = new InstanceParameter("block2", "value2");
        List<InstanceParameter> params = Arrays.asList(param1, param2);

        InstanceParameters parameters = new InstanceParameters(params);

        assertThat(parameters.getParameter("block1")).isEqualTo(param1);
        assertThat(parameters.getParameter("block2")).isEqualTo(param2);
        assertThat(parameters.getParameter("block3")).isNull();
    }

    @Test
    void testGetParameterWithNullInternalBlockId() {
        InstanceParameters parameters = new InstanceParameters(Collections.emptyList());

        assertThatThrownBy(() -> parameters.getParameter(null))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("internalBlockId must not be null or blank");
    }

    @Test
    void testGetParameterWithBlankInternalBlockId() {
        InstanceParameters parameters = new InstanceParameters(Collections.emptyList());

        assertThatThrownBy(() -> parameters.getParameter(""))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("internalBlockId must not be null or blank");

        assertThatThrownBy(() -> parameters.getParameter("   "))
                .isInstanceOf(NullArgumentException.class)
                .hasMessageContaining("internalBlockId must not be null or blank");
    }
}

