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

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Collection;

public sealed interface StorageFacility
        permits StorageFacilityAmazonS3 {
    static StorageFacility forAws(AwsCredentialsProvider credentialsProvider, Region region) {
        final var s3Client = S3Client.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .build();
        final var s3Presigner = S3Presigner.builder()
                .credentialsProvider(credentialsProvider)
                .region(region)
                .s3Client(s3Client)
                .build();
        return new StorageFacilityAmazonS3(s3Client, s3Presigner);
    }

    boolean checkBucketExists(String bucket);

    String prepareMultipartUpload(String bucket, String key, String contentType);

    void abortMultipartUpload(String bucket, String key, String uploadId);

    String presignedUploadPartRequest(String bucket, String key, String uploadId, int partNumber, String contentMd5, final Duration signatureDuration);

    void completeMultipartUpload(String bucket, String key, String uploadId, Collection<? extends UploadedPart> parts);

    interface UploadedPart {
        int partNumber();

        String eTag();
    }
}
