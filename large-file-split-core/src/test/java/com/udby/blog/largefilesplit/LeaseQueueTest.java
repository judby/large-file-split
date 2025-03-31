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
package com.udby.blog.largefilesplit;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaseQueueTest {
    @Test
    void resetAndOffer_sunshine_succeeds() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new, Tester::reset);

        // When
        final var t = new Tester();
        leaseQueue.resetAndOffer(t);

        // Then
        assertThat(t.resetCount).isOne();
        final var x = leaseQueue.pollOrCreate();
        assertThat(x).isSameAs(t);
    }

    @Test
    void resetAndOffer_resetFails_itemNotOfferedToQueue() {
        // Given
        Consumer<Tester> resetMethod = _ -> {
            throw new IllegalStateException("failed");
        };

        final var leaseQueue = new LeaseQueue<>(1, Tester::new, resetMethod);

        // When - Then
        final var t = new Tester();
        try {
            leaseQueue.resetAndOffer(t);
        } catch (IllegalStateException _) {
        }

        // Then
        final var x = leaseQueue.pollOrCreate();
        assertThat(x).isNotSameAs(t);
    }

    @Test
    void resetAndOffer_noResetRequired_isNotReset() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new);

        // When
        final var t = new Tester();
        leaseQueue.resetAndOffer(t);

        // Then
        assertThat(t.resetCount).isZero();
        final var x = leaseQueue.pollOrCreate();
        assertThat(x).isSameAs(t);
    }

    @Test
    void resetAndOffer_null_throwsNPE() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new);

        // When - Then
        assertThatThrownBy(() -> leaseQueue.resetAndOffer(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void lease_queueIsEmpty_newItemCreated() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new);

        // When
        final var lease = leaseQueue.lease();

        // Then
        assertThat(lease).isNotNull();
        assertThat(lease.get()).isNotNull();
    }

    @Test
    void lease_queueIsNotEmpty_returnsQueued() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new);
        final var t = new Tester();
        leaseQueue.resetAndOffer(t);

        // When
        final var lease = leaseQueue.lease();

        // Then
        assertThat(lease).isNotNull();
        assertThat(lease.get()).isNotNull().isSameAs(t);
    }

    @Test
    void pollOrCreate_queueIsEmpty_newItemCreated() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new);

        // When
        final var t = leaseQueue.pollOrCreate();

        // Then
        assertThat(t).isNotNull();
    }

    @Test
    void pollOrCreate_queueIsNotEmpty_returnsQueued() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new);
        final var t = new Tester();
        leaseQueue.resetAndOffer(t);

        // When
        final var x = leaseQueue.pollOrCreate();

        // Then
        assertThat(x).isNotNull()
                .isSameAs(t);
    }

    @Test
    void leaseAndDo_sunshine_succeeds() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new, Tester::reset);
        final var t = new Tester();
        leaseQueue.resetAndOffer(t);

        // When
        final int lifeTheUniverseEtc = leaseQueue.leaseAndDo(_ -> 42);

        // Then
        assertThat(lifeTheUniverseEtc).isEqualTo(42);
        // actually reset twice...
        assertThat(t.resetCount).isEqualTo(2);
        // and it is back in the queue
        final var x = leaseQueue.pollOrCreate();
        assertThat(x).isSameAs(t);
    }

    @Test
    void leaseAndDo_actionThrowsException_itemResetAndPutBack() {
        // Given
        final var leaseQueue = new LeaseQueue<>(1, Tester::new, Tester::reset);

        // When
        try {
            leaseQueue.leaseAndDo(_ -> {
                throw new IllegalStateException();
            });
        } catch (IllegalStateException _) {
        }

        // Then
        final var x = leaseQueue.pollOrCreate();
        assertThat(x.resetCount).isOne();
    }

    @Nested
    class LeaseQueueLeaseTest {
        @Test
        void close_once_returnsItemToQueue() {
            // Given
            final var leaseQueue = new LeaseQueue<>(1, Tester::new, Tester::reset);
            final var lease = leaseQueue.lease();

            // When
            final var t = lease.get();
            lease.close();

            // Then
            assertThat(t.resetCount).isOne();
            final var x = leaseQueue.pollOrCreate();
            assertThat(x).isSameAs(t);
        }

        @Test
        void close_twice_onlyResetsOnce() {
            // Given
            final var leaseQueue = new LeaseQueue<>(1, Tester::new, Tester::reset);
            final var lease = leaseQueue.lease();

            // When
            final var t = lease.get();
            // closing twice
            lease.close();
            lease.close();

            // Then
            assertThat(t.resetCount).isOne();
            final var x = leaseQueue.pollOrCreate();
            assertThat(x).isSameAs(t);
        }

        @Test
        void get_closedLease_throwsISE() {
            // Given
            final var leaseQueue = new LeaseQueue<>(1, Tester::new, Tester::reset);
            final var lease = leaseQueue.lease();
            lease.close();

            // When - Then
            assertThatThrownBy(lease::get)
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    static class Tester {
        private int resetCount;

        void reset() {
            resetCount++;
        }
    }
}
