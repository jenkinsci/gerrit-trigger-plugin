/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne <rinrin.ne@gmail.com>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.gerritevents.handler;

//CS IGNORE LineLength FOR NEXT 3 LINES. REASON: static import.
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_NR_OF_RECEIVING_WORKER_THREADS;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_RABBITMQ_URI;
import static com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritDefaultValues.DEFAULT_GERRIT_RABBITMQ_QUEUE_NAME;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.GerritConnectionConfig;
import com.sonyericsson.hudson.plugins.gerrit.gerritevents.workers.StreamEventsStringWork;

/**
 * Handler class for Gerrit stream events via RabbiMQ. Contains the main loop for connecting and
 * reading gerrit stream events from Gerrit in message queue.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public final class RabbitmqHandler extends AbstractHandler {

    private static final Logger logger = LoggerFactory.getLogger(RabbitmqHandler.class);
    private String rabbitmqUri;
    private String rabbitmqQueueName;

    private static final String MESSAGE_CONTENT_TYPE = "application/json";
    private static final long PAUSE_MILLIS = 500;

    /**
     * Creates a RabbitmqHandler with all the default values set.
     *
     * @see GerritDefaultValues#DEFAULT_GERRIT_RABBITMQ_URI
     * @see GerritDefaultValues#DEFAULT_GERRIT_RABBITMQ_QUEUE_NAME
     * @see GerritDefaultValues#DEFAULT_NR_OF_RECEIVING_WORKER_THREADS
     */
    public RabbitmqHandler() {
        this(
                DEFAULT_GERRIT_RABBITMQ_URI,
                DEFAULT_GERRIT_RABBITMQ_QUEUE_NAME,
                DEFAULT_NR_OF_RECEIVING_WORKER_THREADS,
                null);
    }

    /**
     * Creates a RabbitmqHandler with the specified value.
     *
     * @param config the configuration containing the connection values.
     */
    public RabbitmqHandler(GerritConnectionConfig config) {
        super(config.getNumberOfReceivingWorkerThreads(), config.getGerritEMail());
        this.rabbitmqUri = config.getGerritRabbitmqUri();
        this.rabbitmqQueueName = config.getGerritRabbitmqQueueName();
    }

    /**
     * Creates a RabbitmqHandler with the specified value.
     *
     * @param rabbitmqUri           the URI for RabbitMQ.
     * @param rabbitmqQueueName     the name of queue in RabbitMQ.
     * @param numberOfWorkerThreads the number of eventthreads.
     * @param ignoreEMail           the e-mail to ignore events from.
     */
    public RabbitmqHandler(
            String rabbitmqUri,
            String rabbitmqQueueName,
            int numberOfWorkerThreads,
            String ignoreEMail) {
        super(numberOfWorkerThreads, ignoreEMail);
        this.rabbitmqUri = rabbitmqUri;
        this.rabbitmqQueueName = rabbitmqQueueName;
    }

    @Override
    protected void listenEvent() {
        Connection connection = connect();

        if (connection == null) {
            return;
        }

        try {
            Channel ch = connection.createChannel();
            GetResponse response;

            while (!shutdownRequested) {
                response = ch.basicGet(rabbitmqQueueName, false);
                if (response != null) {
                    try {
                        logger.trace("Received message.");
                        String body = new String(response.getBody(), "UTF-8");
                        AMQP.BasicProperties props = response.getProps();
                        if (body != null && body.length() > 0
                                && MESSAGE_CONTENT_TYPE.equals(props.getContentType())) {
                            logger.debug("Gerrit event message from RabbitMQ: {}", body);

                            StreamEventsStringWork work = new StreamEventsStringWork(body);
                            logger.trace("putting work on queue: {}", work);
                            getWorkQueue().put(work);
                        }
                    } catch (InterruptedException e) {
                        logger.warn("Interrupted while putting work on queue!", e);
                    } finally {
                        ch.basicAck(response.getEnvelope().getDeliveryTag(), false);
                    }
                } else {
                    if (!shutdownRequested) {
                        try {
                            Thread.sleep(PAUSE_MILLIS);
                        } catch (InterruptedException e) {
                            logger.warn("Interrupted in pause.", e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.warn("Connection error.");
        } finally {
            if (shutdownRequested) {
                logger.trace("Disconnecting connection due to shutdown requested.");
            }
            try {
                logger.trace("Connection closing...");
                connection.close();
            } catch (IOException e) {
                logger.warn("Error when closing connection.", e);
            }
        }
    }

    /**
     * Connects to the RabbitMQ.
     *
     * @return not null if everything is well, null if connect and reconnect failed.
     */
    private Connection connect() {
        final ConnectionFactory factory = new ConnectionFactory();
        Connection conn = null;

        logger.trace("Connecting to RabbitMQ.");
        try {
            factory.setUri(rabbitmqUri);
        } catch (Exception e) {
            logger.warn("Unexpected operation.", e);
            return null;
        }

        while (!shutdownRequested) {
            try {
                conn = factory.newConnection();
                logger.trace("Connection opened.");

                conn.addShutdownListener(new ShutdownListener() {
                    @Override
                    public void shutdownCompleted(ShutdownSignalException cause) {
                        logger.trace("Connection closed.");
                        notifyConnectionDown();
                    }
                });

                notifyConnectionEstablished();
                logger.info("Ready to receive data from Gerrit");
            } catch (IOException e) {
                logger.warn("Cannot establish connection to RabbitMQ.", e);
                notifyConnectionDown();
            }

            if (conn == null) {
                //If we end up here, sleep for a while and then go back up in the loop.
                logger.trace("Sleeping for a bit.");
                try {
                    Thread.sleep(CONNECT_SLEEP);
                } catch (InterruptedException e) {
                    logger.warn("Got interrupted while sleeping.", e);
                }
            } else {
                break;
            }
        }
        return conn;
    }
}
