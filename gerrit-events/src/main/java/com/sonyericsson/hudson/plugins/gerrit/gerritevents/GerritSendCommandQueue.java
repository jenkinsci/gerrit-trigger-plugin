/*
 * The MIT License
 *
 * Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.sonyericsson.hudson.plugins.gerrit.gerritevents;

import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.cmd.AbstractSendCommandJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A thread-pool and queue implementation for queueing commands to the Gerrit server.
 *
 * @author Robert Sandell &lt;robert.sandell@sonyericsson.com&gt;
 */
public final class GerritSendCommandQueue {

    private static final Logger logger = LoggerFactory.getLogger(GerritSendCommandQueue.class);
    private static GerritSendCommandQueue instance;
    private ThreadPoolExecutor executor = null;
    private static final int THREAD_KEEP_ALIVE_TIME = 20;
    /**
     * The minimum size of the job-queue before monitors should begin to warn the administrator(s).
     */
    public static final int SEND_QUEUE_SIZE_WARNING_THRESHOLD = 20;
    private static final int WAIT_FOR_JOBS_SHUTDOWN_TIMEOUT = 30;

    /**
     * Private Default constructor.
     */
    private GerritSendCommandQueue() {

    }

    /**
     * Returns the singleton instance of the command-queue.
     * Updating it with the latest connection configuration.
     *
     * @param config the config.
     * @return the instance.
     */
    public static synchronized GerritSendCommandQueue getInstance(GerritConnectionConfig config) {
        if (instance == null) {
            if (config == null) {
                throw new NullPointerException("A config instance is needed for the first init.");
            }
            instance = new GerritSendCommandQueue();
        }
        if (config != null) {
            instance.startQueue(config);
        }
        return instance;
    }

    /**
     * Adds a command-job to the singleton instance's queue.
     *
     * @param job the job to do.
     */
    public static void queue(AbstractSendCommandJob job) {
        getInstance(job.getConfig()).queueJob(job);
    }

    /**
     * Returns the current queue size.
     *
     * @return the queue size,
     * @see java.util.concurrent.ThreadPoolExecutor#getQueue()
     */
    public static int getQueueSize() {
        if (instance != null && instance.executor != null) {
            return instance.executor.getQueue().size();
        } else {
            return 0;
        }
    }

    /**
     * Adds a job to the queue.
     * At the same time tries to update the thread-pool size from the latest config of the job.
     *
     * @param job the job to do.
     * @see java.util.concurrent.ThreadPoolExecutor#submit(Runnable)
     */
    public void queueJob(AbstractSendCommandJob job) {
        try {
            logger.debug("Queueing job {}", job);
            executor.submit(job);
        } catch (RejectedExecutionException e) {
            logger.error("Unable to queue a send-command-job! ", e);
        }

        int queueSize = getQueueSize();
        if (queueSize >= SEND_QUEUE_SIZE_WARNING_THRESHOLD) {
            logger.warn("The Gerrit-trigger send commands queue contains {} items!"
                    + " Something might be stuck, or your system can't process the commands fast enough."
                    + " Try to increase the number of sending worker threads on the Gerrit configuration page."
                    + " Current thread-pool size: {}",
                    queueSize, executor.getPoolSize());
            logger.info("Nr of active pool-threads: {}", executor.getActiveCount());
        }
    }

    /**
     * Starts the executor if it hasn't started yet, or updates the thread-pool size if it is started.
     *
     * @param config the config with the pool-size.
     */
    protected void startQueue(GerritConnectionConfig config) {
        if (executor == null) {
            logger.debug("Starting the sending thread pool.");
            executor = new ThreadPoolExecutor(
                    config.getNumberOfSendingWorkerThreads(),
                    config.getNumberOfSendingWorkerThreads(),
                    THREAD_KEEP_ALIVE_TIME, TimeUnit.MINUTES,
                    new LinkedBlockingQueue<Runnable>());
            executor.allowCoreThreadTimeOut(true);
            //Start with one thread, and build it up gradually as it needs.
            executor.prestartCoreThread();
            logger.info("SendQueue started! Current pool size: {}", executor.getPoolSize());
        } else {
            if (executor.getCorePoolSize() < config.getNumberOfSendingWorkerThreads()) {
                //If the number has increased we need to set the max first, or we'll get an IllegalArgumentException
                executor.setMaximumPoolSize(config.getNumberOfSendingWorkerThreads());
                executor.setCorePoolSize(config.getNumberOfSendingWorkerThreads());
            } else if (executor.getCorePoolSize() > config.getNumberOfSendingWorkerThreads()) {
                //If the number has decreased we need to set the core first.
                executor.setCorePoolSize(config.getNumberOfSendingWorkerThreads());
                executor.setMaximumPoolSize(config.getNumberOfSendingWorkerThreads());
            }
            logger.debug("SendQueue running. Current pool size: {}. Current Queue size: {}",
                    executor.getPoolSize(), getQueueSize());
            logger.debug("Nr of active pool-threads: {}", executor.getActiveCount());
        }
    }

    /**
     * Shuts down the executor(s).
     * Gracefully waits for {@link #WAIT_FOR_JOBS_SHUTDOWN_TIMEOUT} seconds for all jobs to finish
     * before forcefully shutting them down.
     */
    public static void shutdown() {
        if (instance != null && instance.executor != null) {
            ThreadPoolExecutor pool = instance.executor;
            instance.executor = null;
            pool.shutdown(); // Disable new tasks from being submitted
            try {
                // Wait a while for existing tasks to terminate
                if (!pool.awaitTermination(WAIT_FOR_JOBS_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                    pool.shutdownNow(); // Cancel currently executing tasks
                    // Wait a while for tasks to respond to being cancelled
                    if (!pool.awaitTermination(WAIT_FOR_JOBS_SHUTDOWN_TIMEOUT, TimeUnit.SECONDS)) {
                        logger.error("Pool did not terminate");
                    }
                }
            } catch (InterruptedException ie) {
                // (Re-)Cancel if current thread also interrupted
                pool.shutdownNow();
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }
}
