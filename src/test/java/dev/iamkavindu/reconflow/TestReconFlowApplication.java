package dev.iamkavindu.reconflow;

import org.springframework.boot.SpringApplication;

public class TestReconFlowApplication {

    public static void main(String[] args) {
        SpringApplication.from(ReconFlowApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
