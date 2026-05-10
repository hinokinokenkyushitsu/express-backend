package com.fiveok.express.base.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 国际化消息工具
 * <p>
 * 根据当前线程绑定的 Locale（由 LocaleResolver 从 Accept-Language 解析）
 * 翻译消息 key。所有业务异常和响应文本都应通过此工具输出。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class I18nUtil {

    private final MessageSource messageSource;

    /**
     * 翻译消息 key
     *
     * @param code 消息 key（如 "order.grab.taken"）
     * @param args 占位符参数
     */
    public String t(String code, Object... args) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, args, code, locale);
    }

    /**
     * 翻译消息 key（指定语言）
     */
    public String t(String code, Locale locale, Object... args) {
        return messageSource.getMessage(code, args, code, locale);
    }
}
