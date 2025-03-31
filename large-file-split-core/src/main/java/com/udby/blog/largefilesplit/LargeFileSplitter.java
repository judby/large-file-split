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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.file.StandardOpenOption.READ;

/**
 * Simple tool for splitting very large files (>100M) and processing as smaller parts.
 * Part size must be below 2G.
 * Specifically created for using the amazon <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpu-upload-object.html">S3 multipart file upload</a>.
 * <p/>
 * Simple usage:
 * <pre>
 * {@code
 *     // create splitter that splits into parts of ~16MiB
 *     var largeFileSplitter = LargeFileSplitter.fromFile(pathToLargeFile, 16_777_216L);
 *     // do the splitting and wait for its termination
 *     var partCount = largeFileSplitter.processInVirtualThreads((partNumber, byteBuffer) -> {
 *         // process part in byteBuffer
 *     });
 *     // check exception status...
 *     if (largeFileSplitter.exception() != null) {
 *         // handle Exception
 *     }
 * }
 * </pre>
 */
public class LargeFileSplitter {
    public static final long ONE_K = 1024L;
    public static final long ONE_M = ONE_K * ONE_K;
    public static final long ONE_G = ONE_M * ONE_K;
    public static final long TWO_G = 2L * ONE_G;

    private final Path file;
    private final long partSize;
    private final AtomicReference<Exception> exceptionCaught = new AtomicReference<>();

    /**
     * Create LargeFileSplitter given parameters:
     *
     * @param file     Path to file to split
     * @param partSize Part size
     */
    public LargeFileSplitter(Path file, long partSize) {
        if (TWO_G <= partSize) {
            throw new IllegalArgumentException("partSize must be below 2G, %d".formatted(partSize));
        }
        if (!Files.isReadable(Objects.requireNonNull(file, "file"))) {
            throw new IllegalArgumentException("File not readable: %s".formatted(file));
        }
        this.file = file;
        this.partSize = partSize;
    }

    /**
     * Create LargeFileSplitter from large file
     *
     * @param file     Path to file to split
     * @param partSize P    art size
     * @return Instance with sane defaults
     */
    public static LargeFileSplitter fromFile(Path file, long partSize) {
        return new LargeFileSplitter(file, partSize);
    }

    /**
     * Split the file using virtual threads using the given part processor and await termination
     *
     * @param processor FilePartProcessor handling each part of the file
     * @return number of parts created
     */
    public int processInVirtualThreads(FilePartProcessor processor) {
        try (final var executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            return process(executorService, processor);
        }
    }

    private int process(ExecutorService executorService, FilePartProcessor processor) {
        final var size = fileSize();

        // current part within all parts of this file...
        int parts = 0;
        try (final var channel = FileChannel.open(file, READ); final var arena = Arena.ofShared()) {
            final var memorySegment = channel.map(READ_ONLY, 0L, size, arena);

            // running offset into off-heap memory segment
            long offset = 0L;
            while (offset < size) {
                parts++;

                final var length = length(size, offset, partSize);
                final var slice = memorySegment.asSlice(offset, length);
                final var partBuffer = slice.asByteBuffer();

                final var partNumber = parts;

                // Send this part for processing via the executor service
                executorService.execute(() -> {
                    try {
                        processor.processPart(partNumber, partBuffer);
                    } catch (Exception e) {
                        exceptionCaught.compareAndSet(null, e);
                        executorService.shutdownNow();
                        throw new IllegalStateException("Processing part %d of %s (shutting down execution)".formatted(partNumber, file), e);
                    }
                });

                offset += length;
            }

            executorService.shutdown();
            executorService.awaitTermination(10, TimeUnit.HOURS);
        } catch (Exception e) {
            exceptionCaught.compareAndSet(null, e);
            executorService.shutdownNow();
            throw new IllegalStateException("Processing slices (part %d) of %s (shutting down execution)".formatted(parts, file), e);
        }

        return parts;
    }

    /**
     * If processing is being terminated by an Exception returns the Exception
     *
     * @return Optional with exception if there was an IOException terminating the processing
     */
    public Optional<Exception> exception() {
        return Optional.ofNullable(exceptionCaught.get());
    }

    private long length(long size, long offset, long blockSize) {
        return Math.min(blockSize, size - offset);
    }

    private long fileSize() {
        try {
            return Files.size(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to get size of file %s".formatted(file), e);
        }
    }

    @FunctionalInterface
    public interface FilePartProcessor {
        /**
         * Process part (partNumber) of a file mapped into the byteBuffer
         *
         * @param partNumber Part number, 1-based
         * @param byteBuffer Mapped file part
         * @throws IOException
         */
        void processPart(int partNumber, ByteBuffer byteBuffer) throws IOException;
    }
}
