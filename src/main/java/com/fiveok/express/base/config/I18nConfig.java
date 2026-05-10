package com.fiveok.express.base.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import java.util.List;
import java.util.Locale;

/**
 * 国际化配置
 * <p>
 * 三个核心 Bean：
 * 1. MessageSource —— 加载 messages_*.properties + ValidationMessages_*.properties
 * 2. LocaleResolver —— 从请求 Header 的 Accept-Language 解析语言
 * 3. LocalValidatorFactoryBean —— 让 @NotBlank 等校验注解的 message="{xxx.key}" 通过 MessageSource 翻译
 * </p>
 */
@Configuration
public class I18nConfig {

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        // 同时加载业务消息和校验消息
        ms.setBasenames("classpath:messages", "classpath:ValidationMessages");
        ms.setDefaultEncoding("UTF-8");
        ms.setUseCodeAsDefaultMessage(true);
        ms.setCacheSeconds(3600);
        return ms;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(List.of(Locale.SIMPLIFIED_CHINESE, Locale.ENGLISH));
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    /**
     * 让 Bean Validation 的 message="{xxx.key}" 也走 Spring MessageSource，
     * 从而支持运行时按 Locale 切换校验消息。
     */
    @Bean
    public LocalValidatorFactoryBean validator(MessageSource messageSource) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
