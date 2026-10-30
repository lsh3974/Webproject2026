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
- 판매자 매장 등록 및 정보 수정 (소유권 검증 포함)
- 판매자 상품 등록, 목록 조회, 정보 수정 및 판매상태 관리 (이미지 업로드 및 삭제 지원)
- 판매자 공동구매 모집 등록, 초안 저장(DRAFT), 정식 게시(OPEN), 수정 및 삭제 관리 (6번 기능)
- 구매자 공동구매 목록 검색·필터·페이징 및 상세 조회 (7번 기능)
- 공동구매 주문 수량 예약, 동시성 선점 제어, 타임아웃 만료 및 예약 취소 (8번 기능)

결제(9번) 기능은 다음 단계로 연동 예정입니다.

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

### 회원 및 프로젝트 테이블 SQL 적용 순서

Workbench에서 프로젝트 DB에 연결한 뒤 다음 파일을 순서대로 실행합니다.

1. `database/001-create-users.sql`: 회원 테이블 생성
2. `database/002-add-unique-nickname.sql`: 닉네임 중복 금지 제약 추가
3. `database/003-create-stores-and-products.sql`: 매장 및 상품 테이블 생성
4. `database/004-create-group-purchases.sql`: 공동구매 모집 테이블 생성
5. `database/005-create-orders.sql`: 주문·수량 예약 테이블 생성 (8번 기능용)

회원가입, 매장/상품/공동구매 및 주문 예약 기능을 사용하기 전에 위 SQL을 순서대로 적용해야 합니다.
파일을 GitHub에서 받는 것만으로 DB에 반영되지는 않습니다.

- 이미 적용한 파일은 다시 실행하지 않습니다. 특히 002는 반복 실행 시 오류가 발생합니다.
- 001의 `CREATE TABLE IF NOT EXISTS`는 기존 테이블 구조를 수정하지 않습니다.
- 기존에 중복 닉네임이 있으면 002 적용이 실패합니다. 기존 데이터를 임의로 삭제하지 말고 팀과 정리 방법을 협의합니다.
- 각자 적용한 SQL 번호를 기록합니다.
- 이후 DB 구조 변경도 별도 번호의 SQL 파일로 공유합니다.

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
| 공동구매 둘러보기 | http://localhost:8080/group-purchases |
| 내 주문·수량 예약 내역 | http://localhost:8080/orders |
| 새 수량 예약 신청 | http://localhost:8080/orders/new |
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
- 성공하면 항상 `/`로 이동합니다.
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
- 판매자 전용 접근 제한과 자기 매장·본인 주문 확인은 별도로 구현해야 합니다.
- 권한 정보가 있다는 것만으로 역할별 접근 제한이 자동 완성되는 것은 아닙니다.
- 세션에는 로그인 시점의 회원 정보가 들어갑니다.
- DB에서 상태·역할·닉네임을 바꿔도 기존 세션에 즉시 반영되지 않습니다.
- 기존 세션 강제 만료는 아직 구현하지 않았습니다.
- 세션은 현재 서버 메모리에 저장하므로 서버 재시작 후 재로그인이 필요할 수 있습니다.

## 10. 판매자 매장 및 상품 관리 (5번 기능)

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
| 공동구매 삭제 | POST | `/seller/stores/{storeId}/group-purchases/{id}/delete` | 참여자 및 주문 내역이 없는 공동구매 삭제 처리 |

### 주요 정책 및 검증 규칙

- **초안 저장 및 공개 게시 분리**: 모집 등록 시 `DRAFT`(초안/임시저장)로 먼저 저장해두고 검토 후 `OPEN`(공개 게시)으로 전환하거나, 등록 즉시 `OPEN`으로 게시할 수 있습니다.
- **수량 및 기간 검증**: 최대 모집 수량은 목표 달성 수량 이상이어야 하며(`max_quantity >= target_quantity`), 모집 마감 일시는 시작 일시 이후, 수령 마감 일시는 수령 시작 일시 이후여야 합니다.
- **1인당 제한 수량 선택 설정**: `limit_per_user`는 선택 사항(기본값 제한 없음/`NULL`)으로, 사재기 방지가 필요한 특가 상품에 한해 설정할 수 있습니다.
- **양방향 일수 계산 UI**: 모집 기간과 매장 수령 기간 설정 시 일수(예: 3일간)를 입력하면 마감 일시가 자동 계산되고, 반대로 마감 일시를 직접 지정하면 몇 일 동안 진행되는지 자동으로 역계산되어 표시됩니다.
- **상품 관리 탭 연동**: 상품 목록 화면(`/seller/stores/{storeId}/products`)에서 각 상품의 공동구매 상태(`미등록`, `공동구매 모집 초안`, `공동구매 모집중`, `공동구매 마감`)가 동적 버튼으로 표시됩니다.
- **외래키 제약조건 보호**: 예약 또는 주문 내역이 존재하는 공동구매는 삭제 시 외래키 위반 에러를 방지하기 위해 400 에러로 차단됩니다.

## 13. 구매자 공동구매 조회 (7번 기능)

### 범위

- `GET /group-purchases`: 목록, 제목·상품명·매장명 검색, 지역·카테고리 검색, 12개 단위 페이징.
- `GET /group-purchases/{id}`: 상품 설명, 가격, 현재·목표·최대 수량, 마감과 수령 정보.
- 초기 `siheung_ui_20261023_fixed.html`의 색상, 카드, 레이아웃을 적용했다. 배너 이미지는 해당 시안에서 추출했다.
- `GroupPurchase`, `Product`, `Store`와 기존 `group_purchases` 테이블을 재사용한다. 새 DB SQL은 없다.
- SecurityConfig와 로그인 정책은 변경하지 않는다. 현재 main 기준으로 목록·상세 모두 로그인해야 접근 가능하다.
- 비로그인 공개 여부는 팀 결정 후 별도 반영한다. 사용자 세션 키를 새로 만들지 않는다.

### 이번 구현의 조회 조건 (팀 검토 대상)

- `OPEN`이며 `start_at <= 서버 현재 시각 < end_at`인 모집만 표시한다.
- 매장이 `ACTIVE`, 상품이 `ON_SALE`이어야 한다.
- 상세에도 같은 조건을 적용한다. 초안, 시작 전, 마감, 숨김·판매 중지 상품, 비활성 매장 및 없는 ID는 404이다.
- 완료·마감 모집 이력 조회는 이번 범위에 포함하지 않는다. 이후 내 주문에서 참조할 이력 조회는 별도 정책을 정한다.
- 정렬은 ID 내림차순이며, 제목·상품명·매장명은 통합 부분 검색한다. 지역은 매장 주소, 카테고리는 기존 문자열 필드의 부분 검색이다.
- 현재 Store에는 구조화된 행정동 컬럼이 없다. 따라서 별도 지역 목록이나 DB 컬럼을 임의로 추가하지 않았다.
- 현재 기존 코드와 동일한 `LocalDateTime.now()` 기준이다. UTC 저장 정책 및 기존 데이터의 시간대는 통합 전에 확인해야 한다.

### 수량 관련 미확정 사항

첨부 설계는 `campaigns`의 예약·결제 수량을 분리하지만 현재 main은 `group_purchases.current_quantity`를 사용한다.
또한 기존 설계는 목표 수량이 판매 상한이지만, main은 `target_quantity`와 `max_quantity`를 구분한다.

- 이번 화면은 DB의 `current_quantity`를 **현재 수량**으로만 표시한다.
- 진행률은 현재/목표 수량이며 화면 막대는 0~100%로 제한한다. 현재 수량 원본은 별도로 표시한다.
- 잔여 주문 가능 수량, 수량 예약·차감, 주문 생성 및 결제 처리는 하지 않는다.
- 8번·9번 담당은 예약 수량 저장 위치, `current_quantity`의 의미, 판매 상한 및 성공 시점을 먼저 확정한다.
- 주문 버튼 대신 현재 조회만 가능한 상태를 명시한다.

### 로컬 확인

1. 5·6번이 병합된 최신 main을 본인 작업 브랜치에 반영한다.
2. 이미 적용하지 않은 경우에만 DB SQL `003-create-stores-and-products.sql`, `004-create-group-purchases.sql`을 순서대로 적용한다.
3. 별도의 판매자 테스트 계정을 로컬 DB에서 SELLER로 설정하고 재로그인한다.
4. `/seller/stores`에서 ACTIVE 매장을 만들고, ON_SALE 상품과 현재 진행 중인 OPEN 모집을 만든다.
5. 검색·필터, 카드의 상세 링크, 빈 결과, 비공개 모집의 상세 404를 확인한다.
6. 로그아웃 후 두 조회 주소 모두 로그인 페이지로 이동하는지 확인한다.

### 적용 파일

- `GroupPurchaseBrowseController`: 기존 기본 화면용 파일이 있으면 내용을 교체한다. 같은 경로의 컨트롤러를 중복 생성하지 않는다.
- `GroupPurchaseQueryService`: 조회 조건·필터·페이징.
- `GroupPurchaseView`: 고객 화면용 데이터. 회원 비밀번호 등 User 엔티티는 전달하지 않는다.
- 기존 `GroupPurchaseRepository`: Specification 조회와 store/product EntityGraph만 추가한다. 판매자용 기존 메서드는 유지한다.
- `templates/group-purchases/`: 목록·상세·공통 헤더/푸터.
- `static/css/group-purchases.css`, `static/images/catalog-hero.webp`: 화면 리소스.
- `index.html`: 홈에 목록 진입 링크 추가.

### 수정 사항

- 비회원도 GET `/group-purchases`, `/group-purchases/{id}`를 조회할 수 있습니다.
- 목록과 상세는 모두 OPEN이고 모집 기간 안에 있으며, 매장이 ACTIVE이고 상품이 ON_SALE인 경우만 공개합니다. 그 외 상세는 404입니다.
- 기존 `/seller/**` SELLER 제한, 나머지 주소의 인증 제한, CSRF, 역할별 로그인 성공 처리를 유지합니다.
- `GroupPurchaseView.imageUrl`은 상품의 `imageUrl`을 전달합니다. 이미지가 없는 상세도 정상 렌더링합니다.
- 새 DB 컬럼이나 SQL 변경은 없습니다. H2는 test 범위로만 사용하고 실행 DB는 기존 MySQL입니다.
- 조회 통합 테스트와 기존 서비스 테스트 실행: `.\mvnw.cmd "-Dtest=GroupPurchaseBrowseIntegrationTest,*ServiceTest" test`
- 통합 테스트는 별도 H2 메모리 DB를 사용하며 비회원 목록·상세 실제 HTML, 이미지 유무, 공개 대상 필터, 판매자 접근 제한을 확인합니다. 로컬 MySQL 검증은 별도로 진행합니다.

## 14. 공동구매 주문 수량 예약 및 선점 관리 (8번 기능)

### 지원 기능 및 엔드포인트

| 기능 | HTTP 메서드 | 요청 주소 | 설명 |
|---|---|---|---|
| 내 주문·예약 목록 | GET | `/orders` | 로그인한 구매자의 전체 주문/예약 이력 및 상태 확인 |
| 예약 대상 공구 선택 | GET | `/orders/new` | 현재 모집 중인 공동구매 목록 중 수량을 예약할 공구 선택 |
| 수량 예약 신청 화면 | GET | `/orders/new?groupPurchaseId={id}` | 단가, 잔여 수량, 1인당 구매 제한을 확인하고 수량을 선택하는 폼 |
| 수량 예약 신청 처리 | POST | `/orders/new` | 비관적 락으로 수량을 안전하게 선점하고 15분 결제 대기(`PENDING`) 주문 생성 |
| 주문·예약 상세 화면 | GET | `/orders/{orderId}` | 주문 상세 정보, 수령 안내, 결제 만료 카운트다운 타이머 제공 |
| 수량 예약 취소 처리 | POST | `/orders/{orderId}/cancel` | `PENDING` 상태의 예약 취소 및 선점 수량 즉시 반환 |
| REST: 수량 예약 생성 | POST | `/api/v1/orders` | REST API 기반 수량 예약 주문 생성 (JSON 응답) |
| REST: 내 주문 목록 | GET | `/api/v1/orders` | 로그인한 사용자의 주문 목록 조회 |
| REST: 단일 주문 상세 | GET | `/api/v1/orders/{orderId}` | 단일 주문 상세 JSON 조회 |
| REST: 수량 예약 취소 | POST | `/api/v1/orders/{orderId}/cancel-reservation` | REST API 기반 예약 취소 (선점 수량 반환) |

### 주요 정책 및 무결성 보장

- **비관적 쓰기 락(PESSIMISTIC_WRITE) 기반 동시성 제어**:
  - 다수의 사용자가 동시에 마지막 남은 재고를 주문하더라도 `GroupPurchase` 행 락을 획득하여 **초과 예약(Overselling)을 원천 차단**합니다.
  - 예약 취소 시에도 `Order` 행 락을 획득하여 취소 처리와 결제 완료 처리의 동시 충돌을 방지합니다.
- **실시간 재고 선점 및 타임아웃 만료(15분 Hold)**:
  - 주문 생성 시 기본 15분(`RESERVATION_HOLD_MINUTES`) 동안 수량을 선점하며 상태는 `PENDING`이 됩니다.
  - 공동구매 마감 시각(`endAt`)이 15분 미만으로 남은 경우 선점 기한(`reservedUntil`)을 마감 시각으로 제한합니다.
  - 선점 기한이 지난 주문은 신규 예약 및 조회 시점에 자동으로 `EXPIRED`로 전환되어 다른 사용자를 위한 잔여 수량으로 즉시 환원됩니다.
- **1인당 구매 제한(`limit_per_user`) 검증 및 사용자 알림**:
  - 사용자의 확정 수량 + 유효 예약 수량 합계가 `limit_per_user`를 초과할 수 없도록 서버에서 철저히 검증합니다.
  - 화면에서도 초과 수량을 선택하거나 제출 시 브라우저 경고 알림(`alert`)을 띄우고 폼 전송을 차단합니다.
- **소유권 및 자전거래 검증**:
  - 본인의 주문만 조회 및 취소할 수 있으며, 타인 주문 접근 시 `403 FORBIDDEN`을 반환합니다.
  - 판매자가 본인이 운영하는 매장의 공동구매에 예약하려 할 경우 `400 BAD_REQUEST`로 차단합니다.
- **가독성 최적화**:
  - 상품 단위(`unit`)가 `"30개"`, `"10개"` 등 숫자가 포함된 경우 화면에 숫자가 겹쳐 보이던 문제를 개선하여, 상단 상품명에만 단위를 명시하고 수량 영역은 순수 숫자만 깔끔하게 출력합니다.
- **DB 스키마 추가**: `database/005-create-orders.sql` (주문 테이블 생성)
