package com.udby.blog.doublecheckedlocking;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class VeryExpensiveResource {
    private static final AtomicInteger CREATE_COUNT = new AtomicInteger();

    public VeryExpensiveResource() {
        System.out.printf("VeryExpensiveResource created: %d%n", CREATE_COUNT.incrementAndGet());
        try {
            Thread.sleep(Duration.ofSeconds(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static int createCount() {
        return CREATE_COUNT.get();
    }
}
