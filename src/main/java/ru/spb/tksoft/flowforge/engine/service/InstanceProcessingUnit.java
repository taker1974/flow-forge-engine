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

package ru.spb.tksoft.flowforge.engine.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import ru.spb.tksoft.flowforge.sdk.enumeration.RunnableState;
import ru.spb.tksoft.flowforge.engine.exception.CommandFailedException;
import ru.spb.tksoft.flowforge.engine.exception.InstanceAddFailedException;
import ru.spb.tksoft.common.exception.NullArgumentException;
import ru.spb.tksoft.utils.log.LogEx;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import ru.spb.tksoft.common.exception.ConfigurationMismatchException;
import ru.spb.tksoft.common.exception.ObjectAlreadyExistsException;
import ru.spb.tksoft.flowforge.engine.contract.Instance;

/**
 * Instance processing unit.
 * 
 * Open for subclassing.
 * 
 * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
 */
@RequiredArgsConstructor
public class InstanceProcessingUnit {

    protected static final Logger log = LoggerFactory.getLogger(InstanceProcessingUnit.class);

    /** Default processing delay. */
    public static final Duration DEFAULT_PROCESSING_DELAY = Duration.ofSeconds(1);

    /**
     * Instance entry record.
     * 
     * @param instance - instance.
     */
    protected final record InstanceEntry(Instance instance) {
    }

    private final Map<Long /* instance id */, InstanceEntry> instances = new ConcurrentHashMap<>();

    /**
     * Command enum.
     * 
     * @author Konstantin Terskikh, kostus.online.1974@yandex.ru, 2025
     */
    public enum Command {
        SET_READY, PAUSE, RESUME, STOP, ABORT, RESET, REMOVE
    }

    /**
     * Command entry record.
     * 
     * @param command - command.
     * @param instanceId - instance ID.
     */
    protected final record CommandEntry(Command command, Long instanceId) {
    }

    protected final Queue<CommandEntry> commandQueue = new ConcurrentLinkedQueue<>();

    /**
     * Processing delay.
     */
    protected Duration processingDelay = DEFAULT_PROCESSING_DELAY;

    protected ScheduledExecutorService scheduler;
    protected ScheduledFuture<?> scheduledTask;
    protected volatile boolean isRunning = false;

    /**
     * Start the scheduler.
     */
    public synchronized void startProcessing() {

        if (isRunning) {
            LogEx.warn(log, LogEx.me(), "Instance processing scheduler is already running");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(1,
                Thread.ofVirtual()
                        .name("instance-processor-", 0)
                        .factory());

        scheduledTask = scheduler.scheduleWithFixedDelay(
                this::processTick,
                0,
                processingDelay.toMillis(),
                TimeUnit.MILLISECONDS);

        isRunning = true;
        LogEx.trace(log, LogEx.me(), LogEx.STOPPED,
                "Instance processing scheduler started with delay: {} ms",
                processingDelay.toMillis());
    }

    /**
     * Stop the scheduler.
     * 
     * @param timeout - timeout.
     * @param unit - time unit.
     * @throws InterruptedException - if the thread is interrupted.
     */
    public synchronized void stopProcessing(final long timeout, final TimeUnit unit) {

        if (!isRunning) {
            LogEx.warn(log, LogEx.me(), "Instance processing scheduler is not running");
            return;
        }

        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }

        scheduler.shutdown();

        try {
            if (!scheduler.awaitTermination(timeout, unit)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        isRunning = false;
        LogEx.trace(log, LogEx.me(), LogEx.STOPPED, "Instance processing scheduler stopped");
    }

    /**
     * Add instance to the pool for processing.
     * 
     * Processing pool is not necessarily a queue. It can be a set, a list, a map, etc.
     * 
     * @param instance - instance.
     * @throws ObjectAlreadyExistsException - if instance already exists.
     * @throws InstanceAddFailedException - if instance add failed.
     */
    public void addInstance(final Instance instance) {

        LogEx.trace(log, LogEx.me(), LogEx.STARTING, instance.getInstanceId());

        if (instances.containsKey(instance.getInstanceId())) {
            throw new ObjectAlreadyExistsException("instance already exists");
        }

        InstanceEntry instanceEntry = new InstanceEntry(instance);

        try {
            instances.put(instance.getInstanceId(), instanceEntry);

        } catch (Exception e) {
            throw new InstanceAddFailedException(
                    "failed to add instance " + instance.getInstanceId());
        }

        LogEx.trace(log, LogEx.me(), LogEx.STOPPED);
    }

    /**
     * Instance list item record.
     * 
     * @param instanceId - instance ID.
     * @param ownerId - owner ID.
     * @param name - instance name.
     * @param state - instance state.
     */
    public record InstanceListItem(Long instanceId, Long ownerId, String name,
            RunnableState state) {
    }

    /**
     * Get instance list.
     * 
     * @param userId - user ID.
     * @return instance list items.
     */
    public List<InstanceListItem> getInstanceListItems(final Long userId) {

        return instances.values().stream()
                .filter(instanceEntry -> instanceEntry.instance.getInstanceUserId().equals(userId))
                .map(instanceEntry -> new InstanceListItem(instanceEntry.instance.getInstanceId(),
                        instanceEntry.instance.getInstanceUserId(),
                        instanceEntry.instance.getInstanceName(),
                        instanceEntry.instance.getState()))
                .toList();
    }

    /**
     * Put command to the command queue.
     * 
     * @param instanceId - instance ID.
     * @param command - command.
     * @throws NullArgumentException - if instanceId is null or blank or command is null.
     * @throws CommandFailedException - if command failed.
     */
    public void putCommand(final Command command, final Long instanceId) {

        LogEx.trace(log, LogEx.me(), LogEx.STARTING, instanceId, command);

        if (instanceId == null || instanceId <= 0) {
            throw new NullArgumentException(
                    "instanceId must not be null or blank");
        }
        if (command == null) {
            throw new NullArgumentException("command must not be null");
        }

        try {
            commandQueue.add(new CommandEntry(command, instanceId));
        } catch (Exception e) {
            throw new CommandFailedException(
                    "failed to add command " + command + " for instance " + instanceId);
        }

        LogEx.trace(log, LogEx.me(), LogEx.STOPPED);
    }

    /**
     * Run instance.
     * 
     * @param instanceEntry - instance entry.
     * @throws ConfigurationMismatchException - if instance is not configured.
     */
    protected void runInstance(final InstanceEntry instanceEntry) {

        LogEx.trace(log, LogEx.me(), LogEx.STARTING, instanceEntry.instance.getInstanceId());

        final Instance instance = instanceEntry.instance;
        if (instance.getState() == RunnableState.NOT_CONFIGURED) {
            throw new ConfigurationMismatchException("instance is not configured");
        }

        // Do nothing if the instance is not ready or running.
        if (instance.getState() != RunnableState.READY &&
                instance.getState() != RunnableState.RUNNING) {
            return;
        }

        // Run the instance.
        instance.run();

        LogEx.trace(log, LogEx.me(), LogEx.STOPPED);
    }

    /**
     * Process each command in the command queue. Process each instance in the pool.
     * 
     * Warning: this method is not thread-safe.
     */
    protected void processTick() {

        if (instances.isEmpty()) {
            commandQueue.clear();
            return;
        }

        // Process commands.
        CommandEntry commandEntry;
        while ((commandEntry = commandQueue.poll()) != null) {

            final InstanceEntry instanceEntry = instances.get(commandEntry.instanceId);
            if (instanceEntry == null) {
                continue;
            }
            final Instance instance = instanceEntry.instance;

            switch (commandEntry.command) {

                // Set instance to READY state without resetting it.
                case SET_READY -> instance.setReady();

                case PAUSE, RESUME -> {
                    // Not supported yet.
                }

                case STOP -> instance.stop();
                case ABORT -> instance.abort();
                case RESET -> instance.reset();

                case REMOVE -> instances.remove(commandEntry.instanceId);
            }
        }

        // Run each instance.
        instances.values().stream()
                .filter(instanceEntry -> instanceEntry.instance.getState().isReadyToRun())
                .forEach(this::runInstance);
    }
}
