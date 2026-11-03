package com.trustvault.service;

import com.trustvault.exception.FileStorageException;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageLocation;
    private final Tika tika = new Tika();

    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList(
            "pdf", "docx", "doc", "txt", "csv", "xlsx", "xls", "png", "jpg", "jpeg", "json"
    );

    private static final long MAX_FILE_SIZE = 15 * 1024 * 1024; // 15 MB

    public FileStorageService(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.storageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageLocation);
        } catch (Exception ex) {
            throw new FileStorageException("Could not create the upload directory: " + uploadDir);
        }
    }

    public String storeFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new FileStorageException("Failed to store empty file.");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new FileStorageException("File size exceeds maximum limit of 15MB.");
        }

        String rawFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed");

        // Path Traversal Defense
        if (rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            throw new FileStorageException("Filename contains invalid path sequence: " + rawFilename);
        }

        // Extension validation
        String extension = getFileExtension(rawFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException("File extension ." + extension + " is not permitted for security reasons.");
        }

        // Magic Byte & MIME Type verification via Apache Tika
        try (InputStream is = file.getInputStream()) {
            String detectedType = tika.detect(is, rawFilename);
            if (isExecutableOrScript(detectedType, extension)) {
                throw new FileStorageException("Potential malicious file detected. MIME type: " + detectedType);
            }
        } catch (IOException e) {
            throw new FileStorageException("Failed to inspect file contents: " + e.getMessage());
        }

        // Store file with safe unique UUID name
        String uniqueFilename = UUID.randomUUID().toString() + "_" + System.currentTimeMillis() + "." + extension;

        try {
            Path targetLocation = this.storageLocation.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return uniqueFilename;
        } catch (IOException ex) {
            throw new FileStorageException("Could not store file " + rawFilename + ". Please try again!");
        }
    }

    public Resource loadFileAsResource(String storageFilename) {
        try {
            Path filePath = this.storageLocation.resolve(storageFilename).normalize();
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new FileStorageException("File not found or unreadable: " + storageFilename);
            }
        } catch (MalformedURLException ex) {
            throw new FileStorageException("File not found " + storageFilename);
        }
    }

    public Path getFilePath(String storageFilename) {
        return this.storageLocation.resolve(storageFilename).normalize();
    }

    public String calculateChecksum(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Error calculating SHA-256 file checksum", e);
        }
    }

    private String getFileExtension(String filename) {
        int lastIndex = filename.lastIndexOf('.');
        if (lastIndex == -1 || lastIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastIndex + 1);
    }

    private boolean isExecutableOrScript(String mimeType, String extension) {
        List<String> dangerousMimes = Arrays.asList(
                "application/x-dosexec", "application/x-executable", "application/x-sh",
                "application/javascript", "text/javascript", "application/x-php",
                "application/x-httpd-php", "text/x-python", "text/x-perl"
        );
        List<String> dangerousExts = Arrays.asList(
                "exe", "bat", "cmd", "sh", "php", "jsp", "asp", "aspx", "vbs", "ps1"
        );
        return dangerousMimes.contains(mimeType.toLowerCase()) || dangerousExts.contains(extension.toLowerCase());
    }
}
