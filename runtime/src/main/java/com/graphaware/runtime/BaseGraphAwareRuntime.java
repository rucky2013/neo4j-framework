/*
 * Copyright (c) 2013 GraphAware
 *
 * This file is part of GraphAware.
 *
 * GraphAware is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy of
 * the GNU General Public License along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.graphaware.runtime;

import com.graphaware.runtime.config.DefaultRuntimeConfiguration;
import com.graphaware.runtime.config.RuntimeConfiguration;
import com.graphaware.runtime.config.RuntimeConfigured;
import com.graphaware.runtime.module.RuntimeModule;
import org.apache.log4j.Logger;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.event.ErrorState;
import org.neo4j.graphdb.event.KernelEventHandler;

import java.util.Set;

/**
 * Abstract base-class for {@link GraphAwareRuntime} implementations. Handles lifecycle of the runtime and basic
 * module-related sanity checks.
 */
public abstract class BaseGraphAwareRuntime implements GraphAwareRuntime, KernelEventHandler {

    private static final Logger LOG = Logger.getLogger(BaseGraphAwareRuntime.class);

    private final RuntimeConfiguration configuration;

    private State state = State.NONE;

    private enum State {
        NONE,
        STARTING,
        STARTED
    }

    /**
     * Create a new instance of the runtime with {@link com.graphaware.runtime.config.DefaultRuntimeConfiguration}.
     */
    protected BaseGraphAwareRuntime() {
        this(DefaultRuntimeConfiguration.getInstance());
    }

    /**
     * Create a new instance.
     *
     * @param configuration config.
     */
    private BaseGraphAwareRuntime(RuntimeConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void registerModule(RuntimeModule module) {
        if (!State.NONE.equals(state)) {
            LOG.error("Modules must be registered before GraphAware Runtime is started!");
            throw new IllegalStateException("Modules must be registered before GraphAware Runtime is started!");
        }

        LOG.info("Registering module " + module.getId() + " with GraphAware Runtime.");

        doRegisterModule(module);

        if (module instanceof RuntimeConfigured) {
            ((RuntimeConfigured) module).configurationChanged(configuration);
        }
    }

    /**
     * Perform the actual module registration after sanity checks have passed.
     *
     * @param module to register.
     */
    protected abstract void doRegisterModule(RuntimeModule module);

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void start() {
        start(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final synchronized void start(boolean skipInitialization) {
        if (State.STARTED.equals(state)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("GraphAware already started");
            }
            return;
        }

        if (State.STARTING.equals(state)) {
            throw new IllegalStateException("Attempt to start GraphAware from multiple different threads. This is a bug");
        }

        LOG.info("Starting GraphAware...");
        state = State.STARTING;

        doStart(skipInitialization);

        state = State.STARTED;
        LOG.info("GraphAware started.");
    }

    protected void doStart(boolean skipInitialization) {
        if (skipInitialization) {
            LOG.info("Initialization skipped.");
        } else {
            LOG.info("Initializing modules...");
            try (Transaction tx = startTransaction()) {
                Set<String> moduleIds = initializeModules();
                performCleanup(moduleIds);
                tx.success();
            }
            LOG.info("Modules initialized.");
        }
    }

    /**
     * Start a database transaction.
     *
     * @return tx.
     */
    protected abstract Transaction startTransaction();

    /**
     * Initialize modules if needed. Modules typically need to be initialized only the very first time they are used
     * with a database instance.
     *
     * @return IDs of all modules registered with the runtime, no matter whether they needed to be initialized or not.
     */
    protected abstract Set<String> initializeModules();

    /**
     * Perform cleanup of metadata potentially written to the graph by modules that aren't used any more.
     *
     * @param usedModules IDs of all the used modules (should be the same as returned by {@link #initializeModules()}.
     */
    protected abstract void performCleanup(Set<String> usedModules);

    /**
     * Checks to see if this {@link GraphAwareRuntime} has <b>NOT</b> yet been started, starting it if necessary and possible.
     *
     * @return <code>false</code> if the database isn't yet available or the runtime is currently starting,
     *         <code>true</code> if it's alright to delegate onto modules.
     */
    protected final boolean tryToStartIfNotStarted() {
        if (!databaseAvailable()) {
            return false;
        }

        //todo: is this a bottleneck? all transactions arrive here!
        synchronized (this) {
            switch (state) {
                case NONE:
                    start();
                    return true;
                case STARTING:
                    return false;
                case STARTED:
                    return true;
                default:
                    throw new IllegalStateException("Unknown GraphAware Runtime state. This is a bug.");
            }
        }
    }

    /**
     * Find out whether the database is available and ready for use.
     *
     * @return true iff the database is ready.
     */
    protected abstract boolean databaseAvailable();

    /**
     * {@inheritDoc}
     */
    @Override
    public final void beforeShutdown() {
        LOG.info("Shutting down GraphAware Runtime... ");
        shutdownModules();
        LOG.info("GraphAware Runtime shut down.");
    }

    /**
     * Shutdown all modules.
     */
    protected abstract void shutdownModules();

    /**
     * {@inheritDoc}
     */
    @Override
    public final void kernelPanic(ErrorState error) {
        //do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Object getResource() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final ExecutionOrder orderComparedTo(KernelEventHandler other) {
        return ExecutionOrder.DOESNT_MATTER;
    }
}
