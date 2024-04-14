package org.example.util;

import java.util.Base64;

public class EncoderUtility {
    public static String encodeBase64(String input) {
        final byte[] authBytes = input.getBytes();
        final String encoded = Base64.getEncoder().encodeToString(authBytes);
        return encoded;
    }
}
