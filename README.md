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

회원가입, 상품 등록, 주문, 결제 기능은 아직 구현되지 않았습니다.

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
테이블 생성 SQL은 추후 별도로 추가할 예정입니다.

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
- 상태 확인: http://localhost:8080/actuator/health

상태 확인 응답의 `status`가 `UP`이면 등록된 상태 검사 기준으로 정상입니다.
기본 로그인 화면의 계정은 MySQL 계정과 별개입니다.

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