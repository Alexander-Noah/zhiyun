package org.example.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智云实验室API")
                        .description("智云实验室——基于数据驱动的实验室协同管控平台 API 文档")
                        .version("1.0")
                        .termsOfService("https://www.baidu.com")
                        .contact(new Contact()
                                .name("柯段秋")
                                .url("https://blog.csdn.net/")
                                .email("123456789@163.com"))
                        .license(new License()
                                .name("Swagger 使用说明")
                                .url("https://blog.csdn.net"))
                );
    }
}