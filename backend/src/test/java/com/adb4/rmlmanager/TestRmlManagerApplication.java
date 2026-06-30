package com.adb4.rmlmanager;

import org.springframework.boot.SpringApplication;

public class TestRmlManagerApplication {

    public static void main(String[] args) {
        SpringApplication.from(RmlManagerApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
