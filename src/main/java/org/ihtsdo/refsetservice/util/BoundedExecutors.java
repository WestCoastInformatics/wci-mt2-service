/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared bounded executors for Snowstorm fan-out and background jobs. Callers must not shut these pools down.
 */
public final class BoundedExecutors {

    /** Max concurrent Snowstorm fan-out tasks across the process. */
    private static final int FANOUT_SIZE = 30;

    /** Max concurrent export / Norway report style background jobs. */
    private static final int BACKGROUND_SIZE = 4;

    /** Shared fan-out pool (CallerRuns when saturated). */
    private static final ExecutorService FANOUT = new ThreadPoolExecutor(FANOUT_SIZE, FANOUT_SIZE, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(FANOUT_SIZE), namedDaemonFactory("snowstorm-fanout"), new ThreadPoolExecutor.CallerRunsPolicy());

    /** Shared background pool for exports and long reports. */
    private static final ExecutorService BACKGROUND = new ThreadPoolExecutor(BACKGROUND_SIZE, BACKGROUND_SIZE, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(BACKGROUND_SIZE), namedDaemonFactory("background-job"), new ThreadPoolExecutor.CallerRunsPolicy());

    /**
     * Instantiates a new bounded executors.
     */
    private BoundedExecutors() {

        // n/a
    }

    /**
     * Returns the shared Snowstorm fan-out executor. Do not shut down.
     *
     * @return the executor
     */
    public static ExecutorService fanout() {

        return FANOUT;
    }

    /**
     * Returns the shared background-job executor. Do not shut down.
     *
     * @return the executor
     */
    public static ExecutorService background() {

        return BACKGROUND;
    }

    /**
     * Starts a fan-out batch that tracks submitted futures so callers can await without shutting down the pool.
     *
     * @return the batch
     */
    public static Batch fanoutBatch() {

        return new Batch(FANOUT);
    }

    /**
     * Named daemon thread factory.
     *
     * @param prefix the prefix
     * @return the factory
     */
    private static ThreadFactory namedDaemonFactory(final String prefix) {

        final AtomicInteger index = new AtomicInteger();
        return runnable -> {
            final Thread thread = new Thread(runnable, prefix + "-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Tracks futures submitted to a shared executor for a single logical batch of work.
     */
    public static final class Batch {

        /** The executor. */
        private final ExecutorService executor;

        /** Submitted futures. */
        private final List<Future<?>> futures = new ArrayList<>();

        /**
         * Instantiates a new batch.
         *
         * @param executor the executor
         */
        private Batch(final ExecutorService executor) {

            this.executor = executor;
        }

        /**
         * Submit.
         *
         * @param task the task
         * @return the future
         */
        public Future<?> submit(final Runnable task) {

            final Future<?> future = executor.submit(task);
            futures.add(future);
            return future;
        }

        /**
         * Awaits all submitted tasks. Does not shut down the shared pool.
         *
         * @param timeout the timeout
         * @param unit the unit
         * @throws InterruptedException if interrupted or timed out
         */
        public void await(final long timeout, final TimeUnit unit) throws InterruptedException {

            final long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
            for (final Future<?> future : futures) {
                final long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0) {
                    throw new InterruptedException("Timed out waiting for fan-out tasks");
                }
                try {
                    future.get(remainingNanos, TimeUnit.NANOSECONDS);
                } catch (final TimeoutException e) {
                    throw new InterruptedException("Timed out waiting for fan-out tasks");
                } catch (final ExecutionException e) {
                    final Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    throw new RuntimeException(cause);
                }
            }
        }
    }
}
