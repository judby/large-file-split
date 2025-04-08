package com.udby.blog.doublecheckedlocking;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.StructuredTaskScope;

import static org.assertj.core.api.Assertions.*;

class StableValueSingletonTest {
    @Test
    void getInstanceMultiThreaded() throws Exception {
        final var availableProcessors = Runtime.getRuntime().availableProcessors();
        final var threadCount = availableProcessors > 1 ? availableProcessors - 1 : availableProcessors;
        final var threadFactory = Thread.ofPlatform().factory();
        final var wait = new CountDownLatch(threadCount);
        final var go = new CountDownLatch(1);

        try (final var scope = new StructuredTaskScope.ShutdownOnSuccess<VeryExpensiveResource>("demo", threadFactory)) {
            for (int i = 0; i < threadCount; i++) {
                final var id = i + 1;
                scope.fork(() -> {
                    wait.countDown();
                    System.out.printf("Started %d and waiting...%n", id);
                    go.await();
                    return StableValueSingleton.getInstance();
                });
            }
            // Wait until all threads are started
            wait.await();
            // Then get them going
            go.countDown();
            scope.join();
        }

        System.out.println(VeryExpensiveResource.createCount());

        assertThat(VeryExpensiveResource.createCount()).isOne();
    }
}
