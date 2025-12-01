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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.spb.tksoft.common.exceptions.ConfigurationMismatchException;
import ru.spb.tksoft.common.exceptions.NullArgumentException;
import ru.spb.tksoft.flowforge.engine.model.InstanceImpl;
import ru.spb.tksoft.flowforge.engine.model.InstanceParameters;
import ru.spb.tksoft.flowforge.engine.model.InstanceParameter;
import ru.spb.tksoft.flowforge.sdk.contract.Block;
import ru.spb.tksoft.flowforge.sdk.contract.EventListener;
import ru.spb.tksoft.flowforge.sdk.contract.Line;
import ru.spb.tksoft.flowforge.sdk.contract.LineJunction;
import ru.spb.tksoft.flowforge.sdk.contract.Modifiable;
import ru.spb.tksoft.flowforge.sdk.enumeration.LineState;
import ru.spb.tksoft.flowforge.sdk.enumeration.RunnableState;
import ru.spb.tksoft.flowforge.engine.model.ModifiableChangedEvent;

/**
 * Tests for InstanceImpl.
 *
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
class InstanceImplTest {

    private static final Long TEMPLATE_ID = 1L;
    private static final Long INSTANCE_ID = 100L;
    private static final Long INSTANCE_USER_ID = 200L;
    private static final String INSTANCE_NAME = "Test Instance";

    private InstanceImpl instance;
    private InstanceParameters parameters;
    private List<Block> blocks;
    private List<Line> lines;

    @BeforeEach
    void setUp() {
        blocks = new ArrayList<>();
        lines = new ArrayList<>();
        parameters = new InstanceParameters(Collections.emptyList());
        instance = new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID, INSTANCE_NAME,
                parameters, blocks, lines);
    }

    @Test
    void testConstructor() {
        assertThat(instance.getTemplateId()).isEqualTo(TEMPLATE_ID);
        assertThat(instance.getInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(instance.getInstanceUserId()).isEqualTo(INSTANCE_USER_ID);
        assertThat(instance.getInstanceName()).isEqualTo(INSTANCE_NAME);
        assertThat(instance.getState()).isEqualTo(RunnableState.READY);
        assertThat(instance.isModified()).isTrue();
        assertThat(instance.hasError()).isFalse();
    }

    @Test
    void testConstructorWithNullInstanceId() {
        assertThatThrownBy(() -> new InstanceImpl(null, TEMPLATE_ID, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, blocks, lines))
                        .isInstanceOf(NullArgumentException.class);
    }

    @Test
    void testConstructorWithZeroInstanceId() {
        assertThatThrownBy(() -> new InstanceImpl(0L, TEMPLATE_ID, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, blocks, lines))
                        .isInstanceOf(NullArgumentException.class);
    }

    @Test
    void testConstructorWithNullTemplateId() {
        assertThatThrownBy(() -> new InstanceImpl(INSTANCE_ID, null, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, blocks, lines))
                        .isInstanceOf(NullArgumentException.class);
    }

    @Test
    void testConstructorWithZeroTemplateId() {
        assertThatThrownBy(() -> new InstanceImpl(INSTANCE_ID, 0L, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, blocks, lines))
                        .isInstanceOf(NullArgumentException.class);
    }

    @Test
    void testConstructorWithNullInstanceName() {
        assertThatThrownBy(() -> new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID,
                null, parameters, blocks, lines))
                        .isInstanceOf(NullArgumentException.class);
    }

    @Test
    void testConstructorWithBlankInstanceName() {
        assertThatThrownBy(() -> new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID,
                "", parameters, blocks, lines))
                        .isInstanceOf(NullArgumentException.class);
    }

    @Test
    void testConstructorWithNullParameters() {
        InstanceImpl inst = new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID,
                INSTANCE_NAME, null, blocks, lines);

        assertThat(inst).isNotNull();
    }

    @Test
    void testConstructorWithNullBlocks() {
        InstanceImpl inst = new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, null, lines);

        assertThat(inst).isNotNull();
    }

    @Test
    void testConstructorWithNullLines() {
        InstanceImpl inst = new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, blocks, null);

        assertThat(inst).isNotNull();
    }

    @Test
    void testConstructorWithLinesButNoBlocks() {
        Line line = mock(Line.class);
        List<Line> linesWithData = Collections.singletonList(line);
        List<Block> emptyBlocks = Collections.emptyList();

        assertThatThrownBy(() -> new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID,
                INSTANCE_NAME, parameters, emptyBlocks, linesWithData))
                        .isInstanceOf(ConfigurationMismatchException.class)
                        .hasMessageContaining(
                                "lines must not be present if blocks are not present");
    }

    @Test
    void testSetModified() {
        instance.resetModified();
        instance.setModified();

        assertThat(instance.isModified()).isTrue();
    }

    @Test
    void testResetModified() {
        instance.resetModified();

        assertThat(instance.isModified()).isFalse();
    }

    @Test
    void testGetModifiedObjects() {
        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        Line line1 = mock(Line.class);

        when(block1.isModified()).thenReturn(true);
        when(block2.isModified()).thenReturn(false);
        when(line1.isModified()).thenReturn(true);

        blocks.add(block1);
        blocks.add(block2);
        lines.add(line1);

        List<Modifiable> modifiedObjects = instance.getModifiedObjects();

        assertThat(modifiedObjects)
                .hasSize(2)
                .contains(block1, line1)
                .doesNotContain(block2);
    }

    @Test
    void testGetModifiedObjectsWithEmptyLists() {
        List<Modifiable> modifiedObjects = instance.getModifiedObjects();

        assertThat(modifiedObjects).isEmpty();
    }

    @Test
    void testStop() {
        Block block = mock(Block.class);
        Line line = mock(Line.class);
        blocks.add(block);
        lines.add(line);

        instance.stop();

        assertThat(instance.getState()).isEqualTo(RunnableState.STOPPED);
        assertThat(instance.isModified()).isTrue();
        verify(block).stop();
        verify(line).setState(LineState.OFF);
    }

    @Test
    void testAbort() {
        Block block = mock(Block.class);
        Line line = mock(Line.class);
        blocks.add(block);
        lines.add(line);

        instance.abort();

        assertThat(instance.getState()).isEqualTo(RunnableState.ABORTED);
        assertThat(instance.isModified()).isTrue();
        verify(block).abort();
        verify(line).setState(LineState.OFF);
    }

    @Test
    void testReset() {
        Block block = mock(Block.class);
        Line line = mock(Line.class);
        blocks.add(block);
        lines.add(line);

        instance.reset();

        assertThat(instance.getState()).isEqualTo(RunnableState.READY);
        assertThat(instance.hasError()).isFalse();
        assertThat(instance.isModified()).isTrue();
        verify(block).reset();
        verify(line).reset();
    }

    @Test
    void testRunFromReady() {
        Block block = mock(Block.class);
        LineJunction inputJunction = mock(LineJunction.class);

        when(block.getInternalBlockId()).thenReturn("block1");
        when(block.getInputJunction()).thenReturn(inputJunction);
        when(inputJunction.hasLines()).thenReturn(false);

        blocks.add(block);

        instance.run();

        assertThat(instance.getState()).isEqualTo(RunnableState.RUNNING);
    }

    @Test
    void testRunFromReadyWithParameters() {
        Block block = mock(Block.class);
        LineJunction inputJunction = mock(LineJunction.class);
        InstanceParameter param = new InstanceParameter("block1", "param value");

        when(block.getInternalBlockId()).thenReturn("block1");
        when(block.getInputJunction()).thenReturn(inputJunction);
        when(inputJunction.hasLines()).thenReturn(false);

        blocks.add(block);
        parameters = new InstanceParameters(Collections.singletonList(param));
        instance = new InstanceImpl(INSTANCE_ID, TEMPLATE_ID, INSTANCE_USER_ID, INSTANCE_NAME,
                parameters, blocks, lines);

        instance.run();

        verify(block).setInputText("param value");
    }

    @Test
    void testRunFromRunning() {
        Block block1 = mock(Block.class);
        Block block2 = mock(Block.class);
        Line line = mock(Line.class);
        LineJunction inputJunction1 = mock(LineJunction.class);
        LineJunction inputJunction2 = mock(LineJunction.class);
        LineJunction outputJunction1 = mock(LineJunction.class);

        when(block1.getInternalBlockId()).thenReturn("block1");
        when(block2.getInternalBlockId()).thenReturn("block2");
        when(block1.getInputJunction()).thenReturn(inputJunction1);
        when(block2.getInputJunction()).thenReturn(inputJunction2);
        when(block1.getOutputJunction()).thenReturn(outputJunction1);
        when(inputJunction1.hasLines()).thenReturn(false);
        when(inputJunction2.hasLines()).thenReturn(true);
        when(block1.getState()).thenReturn(RunnableState.RUNNING, RunnableState.DONE);
        when(block2.getState()).thenReturn(RunnableState.READY);
        when(line.getState()).thenReturn(LineState.ON);
        when(line.getBlockTo()).thenReturn(block2);
        when(block1.isModified()).thenReturn(false);

        blocks.add(block1);
        blocks.add(block2);
        lines.add(line);

        // First run to set state to RUNNING and add block1 to plan
        instance.run();
        assertThat(instance.getState()).isEqualTo(RunnableState.RUNNING);

        // Second run to execute block1 from plan
        instance.run();
        verify(block1).run();
    }

    @Test
    void testAddAndRemoveListener() {
        @SuppressWarnings("unchecked")
        EventListener<ModifiableChangedEvent> listener = mock(EventListener.class);

        instance.addListener(listener);
        instance.removeListener(listener);

        // Verify listeners can be added and removed
        assertThat(instance).isNotNull();
    }
}

