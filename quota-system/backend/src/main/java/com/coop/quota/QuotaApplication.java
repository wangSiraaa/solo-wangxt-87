package com.coop.quota;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 渔业合作组织配额账本。
 * 注意：本系统使用虚构物种与许可规则，不连接任何监管系统，不作为真实捕捞许可。
 */
@SpringBootApplication
public class QuotaApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuotaApplication.class, args);
    }
}
