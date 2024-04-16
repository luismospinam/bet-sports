package org.example;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TestMainClass {
    public static void main(String[] args) throws IOException {
        printEncodeTokenNow(true);
    }


    public static String printEncodeTokenNow(boolean runForEver) {
        int count = 0;
        String encoded = "";
        while (runForEver || count < 1) {
            long timeLong = new Date().getTime() / 1000;
            System.out.println(timeLong);
            final String s = """
                    {"BPCValid":true,"iat":%d}""".formatted(timeLong);
            final byte[] authBytes = s.getBytes();
            encoded = Base64.getEncoder().encodeToString(authBytes);
            System.out.println(encoded);

            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            count++;
        }

        return encoded.replaceAll("==", "");
    }


}