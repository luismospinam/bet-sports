package org.example;

import org.example.db.ai.AIDao;
import org.example.db.basketball.*;
import org.example.logic.ai.AIService;
import org.example.logic.basketball.*;
import org.example.model.EventNbaPoints;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class TestMainClass {
    public static void main(String[] args) {
        while (true) {

            long timeLong = new Date().getTime() / 1000;
            System.out.println(timeLong);
            final String s = """
                    {"BPCValid":true,"iat":%d}""".formatted(timeLong);
            final byte[] authBytes = s.getBytes();
            final String encoded = Base64.getEncoder().encodeToString(authBytes);
            System.out.println(encoded);

            try {
                TimeUnit.SECONDS.sleep(15);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}