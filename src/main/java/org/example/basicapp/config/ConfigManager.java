package org.example.basicapp.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static final String CONFIG_FILE = "/application.properties";
    private static ConfigManager instance;
    private Properties properties;

    private ConfigManager() {
        properties = new Properties();
        loadProperties();
    }

    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream inputStream = getClass().getResourceAsStream(CONFIG_FILE)) {
            if (inputStream == null) {
                throw new RuntimeException("Configuration file not found: " + CONFIG_FILE);
            }
            properties.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Error loading configuration file", e);
        }
    }

    public String getProperty(String key) {
        return properties.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    public double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Double.parseDouble(value) : defaultValue;
    }

    public float getFloatProperty(String key, float defaultValue) {
        String value = properties.getProperty(key);
        return value != null ? Float.parseFloat(value) : defaultValue;
    }
}

