package com.iexec.worker.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
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

@Slf4j
public class FileHelper {

    private FileHelper() {
        throw new UnsupportedOperationException();
    }

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

    private static boolean createDirectories(String directoryPath) {
        File baseDirectory = new File(directoryPath);
        if (!baseDirectory.exists()) {
            return baseDirectory.mkdirs();
        } else {
            return true;
        }
    }

    public static boolean deleteFile(String filePath) {
        try {
            Files.delete(Paths.get(filePath));
            log.info("File has been deleted [path:{}]", filePath);
            return true;
        } catch (IOException e) {
            log.error("Problem when trying to delete the file [path:{}]", filePath);
        }
        return false;
    }

    public static boolean deleteFolder(String folderPath) {
        File folder = new File(folderPath);
        try {
            FileUtils.deleteDirectory(folder);
            log.info("Folder has been deleted [path:{}]", folderPath);
            return true;
        } catch (IOException e) {
            log.error("Problem when trying to delete the folder [path:{}]", folderPath);
        }
        return false;
    }

    public static File zipTaskResult(String folderPath) {
        String zipFilePath = folderPath + ".zip";
        try {
            zipFolder(Paths.get(folderPath), Paths.get(zipFilePath));
            log.info("Result folder zip completed [path:{}]", zipFilePath);
            return new File(zipFilePath);
        } catch (Exception e) {
            log.error("Failed to zip task result [path:{}]", zipFilePath);
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
