package com.udby.blog.doublecheckedlocking;

import java.util.function.Supplier;

public class StableValueSingleton {
    private static final Supplier<VeryExpensiveResource> SINGLETON_SUPPLIER =
            StableValue.supplier(() -> new VeryExpensiveResource());

    public static VeryExpensiveResource getInstance() {
        return SINGLETON_SUPPLIER.get();
    }
}
