package com.fiveok.express;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类
 * <p>扫描 user 和 order 两个模块的 Mapper。开启 @Scheduled 用于超时订单结算。</p>
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({
    "com.fiveok.express.user.mapper",
    "com.fiveok.express.order.mapper"
})
public class ExpressApplication {
    public static void main(String[] args) {
        SpringApplication.run(ExpressApplication.class, args);
    }
}
