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

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.tomakehurst.wiremock.client.WireMock.givenThat;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class LargeFileSplitterWiremockTest {
    @TempDir
    private Path output;

    @Test
    void processInVirtualThreads_putViaWiremock_succeeds(WireMockRuntimeInfo wmRuntimeInfo) {
        givenThat(put(urlMatching("/uploads\\?partNumber=(\\d+)"))
                .willReturn(ok()));

        // somewhat below 2G
        final var largeFile = createLargeTempFile(2_000_000_000L);

        try (final var httpClient = HttpClient.newHttpClient()) {
            final var largeFileSplitter = LargeFileSplitter.fromFile(largeFile, 32 * LargeFileSplitter.ONE_M);
            largeFileSplitter.processInVirtualThreads((partNumber, buffer) -> {
                final var byteBufferBodyPublisher = new SimpleByteBufferBodyPublisher(buffer);

                final var uri = URI.create((wmRuntimeInfo.getHttpBaseUrl() + "/uploads?partNumber=%d").formatted(partNumber));
                HttpRequest httpRequestUpload = HttpRequest.newBuilder()
                        .header("Content-Type", "application/pdf")
                        .uri(uri)
                        .PUT(byteBufferBodyPublisher)
                        .build();

                final HttpResponse<String> response;
                try {
                    response = httpClient.send(httpRequestUpload, HttpResponse.BodyHandlers.ofString());
                } catch (InterruptedException e) {
                    throw new IllegalStateException(e);
                }

                if (response.statusCode() != 200) {
                    throw new IOException("HTTP Status: %d".formatted(response.statusCode()));
                }
            });

            assertThat(largeFileSplitter.exception()).isNull();
        }
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
}
