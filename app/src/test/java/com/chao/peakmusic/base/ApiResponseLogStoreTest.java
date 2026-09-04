package com.chao.peakmusic.base;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;

public class ApiResponseLogStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void trimRemovesOldestFilesByCount() throws Exception {
        File directory = temporaryFolder.newFolder("logs");
        File oldest = create(directory, "1.json", 4, 1);
        File middle = create(directory, "2.json", 4, 2);
        File newest = create(directory, "3.json", 4, 3);

        ApiResponseLogStore.trim(directory, null, 100, 2);

        assertTrue(!oldest.exists());
        assertArrayEquals(new String[]{"2.json", "3.json"}, names(directory));
        assertTrue(middle.exists() && newest.exists());
    }

    @Test
    public void trimHonorsByteLimitAndKeepsCurrentResponse() throws Exception {
        File directory = temporaryFolder.newFolder("size-logs");
        File oldest = create(directory, "1.json", 6, 1);
        File current = create(directory, "2.json", 8, 2);

        ApiResponseLogStore.trim(directory, current, 8, 20);

        assertTrue(!oldest.exists());
        assertTrue(current.exists());
    }

    private static File create(File directory, String name, int size, long modified)
            throws Exception {
        File file = new File(directory, name);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(new byte[size]);
        }
        assertTrue(file.setLastModified(modified));
        return file;
    }

    private static String[] names(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return new String[0];
        }
        java.util.Arrays.sort(files, (first, second) -> first.getName().compareTo(second.getName()));
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }
        return names;
    }
}
