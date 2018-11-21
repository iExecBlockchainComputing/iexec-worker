package com.iexec.worker.utils;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class FileHelperTests {

    private static final String TEST_FOLDER = "/tmp/iexec-test";

    // clean the test repo before and after each test
    @Before
    public void init() throws IOException {
        MockitoAnnotations.initMocks(this);
        FileUtils.deleteDirectory(new File(TEST_FOLDER));
    }

    @After
    public void tearDown() throws IOException {
        FileUtils.deleteDirectory(new File(TEST_FOLDER));
    }

    @Test
    public void shouldCreateFileWithContent() throws IOException {
        String data = "a test";
        File file = FileHelper.createFileWithContent(TEST_FOLDER + "/test.txt", data);
        assertThat(file).isNotNull();
        assertThat(file).exists();
        assertThat(file).isFile();
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo(data);
    }

    @Test
    public void shouldZipFolder() {
        FileHelper.createFileWithContent(TEST_FOLDER + "/taskId/test.txt", "a test");
        File zipFile = FileHelper.zipFolder(TEST_FOLDER + "/taskId");
        assertThat(zipFile).isNotNull();
        assertThat(zipFile).exists();
        assertThat(zipFile).isFile();
        assertThat(zipFile.getAbsolutePath()).isEqualTo(TEST_FOLDER + "/taskId.zip");
    }
}
