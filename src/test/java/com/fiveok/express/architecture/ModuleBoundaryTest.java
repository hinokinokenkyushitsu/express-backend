package com.fiveok.express.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块边界守护测试
 * <p>
 * 模块化单体的关键：通过自动测试强制约束模块依赖关系，
 * 防止有人偷偷跨模块直接 import 内部实现。
 * 任何违反都会让 CI 红灯，从而保护架构。
 * </p>
 */
@AnalyzeClasses(
        packages = "com.fiveok.express",
        importOptions = ImportOption.DoNotIncludeTests.class
)
public class ModuleBoundaryTest {

    /**
     * 规则 1：order 模块不能访问 user 模块的内部实现，
     * 只能通过 user.api 包（UserFacade、UserView）。
     */
    @ArchTest
    static final ArchRule order_should_only_access_user_via_api =
            noClasses()
                    .that().resideInAPackage("..order..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..user.entity..",
                            "..user.mapper..",
                            "..user.service..",
                            "..user.dto..",
                            "..user.controller..",
                            "..user.enums.."
                    )
                    .because("order 模块只能通过 user.api 包访问 user 模块（UserFacade/UserView）");

    /**
     * 规则 2：user 模块不能反向依赖 order 模块。
     * 用户模块是基础模块，不应该知道订单的存在。
     */
    @ArchTest
    static final ArchRule user_should_not_depend_on_order =
            noClasses()
                    .that().resideInAPackage("..user..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..order..")
                    .because("user 是基础模块，不应反向依赖 order 模块");

    /**
     * 规则 3：base 模块（公共基础设施）不能依赖任何业务模块。
     */
    @ArchTest
    static final ArchRule base_should_not_depend_on_business_modules =
            noClasses()
                    .that().resideInAPackage("..base..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..user..", "..order..")
                    .because("base 是公共基础设施，不应依赖任何业务模块");
}
