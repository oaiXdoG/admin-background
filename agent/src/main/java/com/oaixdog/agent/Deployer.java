package com.oaixdog.agent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 部署：按块下载发布包到临时文件并校验，再解压覆盖到工作目录（同路径替换、新文件加入、其余不变）。
 * 整个包外面只有一层文件夹时去掉这一层；macOS 压缩时带的 __MACOSX、.DS_Store、._ 开头的附加属性文件忽略；拒绝超出工作目录的路径。
 */
final class Deployer {

    interface ChunkSource {
        byte[] get(int index) throws Exception;
    }

    private Deployer() {
    }

    /** 返回执行输出；失败抛出带原因的异常 */
    static String deploy(ChunkSource source, int chunks, long size, String sha256, Path workDir, String runUser) throws Exception {
        Path temp = Files.createTempFile("admin-agent-deploy-", ".zip");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (OutputStream output = Files.newOutputStream(temp)) {
                for (int index = 0; index < chunks; index++) {
                    byte[] bytes = source.get(index);
                    digest.update(bytes);
                    output.write(bytes);
                }
            }
            if (Files.size(temp) != size || !HexFormat.of().formatHex(digest.digest()).equals(sha256)) {
                throw new IOException("发布包下载不完整或校验失败");
            }
            String output = "已下载发布包 " + size + " 字节，校验通过\n" + extract(temp, workDir);
            if (runUser != null && !runUser.isBlank()) {
                Systemd.Result chown = Systemd.run(120, "chown", "-R", runUser + ":", workDir.toString());
                if (!chown.success()) {
                    throw new IOException("修改文件属主失败：" + chown.output());
                }
            }
            return output;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static String extract(Path zipFile, Path workDir) throws IOException {
        Path root = workDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries()).stream().filter(entry -> !ignored(entry.getName())).toList();
            String prefix = commonTopDirectory(entries);
            int files = 0;
            for (ZipEntry entry : entries) {
                String name = entry.getName().replace('\\', '/').substring(prefix.length());
                if (name.isEmpty()) {
                    continue;
                }
                Path target = root.resolve(name).normalize();
                if (name.startsWith("/") || !target.startsWith(root)) {
                    throw new IOException("压缩包包含超出工作目录的路径：" + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = zip.getInputStream(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                files++;
            }
            return "已解压 " + files + " 个文件到 " + root + (prefix.isEmpty() ? "" : "（去掉了外层目录 " + prefix + "）") + "\n";
        }
    }

    private static boolean ignored(String name) {
        String path = name.replace('\\', '/');
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        return path.startsWith("__MACOSX/") || fileName.equals(".DS_Store") || fileName.startsWith("._");
    }

    /** 所有条目都在同一个顶层目录下时返回「目录名/」，否则返回空 */
    private static String commonTopDirectory(List<? extends ZipEntry> entries) {
        String top = null;
        for (ZipEntry entry : entries) {
            String name = entry.getName().replace('\\', '/');
            int slash = name.indexOf('/');
            if (slash <= 0) {
                return "";
            }
            String first = name.substring(0, slash + 1);
            if (top != null && !top.equals(first)) {
                return "";
            }
            top = first;
        }
        return top == null ? "" : top;
    }
}
