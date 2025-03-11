package t.me.p1azmer.discord.verify.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@UtilityClass
public class FileUtils {

    public void createFile(@NotNull File file) {
        if (file.exists()) {
            log.warn("File already exists. Skipping creation.");
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            log.error("Parent directory of file is null. Skipping creation.");
            return;
        }

        if (!parent.exists() && !parent.mkdirs()) {
            log.error("Failed to create parent directories");
            return;
        }

        try {
            if (file.createNewFile()) {
                log.info("Created new file: {}", file.getPath());
            }
        } catch (IOException exception) {
            log.error("Got an exception while creating file", exception);
        }
    }

    public void copyFile(@NotNull InputStream inputStream, @NotNull File file) {
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            byte[] array = new byte[1024];
            int read;
            while ((read = inputStream.read(array)) > 0) {
                outputStream.write(array, 0, read);
            }
            log.info("File copied successfully");
        } catch (IOException exception) {
            log.error("Got an exception while copying file", exception);
        }
    }
}
