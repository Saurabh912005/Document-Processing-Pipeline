package com.suretyseven.documentprocessing;

import com.suretyseven.documentprocessing.support.MySQLTestcontainersConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySQLTestcontainersConfig.class)
@Disabled("Optional — run locally with Docker: mvn test -Dtest=MySQLTestcontainersIntegrationTest")
class MySQLTestcontainersIntegrationTest {

    @Test
    void contextLoadsWithMySQL() {}
}
