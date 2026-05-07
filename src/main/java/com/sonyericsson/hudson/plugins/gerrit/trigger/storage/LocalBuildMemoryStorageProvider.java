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
package com.sonyericsson.hudson.plugins.gerrit.trigger.storage;

import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorage;
import com.sonyericsson.hudson.plugins.gerrit.trigger.spi.BuildMemoryStorageProvider;
import hudson.Extension;

/**
 * Provider for local TreeMap-based BuildMemory storage.
 * <p>
 * This is the default storage implementation, always available and used
 * in standalone Jenkins instances (non-cluster mode).
 * <p>
 * Priority is set to 0 (lowest), meaning distributed implementations
 * (e.g., Hazelcast with priority 100) will take precedence when available.
 *
 */
@Extension(ordinal = 0)
public class LocalBuildMemoryStorageProvider extends BuildMemoryStorageProvider {

    /**
     * Default constructor.
     */
    public LocalBuildMemoryStorageProvider() {
        // Default constructor
    }

    @Override
    public int getPriority() {
        return 0;  // Lowest priority - used as fallback
    }

    @Override
    public boolean isAvailable() {
        return true;  // Always available
    }

    @Override
    public BuildMemoryStorage createStorage() {
        return new LocalBuildMemoryStorage();
    }

    @Override
    public String getName() {
        return "Local (TreeMap)";
    }
}
