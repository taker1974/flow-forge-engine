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

import ru.spb.tksoft.common.exception.ConfigurationMismatchException;
import ru.spb.tksoft.common.exception.NullArgumentException;
import ru.spb.tksoft.flowforge.engine.contract.Instance;
import ru.spb.tksoft.flowforge.sdk.contract.Block;
import ru.spb.tksoft.flowforge.sdk.contract.EventListener;
import ru.spb.tksoft.flowforge.sdk.contract.Line;
import ru.spb.tksoft.flowforge.sdk.contract.Modifiable;
import ru.spb.tksoft.flowforge.sdk.enumeration.LineState;
import ru.spb.tksoft.flowforge.sdk.enumeration.RunnableState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import ru.spb.tksoft.utils.log.LogEx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Instance implementation.
 * 
 * Subclassing is not allowed.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
public final class InstanceImpl implements Instance {

    // <editor-fold desc="Fields and tools">

    private static final Logger log = LoggerFactory.getLogger(InstanceImpl.class);

    @NotNull
    private final Long templateId;

    @NotNull
    @Positive
    private final Long instanceId;

    @NotNull
    private final Long instanceUserId;

    @NotNull
    @NotBlank
    private final String instanceName;

    @NotNull
    private final InstanceParameters parameters;

    @NotNull
    private volatile RunnableState state = RunnableState.NOT_CONFIGURED;

    private volatile boolean hasError = false;

    @NotNull
    private String errorMessage = "";

    @NotNull
    private final List<Block> blocks;

    @NotNull
    private final List<Line> lines;

    private volatile boolean modified;

    // Successors might be interested in the modifiable changes, so we provide a way to add and
    // remove listeners. We use a CopyOnWriteArrayList to ensure thread safety.
    private final List<EventListener<ModifiableChangedEvent>> modifiableChangeListeners =
            new CopyOnWriteArrayList<>();

    /**
     * Add a listener for modifiable changes.
     * 
     * @param listener - the listener to add.
     */
    @Override
    public void addListener(final EventListener<ModifiableChangedEvent> listener) {
        modifiableChangeListeners.add(listener);
    }

    /**
     * Remove a listener for modifiable changes.
     * 
     * @param listener - the listener to remove.
     */
    @Override
    public void removeListener(final EventListener<ModifiableChangedEvent> listener) {
        modifiableChangeListeners.remove(listener);
    }

    /**
     * Fire a modifiable changed event to all listeners.
     * 
     * @param modifiedObjects - the list of modified objects.
     */
    protected void fireModifiableChangedEvent(final List<Modifiable> modifiedObjects) {

        modifiableChangeListeners.stream().forEach(listener -> listener.onEvent(
                new ModifiableChangedEvent(this, modifiedObjects)));
    }

    /**
     * Plan of blocks to execute.
     */
    @NotNull
    private final List<Block> plan = new ArrayList<>();

    /**
     * Get the log text.
     * 
     * @param message - the message.
     * @return the log text.
     */
    protected String getLogText(final @NotNull String message) {
        return String.format("%s [%s]: %s", getClass().getSimpleName(), instanceId, message);
    }

    // </editor-fold>

    /**
     * Set the modified flag.
     */
    @Override
    public void setModified() {
        this.modified = true;
    }

    /**
     * Check if the modified flag is set.
     * 
     * @return true if the modified flag is set, false otherwise.
     */
    @Override
    public boolean isModified() {
        return modified;
    }

    /**
     * Reset the modified flag.
     */
    @Override
    public void resetModified() {
        this.modified = false;
    }

    /**
     * Get the modified objects.
     * 
     * @return the unmodifiable list of modified objects.
     */
    @Override
    @NotNull
    public synchronized List<Modifiable> getModifiedObjects() {

        List<Modifiable> modifiedObjects = new ArrayList<>();

        blocks.stream().filter(o -> o.isModified()).forEach(modifiedObjects::add);
        lines.stream().filter(o -> o.isModified()).forEach(modifiedObjects::add);

        return Collections.unmodifiableList(modifiedObjects);
    }

    /**
     * Constructor.
     * 
     * @param templateId - the template id.
     * @param newInstanceId - the new instance id.
     * @param instanceUserId - the instance user id.
     * @param instanceName - the instance name.
     * @param parameters - the instance parameters. Can be null.
     * @param blocks - the instance blocks. Can be null.
     * @param lines - the instance lines. Can be null.
     * @throws NullArgumentException - if templateId, instanceId, instanceUserId, instanceName,
     *         parameters, blocks, lines is less than or equal to 0 or blank.
     */
    public InstanceImpl(final Long newInstanceId, final Long templateId,
            final Long instanceUserId, final String instanceName,
            final InstanceParameters parameters,
            final List<Block> blocks, final List<Line> lines) {

        if (newInstanceId == null || templateId == null || instanceUserId == null ||
                templateId <= 0 || instanceUserId <= 0 ||
                newInstanceId <= 0 || instanceName == null || instanceName.isBlank()) {
            throw new NullArgumentException(
                    "templateId, instanceId, instanceUserId, instanceName must not be null or less than or equal to 0");
        }

        this.instanceId = newInstanceId;
        this.templateId = templateId;
        this.instanceUserId = instanceUserId;
        this.instanceName = instanceName;

        this.parameters =
                Objects.isNull(parameters) ? new InstanceParameters(Collections.emptyList())
                        : parameters;

        this.blocks = Objects.isNull(blocks) ? new ArrayList<>() : blocks;
        this.lines = Objects.isNull(lines) ? new ArrayList<>() : lines;

        if (this.blocks.isEmpty() && !this.lines.isEmpty()) {
            throw new ConfigurationMismatchException(
                    "lines must not be present if blocks are not present");
        }

        this.state = RunnableState.READY;

        // Set the modified flag to true to force the initial state to be modified.
        this.modified = true;
    }

    /**
     * Get the template id.
     * 
     * @return the template id.
     */
    @Override
    @NotNull
    @Positive
    public Long getTemplateId() {
        return templateId;
    }

    /**
     * Get the instance id.
     * 
     * @return the instance id.
     */
    @Override
    @NotNull
    @NotBlank
    public Long getInstanceId() {
        return instanceId;
    }

    /**
     * Get the instance user id.
     * 
     * @return the instance user id.
     */
    @Override
    @NotNull
    @Positive
    public Long getInstanceUserId() {
        return instanceUserId;
    }

    /**
     * Get the instance name.
     * 
     * @return the instance name.
     */
    @Override
    @NotBlank
    public String getInstanceName() {
        return instanceName;
    }

    /**
     * Set the error.
     * 
     * @param hasError - the error flag.
     * @param errorMessage - the error message.
     */
    protected synchronized void setError(final boolean hasError, final String errorMessage) {
        if (this.hasError != hasError) {
            setModified();
        }
        this.hasError = hasError;
        this.errorMessage = errorMessage;
    }

    /**
     * Check if the instance has an error.
     * 
     * @return true if the instance has an error, false otherwise.
     */
    @Override
    public boolean hasError() {
        return hasError;
    }

    /**
     * Get the error message of the instance.
     * 
     * @return the error message of the instance.
     */
    @Override
    @NotNull
    public synchronized String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get the state.
     * 
     * Synchronized to match the synchronization on "setState". sonarqube(java:S2886)
     * 
     * @return the state.
     */
    @Override
    @NotNull
    public synchronized RunnableState getState() {
        return state;
    }

    /**
     * Set the state of the instance.
     * 
     * Internal method dedicated for controlling the state of the instance. A part of state machine.
     * 
     * @param state - the state.
     */
    protected synchronized void setState(final RunnableState state) {

        if (state == null) {
            throw new NullArgumentException(getLogText("state must not be null"));
        }

        if (this.state != state) {
            setModified();
        }

        this.state = state;
        LogEx.info(log, LogEx.me(), getLogText("state changed to " + state));
    }

    /**
     * Forced stop the instance.
     */
    @Override
    public synchronized void abort() {

        setState(RunnableState.ABORTED);

        blocks.stream().forEach(Block::abort);
        lines.stream().forEach(line -> line.setState(LineState.OFF));

        // Ensure the instance is marked as modified.
        setModified();

        LogEx.info(log, LogEx.me(), getLogText("abort completed"));
    }

    /**
     * Stop the instance.
     */
    @Override
    public synchronized void stop() {

        setState(RunnableState.STOPPED);

        blocks.stream().forEach(Block::stop);
        lines.stream().forEach(line -> line.setState(LineState.OFF));

        // Ensure the instance is marked as modified.
        setModified();

        LogEx.info(log, LogEx.me(), getLogText("stop completed"));
    }

    /**
     * Set the instance to READY state without resetting it.
     * 
     * This method is used to set the instance to READY state without resetting it. It is used to
     * set the instance to READY state when the instance is in DONE state for example. Use with
     * caution.
     */
    @Override
    public synchronized void setReady() {

        if (state != RunnableState.DONE && state != RunnableState.ABORTED
                && state != RunnableState.STOPPED) {
            return;
        }

        if (hasError) {
            throw new ConfigurationMismatchException(getLogText(getErrorMessage()));
        }

        setState(RunnableState.READY);

        blocks.stream().forEach(Block::setReady);
        lines.stream().forEach(line -> line.setState(LineState.OFF));
    }

    /**
     * Reset the instance.
     */
    @Override
    public synchronized void reset() {

        setState(RunnableState.READY);

        blocks.stream().forEach(Block::reset);
        lines.stream().forEach(Line::reset);

        hasError = false;
        errorMessage = "";

        // Ensure the instance is marked as modified.
        setModified();

        LogEx.info(log, LogEx.me(), getLogText("reset completed"));
    }

    /**
     * Run the instance.
     */
    @Override
    public synchronized void run() {

        if (state == RunnableState.NOT_CONFIGURED) {
            setError(true, "Instance is not configured");
            throw new ConfigurationMismatchException(getLogText(getErrorMessage()));
        }

        if (state == RunnableState.READY) {

            // Set parameters for blocks.
            blocks.stream().forEach(block -> {
                final InstanceParameter parameter =
                        parameters.getParameter(block.getInternalBlockId());
                if (parameter != null) {
                    block.setInputText(parameter.getParameterValue());
                }
            });

            // Add heading blocks to plan.
            plan.clear();
            blocks.stream().forEach(block -> {
                if (!block.getInputJunction().hasLines()) {
                    plan.add(block);
                }
            });

            // Set the state to running and return immediately.
            setState(RunnableState.RUNNING);
            return;
        }

        if (state == RunnableState.RUNNING) {

            // Execute each block from plan.
            plan.forEach(Block::run);

            // Fire a modifiable changed event to all listeners.
            // Get the list of modified objects. getModifiedObjects() is synchronized and already
            // returns an unmodifiable list.
            fireModifiableChangedEvent(getModifiedObjects());

            // Remove executed blocks from plan.
            plan.removeIf(block -> block.getState() == RunnableState.DONE);

            // Get highlighted lines.
            List<Line> highlightedLines = lines.stream()
                    .filter(line -> line.getState() == LineState.ON).toList();

            // Add next blocks to plan.
            highlightedLines.forEach(line -> {
                Block block = line.getBlockTo();
                if (!plan.contains(block)) {
                    plan.add(block);
                }
            });
        }

        // If plan is empty, set the state to DONE.
        if (plan.isEmpty()) {
            setState(RunnableState.DONE);
        }
    }
}
