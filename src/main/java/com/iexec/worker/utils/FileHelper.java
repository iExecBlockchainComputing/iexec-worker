package com.iexec.worker.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileHelper {

    private static final Logger log = LoggerFactory.getLogger(FileHelper.class);

    public static File createFileWithContent(String directoryPath, String filename, String data) {
        if (createDirectories(directoryPath)) {
            Path path = Paths.get(directoryPath + "/" + filename);
            byte[] strToBytes = data.getBytes();
            try {
                Files.write(path, strToBytes);
                log.debug("File created [directoryPath:{}, filename:{}]", directoryPath, filename);
                return new File(directoryPath + "/" + filename);
            } catch (IOException e) {
                log.error("Failed to create file [directoryPath:{}, filename:{}]", directoryPath, filename);
            }
        } else {
            log.error("Failed to create base directory [directoryPath:{}]", directoryPath);
        }
        return null;
    }

    public static boolean createDirectories(String directoryPath) {
        File baseDirectory = new File(directoryPath);
        if (!baseDirectory.exists()) {
            return baseDirectory.mkdirs();
        } else {
            return true;
        }
    }

    public static File zipTaskResult(String localPath, String taskId) {
        String folderToZip = localPath + "/" + taskId;
        String zipName = folderToZip + ".zip";
        try {
            zipFolder(Paths.get(folderToZip), Paths.get(zipName));
            log.info("Result folder zip completed [taskId:{}]", taskId);
            return new File(zipName);
        } catch (Exception e) {
            log.error("Failed to zip task result [taskId:{}]", taskId);
        }
        return null;
    }

    private static void zipFolder(Path sourceFolderPath, Path zipPath) throws Exception {
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
        Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                log.debug("Adding file to zip [file:{}, zip:{}]", file.toAbsolutePath().toString(), zipPath);
                zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                Files.copy(file, zos);
                zos.closeEntry();
                return FileVisitResult.CONTINUE;
            }
        });
        zos.close();
    }

    public static void copyResultToTaskFolder(InputStream containerResultArchive, String resultBaseDirectory, String taskId) {
        try {
            final TarArchiveInputStream tarStream = new TarArchiveInputStream(containerResultArchive);

            TarArchiveEntry entry;
            while ((entry = tarStream.getNextTarEntry()) != null) {
                log.debug(entry.getName());
                if (entry.isDirectory()) {
                    continue;
                }
                File curfile = new File(resultBaseDirectory + "/" + taskId, entry.getName());
                File parent = curfile.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                IOUtils.copy(tarStream, new FileOutputStream(curfile));
            }
            log.info("Results from remote added to result folder [taskId:{}]", taskId);
        } catch (IOException e) {
            log.error("Failed to copy container results to disk [taskId:{}]", taskId);
        }
    }

}
