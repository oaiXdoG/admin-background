package com.oaixdog.adminbackground.service;

import com.oaixdog.adminbackground.config.ApplicationProperties;
import com.oaixdog.adminbackground.entity.ServerHost;
import com.oaixdog.adminbackground.entity.ServerProcess;
import com.oaixdog.adminbackground.entity.ServerTask;
import com.oaixdog.adminbackground.exception.BusinessException;
import com.oaixdog.adminbackground.exception.ErrorCode;
import com.zfoo.protocol.util.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * 部署：页面把 zip 按 6MB 分块上传，全部上传后生成「部署」指令；Agent 领取后按块下载、校验 SHA-256、解压覆盖工作目录并重启进程。
 * 上传进度存在 Redis（1 小时有效），发布包存在 server.package-dir，指令结束后删除。
 */
@Service
@RequiredArgsConstructor
public class ServerDeployService {

    public static final int CHUNK_SIZE = 6 * 1024 * 1024;
    private static final String MENU = "server_process:deploy";
    private static final String UPLOAD_KEY = "server:upload:";
    private static final Duration UPLOAD_TTL = Duration.ofHours(1);
    private static final Pattern UPLOAD_ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final int CHUNK_MAX = 64;
    private static final int TEXT_MAX_LENGTH = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PermissionService permissionService;
    private final ServerProcessService processService;
    private final ServerTaskService taskService;
    private final ServerMetricService metricService;
    private final RedisService redisService;
    private final ApplicationProperties properties;

    /** 上传进度 Redis 值：进程 ID:已收到块数:总块数 */
    private record Upload(long processId, int received, int total) {
    }

    public Map<String, Object> upload(String authorization, Long projectId, Long processId, String uploadId, Integer index, Integer total, String data) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        ServerProcess process = processService.require(access.projectId(), processId);
        if (index == null || total == null || total < 1 || total > CHUNK_MAX || index < 0 || index >= total || data == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        if (bytes.length == 0 || bytes.length > CHUNK_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        String id;
        if (uploadId == null) {
            if (index != 0) {
                throw new BusinessException(ErrorCode.PACKAGE_UPLOAD_INVALID);
            }
            byte[] random = new byte[16];
            RANDOM.nextBytes(random);
            id = HexFormat.of().formatHex(random);
        } else {
            Upload upload = upload(uploadId);
            if (upload == null || upload.processId() != process.getId() || upload.total() != total || upload.received() != index) {
                throw new BusinessException(ErrorCode.PACKAGE_UPLOAD_INVALID);
            }
            id = uploadId;
        }
        try {
            Path file = partFile(id);
            Files.createDirectories(file.getParent());
            if (index == 0) {
                Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } else {
                Files.write(file, bytes, StandardOpenOption.APPEND);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        redisService.set(UPLOAD_KEY + id, process.getId() + ":" + (index + 1) + ":" + total, UPLOAD_TTL);
        return Map.of("uploadId", id);
    }

    public Map<String, Object> deploy(String authorization, Long projectId, Long processId, String uploadId, String fileName, String remark) {
        PermissionService.ProjectAccess access = permissionService.requireProjectMenu(authorization, projectId, MENU);
        ServerProcess process = processService.require(access.projectId(), processId);
        Upload upload = upload(uploadId);
        if (upload == null || upload.processId() != process.getId() || upload.received() != upload.total()) {
            throw new BusinessException(ErrorCode.PACKAGE_UPLOAD_INVALID);
        }
        String name = text(fileName);
        String note = text(remark);
        if (!metricService.isOnline(process.getServerId())) {
            throw new BusinessException(ErrorCode.SERVER_OFFLINE);
        }
        taskService.requireIdle(List.of(process.getId()));
        Path part = partFile(uploadId);
        try {
            if (!Files.exists(part) || !hasEntries(part)) {
                throw new BusinessException(ErrorCode.PACKAGE_INVALID);
            }
            long size = Files.size(part);
            int chunks = (int) ((size + CHUNK_SIZE - 1) / CHUNK_SIZE);
            String param = JsonUtils.object2String(Map.of("size", size, "sha256", sha256(part), "chunks", chunks));
            String display = (name.isEmpty() ? "发布包" : name) + "（" + formatSize(size) + "）" + (note.isEmpty() ? "" : " · " + note);
            long taskId = taskService.create(process, ServerTaskService.ACTION_DEPLOY, access.account().getAccount(), display, param);
            Files.move(part, packageFile(taskId), StandardCopyOption.REPLACE_EXISTING);
            redisService.delete(UPLOAD_KEY + uploadId);
            return Map.of("taskId", taskId);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Agent 下载部署指令的发布包第 index 块，只能下载本服务器执行中的部署指令 */
    public Map<String, Object> chunk(ServerHost server, Long taskId, Integer index) {
        ServerTask task = taskService.running(server.getId(), taskId);
        if (task == null || !ServerTaskService.ACTION_DEPLOY.equals(task.getAction()) || index == null || index < 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        Path file = packageFile(task.getId());
        if (!Files.exists(file)) {
            throw new BusinessException(ErrorCode.PACKAGE_UPLOAD_INVALID);
        }
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long offset = (long) index * CHUNK_SIZE;
            if (offset >= input.length()) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            byte[] bytes = new byte[(int) Math.min(CHUNK_SIZE, input.length() - offset)];
            input.seek(offset);
            input.readFully(bytes);
            return Map.of("data", Base64.getEncoder().encodeToString(bytes));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** 部署指令结束后删除发布包 */
    public void cleanup(ServerTask task) {
        if (task == null || !ServerTaskService.ACTION_DEPLOY.equals(task.getAction())) {
            return;
        }
        try {
            Files.deleteIfExists(packageFile(task.getId()));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Upload upload(String uploadId) {
        if (uploadId == null || !UPLOAD_ID.matcher(uploadId).matches()) {
            return null;
        }
        String value = redisService.get(UPLOAD_KEY + uploadId);
        String[] parts = value == null ? new String[0] : value.split(":");
        return parts.length == 3 ? new Upload(Long.parseLong(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])) : null;
    }

    private Path partFile(String uploadId) {
        return Path.of(properties.getPackageDir(), "upload-" + uploadId + ".zip");
    }

    private Path packageFile(long taskId) {
        return Path.of(properties.getPackageDir(), "task-" + taskId + ".zip");
    }

    private static boolean hasEntries(Path file) {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.entries().hasMoreElements();
        } catch (IOException exception) {
            return false;
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1024 * 1024];
            for (int read = input.read(buffer); read > 0; read = input.read(buffer)) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String text(String value) {
        String text = value == null ? "" : value.replaceAll("\\p{Cntrl}", "").trim();
        return text.length() > TEXT_MAX_LENGTH ? text.substring(0, TEXT_MAX_LENGTH) : text;
    }

    private static String formatSize(long size) {
        return size >= 1024 * 1024 ? String.format("%.1f MB", size / 1024.0 / 1024.0) : String.format("%.1f KB", size / 1024.0);
    }
}
