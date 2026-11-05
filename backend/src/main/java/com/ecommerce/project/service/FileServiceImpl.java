package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX = 2 * 1024 * 1024;

    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new APIException("Image is required");
        }
        if (file.getSize() > MAX) {
            throw new APIException("Image too large");
        }
        String contentType = file.getContentType();
        if (contentType == null || !TYPES.contains(contentType)) {
            throw new APIException("Invalid image type");
        }
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.contains(".")) {
            throw new APIException("Invalid image name");
        }
        String randomId = UUID.randomUUID().toString();
        String fileName = randomId.concat(originalFileName.substring(originalFileName.lastIndexOf('.')));
        String filePath = path + File.separator + fileName;
        File folder = new File(path);
        if (!folder.exists())
            folder.mkdir();
        Files.copy(file.getInputStream(), Paths.get(filePath));
        return fileName;
    }
}
