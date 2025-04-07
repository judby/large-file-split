package com.udby.blog.doublecheckedlocking;

public class OldFashionedSingleton {
    private static volatile VeryExpensiveResource INSTANCE;

    private static final Object LOCK = new Object();

    public static VeryExpensiveResource getInstance() {
        VeryExpensiveResource instance = INSTANCE;
        if (instance != null) {
            return instance;
        }
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new VeryExpensiveResource();
            }
            instance = INSTANCE;
        }
        return instance;
    }
}
