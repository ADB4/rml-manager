package com.adb4.rmlmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
@Getter
@Setter
public class AdminProperties {
    private String username = "admin";

    /**
     * when left blank, generates and logs random password on startup
     */
    private String password;
}