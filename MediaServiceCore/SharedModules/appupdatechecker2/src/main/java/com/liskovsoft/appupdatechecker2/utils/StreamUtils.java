package com.liskovsoft.appupdatechecker2.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Replacement for prebuilt melautils.jar StreamUtils class.
 * This provides F-Droid compliant source code for the same functionality.
 */
public class StreamUtils {
    private static final int BUFFER_SIZE = 8192;

    /**
     * Reads an InputStream and returns its content as a String.
     */
    public static String inputStreamToString(InputStream is) throws IOException {
        if (is == null) {
            return null;
        }
        
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int length;
        
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        
        return result.toString("UTF-8");
    }
}
