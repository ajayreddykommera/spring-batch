package com.ajay.spring_batch.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlobStorageService {

    private final BlobServiceClient blobServiceClient;
    @Value("${azure.storage.container_name}")
    private String containerName;

    public List<String> uploadFile(List<MultipartFile> files) {
        BlobContainerClient containerClient = blobServiceClient.getBlobContainerClient(containerName);
        if (!containerClient.exists()) {
            containerClient.create();
        }
        List<String> blobUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (validateCSVHeader(file)) {
                String newFileName = LocalDateTime.now().toString() + "-persons";
                BlobClient blobClient = containerClient.getBlobClient(newFileName);
                try (InputStream inputStream = file.getInputStream()) {
                    blobClient.upload(inputStream, file.getSize(), true);
                    blobUrls.add(blobClient.getBlobUrl());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to upload file to Azure Blob Storage", e);
                }
            } else {
                blobUrls.add("Failed to read CSV file - " + file.getName());
            }
        }
        return blobUrls;
    }

    public boolean validateCSVHeader(MultipartFile file) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String headerLine = br.readLine();
            if (headerLine != null) {
                List<String> headers = Arrays.asList(headerLine.split(","));
                return headers.contains("firstname") &&
                        headers.contains("lastname") &&
                        headers.contains("email") &&
                        headers.contains("phone") &&
                        headers.contains("dob");
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read CSV file", e);
        }
        return false;
    }
}

