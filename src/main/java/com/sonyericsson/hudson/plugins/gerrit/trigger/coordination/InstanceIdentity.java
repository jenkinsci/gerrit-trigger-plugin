/*
 *  The MIT License
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.coordination;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;

/**
 * Generates an identifier that is unique among concurrently-running JVM processes of this plugin.
 *
 * <p>Used to name per-instance state files (e.g. missed-events playback timestamps) so that
 * multiple Jenkins controller JVMs sharing one NFS-mounted {@code JENKINS_HOME} never write to
 * the same file. Stability across restarts is deliberately NOT a goal here - directories of
 * per-instance files are rescanned fresh on every use, so a new id after every restart is fine,
 * and in fact avoids ever colliding with a not-yet-cleaned-up file from a previous run of this
 * same process.</p>
 *
 * <p>Not reused from {@code HazelcastConfig.generateInstanceName()}: that method derives a name
 * from the Jenkins root URL host plus the local hostname, which is not guaranteed unique when
 * multiple controller replicas share one external URL, or when multiple JVMs run on the same
 * host/container. This class instead includes the process id, which distinguishes co-located
 * JVMs on the same host.</p>
 */
public final class InstanceIdentity {

    private static final Logger logger = LoggerFactory.getLogger(InstanceIdentity.class);

    private static volatile String instanceId;

    private InstanceIdentity() {
    }

    /**
     * Returns the identifier for this JVM process, computing it on first use.
     *
     * @return a filesystem-safe, unique-among-concurrent-JVMs identifier (non-null)
     */
    @NonNull
    public static String get() {
        String id = instanceId;
        if (id == null) {
            synchronized (InstanceIdentity.class) {
                id = instanceId;
                if (id == null) {
                    id = compute();
                    instanceId = id;
                }
            }
        }
        return id;
    }

    /**
     * Computes a filesystem-safe identifier, e.g. {@code "12345-hostname"} from the JVM's
     * runtime name (typically {@code "<pid>@<hostname>"}), falling back to a timestamp-based
     * value if the runtime name cannot be determined for any reason.
     *
     * @return the computed identifier
     */
    private static String compute() {
        try {
            String runtimeName = ManagementFactory.getRuntimeMXBean().getName();
            String sanitized = runtimeName.replaceAll("[^a-zA-Z0-9.-]", "-");
            if (!sanitized.isEmpty()) {
                return sanitized;
            }
        } catch (Exception e) {
            logger.warn("Failed to determine JVM runtime name for instance identity", e);
        }
        return "unknown-" + System.currentTimeMillis();
    }
}
