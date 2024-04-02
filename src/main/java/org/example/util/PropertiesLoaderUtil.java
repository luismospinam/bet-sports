package org.example.util;

import org.example.db.DatabaseConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertiesLoaderUtil {
    public static Properties loadProperties(String filename) {
        Properties properties = new Properties();

        try (InputStream input = DatabaseConfig.class.getClassLoader().getResourceAsStream(filename)) {
            if (input == null) {
                System.out.println("Sorry, unable to find " + filename);
                System.exit(1);
            }

            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return properties;
    }
}
