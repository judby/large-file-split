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

import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * Helper class for PUT/POST ByteBuffer content via HttpClient
 * <p>
 * Inspired by OneShotPublisher from <a href="https://docs.oracle.com/en/java/javase/23/docs/api/java.base/java/util/concurrent/Flow.html">Flow</a>
 */
public class SimpleByteBufferBodyPublisher implements HttpRequest.BodyPublisher {
    private final ByteBuffer byteBuffer;

    private boolean subscribed; // true after first subscribe

    public SimpleByteBufferBodyPublisher(ByteBuffer byteBuffer) {
        this.byteBuffer = Objects.requireNonNull(byteBuffer, "byteBuffer");
    }

    @Override
    public long contentLength() {
        return byteBuffer.remaining();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (subscribed)
            subscriber.onError(new IllegalStateException());
        else {
            subscribed = true;
            subscriber.onSubscribe(new SimpleByteBufferSubscription(subscriber));
        }
    }

    class SimpleByteBufferSubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super ByteBuffer> subscriber;
        private boolean completed;

        SimpleByteBufferSubscription(Flow.Subscriber<? super ByteBuffer> subscriber) {
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            if (!completed) {
                completed = true;
                if (n <= 0) {
                    subscriber.onError(new IllegalArgumentException());
                } else {
                    subscriber.onNext(byteBuffer);
                    subscriber.onComplete();
                }
            }
        }

        @Override
        public void cancel() {
            completed = true;
        }
    }
}
