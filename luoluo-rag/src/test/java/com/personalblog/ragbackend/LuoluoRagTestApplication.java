package com.personalblog.ragbackend;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = {
        "com.personalblog.ragbackend.controller",
        "com.personalblog.ragbackend.service",
        "com.personalblog.ragbackend.rag.config"
}, exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
})
@ConfigurationPropertiesScan(basePackages = "com.personalblog.ragbackend.rag.config")
public class LuoluoRagTestApplication {
}
