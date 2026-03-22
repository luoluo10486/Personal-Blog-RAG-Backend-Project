package com.personalblog.ragbackend.common.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.PostInitTableInfoHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.personalblog.ragbackend.common.mybatis.handler.LogicDeletePostInitTableInfoHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * MyBatis-Plus 支持配置，统一注册分页和逻辑删除扩展。
 */
@Configuration
@EnableTransactionManagement(proxyTargetClass = true)
public class MybatisPlusSupportConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
        paginationInnerInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        return interceptor;
    }

    @Bean
    public PostInitTableInfoHandler postInitTableInfoHandler(
            @Value("${mybatis-plus.enable-logic-delete:true}") boolean enableLogicDelete
    ) {
        return new LogicDeletePostInitTableInfoHandler(enableLogicDelete);
    }
}
