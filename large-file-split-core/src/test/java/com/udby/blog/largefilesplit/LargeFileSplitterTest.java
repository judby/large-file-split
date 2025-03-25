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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static com.udby.blog.largefilesplit.LargeFileSplitter.ONE_M;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

class LargeFileSplitterTest {
    @TempDir
    private Path output;

    @ParameterizedTest
    @ValueSource(longs = {8 * ONE_M /*, 16 * ONE_M, 32 * ONE_M*/})
    void processInVirtualThreads_splitToTemporaryFiles_succeeds(long splitSize) throws Exception {
        // Given
        // A large file max 25% of available in temporary file system
        final var fileStore = fileStore();

        final var usableSpace = fileStore.getUsableSpace();
        final var twentyFivePercent = usableSpace / 4;
        // Size of actual file inspiring this implementation
        final var actualFileSize = 9841183379L;
        final var size = Math.min(actualFileSize, twentyFivePercent);

        final var largeFile = createLargeTempFile(size);

        System.out.printf("Created file (size %.3fM) to be split by %.1fM%n", size * 1.0 / ONE_M, splitSize * 1.0 / ONE_M);

        final var t0 = System.nanoTime();

        final var largeFileSplitter = LargeFileSplitter.fromFile(largeFile, splitSize);

        // When
        final var parts = largeFileSplitter.processInVirtualThreads((partNumber, byteBuffer) -> {
            final var out = output.resolve("part-%04d.split".formatted(partNumber));
            try (final var channel = FileChannel.open(out, WRITE, CREATE_NEW)) {
                channel.write(byteBuffer);
            }
        });

        System.out.printf("Timing: %fs Part count: %d%n", 1e-9 * (System.nanoTime() - t0), parts);

        // Then
        final var partsSize = calculateSizeOfParts();

        assertThat(partsSize).isEqualTo(size);
    }

    @Test
    void processInVirtualThreads_exceptionThrownAt100Parts_reportsException() throws Exception {
        // Given
        final var whenToFail = 100;
        final var exceptionToThrow = new IOException("failed");

        // A large file max 25% of available in temporary file system
        final var fileStore = fileStore();

        final var usableSpace = fileStore.getUsableSpace();
        final var twentyFivePercent = usableSpace / 4;
        // Size of actual file inspiring this implementation
        final var actualFileSize = 9841183379L;
        final var size = Math.min(actualFileSize, twentyFivePercent);

        final var largeFile = createLargeTempFile(size);

        final var splitSize = 32 * ONE_M;

        System.out.printf("Created file (size %.3fM) to be split by %.1fM%n", size * 1.0 / ONE_M, splitSize * 1.0 / ONE_M);

        final var largeFileSplitter = LargeFileSplitter.fromFile(largeFile, splitSize);

        // When
        final var parts = largeFileSplitter.processInVirtualThreads((partNumber, byteBuffer) -> {
            if (partNumber == whenToFail) {
                System.out.println("Throwing exception!");
                throw exceptionToThrow;
            }

            final var out = output.resolve("part-%04d.split".formatted(partNumber));
            try (final var channel = FileChannel.open(out, WRITE, CREATE_NEW)) {
                channel.write(byteBuffer);
            }
        });

        // Then
        assertThat(largeFileSplitter.exception())
                .isNotNull()
                .isSameAs(exceptionToThrow);

        final var partsSize = calculateSizeOfParts();

        System.out.printf("Transferred %.3fM of %.3fM (%d) bytes%n", partsSize * 1.0 / ONE_M, size * 1.0 / ONE_M, size - partsSize);

        assertThat(partsSize).isLessThan(size);
    }

    private Path createLargeTempFile(long size) {
        try {
            final var largeFile = Files.createTempFile(output, "large-%d-".formatted(size), ".file");
            try (final var channel = FileChannel.open(largeFile, WRITE)) {
                channel.position(size - 1);
                channel.write(ByteBuffer.allocate(1));
            }
            return largeFile;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private FileStore fileStore() {
        try {
            return Files.getFileStore(output);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    private long calculateSizeOfParts() {
        try (final var stream = Files.list(output)) {
            return stream
                    .filter(p -> p.getFileName().toString().startsWith("part-"))
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    })
                    .sum();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
