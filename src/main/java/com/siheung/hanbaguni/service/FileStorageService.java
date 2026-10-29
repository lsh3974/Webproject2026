package com.siheung.hanbaguni.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path rootLocation = Paths.get("uploads").toAbsolutePath().normalize();

    public FileStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            throw new RuntimeException("업로드 폴더를 생성할 수 없습니다.", e);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            originalFilename = "file";
        }

        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex).toLowerCase();
        }

        // 이미지 확장자 검증
        if (!extension.matches("^\\.(png|jpg|jpeg|gif|webp)$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일(png, jpg, jpeg, gif, webp)만 업로드할 수 있습니다.");
        }

        String storedFilename = UUID.randomUUID().toString() + extension;
        Path destinationFile = this.rootLocation.resolve(storedFilename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "파일 저장에 실패했습니다.", e);
        }

        return "/uploads/" + storedFilename;
    }
}
