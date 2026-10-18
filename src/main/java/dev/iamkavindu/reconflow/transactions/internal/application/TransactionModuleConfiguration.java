package dev.iamkavindu.reconflow.transactions.internal.application;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TransactionModuleConfiguration {

    @Bean
    Clock transactionClock() {
        return Clock.systemUTC();
    }
}
