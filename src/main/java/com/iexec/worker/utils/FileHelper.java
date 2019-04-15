package com.iexec.worker.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class FileHelper {

    public static final String SLASH_IEXEC_OUT = File.separator + "iexec_out";
    public static final String SLASH_IEXEC_IN = File.separator + "iexec_in";
    public static final String SLASH_OUTPUT = File.separator + "output";
    public static final String SLASH_INPUT = File.separator + "input";

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

    public static boolean downloadFileInDirectory(String fileUri, String directoryPath) {
        if (!createFolder(directoryPath)) {
            log.error("Failed to create base directory [directoryPath:{}]", directoryPath);
            return false;
        }

        if (fileUri.isEmpty()) {
            log.error("FileUri shouldn't be empty [fileUri:{}]", fileUri);
            return false;
        }

        InputStream in;
        try {
            in = new URL(fileUri).openStream();//Not working with https resources yet
        } catch (IOException e) {
            log.error("Failed to download file [fileUri:{}, exception:{}]", fileUri, e.getCause());
            return false;
        }

        try {
            String fileName = Paths.get(fileUri).getFileName().toString();
            Files.copy(in, Paths.get(directoryPath + File.separator + fileName), StandardCopyOption.REPLACE_EXISTING);
            log.info("Downloaded data [fileUri:{}]", fileUri);
            return true;
        } catch (IOException e) {
            log.error("Failed to copy downloaded file to disk [directoryPath:{}, fileUri:{}]",
                    directoryPath, fileUri);
            return false;
        }
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
        if (!folder.exists()) {
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

    public static boolean renameFile(String oldPath, String newPath) {
        try {
            return new File(oldPath).renameTo(new File(newPath));
        } catch (Exception e) {
            log.error("could not rename file [oldPath:{}, newPath:{}]", oldPath, newPath);
            e.printStackTrace();
            return false;
        }
    }
}
