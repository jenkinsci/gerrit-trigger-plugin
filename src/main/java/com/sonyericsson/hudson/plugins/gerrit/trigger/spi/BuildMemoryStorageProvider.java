/*
 *  The MIT License
 *
 *  Copyright 2010 Sony Ericsson Mobile Communications. All rights reserved.
 *  Copyright 2012 Sony Mobile Communications AB. All rights reserved.
 *  Copyright 2026 CloudBees, Inc.
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
package com.sonyericsson.hudson.plugins.gerrit.trigger.spi;

import hudson.ExtensionPoint;

/**
 * Extension point for BuildMemory storage implementations.
 * <p>
 * Implementations of this abstract class provide different storage backends
 * for BuildMemory. They are discovered automatically by Jenkins via the
 * {@code @Extension} annotation.
 * <p>
 * The factory selects the implementation with the highest priority that is
 * currently available.
 * <p>
 * <strong>Priority Convention:</strong>
 * <ul>
 *   <li>Local implementations: 0 (default, always available)</li>
 *   <li>Distributed implementations: 100 (cluster mode)</li>
 * </ul>
 * <p>
 * <strong>Example usage:</strong>
 * <pre>
 * &#64;Extension(ordinal = 0)
 * public class LocalBuildMemoryStorageProvider extends BuildMemoryStorageProvider {
 *     &#64;Override
 *     public int getPriority() {
 *         return 0;
 *     }
 *
 *     &#64;Override
 *     public boolean isAvailable() {
 *         return true;  // Always available
 *     }
 *
 *     &#64;Override
 *     public BuildMemoryStorage createStorage() {
 *         return new LocalBuildMemoryStorage();
 *     }
 * }
 * </pre>
 *
 * @see BuildMemoryStorage
 * @see com.sonyericsson.hudson.plugins.gerrit.trigger.gerritnotifier.model.BuildMemory
 */
public abstract class BuildMemoryStorageProvider implements ExtensionPoint {

    /**
     * Returns the priority for selecting this implementation.
     * <p>
     * When multiple implementations are available, the one with the highest
     * priority is selected. This allows distributed implementations to take
     * precedence over local ones when cluster mode is enabled.
     * <p>
     * <strong>Convention:</strong>
     * <ul>
     *   <li>Local implementations: 0</li>
     *   <li>Distributed implementations: 100</li>
     * </ul>
     *
     * @return the priority value (higher values take precedence)
     */
    public abstract int getPriority();

    /**
     * Checks if this implementation is currently available.
     * <p>
     * Local implementations typically return {@code true} always, while
     * distributed implementations check if cluster mode is enabled and
     * the cluster infrastructure (e.g., Hazelcast) is accessible.
     * <p>
     * This method is called during factory initialization and may be called
     * multiple times.
     *
     * @return true if this implementation can be used, false otherwise
     */
    public abstract boolean isAvailable();

    /**
     * Creates the storage instance.
     * <p>
     * This method is called once by the factory when this provider is selected.
     * The returned instance will be used for all BuildMemory operations.
     * <p>
     * <strong>Important:</strong> This method should be idempotent - calling it
     * multiple times should return independent storage instances.
     *
     * @return a new storage implementation instance
     */
    public abstract BuildMemoryStorage createStorage();

    /**
     * Returns a human-readable name for this provider.
     * <p>
     * Used for logging and diagnostics. Default implementation returns the
     * simple class name.
     *
     * @return the provider name
     */
    public String getName() {
        return this.getClass().getSimpleName();
    }
}
