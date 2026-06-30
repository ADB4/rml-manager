package com.adb4.rmlmanager;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RmlManagerApplicationTests {

    @Test
    void contextLoads() {
    }

}
