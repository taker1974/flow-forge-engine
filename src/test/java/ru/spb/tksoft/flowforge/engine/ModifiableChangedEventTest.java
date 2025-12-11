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

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import ru.spb.tksoft.flowforge.engine.contract.Instance;
import ru.spb.tksoft.flowforge.engine.model.ModifiableChangedEvent;
import ru.spb.tksoft.flowforge.sdk.contract.Modifiable;

/**
 * Tests for ModifiableChangedEvent.
 *
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
class ModifiableChangedEventTest {

    @Mock
    private Instance instance;

    @Mock
    private Modifiable modifiable;

    @Test
    void testConstructor() {
        List<Modifiable> modifiedObjects = Collections.singletonList(modifiable);
        ModifiableChangedEvent event = new ModifiableChangedEvent(instance, modifiedObjects);

        Assertions.assertThat(event.getInstance()).isEqualTo(instance);
        Assertions.assertThat(event.getModifiedObjects()).isEqualTo(modifiedObjects);
    }

    @Test
    void testConstructorWithEmptyList() {
        List<Modifiable> modifiedObjects = Collections.emptyList();
        ModifiableChangedEvent event = new ModifiableChangedEvent(instance, modifiedObjects);

        Assertions.assertThat(event.getInstance()).isEqualTo(instance);
        Assertions.assertThat(event.getModifiedObjects()).isEmpty();
    }

    @Test
    void testConstructorWithNullList() {
        ModifiableChangedEvent event = new ModifiableChangedEvent(instance, null);

        Assertions.assertThat(event.getInstance()).isEqualTo(instance);
        Assertions.assertThat(event.getModifiedObjects()).isNull();
    }
}

