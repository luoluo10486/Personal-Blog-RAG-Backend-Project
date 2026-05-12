package com.personalblog.ragbackend.knowledge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class RustfsS3Config {

    @Bean
    public S3Client s3Client(RustfsProperties rustfsProperties) {
        return S3Client.builder()
                .endpointOverride(URI.create(rustfsProperties.getUrl()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                rustfsProperties.getAccessKeyId(),
                                rustfsProperties.getSecretAccessKey()
                        )
                ))
                .forcePathStyle(true)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(RustfsProperties rustfsProperties) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(rustfsProperties.getUrl()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                rustfsProperties.getAccessKeyId(),
                                rustfsProperties.getSecretAccessKey()
                        )
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
