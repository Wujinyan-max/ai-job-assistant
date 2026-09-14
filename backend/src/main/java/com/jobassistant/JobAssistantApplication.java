package com.jobassistant;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * AI 求职管理平台启动类。
 */
// 我们不使用表单登录，排除掉自动配置，避免启动日志里打印随机密码
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@MapperScan("com.jobassistant.mapper")
public class JobAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAssistantApplication.class, args);
    }
}
