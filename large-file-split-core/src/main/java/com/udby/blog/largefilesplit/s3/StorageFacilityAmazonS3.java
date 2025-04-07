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

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchUploadException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;

public final class StorageFacilityAmazonS3 implements StorageFacility {
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public StorageFacilityAmazonS3(S3Client s3Client, S3Presigner s3Presigner) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
    }

    @Override
    public boolean checkBucketExists(String bucket) {
        try {
            s3Client.headBucket(requet -> requet.bucket(bucket));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    @Override
    public String prepareMultipartUpload(String bucket, String key, String contentType) {
        final var result = s3Client.createMultipartUpload(multipartRequest ->
                multipartRequest.bucket(bucket)
                        .key(key)
                        .contentType(contentType));
        return result.uploadId();
    }

    @Override
    public void abortMultipartUpload(String bucket, String key, String uploadId) {
        try {
            s3Client.abortMultipartUpload(abortRequest ->
                    abortRequest.bucket(bucket)
                            .key(key)
                            .uploadId(uploadId));
        } catch (NoSuchUploadException ignored) {
            // Don't mind aborting non-existent uploads
        }
    }

    @Override
    public String presignedUploadPartRequest(String bucket, String key, String uploadId, int partNumber, String contentMd5, Duration signatureDuration) {
        final var presigned = s3Presigner.presignUploadPart(presignRequest ->
                presignRequest.uploadPartRequest(uploadPartRequest ->
                                uploadPartRequest.bucket(bucket)
                                        .contentMD5(contentMd5)
                                        .key(key)
                                        .partNumber(partNumber)
                                        .uploadId(uploadId))
                        .signatureDuration(signatureDuration));

        return presigned.url()
                .toString();
    }

    @Override
    public void completeMultipartUpload(String bucket, String key, String uploadId, Collection<? extends UploadedPart> parts) {
        // They must be sorted by partNumber...
        final var sortedCompletedParts = parts.stream()
                .sorted((Comparator.comparing(UploadedPart::partNumber)))
                .map(part ->
                        CompletedPart.builder()
                                .eTag(part.eTag())
                                .partNumber(part.partNumber())
                                .build())
                .toList();

        s3Client.completeMultipartUpload(request ->
                request.bucket(bucket)
                        .key(key)
                        .multipartUpload(mp ->
                                mp.parts(sortedCompletedParts))
                        .uploadId(uploadId));
    }
}
