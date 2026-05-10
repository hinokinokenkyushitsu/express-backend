package com.fiveok.express.base.exception;

import com.fiveok.express.base.utils.I18nUtil;
import com.fiveok.express.base.utils.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final I18nUtil i18n;

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        // 翻译 message key —— 这里用的 Locale 来自当前请求的 Accept-Language
        String translated = i18n.t(e.getMessageKey(), e.getArgs());
        log.warn("业务异常: code={}, key={}, translated={}",
                e.getCode(), e.getMessageKey(), translated);
        return Result.error(e.getCode(), translated);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        // 校验消息直接用 DTO 注解里的文本（注解本身已是中英分别配置场景下需重做，
        // 这里简化处理：仍用注解 message。如需双语校验，可改为 message="{xxx.key}" 配合 ValidationMessages.properties）
        String msg = e.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return Result.error(500, i18n.t("common.server.error"));
    }
}
