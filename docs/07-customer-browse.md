# 7번 구매자 공동구매 조회

## 범위

- `GET /group-purchases`: 목록, 제목·상품명·매장명 검색, 지역·카테고리 검색, 12개 단위 페이징.
- `GET /group-purchases/{id}`: 상품 설명, 가격, 현재·목표·최대 수량, 마감과 수령 정보.
- UI 통일은 이후 진행한다. 현재는 기능 확인에 필요한 검색 폼·목록 표·상세 정보·링크만 제공하고 이미지, 배너, 카드 장식은 사용하지 않는다.
- `GroupPurchase`, `Product`, `Store`와 기존 `group_purchases` 테이블을 재사용한다. 새 DB SQL은 없다.
- 팀 결정에 따라 목록·상세는 비로그인 사용자도 조회할 수 있다.
- SecurityConfig에서 GET `/group-purchases`, `/group-purchases/{id}`만 permitAll로 허용한다. `/group-purchases/**` 전체나 모든 HTTP 메서드를 공개하지 않는다.
- 주문 관련 주소와 나머지 주소는 기존 인증 제한, `/seller/**`는 기존 SELLER 제한을 유지한다. CSRF와 역할별 로그인 이동도 유지한다.
- 로그인 회원 정보는 기존 LoginUser를 사용하며 사용자 세션 키를 새로 만들지 않는다.

## 이번 구현의 조회 조건 (팀 검토 대상)

- `OPEN`이며 `start_at <= 서버 현재 시각 < end_at`인 모집만 표시한다.
- 매장이 `ACTIVE`, 상품이 `ON_SALE`이어야 한다.
- 상세에도 같은 조건을 적용한다. 초안, 시작 전, 마감, 숨김·판매 중지 상품, 비활성 매장 및 없는 ID는 404이다.
- 완료·마감 모집 이력 조회는 이번 범위에 포함하지 않는다. 이후 내 주문에서 참조할 이력 조회는 별도 정책을 정한다.
- 정렬은 ID 내림차순이며, 제목·상품명·매장명은 통합 부분 검색한다. 지역은 매장 주소, 카테고리는 기존 문자열 필드의 부분 검색이다.
- 현재 Store에는 구조화된 행정동 컬럼이 없다. 따라서 별도 지역 목록이나 DB 컬럼을 임의로 추가하지 않았다.
- 현재 기존 코드와 동일한 `LocalDateTime.now()` 기준이다. UTC 저장 정책 및 기존 데이터의 시간대는 통합 전에 확인해야 한다.

## 수량 관련 미확정 사항

첨부 설계는 `campaigns`의 예약·결제 수량을 분리하지만 현재 main은 `group_purchases.current_quantity`를 사용한다.
또한 기존 설계는 목표 수량이 판매 상한이지만, main은 `target_quantity`와 `max_quantity`를 구분한다.

- 이번 화면은 DB의 `current_quantity`를 **현재 수량**으로만 표시한다.
- 진행률은 현재/목표 수량이며 표시 백분율은 0~100%로 제한한다. 현재 수량 원본은 별도로 표시한다.
- 잔여 주문 가능 수량, 수량 예약·차감, 주문 생성 및 결제 처리는 하지 않는다.
- 8번·9번 담당은 예약 수량 저장 위치, `current_quantity`의 의미, 판매 상한 및 성공 시점을 먼저 확정한다.
- 주문 버튼 대신 현재 조회만 가능한 상태를 명시한다.

## 로컬 확인

1. 5·6번이 병합된 최신 main을 본인 작업 브랜치에 반영한다.
2. 이미 적용하지 않은 경우에만 DB SQL `003-create-stores-and-products.sql`, `004-create-group-purchases.sql`을 순서대로 적용한다.
3. 별도의 판매자 테스트 계정을 로컬 DB에서 SELLER로 설정하고 재로그인한다.
4. `/seller/stores`에서 ACTIVE 매장을 만들고, ON_SALE 상품과 현재 진행 중인 OPEN 모집을 만든다.
5. 로그아웃 상태에서 `/group-purchases`와 목록의 상세 주소에 접속한다. 로그인 링크가 표시되고 정상 조회되는지 확인한다.
6. 검색·필터, 목록의 상세 링크, 빈 결과, 비공개 모집의 상세 404를 확인한다.
7. CUSTOMER로 로그인해도 목록·상세가 정상이고 로그아웃 버튼이 표시되는지 확인한다. 비로그인 주문 접근은 로그인 필요, CUSTOMER의 판매자 경로 접근은 403이어야 한다.

## 자동 검증

```powershell
.\mvnw.cmd "-Dtest=GroupPurchaseBrowseIntegrationTest,*ServiceTest" test
```

목록·상세 통합 테스트는 별도 H2 메모리 DB와 실제 Thymeleaf 템플릿을 사용하며 로컬 MySQL 설정/데이터를 변경하지 않는다.
H2는 테스트 의존성으로만 추가한다. H2 테스트가 MySQL에서의 수동 검증을 대체하지는 않는다.
비로그인 실제 목록·상세 렌더링, 비공개 초안 차단, POST 및 하위 주문 주소의 인증 제한, 판매자 권한과 CSRF 유지도 검증한다. 미구현 주문 주소의 테스트는 보안 필터 제한만 검증하며 주문 기능 검증을 의미하지 않는다.

## 적용 파일

- `GroupPurchaseBrowseController`: 기존 기본 화면용 파일이 있으면 내용을 교체한다. 같은 경로의 컨트롤러를 중복 생성하지 않는다.
- `GroupPurchaseQueryService`: 조회 조건·필터·페이징.
- `GroupPurchaseView`: 고객 화면용 데이터. 회원 비밀번호 등 User 엔티티는 전달하지 않는다.
- 기존 `GroupPurchaseRepository`: Specification 조회와 store/product EntityGraph만 추가한다. 판매자용 기존 메서드는 유지한다.
- `templates/group-purchases/`: 목록·상세·공통 헤더/푸터.
- `static/css/group-purchases.css`: 간격·표·입력칸을 위한 최소 스타일. 이미지 파일은 사용하지 않는다.
- `index.html`: 홈에 목록 진입 링크 추가.
- `SecurityConfig`: 목록·상세 GET 공개 규칙만 추가.
- `pom.xml`: test 범위 H2 의존성 추가.

## 문서 간 기존 불일치

README 앞부분의 역할 제한 미구현·로그인 후 항상 홈 이동 문구는 뒤쪽 4번 기능 설명과 맞지 않는다.
역할·로그인 동작은 현재 SecurityConfig와 RoleBasedLoginSuccessHandler 및 README의 4번 기능 설명을 기준으로 확인한다.
이 파일은 7번 변경과 미확정 사항만 기록하며, 타 담당의 정책을 변경하지 않는다.
