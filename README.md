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
- 홈 화면과 정적 파일의 비로그인 접근 허용
- Actuator 서버 상태 확인
- 회원가입 화면과 입력값 검증
- 이메일·닉네임 중복 검사 및 DB UNIQUE 제약
- BCrypt 비밀번호 해시 저장
- 이메일·닉네임 중복 오류를 해당 입력칸 아래에 표시

가입한 회원 정보로 로그인하는 기능, 상품 등록, 주문, 결제는 아직 구현되지 않았습니다.

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
MySQL에 비밀번호 정책이 설정돼 있다면 해당 조건을 충족해야 합니다.

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

실제 서버 버전은 다음 SQL로 확인합니다.

```sql
SELECT VERSION();
```

각자의 DB 데이터는 GitHub를 통해 자동으로 공유되지 않습니다.
현재 `spring.jpa.hibernate.ddl-auto=none`이므로 테이블은 자동 생성되지 않습니다.

### 회원 테이블 SQL 적용 순서

Workbench에서 `hanbaguni` DB에 연결한 뒤, 다음 파일을 순서대로 열어 실행합니다.

1. `database/001-create-users.sql`: 회원 테이블 생성
2. `database/002-add-unique-nickname.sql`: 닉네임 중복 금지 제약 추가

앱 실행과 회원가입 검증 전에 두 SQL을 모두 적용해야 합니다.
파일을 GitHub에서 받는 것만으로 DB에 반영되지는 않습니다.

- 이미 적용한 파일은 다시 실행하지 않습니다. 특히 002는 반복 실행 시 오류가 발생합니다.
- 001의 `CREATE TABLE IF NOT EXISTS`는 기존 테이블 구조를 수정하지 않습니다.
- 기존에 중복 닉네임이 있으면 002 적용이 실패합니다. 기존 데이터를 임의로 삭제하지 말고 팀과 정리 방법을 협의합니다.
- 각자 적용한 SQL 번호를 기록하고, 앞으로 DB 구조 변경도 별도 번호의 SQL 파일로 공유합니다.

## 3. 실행 환경변수

| 이름 | 기본값 / 설정 방법 |
|---|---|
| DB_URL | 기본값: jdbc:mysql://127.0.0.1:3306/hanbaguni |
| DB_USERNAME | 기본값: hanbaguni_app |
| DB_PASSWORD | 필수: 각자 설정한 앱 계정 비밀번호 |

IntelliJ에서 `HanbaguniApplication` 실행 설정을 만들고
Environment variables에 `DB_PASSWORD`를 등록합니다.

비밀번호를 application.properties나 README에 직접 작성하지 않습니다.
개인 비밀번호가 있는 실행 설정은 Store as project file로 저장하지 않습니다.

## 4. 애플리케이션 실행

MySQL 서버가 실행 중인지 확인한 다음,
IntelliJ에서 `HanbaguniApplication`을 실행합니다.

로그에 `Started HanbaguniApplication`이 표시되면 시작 완료입니다.

- 홈: http://localhost:8080/
- 회원가입: http://localhost:8080/signup
- 상태 확인: http://localhost:8080/actuator/health

상태 확인 응답의 `status`가 `UP`이면 등록된 상태 검사 기준으로 정상입니다.
기본 로그인 화면의 계정은 MySQL 계정과 별개입니다.
현재 기본 로그인 화면은 회원 테이블과 아직 연결되지 않았으므로, 가입 성공이 로그인 기능 완성을 의미하지는 않습니다.

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
IntelliJ 실행 설정의 환경변수는 별도 터미널에 자동 적용되지 않습니다.

현재 확인 완료:
- 테스트 실행을 제외한 Maven 패키징
- IntelliJ 애플리케이션 실행
- MySQL 연결
- 상태 확인 UP 응답
- 홈 화면 표시

회원가입 기능은 다음 항목을 수동 확인했습니다.

| 검증 항목 | 확인 결과 |
|---|---|
| 새로운 이메일·닉네임 가입 | 성공 안내 및 DB 저장 |
| 기존 이메일로 가입 | 이메일 입력칸 아래 중복 오류 표시 |
| 기존 닉네임으로 가입 | 닉네임 입력칸 아래 중복 오류 표시 |
| 회원 기본값 | `CUSTOMER`, `ACTIVE` |
| 비밀번호 저장 | BCrypt 형식의 길이 60인 해시 저장 확인 |

해시 길이 확인은 실제 비밀번호 일치 검증을 대신하지 않습니다.
동시 가입 요청 및 비밀번호 일치 검사에 대한 자동 테스트는 아직 수행하지 않았습니다.

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
- 개인 비밀번호, 비밀키, IDE 설정, 빌드 결과물은 커밋하지 않습니다.
- 개인 Java 선택 파일인 .java-version은 현재 공유 대상에서 제외합니다.

## 8. 회원가입 공통 정책

- 필수 입력: 이메일, 비밀번호, 닉네임(`displayName` / DB의 `display_name`).
- 닉네임은 실명이 아닌 고유한 표시 이름으로 사용하며 중복을 허용하지 않습니다.
- 이메일은 앞뒤 공백 제거 후 소문자로 저장합니다. 로그인 구현 시에도 같은 규칙을 적용합니다.
- 닉네임은 앞뒤 공백을 제거하고 저장합니다.
- 현재 DB 정렬 기준 `utf8mb4_0900_ai_ci`에서는 영문 대소문자와 악센트를 구분하지 않고 중복을 판정합니다.
- 비밀번호는 8자 이상, UTF-8 기준 72바이트 이하입니다. 원문에 공백 제거 등의 변환을 적용하지 않습니다.
- 화면에서 72바이트 안내를 생략하더라도 서버 검사는 유지합니다. MySQL 접속 계정의 비밀번호 정책과는 별개입니다.
- 원문 비밀번호는 저장하거나 로그에 출력하지 않고, BCrypt 해시를 `password_hash`에 저장합니다.
- 일반 가입 회원의 역할은 `CUSTOMER`, 상태는 `ACTIVE`로 지정합니다. 가입 요청으로 판매자·관리자 권한을 선택할 수 없습니다.
- 서비스 사전 조회와 DB UNIQUE 제약을 함께 사용합니다. 둘 다 중복이면 현재는 이메일 오류부터 표시합니다.
- 동시 요청으로 사전 검사를 통과한 뒤 DB 제약에 걸리면 공통 저장 오류가 표시됩니다.
- 세션 기반 인증을 사용하며 CSRF 보호를 유지합니다. POST 폼은 Thymeleaf의 `th:action`을 사용합니다.
- `/signup`은 비로그인 상태에서도 GET과 POST 요청을 허용합니다. 나머지 비공개 경로의 인증 요구는 유지합니다.
