package com.ecommerce.project.service;

import com.ecommerce.project.exceptions.APIException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    private static final long MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB

    @Override
    public String uploadImage(String path, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new APIException("Image is required");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new APIException("Image too large (max 2MB)");
        }

        String declaredType = file.getContentType();
        if (declaredType == null || !ALLOWED_CONTENT_TYPES.contains(declaredType.toLowerCase())) {
            throw new APIException("Invalid image content type: " + declaredType);
        }

        // Validate actual magic bytes to prevent stored XSS / malicious file
        // masquerading
        String verifiedExtension = sniffImageExtension(file);
        if (verifiedExtension == null) {
            throw new APIException("Uploaded file is not a valid image or magic bytes mismatch declared type");
        }

        String randomId = UUID.randomUUID().toString();
        String safeFileName = randomId + verifiedExtension;
        String filePath = path + File.separator + safeFileName;

        File folder = new File(path);
        if (!folder.exists()) {
            folder.mkdirs();
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, Paths.get(filePath));
        }

        return safeFileName;
    }

    private String sniffImageExtension(MultipartFile file) throws IOException {
        byte[] header = new byte[12];
        int bytesRead;
        try (InputStream in = file.getInputStream()) {
            bytesRead = in.read(header);
        }
        if (bytesRead < 4) {
            return null;
        }

        // JPEG: FF D8 FF
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (bytesRead >= 8 &&
                (header[0] & 0xFF) == 0x89 && (header[1] & 0xFF) == 0x50 &&
                (header[2] & 0xFF) == 0x4E && (header[3] & 0xFF) == 0x47 &&
                (header[4] & 0xFF) == 0x0D && (header[5] & 0xFF) == 0x0A &&
                (header[6] & 0xFF) == 0x1A && (header[7] & 0xFF) == 0x0A) {
            return ".png";
        }

        // GIF: GIF87a or GIF89a
        if (bytesRead >= 6 &&
                header[0] == 'G' && header[1] == 'I' && header[2] == 'F' &&
                header[3] == '8' && (header[4] == '7' || header[4] == '9') && header[5] == 'a') {
            return ".gif";
        }

        // WEBP: RIFF....WEBP
        if (bytesRead >= 12 &&
                header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F' &&
                header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return ".webp";
        }

        return null;
    }
}
