# 시흥한바구니

지역 마트의 상품을 공동구매하고 매장에서 수령하는 웹 프로그래밍 팀 프로젝트입니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Java | 17 |
| Backend | Spring Boot 4.0.9, Spring MVC |
| Frontend | Thymeleaf, HTML/CSS, JavaScript, Bootstrap 5.3.8 |
| Database | MySQL 8.0.44 |
| ORM | Spring Data JPA, Hibernate |
| Security | Spring Security, Session |
| Build | Maven Wrapper |
| Collaboration | Git, GitHub |

## 현재 구현 범위

- Spring Boot 프로젝트 기본 구성
- 환경변수 기반 MySQL 연결
- Bootstrap 기본 홈 화면
- Actuator 서버 상태 확인
- 회원가입 및 입력값 검증
- 이메일·닉네임 중복 검사와 DB UNIQUE 제약
- BCrypt 비밀번호 해시 저장
- DB 회원 정보를 이용한 이메일 로그인
- 세션 기반 로그인 유지와 홈 화면의 닉네임 표시
- POST 로그아웃과 세션 종료
- 정지·탈퇴 계정의 신규 로그인 차단
- 비로그인 사용자의 비공개 주소 접근 시 로그인 화면으로 이동
- 고객·판매자 역할별 접근 제한
- 판매자 매장 등록 및 정보 수정 (소유권 검증 포함)
- 판매자 상품 등록, 목록 조회, 정보 수정 및 판매상태 관리 (이미지 업로드 및 삭제 지원)
- 판매자 공동구매 모집 등록, 초안 저장(DRAFT), 정식 게시(OPEN), 수정 및 삭제 관리
- 주문 결제 승인 및 취소 상태 처리, 공동구매 참여 수량 연동

결제 API는 현재 외부 결제 대행사와 실제 승인·환불을 수행하는 연동이 아니라, 요청을 받아 내부 주문 상태와 공동구매 수량을 갱신하는 단계입니다.

## 1. Java 설정

Java 17을 설치하고 IntelliJ에서 다음 항목을 확인합니다.

- Project SDK: Java 17
- Project Language Level: 17
- Module SDK: Project SDK
- 실행 설정의 JDK: Java 17

터미널에서는 다음 명령으로 확인합니다.

```bash
java -version
./mvnw -version
```

Windows에서는 `./mvnw` 대신 `.\mvnw.cmd`를 사용합니다.

Maven Wrapper가 있으므로 Maven을 별도로 설치할 필요는 없습니다.

## 2. 로컬 DB 준비

각자 MySQL 8.0.44 서버를 준비하고 실행합니다.
관리자 계정으로 접속한 뒤 아래 SQL을 실행합니다.

`YOUR_LOCAL_PASSWORD`는 각자 정한 실제 비밀번호로 바꿉니다.
MySQL에 비밀번호 정책이 설정되어 있다면 해당 조건을 충족해야 합니다.

```sql
CREATE DATABASE IF NOT EXISTS hanbaguni
CHARACTER SET utf8mb4
COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'hanbaguni_app'@'localhost'
IDENTIFIED BY 'YOUR_LOCAL_PASSWORD';

GRANT ALL PRIVILEGES ON hanbaguni.*
TO 'hanbaguni_app'@'localhost';
```

이미 계정을 생성했다면 `CREATE USER`를 다시 실행하지 않습니다.
위 권한은 로컬 개발용이며 `hanbaguni` DB에만 적용됩니다.

실제로 접속한 서버 버전은 다음 SQL로 확인합니다.

```sql
SELECT VERSION();
```

각자의 DB 데이터는 GitHub를 통해 자동으로 공유되지 않습니다.
현재 `spring.jpa.hibernate.ddl-auto=none`이므로 테이블은 자동 생성되지 않습니다.

### 회원 테이블 SQL 적용 순서

Workbench에서 프로젝트 DB에 연결한 뒤 다음 파일을 순서대로 실행합니다.

1. `database/001-create-users.sql`: 회원 테이블 생성
2. `database/002-add-unique-nickname.sql`: 닉네임 중복 금지 제약 추가
3. `database/003-create-stores-and-products.sql`: 매장 및 상품 테이블 생성
4. `database/004-create-group-purchases.sql`: 공동구매 모집 테이블 생성

회원가입 및 매장/상품/공동구매 기능을 사용하기 전에 위 SQL을 순서대로 적용해야 합니다.
파일을 GitHub에서 받는 것만으로 DB에 반영되지는 않습니다.

- 이미 적용한 파일은 다시 실행하지 않습니다. 특히 002는 반복 실행 시 오류가 발생합니다.
- 001의 `CREATE TABLE IF NOT EXISTS`는 기존 테이블 구조를 수정하지 않습니다.
- 기존에 중복 닉네임이 있으면 002 적용이 실패합니다. 기존 데이터를 임의로 삭제하지 말고 팀과 정리 방법을 협의합니다.
- 각자 적용한 SQL 번호를 기록합니다.
- 이후 DB 구조 변경도 별도 번호의 SQL 파일로 공유합니다.
- 이번 로그인 기능을 위해 추가로 적용할 SQL은 없습니다.

## 3. 실행 환경변수

| 이름 | 기본값 / 설정 방법 |
|---|---|
| DB_URL | 기본값: jdbc:mysql://127.0.0.1:3306/hanbaguni |
| DB_USERNAME | 기본값: hanbaguni_app |
| DB_PASSWORD | 필수: 각자 설정한 앱 계정 비밀번호 |

IntelliJ에서 `HanbaguniApplication` 실행 설정을 만들고,
Environment variables에 `DB_PASSWORD`를 등록합니다.

비밀번호를 application.properties나 README에 직접 작성하지 않습니다.
개인 비밀번호가 있는 실행 설정은 Store as project file로 저장하지 않습니다.

IntelliJ 실행 설정의 환경변수는 별도 터미널에 자동 적용되지 않습니다.

## 4. 애플리케이션 실행

MySQL 서버가 실행 중인지 확인한 다음,
IntelliJ에서 `HanbaguniApplication`을 실행합니다.

로그에 `Started HanbaguniApplication`이 표시되면 시작 완료입니다.

| 화면 / 기능 | 주소 |
|---|---|
| 홈 | http://localhost:8080/ |
| 회원가입 | http://localhost:8080/signup |
| 로그인 | http://localhost:8080/login |
| 상태 확인 | http://localhost:8080/actuator/health |
| 내 매장 관리 (판매자) | http://localhost:8080/seller/stores |
| 새 매장 등록 (판매자) | http://localhost:8080/seller/stores/new |

상태 확인 응답의 `status`가 `UP`이면 등록된 상태 검사 기준으로 정상입니다.

회원가입 화면에서 만든 이메일·비밀번호로 로그인합니다.
MySQL 접속 계정과 웹사이트 회원 계정은 별개입니다.

로그인에 성공하면 홈으로 이동하고 닉네임과 로그아웃 버튼이 표시됩니다.
홈의 로그아웃 버튼을 누르면 세션이 종료되고 `/login?logout`으로 이동합니다.

Bootstrap CSS는 CDN에서 불러오므로 인터넷 연결이 필요합니다.

## 5. 빌드와 테스트

컴파일과 JAR 생성을 확인하되 테스트 실행을 제외하는 명령입니다.

```bash
./mvnw clean package -DskipTests
```

테스트 실행 명령입니다.

```bash
./mvnw test
```

현재 기본 테스트는 Spring 애플리케이션을 시작하므로
MySQL 실행과 DB_PASSWORD 설정이 필요합니다.

### 기본 환경 확인

- 초기 설정 단계에서 테스트 실행을 제외한 Maven 패키징 성공
- IntelliJ 애플리케이션 실행
- MySQL 연결
- 상태 확인 UP 응답
- 홈 화면 표시

### 회원가입 수동 검증

| 검증 항목 | 확인 결과 |
|---|---|
| 새로운 이메일·닉네임 가입 | 성공 안내 및 DB 저장 |
| 기존 이메일로 가입 | 이메일 입력칸 아래 중복 오류 표시 |
| 기존 닉네임으로 가입 | 닉네임 입력칸 아래 중복 오류 표시 |
| 회원 기본값 | CUSTOMER, ACTIVE |
| 비밀번호 저장 | BCrypt 형식의 길이 60인 해시 저장 확인 |

해시 길이 확인만으로 실제 비밀번호 일치 여부를 검증할 수는 없습니다.
정상·오류 비밀번호 비교는 아래 로그인 수동 테스트로 확인했습니다.

### 로그인·로그아웃 수동 검증

| 검증 항목 | 확인 결과 |
|---|---|
| 가입한 계정의 정상 로그인 | 홈으로 이동하고 닉네임 표시 |
| 홈 새로고침 | 로그인 상태 유지 |
| 로그아웃 | 완료 안내 후 홈에서 로그인·회원가입 버튼 표시 |
| 틀린 비밀번호 | 공통 로그인 실패 안내, 비로그인 상태 유지 |
| 미가입 이메일 | 동일한 공통 로그인 실패 안내 |
| 정지 계정(SUSPENDED) | 로그아웃 후 정확한 비밀번호로도 신규 로그인 차단 |
| 탈퇴 상태(WITHDRAWN) | 로그아웃 후 신규 로그인 차단 |
| 비로그인 상태에서 /private-check 요청 | 로그인 화면으로 이동 |
| 로그인 상태에서 /private-check 요청 | 인증 통과 후 존재하지 않는 주소이므로 404 |
| 로그아웃 후 /private-check 재요청 | 다시 로그인 화면으로 이동 |

`/private-check`는 접근 제한 확인에 사용한 미구현 주소이며,
실제 기능 엔드포인트가 아닙니다.

이 테스트는 공통 인증 제한만 확인한 것으로,
실제 비공개 기능이나 역할·소유권 검사를 검증한 것은 아닙니다.

계정 상태 테스트는 테스트 회원만 사용했고,
종료 후 ACTIVE로 복구했습니다.
실제 회원 탈퇴 처리 기능을 검증한 것은 아닙니다.

### 남은 검증

- 회원가입·로그인 자동 테스트
- 동시 중복 가입 요청 처리
- CSRF 토큰이 없는 POST 요청의 거부
- 실제 비공개 기능의 역할·소유권 검사

자동 테스트 실행은 아직 확인하지 않았습니다.

## 6. IntelliJ Maven 동기화 오류

Maven Wrapper는 현재 Maven 3.10.0을 사용합니다.

IntelliJ 동기화에서 아래 오류가 발생한 경우:

`No implementation for ArtifactHandlerManager was bound`

Settings → Build Tools → Maven → Maven home path를
`Bundled (Maven 3)`로 바꾸고 Reload All Maven Projects를 실행합니다.

이 방법으로 현재 개발 환경의 동기화와 실행을 확인했습니다.
이 경우 IntelliJ 내장 Maven과 터미널의 Wrapper 버전은 다를 수 있습니다.

## 7. 협업 규칙

- 기준 브랜치: main
- 기능별 브랜치에서 작업하고 PR로 병합합니다.
- main 병합 전 최소 한 명의 검토를 받습니다.
- 기능 단위로 구현과 검증을 마친 뒤 커밋합니다.
- 개인 비밀번호, 비밀키, IDE 설정, 빌드 결과물은 커밋하지 않습니다.
- 개인 Java 선택 파일인 .java-version은 현재 공유 대상에서 제외합니다.
- 공통 설정과 DB 구조를 변경하면 README와 관련 SQL도 함께 갱신합니다.

## 8. 회원가입 공통 정책

- 필수 입력: 이메일, 비밀번호, 닉네임.
- 닉네임의 Java 필드명은 `displayName`, DB 열 이름은 `display_name`입니다.
- 닉네임은 고유한 표시 이름으로 사용하며 중복을 허용하지 않습니다.
- 이메일은 앞뒤 공백 제거 후 소문자로 저장하며 로그인 조회에도 같은 규칙을 적용합니다.
- 닉네임은 앞뒤 공백을 제거하고 저장합니다.
- 현재 DB 정렬 기준 `utf8mb4_0900_ai_ci`에서는 영문 대소문자와 악센트를 구분하지 않고 중복을 판정합니다.
- 비밀번호는 8자 이상, UTF-8 기준 72바이트 이하입니다.
- 비밀번호 원문에 공백 제거 등의 변환을 적용하지 않습니다.
- 화면에서 72바이트 안내를 생략하더라도 서버 검사는 유지합니다.
- 웹사이트 비밀번호 정책은 MySQL 접속 계정의 비밀번호 정책과 별개입니다.
- 원문 비밀번호는 저장하거나 로그에 출력하지 않고 BCrypt 해시를 저장합니다.
- 일반 가입 회원의 역할은 CUSTOMER, 상태는 ACTIVE입니다.
- 가입 요청으로 판매자·관리자 권한을 선택할 수 없습니다.
- 서비스 사전 조회와 DB UNIQUE 제약을 함께 사용합니다.
- 이메일과 닉네임이 모두 중복이면 현재는 이메일 오류부터 표시합니다.
- 동시 요청으로 사전 검사를 통과한 뒤 DB 제약에 걸리면 공통 저장 오류가 표시됩니다.

## 9. 로그인·권한 연결 안내

### 인증 흐름과 주소

- `GET /login`: LoginController가 로그인 화면을 표시합니다.
- `POST /login`: Spring Security가 email, password 입력값으로 인증합니다.
- 별도의 POST 로그인 컨트롤러를 만들지 않습니다.
- CustomUserDetailsService가 이메일을 정리해 DB 회원을 조회하고 LoginUser를 반환합니다.
- Spring Security가 PasswordEncoder로 입력 비밀번호와 DB 해시를 비교합니다.
- 로그인 성공 시 역할별 핸들러(`RoleBasedLoginSuccessHandler`)에 의해 이동합니다. (SELLER: `/seller/stores`, CUSTOMER: `/`)
- 실패하면 `/login?error`에서 공통 오류를 표시합니다.
- 정지·탈퇴 계정의 신규 로그인을 차단합니다.
- 로그인 실패 원인을 세부적으로 구분해 계정 존재 여부를 알리지 않습니다.
- `POST /logout`은 인증 정보와 세션을 제거하고 JSESSIONID 쿠키를 삭제합니다.
- CSRF 보호를 유지합니다.
- 로그인·로그아웃 POST 폼은 Thymeleaf의 th:action을 사용합니다.
- 로그아웃을 단순 GET 링크로 구현하지 않습니다.

### 현재 회원 정보 조회

현재 로그인한 회원은 다음 클래스로 표현합니다.

`com.siheung.hanbaguni.security.LoginUser`

컨트롤러에서는 아래 매개변수 선언으로 받을 수 있습니다.

```java
@AuthenticationPrincipal LoginUser loginUser
```

필요한 import는 다음과 같습니다.

```java
import com.siheung.hanbaguni.security.LoginUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
```

| 메서드 | 의미 |
|---|---|
| getUserId() | DB 회원 ID |
| getDisplayName() | 닉네임 |
| getUsername() | 로그인 이메일 |
| getAuthorities() | ROLE_CUSTOMER, ROLE_SELLER, ROLE_ADMIN 권한 |

### 다른 기능과 연결할 때 주의할 점

- 공개 경로에서는 비로그인 요청의 loginUser가 없을 수 있으므로 확인 후 사용합니다.
- 회원 ID와 권한은 클라이언트가 보낸 값을 신뢰하지 말고 인증 정보에서 가져옵니다.
- LoginUser 자체를 응답이나 로그로 출력하지 않고 필요한 필드만 사용합니다.
- 현재 비공개 주소는 로그인 여부만 검사합니다.
- 판매자 전용 접근 제한과 매장 소유권 검증은 구현되어 있습니다. 결제 API의 주문 소유권 검증은 별도로 보완해야 합니다.
- 권한 정보가 있다는 것만으로 역할별 접근 제한이 자동 완성되는 것은 아닙니다.
- 세션에는 로그인 시점의 회원 정보가 들어갑니다.
- DB에서 상태·역할·닉네임을 바꿔도 기존 세션에 즉시 반영되지 않습니다.
- 기존 세션 강제 만료는 아직 구현하지 않았습니다.
- 세션은 현재 서버 메모리에 저장하므로 서버 재시작 후 재로그인이 필요할 수 있습니다.

## 10. 고객·판매자 권한 처리 (4번 기능)

### 접근 정책

| 사용자 | `/seller/**` 접근 |
|---|---|
| 비로그인 | 로그인 화면으로 이동 |
| CUSTOMER | 403 접근 거부 |
| SELLER | 접근 허용 |
| ADMIN | 현재 판매자 접근 권한 없음 |

판매자 경로는 `hasRole("SELLER")`로 제한합니다.
관리자 권한이 판매자 권한을 자동으로 포함하지는 않습니다.

### 로그인 후 이동

- SELLER: `/seller/stores`
- CUSTOMER 및 그 외 계정: `/`
- 로그인 전 방문 주소보다 권한별 시작 화면을 우선합니다.
- `RoleBasedLoginSuccessHandler`에서 이동 주소를 결정합니다.

### 접근 거부 화면

- 접근 권한이 없으면 HTTP 403을 반환합니다.
- `templates/error/403.html`에서 안내 화면을 표시합니다.
- 화면의 홈 버튼을 통해 `/`로 이동할 수 있습니다.

### 팀 개발 시 참고 사항

- 로그인 사용자 정보는 `@AuthenticationPrincipal LoginUser`로 받습니다.
- 회원가입 기본 권한은 CUSTOMER입니다.
- 판매자 테스트 계정은 각자 로컬 DB에서 role을 SELLER로 변경합니다.
- DB에서 권한을 변경한 뒤에는 로그아웃 후 다시 로그인해야 합니다.
- 5번: 매장·상품 관리에도 동일한 판매자 경로 제한이 있으므로 병합 시 중복 없이 유지합니다.
- 판매자 역할 확인과 개별 매장·상품의 소유권 검사는 별개입니다. 소유권 검사는 해당 기능에서 유지해야 합니다.

### 수동 검증 결과

- 판매자 로그인 후 `/seller/stores`로 이동 확인
- 구매자의 판매자 경로 접근 시 403 확인
- 403 안내 화면 표시 확인
- 비로그인 상태의 판매자 경로 접근 시 로그인 화면 이동 확인

## 11. 판매자 매장 및 상품 관리 (5번 기능)

### 지원 기능 및 엔드포인트

| 기능 | HTTP 메서드 | 요청 주소 | 설명 |
|---|---|---|---|
| 매장 목록 | GET | `/seller/stores` | 로그인한 사용자가 등록한 매장 목록 및 대시보드 |
| 매장 등록 폼 | GET | `/seller/stores/new` | 신규 매장 정보 입력 화면 |
| 매장 등록 처리 | POST | `/seller/stores/new` | 입력값 검증 후 신규 매장 DB 생성 |
| 매장 수정 폼 | GET | `/seller/stores/{id}/edit` | 본인 매장 정보 수정 화면 (소유권 검증) |
| 매장 수정 처리 | POST | `/seller/stores/{id}/edit` | 본인 매장 정보 및 영업상태(ACTIVE/CLOSED/SUSPENDED) 갱신 |
| 상품 목록 | GET | `/seller/stores/{storeId}/products` | 특정 매장의 상품 목록 및 상태 표시 |
| 상품 등록 폼 | GET | `/seller/stores/{storeId}/products/new` | 매장 소속 신규 상품 등록 화면 |
| 상품 등록 처리 | POST | `/seller/stores/{storeId}/products/new` | 상품명, 카테고리, 가격, 단위, 설명 등록 (파일 업로드 지원) |
| 상품 수정 폼 | GET | `/seller/stores/{storeId}/products/{productId}/edit` | 본인 상품 수정 화면 |
| 상품 수정 처리 | POST | `/seller/stores/{storeId}/products/{productId}/edit` | 상품 정보 및 판매상태(ON_SALE/SOLD_OUT/HIDDEN) 갱신 |
| 상품 상태 간편 변경 | POST | `/seller/stores/{storeId}/products/{productId}/status` | 상품 목록에서 원클릭으로 판매상태(판매중/품절/숨김) 변경 |
| 상품 삭제 | POST | `/seller/stores/{storeId}/products/{productId}/delete` | 본인 상품 삭제 처리 (소유권 검증 및 확인창 제공) |

### 소유권 검증 원칙

- 매장 및 상품 조회/수정 요청 시 클라이언트의 입력값이 아닌 세션의 `@AuthenticationPrincipal LoginUser loginUser`에서 `userId`를 획득하여 해당 매장의 `seller_id`와 일치하는지 검증합니다.
- 타인의 매장 또는 상품에 대한 접근 및 수정 시도시 `403 FORBIDDEN` 예외를 발생시켜 비인가 접근을 차단합니다.
- 4번 권한 기능(판매자 역할 체크) 병합 전이라도 현재 로그인한 계정으로 즉시 매장과 상품을 생성하고 CRUD를 테스트할 수 있습니다.

## 12. 판매자 공동구매 모집 관리 (6번 기능)

### 지원 기능 및 엔드포인트

| 기능 | HTTP 메서드 | 요청 주소 | 설명 |
|---|---|---|---|
| 공동구매 목록 | GET | `/seller/stores/{storeId}/group-purchases` | 특정 매장에 등록된 공동구매 캠페인 목록 및 현황 표시 |
| 공동구매 등록 폼 | GET | `/seller/stores/{storeId}/group-purchases/new` | 대상 상품 선택, 공구가/수량/모집기간/수령조건 입력 화면 (`?productId=` 자동 선택 지원) |
| 공동구매 등록 처리 | POST | `/seller/stores/{storeId}/group-purchases/new` | 초안 저장(`DRAFT`) 또는 즉시 공개 게시(`OPEN`)로 DB 저장 |
| 공동구매 수정 폼 | GET | `/seller/stores/{storeId}/group-purchases/{id}/edit` | 본인 매장의 공동구매 설정 및 상태 수정 화면 |
| 공동구매 수정 처리 | POST | `/seller/stores/{storeId}/group-purchases/{id}/edit` | 공동구매 조건 및 상태(`DRAFT`/`OPEN`/`CLOSED`) 갱신 |
| 초안 즉시 게시 | POST | `/seller/stores/{storeId}/group-purchases/{id}/publish` | 초안(`DRAFT`) 상태의 모집을 즉시 공개(`OPEN`)로 전환 |
| 모집 상태 간편 변경 | POST | `/seller/stores/{storeId}/group-purchases/{id}/status` | 목록 화면에서 원클릭으로 상태(`DRAFT`/`OPEN`/`CLOSED`) 전환 |
| 공동구매 삭제 | POST | `/seller/stores/{storeId}/group-purchases/{id}/delete` | 참여자가 없는(`current_quantity == 0`) 공동구매 삭제 처리 |

### 주요 정책 및 검증 규칙

- **초안 저장 및 공개 게시 분리**: 모집 등록 시 `DRAFT`(초안/임시저장)로 먼저 저장해두고 검토 후 `OPEN`(공개 게시)으로 전환하거나, 등록 즉시 `OPEN`으로 게시할 수 있습니다.
- **수량 및 기간 검증**: 최대 모집 수량은 목표 달성 수량 이상이어야 하며(`max_quantity >= target_quantity`), 모집 마감 일시는 시작 일시 이후, 수령 마감 일시는 수령 시작 일시 이후여야 합니다.
- **1인당 제한 수량 선택 설정**: `limit_per_user`는 선택 사항(기본값 제한 없음/`NULL`)으로, 사재기 방지가 필요한 특가 상품에 한해 설정할 수 있습니다.
- **양방향 일수 계산 UI**: 모집 기간과 매장 수령 기간 설정 시 일수(예: 3일간)를 입력하면 마감 일시가 자동 계산되고, 반대로 마감 일시를 직접 지정하면 몇 일 동안 진행되는지 자동으로 역계산되어 표시됩니다.
- **상품 관리 탭 연동**: 상품 목록 화면(`/seller/stores/{storeId}/products`)에서 각 상품의 공동구매 상태(`미등록`, `공동구매 모집 초안`, `공동구매 모집중`, `공동구매 마감`)가 동적 버튼으로 표시됩니다.

## 13. 주문 및 결제 (Phase 9·10)

### 기능 개요

- **결제 승인**: 주문을 조회하고 결제 금액이 요청에 포함된 경우 주문 금액과 비교합니다. 승인 시 주문 상태를 `PAID`로 변경하고, 결제 키가 제공되면 저장하며, 연결된 공동구매의 `current_quantity`를 주문 수량만큼 증가시킵니다.
- **취소 처리**: 주문 상태를 `CANCELLED`로 변경하고 취소 사유를 저장하며, 공동구매의 `current_quantity`를 주문 수량만큼 감소시킵니다. 감소 결과가 음수가 되지 않도록 보정합니다.
- **트랜잭션**: 승인·취소 서비스는 `@Transactional`로 처리되어 주문과 공동구매 수량 변경을 하나의 DB 트랜잭션으로 묶습니다.
- **오류 응답**: 없는 주문/공동구매 및 금액 불일치는 `IllegalArgumentException`, 이미 처리된 상태는 `IllegalStateException`으로 처리합니다. `GlobalExceptionHandler`는 두 예외를 `400 Bad Request`로 응답합니다.

### 데이터 매핑 및 연동

`Order` 엔티티는 `orders` 테이블에 매핑됩니다. `database/005-create-orders.sql`과 엔티티의 주요 컬럼은 다음과 같습니다.

| DB 컬럼 | 엔티티 필드 | 용도 |
|---|---|---|
| `id` | `id` | 주문 식별자 |
| `user_id` | `userId` | 주문한 회원 |
| `group_purchase_id` | `groupPurchaseId` | 연결된 공동구매 |
| `quantity` | `quantity` | 주문 수량 |
| `total_price` | `totalPrice` | 주문 금액 |
| `status` | `status` | `PENDING`, `PAID`, `CANCELLED` 등 |
| `payment_key` | `paymentKey` | 결제 대행사 결제 키 |
| `cancel_reason` | `cancelReason` | 취소 사유 |
| `created_at`, `updated_at` | `createdAt`, `updatedAt` | 생성·수정 시각 |

`PaymentService`는 `OrderRepository`와 `GroupPurchaseRepository`를 사용해 주문 및 공동구매를 조회하고 갱신합니다. Hibernate 자동 스키마 변경은 비활성화되어 있으므로(`spring.jpa.hibernate.ddl-auto=none`), 로컬 DB에 SQL을 반영할 때 엔티티와 기존 테이블의 컬럼이 일치하는지 확인해야 합니다.

### API 명세

| 기능 | 메서드 및 경로 | 성공 응답 |
|---|---|---|
| 결제 승인 | `POST /api/v1/orders/{orderId}/payments/confirm` | `200 OK`, `결제가 성공적으로 승인되었습니다.` |
| 취소·환불 상태 처리 | `POST /api/v1/orders/{orderId}/payments/cancel` | `200 OK`, `주문이 취소 및 환불 처리되었습니다. 사유: {cancelReason}` |

승인 요청의 실제 DTO 필드는 `provider`, `providerPaymentKey`, `providerOrderRef`, `amountKrw`입니다. `amountKrw`는 원 단위 정수이며, `providerPaymentKey`가 있으면 `orders.payment_key`에 저장됩니다.

```http
POST /api/v1/orders/1/payments/confirm
Content-Type: application/json
Cookie: JSESSIONID=<로그인 세션>
X-CSRF-TOKEN: <현재 세션의 CSRF 토큰>

{
  "provider": "TOSS_PAYMENTS",
  "providerPaymentKey": "<결제 키>",
  "providerOrderRef": "<주문 참조값>",
  "amountKrw": 2222220
}
```

취소 요청의 실제 DTO 필드는 `cancelAmountKrw`, `cancelReason`입니다.

```http
POST /api/v1/orders/1/payments/cancel
Content-Type: application/json
Cookie: JSESSIONID=<로그인 세션>
X-CSRF-TOKEN: <현재 세션의 CSRF 토큰>

{
  "cancelAmountKrw": 2222220,
  "cancelReason": "고객 요청에 따른 취소"
}
```

### 인증 및 CSRF

- 두 API는 `SecurityConfig`의 `.anyRequest().authenticated()` 정책에 따라 로그인 세션이 필요합니다. `/api/v1/orders/**`를 `permitAll()`로 공개하거나 CSRF 예외 경로로 지정하지 않았습니다.
- POST 요청은 CSRF 보호 대상입니다. 먼저 같은 클라이언트·세션으로 `GET /login`을 호출해 HTML 폼의 `_csrf` 토큰을 얻고, 로그인 POST에는 `_csrf` 폼 필드로 보냅니다. 로그인 성공 후 갱신된 `JSESSIONID`를 사용하고, 해당 로그인 세션에서 새 토큰을 얻어 이후 POST 요청의 `X-CSRF-TOKEN` 헤더로 전달합니다.
- 이 프로젝트는 현재 `ROLE_CUSTOMER` 자체를 결제 API의 접근 규칙으로 지정하지 않습니다. 로그인 여부 검증과 주문 소유권 검증은 별개이며, 현재 결제 서비스에는 요청자의 주문 소유권 확인이 없습니다.

### 검증 결과와 현재 제한 사항

브라우저에서 발급한 로그인 세션을 IntelliJ HTTP Client 요청에 연결해 결제 승인·취소 및 잘못된 금액, 중복 처리, 존재하지 않는 주문 등 정상·오류 시나리오를 검증했습니다.

현재 구현에서 확인해야 할 제한 사항은 다음과 같습니다.

- 외부 PG에 결제 승인·환불을 요청하거나 결제 결과를 검증하는 코드는 없습니다. 성공 응답은 현재 내부 DB 상태 변경을 의미하며, 실제 결제 완료를 보증하지 않습니다.
- 승인 금액이 누락되면 금액 비교를 건너뜁니다. `provider`와 `providerOrderRef`는 DTO에 있지만 서비스에서 검증하지 않습니다.
- 취소 요청의 `cancelAmountKrw`는 현재 서비스에서 사용하거나 검증하지 않습니다.
- 중복 승인/취소 확인은 각각 현재 `PAID`/`CANCELLED` 상태만 검사합니다. 다른 상태 전이의 허용 여부를 포함해 상태 전이 규칙을 보강해야 합니다.
- 트랜잭션은 단일 요청의 DB 변경을 묶지만, 동시 요청에서 중복 처리나 수량 경합을 방지하는 잠금·낙관적 버전 검사는 구현되어 있지 않습니다. 또한 승인 시 공동구매 최대 수량 초과 검증도 없습니다.
- 주문 소유권 및 `ROLE_CUSTOMER` 검증, 실제 PG 검증, 필수 금액·취소 금액 검증을 운영 환경에 적용하기 전에 구현해야 합니다.
