package com.udby.blog.largefilesplit;

/*
Copyright 2025 Jesper Udby

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * LeaseQueue caches a maximum number of instances of T.
 * Implementation is meant for traditional object pooling where non-thread-safe instances
 * are shared among different threads in a thread-safe manner.
 * A reset concept is used to reset potentially stateful instances before returning them to the cache.
 * Implementation is carefully designed not to use traditional JAVA synchronization methods to avoid
 * pinning (JAVA-23) virtual threads.
 *
 * @param <T> Type of item to manage
 */
public class LeaseQueue<T> {
    private final Queue<T> leaseQueue;
    private final Supplier<T> newInstance;
    private final Consumer<T> resetInstance;

    /**
     * Create LeaseQueue with configured max capacity and the given creator and reset methods
     *
     * @param maxSize       Max number of instances of T to cache
     * @param newInstance   Supplier of T when queue is empty
     * @param resetInstance Consumer of T that resets instances of T when returning to the cache,
     *                      null if reset functionality is not needed
     */
    public LeaseQueue(int maxSize, Supplier<T> newInstance, Consumer<T> resetInstance) {
        this.newInstance = Objects.requireNonNull(newInstance);
        // ArrayBlockingQueue (JAVA-23) implementation does not use traditional synchronization
        this.leaseQueue = new ArrayBlockingQueue<>(maxSize);
        this.resetInstance = resetInstance;
    }

    public LeaseQueue(int maxSize, Supplier<T> newInstance) {
        this(maxSize, newInstance, null);
    }

    /**
     * Reset a non-null instance of T and return it to the queue
     *
     * @param value
     */
    public void resetAndOffer(T value) {
        Objects.requireNonNull(value);
        if (resetInstance != null) {
            resetInstance.accept(value);
        }
        leaseQueue.offer(value);
    }

    /**
     * Lease an instance of T, will not be queued until the lease is released(closed), to be used with
     * try-with-resources
     *
     * @return a Lease with a reference to an unqueued instance of T
     */
    public Lease lease() {
        return new Lease();
    }

    /**
     * Poll an instance of T from the queue or create a new is queue is empty.
     *
     * @return
     */
    public T pollOrCreate() {
        final T value = leaseQueue.poll();
        return value == null ? newInstance.get() : value;
    }

    /**
     * Obtain an instance of T (possibly queued), invoke the action on it, reset it, putting it back on the
     * queue and return the result of the action.
     *
     * @param action
     * @param <R>
     * @return
     */
    public <R> R leaseAndDo(Function<T, R> action) {
        final T value = pollOrCreate();
        try {
            return action.apply(value);
        } finally {
            resetAndOffer(value);
        }
    }

    /**
     * Lease holder. AutoCloseable to be used with try-with-resources; the lease is released when closed
     */
    public class Lease implements AutoCloseable, Supplier<T> {
        private final AtomicBoolean queued = new AtomicBoolean();
        private final T value;

        private Lease() {
            this.value = pollOrCreate();
        }

        @Override
        public void close() {
            if (queued.compareAndSet(false, true)) {
                resetAndOffer(this.value);
            }
        }

        @Override
        public T get() {
            if (queued.get()) {
                throw new IllegalStateException("Is queued");
            }
            return value;
        }
    }
}
