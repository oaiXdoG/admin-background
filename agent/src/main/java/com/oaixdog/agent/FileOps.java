package com.oaixdog.agent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** 进程工作目录下的文件操作：list、read、write、delete。路径相对工作目录，不能超出工作目录 */
final class FileOps {

    private static final int LIST_MAX = 1000;
    private static final long READ_MAX = 1024 * 1024;

    private FileOps() {
    }

    /** 返回结果数据（write、delete 为 null）；失败抛出带原因的异常 */
    static Object run(String op, Path workDir, String path, String content) throws IOException {
        Path root = workDir.toAbsolutePath().normalize();
        Path target = path == null || path.isEmpty() ? root : root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("路径超出工作目录");
        }
        return switch (op) {
            case "list" -> list(target);
            case "read" -> read(target);
            case "write" -> write(target, content == null ? "" : content);
            case "delete" -> delete(root, target);
            default -> throw new IOException("不支持的文件操作：" + op);
        };
    }

    private static Map<String, Object> list(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IOException("目录不存在");
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path file : stream.sorted(Comparator.comparing((Path item) -> !Files.isDirectory(item)).thenComparing(item -> item.getFileName().toString()))
                    .limit(LIST_MAX).toList()) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", file.getFileName().toString());
                try {
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                    entry.put("dir", attributes.isDirectory());
                    entry.put("size", attributes.isDirectory() ? 0 : attributes.size());
                    entry.put("modifiedAt", attributes.lastModifiedTime().toMillis());
                } catch (IOException exception) {
                    // 失效的软链接等读不到属性的条目照样列出
                    entry.put("dir", false);
                    entry.put("size", 0);
                    entry.put("modifiedAt", 0);
                }
                entries.add(entry);
            }
        }
        return Map.of("entries", entries);
    }

    private static Map<String, Object> read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("文件不存在");
        }
        long size = Files.size(file);
        if (size > READ_MAX) {
            throw new IOException("文件超过 1MB，不能在线查看");
        }
        byte[] bytes = Files.readAllBytes(file);
        for (byte value : bytes) {
            if (value == 0) {
                throw new IOException("不是文本文件，不能在线查看");
            }
        }
        return Map.of("content", new String(bytes, StandardCharsets.UTF_8), "size", size);
    }

    /** 只能修改已存在的文件；先写临时文件再替换，保留原有属主和权限 */
    private static Object write(Path file, String content) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("文件不存在");
        }
        PosixFileAttributes attributes = Files.readAttributes(file, PosixFileAttributes.class);
        Path temp = file.resolveSibling(file.getFileName() + ".admin-agent.tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        PosixFileAttributeView view = Files.getFileAttributeView(temp, PosixFileAttributeView.class);
        view.setPermissions(attributes.permissions());
        view.setOwner(attributes.owner());
        view.setGroup(attributes.group());
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        return null;
    }

    /** 目录递归删除；软链接只删除链接本身 */
    private static Object delete(Path root, Path target) throws IOException {
        if (target.equals(root)) {
            throw new IOException("不能删除工作目录");
        }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("文件不存在");
        }
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            try (Stream<Path> stream = Files.walk(target)) {
                for (Path file : stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        } else {
            Files.delete(target);
        }
        return null;
    }
}
