package com.ecommerce.project.service;

/**
 * File Asset Service Interface
 *
 * Why & Where it is used:
 *   Specifies the contract for securely storing uploaded media assets (product images).
 *
 * Imports & Key Dependencies:
 *   - Spring Web: MultipartFile for receiving uploaded binary payloads.
 *
 * Exports & Consumers:
 *   - Implemented by FileServiceImpl.
 *   - Injected into ProductServiceImpl.
 *
 * Project Impact:
 *   Standardizes asset storage signatures for disk or object-store persistence.
 */

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileService {
    String uploadImage(String path, MultipartFile file) throws IOException;
}
