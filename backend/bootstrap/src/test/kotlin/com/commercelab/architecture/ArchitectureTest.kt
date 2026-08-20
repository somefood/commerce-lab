package com.commercelab.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

/**
 * 설계문서 §3.3의 불변 규칙을 강제한다.
 * 이 테스트가 깨지면 구현이 아니라 설계가 무너진 것이다. 규칙을 고쳐서 통과시키지 말 것.
 */
class ArchitectureTest {

    companion object {
        private lateinit var classes: JavaClasses

        // 두 애노테이션 모두 스프링이 트랜잭션 경계로 인식한다.
        // 한쪽만 검사하면 다른 쪽을 import해 규칙을 우회할 수 있고,
        // 그때 규칙은 초록불을 유지한 채 아무것도 막지 못한다.
        private val TRANSACTIONAL_ANNOTATIONS = arrayOf(
            "org.springframework.transaction.annotation.Transactional",
            "jakarta.transaction.Transactional",
        )

        @BeforeAll
        @JvmStatic
        fun importClasses() {
            classes = ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.commercelab")
        }
    }

    @Test
    fun `order는 payment를 알지 못한다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.order..")
            .should().dependOnClassesThat().resideInAPackage("com.commercelab.payment..")
            .because("모듈 간 통신은 이벤트로만 한다. 직접 참조는 M4 분리를 불가능하게 만든다")
            .check(classes)
    }

    @Test
    fun `payment는 order를 알지 못한다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.payment..")
            .should().dependOnClassesThat().resideInAPackage("com.commercelab.order..")
            .because("모듈 간 통신은 이벤트로만 한다. 직접 참조는 M4 분리를 불가능하게 만든다")
            // payment 모듈은 M2에서 첫 클래스가 생긴다. 그때까지 이 규칙은 0개 클래스에 매칭되는데,
            // ArchUnit은 빈 매칭을 기본적으로 실패로 처리한다(archRule.failOnEmptyShould=true).
            // 그 기본값은 "패키지 이름이 바뀌어 규칙이 아무것도 검사하지 않게 된 상태"를 잡기 위한 것이므로
            // 전역으로 끄지 않고, 지금 비어 있는 것이 확실한 이 규칙에만 예외를 준다.
            // M2에서 payment에 클래스가 생기면 이 줄을 지운다.
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `contract는 어떤 프레임워크도 알지 못한다`() {
        noClasses()
            .that().resideInAPackage("com.commercelab.contract..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "org.springframework..",
                "jakarta..",
                "com.fasterxml.jackson..",
            )
            .because("계약은 JSON으로 직렬화되어 프로세스 경계를 넘는다. 프레임워크에 묶이면 독립 배포가 죽는다")
            .check(classes)
    }

    @Test
    fun `order 도메인은 영속성 기술을 알지 못한다`() {
        // 도메인 모델과 JPA 엔티티를 분리하기로 한 결정(M1 §4-1)을 강제한다.
        // 매핑이 귀찮다고 도메인 클래스에 @Entity를 붙이는 순간 빌드가 깨진다.
        //
        // 왜 막는가: JPA는 엔티티가 변경 가능하고 기본 생성자를 갖기를 요구한다.
        // 도메인이 그 요구를 받아들이면 불변 설계와 copy() 기반 상태 전이가 무너진다.
        // 3단계의 조건부 UPDATE도 더티 체킹으로는 표현할 수 없어 결국 SQL이
        // 도메인 옆에 붙게 된다.
        noClasses()
            .that().resideInAPackage("com.commercelab.order.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                "jakarta.persistence..",
                "org.springframework.data..",
                "org.hibernate..",
            )
            .because("도메인은 저장 방식을 몰라야 한다. 영속성 모델은 infrastructure 패키지가 갖는다")
            // 아직 domain 패키지가 비어 있다. 클래스가 생기면 이 줄을 지운다.
            .allowEmptyShould(true)
            .check(classes)
    }

    @Test
    fun `order의 트랜잭션 경계는 application 패키지에만 있다`() {
        // 리포지터리마다 트랜잭션을 열면 주문 생성이 원자적이지 않게 된다.
        // 트랜잭션을 여는 지점을 한 계층으로 못 박는다.
        TRANSACTIONAL_ANNOTATIONS.forEach { annotation ->
            noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("com.commercelab.order..")
                .and().areDeclaredInClassesThat().resideOutsideOfPackage("com.commercelab.order.application..")
                .should().beAnnotatedWith(annotation)
                .because("트랜잭션 경계는 애플리케이션 서비스가 소유한다. 도메인도 리포지터리도 열지 않는다")
                .allowEmptyShould(true)
                .check(classes)

            noClasses()
                .that().resideInAPackage("com.commercelab.order..")
                .and().resideOutsideOfPackage("com.commercelab.order.application..")
                .should().beAnnotatedWith(annotation)
                .because("트랜잭션 경계는 애플리케이션 서비스가 소유한다. 도메인도 리포지터리도 열지 않는다")
                .allowEmptyShould(true)
                .check(classes)
        }
    }

    @Test
    fun `bootstrap 클래스에는 트랜잭션 경계가 없다`() {
        TRANSACTIONAL_ANNOTATIONS.forEach { annotation ->
            noClasses()
                .that().resideInAPackage("com.commercelab.bootstrap..")
                .should().beAnnotatedWith(annotation)
                .because("bootstrap이 트랜잭션을 열면 여러 모듈이 한 트랜잭션에 묶인다. 그러면 M4에서 분리할 수 없다")
                .check(classes)
        }
    }

    @Test
    fun `bootstrap 메서드에도 트랜잭션 경계가 없다`() {
        // 클래스 검사만으로는 부족하다. @Transactional은 메서드에 붙는 경우가 더 흔하다.
        TRANSACTIONAL_ANNOTATIONS.forEach { annotation ->
            noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("com.commercelab.bootstrap..")
                .should().beAnnotatedWith(annotation)
                .because("bootstrap이 트랜잭션을 열면 여러 모듈이 한 트랜잭션에 묶인다. 그러면 M4에서 분리할 수 없다")
                .check(classes)
        }
    }
}
