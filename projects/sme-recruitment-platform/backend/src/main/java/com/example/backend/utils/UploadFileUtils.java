/*
 * 文件速览：
 * 1. 文件职责：收口通用上传辅助逻辑，包括扩展名提取、文件名清洗、uploads 目录落盘和 URL 生成。
 * 2. 对外入口：extractExtension、isAllowedExtension、storeUnderUploads、resolveUploadsDirectory。
 * 3. 关键结构：ASCII 安全文件名、统一 uploads 根目录、相对目录转访问 URL。
 * 4. 阅读建议：先看 storeUnderUploads，再看文件名与 URL 生成辅助方法。
 */
package com.example.backend.utils;

import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件工具类。
 * 仅处理通用的文件名与落盘逻辑，不承载业务层的大小、权限和格式错误提示。
 */
public final class UploadFileUtils {

    private UploadFileUtils() {
    }

    /**
     * 提取文件扩展名，统一转为小写；无扩展名时返回空字符串。
     */
    public static String extractExtension(String originalName) {
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            return "";
        }
        return originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 判断扩展名是否在允许范围内。
     */
    public static boolean isAllowedExtension(String extension, String... allowedExtensions) {
        if (!StringUtils.hasText(extension) || allowedExtensions == null || allowedExtensions.length == 0) {
            return false;
        }
        for (String allowedExtension : allowedExtensions) {
            if (extension.equalsIgnoreCase(allowedExtension)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将文件保存到 uploads 下的指定目录，并返回可直接访问的相对 URL。
     */
    public static String storeUnderUploads(MultipartFile file,
                                           Long ownerId,
                                           String fallbackBaseName,
                                           String... relativeDirSegments) throws IOException {
        Path uploadDir = resolveUploadsDirectory(relativeDirSegments);
        Files.createDirectories(uploadDir);

        String fileName = buildStoredFileName(ownerId, file == null ? null : file.getOriginalFilename(), fallbackBaseName);
        Path targetPath = uploadDir.resolve(fileName).normalize();
        file.transferTo(targetPath.toFile());
        return buildUploadUrl(fileName, relativeDirSegments);
    }

    /**
     * 解析 uploads 根目录下的目标目录。
     */
    public static Path resolveUploadsDirectory(String... relativeDirSegments) {
        Path uploadRoot = Paths.get(System.getProperty("user.dir"), "uploads");
        if (relativeDirSegments == null || relativeDirSegments.length == 0) {
            return uploadRoot.toAbsolutePath().normalize();
        }
        Path uploadDir = uploadRoot;
        for (String segment : relativeDirSegments) {
            if (StringUtils.hasText(segment)) {
                uploadDir = uploadDir.resolve(segment);
            }
        }
        return uploadDir.toAbsolutePath().normalize();
    }

    /**
     * 根据 uploads URL 或相对路径，定位当前可读取的真实文件路径。
     * 兼容 backend/uploads 与项目根目录 uploads 两种运行布局。
     */
    public static Path resolveExistingUploadPath(String uploadUrlOrRelativePath) {
        String relativePath = normalizeUploadRelativePath(uploadUrlOrRelativePath);
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        for (Path uploadRoot : resolveUploadRootCandidates()) {
            Path resolved = uploadRoot.resolve(relativePath).normalize();
            if (resolved.startsWith(uploadRoot) && Files.exists(resolved)) {
                return resolved;
            }
        }
        return null;
    }

    /**
     * 将 /uploads/xxx、uploads/xxx 或完整 URL 统一规整为 uploads 根目录下的相对路径。
     */
    public static String normalizeUploadRelativePath(String uploadUrlOrRelativePath) {
        if (!StringUtils.hasText(uploadUrlOrRelativePath)) {
            return null;
        }
        String normalized = uploadUrlOrRelativePath.trim().replace('\\', '/');
        int marker = normalized.indexOf("/uploads/");
        if (marker >= 0) {
            normalized = normalized.substring(marker + "/uploads/".length());
        } else if (normalized.startsWith("uploads/")) {
            normalized = normalized.substring("uploads/".length());
        }
        normalized = normalized.replaceFirst("^/+", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    /**
     * 统一构造数据库内使用的 uploads 相对 URL。
     */
    public static String buildRelativeUploadUrl(String uploadUrlOrRelativePath) {
        String relativePath = normalizeUploadRelativePath(uploadUrlOrRelativePath);
        return StringUtils.hasText(relativePath) ? "/uploads/" + relativePath : null;
    }

    private static List<Path> resolveUploadRootCandidates() {
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize());
        roots.add(Paths.get(System.getProperty("user.dir"), "..", "uploads").toAbsolutePath().normalize());
        return new ArrayList<>(roots);
    }

    private static String buildStoredFileName(Long ownerId, String originalName, String fallbackBaseName) {
        String safeName = sanitizeAsciiFileName(originalName, fallbackBaseName);
        String ownerPrefix = ownerId == null ? "" : ownerId + "_";
        return ownerPrefix + System.currentTimeMillis() + "_" + safeName;
    }

    private static String sanitizeAsciiFileName(String originalName, String fallbackBaseName) {
        String sourceName = StringUtils.hasText(originalName) ? originalName : fallbackBaseName;
        String cleaned = sourceName == null ? "" : sourceName.replaceAll("[^a-zA-Z0-9._-]", "_").trim();
        if (StringUtils.hasText(cleaned)) {
            return cleaned;
        }
        return StringUtils.hasText(fallbackBaseName) ? fallbackBaseName : "file";
    }

    private static String buildUploadUrl(String fileName, String... relativeDirSegments) {
        StringBuilder builder = new StringBuilder("/uploads");
        if (relativeDirSegments != null) {
            for (String segment : relativeDirSegments) {
                if (StringUtils.hasText(segment)) {
                    builder.append("/").append(segment);
                }
            }
        }
        builder.append("/").append(fileName);
        return builder.toString();
    }
}
