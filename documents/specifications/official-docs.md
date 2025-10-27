좋아요! Kotlin + Spring Boot best practices 문서들을 찾았어요. 핵심 링크들을 정리해줄게요.

## 📚 공식 문서 (가장 권위 있음)

### 1. **Spring 공식 Kotlin 가이드** ⭐⭐⭐⭐⭐
**URL**: https://docs.spring.io/spring-framework/reference/languages/kotlin.html

- Spring Framework 공식 Kotlin 문서
- 모든 코드 샘플이 Java와 Kotlin 두 버전으로 제공
- Null Safety, Coroutines, DSL 등 다룸
- **제일 신뢰할 수 있는 소스**

### 2. **Spring Boot Kotlin Support**
**URL**: https://docs.spring.io/spring-boot/reference/features/kotlin.html

- Spring Boot 공식 Kotlin 지원 문서
- Dependency Management
- Configuration Properties
- Testing 등 다룸

### 3. **Kotlin 공식 - Spring Boot 시작하기**
**URL**: https://kotlinlang.org/docs/jvm-get-started-spring-boot.html

- JetBrains(Kotlin 만든 회사) 공식 튜토리얼
- 프로젝트 생성부터 DB 연동까지
- 단계별로 매우 상세함

---

## 📖 실전 가이드 (추천)

### 4. **Pro Spring Boot 3 with Kotlin** (2025년 1월 출간) ⭐⭐⭐⭐⭐
**URL**: https://link.springer.com/book/10.1007/979-8-8688-1131-9

- **가장 최신** 책 (2025년 1월 출간)
- Pivotal(Spring 만든 회사) 시니어 아키텍트 집필
- Cloud-Native, Microservices 포커스
- 936 페이지 분량
- **Best practices가 가장 잘 정리됨**

**참고**: O'Reilly나 도서관에서 접근 가능

### 5. **Kotlin Data Classes in Spring Boot**
**URL**: https://kotlincraft.dev/articles/kotlin-data-classes-in-spring-boot

- Data Class 사용법과 안티패턴
- JPA Entity 사용 시 주의사항
- DTO 매핑 best practices
- 실용적인 예제 많음

### 6. **Configuration Properties Best Practices**
**URL**: https://dev.to/art_ptushkin/kotlin-spring-boot-configuration-properties-best-practices-13e6

- @ConfigurationProperties 올바른 사용법
- Kotlin의 default value 활용
- 안티패턴 피하기

---

## 🎯 핵심 포인트 요약

### Spring + Kotlin의 주요 Best Practices

```kotlin
// 1. Constructor Injection (권장)
@Service
class UserService(
    private val userRepository: UserRepository,
    private val emailService: EmailService
) {
    // @Autowired 불필요
}

// 2. Data Class for DTOs
data class UserDto(
    val id: Long,
    val username: String,
    val email: String
)

// 3. Nullable vs Non-nullable
@Entity
class User(
    @Id
    var id: Long? = null,  // JPA는 null 필요
    
    val username: String,   // 필수값은 non-null
    val email: String? = null  // 선택값은 nullable
)

// 4. Extension Functions
fun User.toDto() = UserDto(
    id = id!!,
    username = username,
    email = email
)

// 5. Coroutines (Reactive 대신)
@RestController
class UserController(
    private val userService: UserService
) {
    @GetMapping("/users")
    suspend fun getUsers(): List<UserDto> {
        return userService.findAll()
    }
}
```

---

## 🔗 빠른 참조 링크 모음

| 주제 | 링크 |
|------|------|
| **공식 Kotlin 가이드** | https://docs.spring.io/spring-framework/reference/languages/kotlin.html |
| **Spring Boot Kotlin** | https://docs.spring.io/spring-boot/reference/features/kotlin.html |
| **시작 튜토리얼** | https://kotlinlang.org/docs/jvm-get-started-spring-boot.html |
| **DB 연동 가이드** | https://kotlinlang.org/docs/jvm-spring-boot-add-db-support.html |
| **Data Classes** | https://kotlincraft.dev/articles/kotlin-data-classes-in-spring-boot |
| **Config Properties** | https://dev.to/art_ptushkin/kotlin-spring-boot-configuration-properties-best-practices-13e6 |

---

## 💡 추천 학습 순서

1. **시작**: Kotlin 공식 튜토리얼 (1-2시간)
   - https://kotlinlang.org/docs/jvm-get-started-spring-boot.html

2. **심화**: Spring 공식 문서 읽기 (2-3시간)
   - https://docs.spring.io/spring-framework/reference/languages/kotlin.html

3. **실전**: 프로젝트 시작하면서 필요한 부분 참조
   - Data Classes, Configuration 등

4. **마스터**: Pro Spring Boot 3 with Kotlin 책 읽기
   - 시간 날 때 조금씩
