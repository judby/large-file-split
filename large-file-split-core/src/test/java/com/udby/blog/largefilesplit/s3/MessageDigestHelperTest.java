package com.udby.blog.largefilesplit.s3;

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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MessageDigestHelperTest {
    @Test
    void digest_sunshine_succeeds() {
        // Given
        final var byteBuffer = ByteBuffer.wrap("The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));

        // When
        final var digest = MessageDigestHelper.MD5.digest(byteBuffer);

        // Then
        assertThat(digest).asHexString()
                .isEqualTo("9E107D9D372BB6826BD81D3542A419D6");
    }
}
