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

역할별 접근 제한과 매장·주문 소유권 검사, 상품 등록, 주문, 결제는 아직 구현되지 않았습니다.

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

회원가입 기능을 사용하기 전에 두 SQL을 모두 적용해야 합니다.
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