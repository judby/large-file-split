package com.udby.blog.doublecheckedlocking;

public class ModernSingleton {
    public static VeryExpensiveResource getInstance() {
        return SingleValue.INSTANCE;
    }
    private enum SingleValue {
        ;
        private static final VeryExpensiveResource INSTANCE = new VeryExpensiveResource();
    }
}
