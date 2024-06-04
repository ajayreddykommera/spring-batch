package com.ajay.spring_batch.service;

import com.azure.storage.blob.BlobClient;
import org.springframework.core.io.InputStreamResource;

public class BlobInputStreamResource extends InputStreamResource {

    private final BlobClient blobClient;

    public BlobInputStreamResource(BlobClient blobClient) {
        super(blobClient.openInputStream());
        this.blobClient = blobClient;
    }

    @Override
    public String getFilename() {
        return blobClient.getBlobName();
    }
}
