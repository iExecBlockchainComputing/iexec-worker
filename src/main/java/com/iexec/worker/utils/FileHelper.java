package com.iexec.worker.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class FileHelper {

    private FileHelper() {
        throw new UnsupportedOperationException();
    }

    public static File createFileWithContent(String filePath, String data) {
        String directoryPath = new File(filePath).getParent();

        if (createFolder(directoryPath)) {
            try {
                Files.write(Paths.get(filePath), data.getBytes());
                log.debug("File created [filePath:{}]", filePath);
                return new File(filePath);
            } catch (IOException e) {
                log.error("Failed to create file [filePath:{}]", filePath);
            }
        } else {
            log.error("Failed to create base directory [directoryPath:{}]", directoryPath);
        }
        return null;
    }

    public static boolean createFolder(String folderPath) {
        File baseDirectory = new File(folderPath);
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
        if(!folder.exists()) {
            log.info("Folder doesn't exist so can't be deleted [path:{}]", folderPath);
            return false;
        }

        try {
            FileUtils.deleteDirectory(folder);
            log.info("Folder has been deleted [path:{}]", folderPath);
            return true;
        } catch (IOException e) {
            log.error("Problem when trying to delete the folder [path:{}]", folderPath);
        }
        return false;
    }

    public static File zipFolder(String folderPath) {
        String zipFilePath = folderPath + ".zip";
        Path sourceFolderPath = Paths.get(folderPath);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(zipFilePath)))) {
            Files.walkFileTree(sourceFolderPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    log.debug("Adding file to zip [file:{}, zip:{}]", file.toAbsolutePath().toString(), zipFilePath);
                    zos.putNextEntry(new ZipEntry(sourceFolderPath.relativize(file).toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
            log.info("Folder zipped [path:{}]", zipFilePath);
            return new File(zipFilePath);

        } catch (Exception e) {
            log.error("Failed to zip folder [path:{}]", zipFilePath);
        }
        return null;
    }
}
