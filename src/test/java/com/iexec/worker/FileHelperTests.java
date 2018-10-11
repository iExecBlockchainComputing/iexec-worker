package com.iexec.worker;

import com.iexec.worker.utils.FileHelper;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

public class FileHelperTests {


    @Before
    public void init() throws IOException {
        MockitoAnnotations.initMocks(this);
        FileUtils.deleteDirectory(new File("/tmp/iexec-test"));
    }

    @Test
    public void shouldCreateFileWithContent() throws IOException {
        File file = FileHelper.createFileWithContent("/tmp/iexec-test", "test.txt", "a test");
        assertThat(file.exists()).isTrue();
        String content = new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())), "UTF-8");
        assertThat(content.equals("a test")).isTrue();
    }

    @Test
    public void shouldZipFolder() throws IOException {
        File resultFile = FileHelper.createFileWithContent("/tmp/iexec-test/taskId", "test.txt", "a test");
        File zipFile = FileHelper.zipTaskResult("/tmp/iexec-test", "taskId");
        assertThat(zipFile.exists()).isTrue();
        assertThat(zipFile.getAbsolutePath().equals("/tmp/iexec-test/taskId.zip")).isTrue();
    }


}
