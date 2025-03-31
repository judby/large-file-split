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
package com.udby.blog.largefilesplit.s3;

import com.udby.blog.largefilesplit.LeaseQueue;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Helps doing message digests/MD5 checksums in a thread-safe not-synchronized manner...
 */
public class MessageDigestHelper {
    public static final MessageDigestHelper MD5 = new MessageDigestHelper("MD5");

    private final LeaseQueue<MessageDigest> leaseQueue = new LeaseQueue<>(256, this::clonedPrototype, MessageDigest::reset);
    private final MessageDigest prototype;

    public MessageDigestHelper(String algorithm) {
        try {
            final var messageDigest = MessageDigest.getInstance(algorithm);
            final var cloned = cloned(messageDigest);
            this.prototype = messageDigest;
            this.leaseQueue.resetAndOffer(cloned);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private MessageDigest clonedPrototype() {
        return cloned(prototype);
    }

    private static MessageDigest cloned(MessageDigest messageDigest) {
        try {
            return (MessageDigest) messageDigest.clone();
        } catch (CloneNotSupportedException e) {
            throw new IllegalStateException(e);
        }
    }

    public byte[] digest(ByteBuffer byteBuffer) {
        return leaseQueue.leaseAndDo(digest -> {
            digest.update(byteBuffer);
            return digest.digest();
        });
    }
}
