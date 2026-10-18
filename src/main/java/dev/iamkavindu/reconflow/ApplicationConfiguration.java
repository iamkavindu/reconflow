package dev.iamkavindu.reconflow;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
