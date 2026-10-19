package dev.iamkavindu.reconflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"reconflow.kafka.enabled=false", "reconflow.security.enabled=false"})
class ReconFlowApplicationTests {

    @Test
    void contextLoads() {}
}
