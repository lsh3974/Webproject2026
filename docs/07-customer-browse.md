# 7번 구매자 공동구매 조회

## 범위

- `GET /group-purchases`: 목록, 제목·상품명·매장명 검색, 지역·카테고리 검색, 12개 단위 페이징.
- `GET /group-purchases/{id}`: 상품 설명, 가격, 현재·목표·최대 수량, 마감과 수령 정보.
- UI는 선택한 1번 동네 장터 디자인으로 통일한다. 목록 카드·상품 사진·공통 메뉴와 8번 예약 진입을 포함하며 상세 내용은 [MARKET-UI.md](MARKET-UI.md)를 따른다.
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

## 수량 표시와 8번 연동

첨부 설계는 `campaigns`의 예약·결제 수량을 분리하지만 현재 main은 `group_purchases.current_quantity`를 사용한다.
또한 기존 설계는 목표 수량이 판매 상한이지만, main은 `target_quantity`와 `max_quantity`를 구분한다.

- 이번 화면은 DB의 `current_quantity`를 **현재 수량**으로만 표시한다.
- 진행률은 현재/목표 수량이며 표시 백분율은 0~100%로 제한한다. 현재 수량 원본은 별도로 표시한다.
- 7번 조회 서비스는 수량 예약·차감·주문 생성을 처리하지 않는다. 상세의 예약 링크 이후에는 8번 서비스가 잔여 수량·예약 생성을 처리한다.
- 8번 병합 후 예약·확정 수량 및 구매 제한은 README의 8번 기능 설명과 `OrderReservationService`를 기준으로 확인한다. 이 디자인 변경에서는 정책을 바꾸지 않는다.
- 8번 병합 후 상세의 예약 버튼은 기존 `/orders/new?groupPurchaseId={id}` 화면으로 연결한다. 예약·결제 정책은 8번 서비스와 해당 문서를 따른다.

## 로컬 확인

1. 8번까지 병합된 최신 main에서 기능 브랜치를 만든다.
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
비로그인 실제 목록·상세 렌더링, 비공개 초안 차단, POST 및 하위 주문 주소의 인증 제한, 판매자 권한과 CSRF 유지도 검증한다. 7번 테스트의 임의 주문 하위 주소 검사는 보안 필터 제한만 확인한다. 실제 8번 주소는 `OrderIntegrationTest` 및 `MarketUiIntegrationTest`에서 검증한다.

## 적용 파일

- `GroupPurchaseBrowseController`: 기존 기본 화면용 파일이 있으면 내용을 교체한다. 같은 경로의 컨트롤러를 중복 생성하지 않는다.
- `GroupPurchaseQueryService`: 조회 조건·필터·페이징.
- `GroupPurchaseView`: 고객 화면용 데이터. 회원 비밀번호 등 User 엔티티는 전달하지 않는다.
- 기존 `GroupPurchaseRepository`: Specification 조회와 store/product EntityGraph만 추가한다. 판매자용 기존 메서드는 유지한다.
- `templates/group-purchases/`: 목록·상세·공통 헤더/푸터.
- `static/css/market.css`, `static/js/ui.js`, `templates/fragments/ui.html`: 공통 스타일·메뉴·이미지 대체 표시.
- `index.html`: 홈에 목록 진입 링크 추가.
- `SecurityConfig`: 목록·상세 GET 공개 규칙만 추가.
- `pom.xml`: test 범위 H2 의존성 추가.

## 문서 간 기존 불일치

README 앞부분의 역할 제한 미구현·로그인 후 항상 홈 이동 문구는 뒤쪽 4번 기능 설명과 맞지 않는다.
역할·로그인 동작은 현재 SecurityConfig와 RoleBasedLoginSuccessHandler 및 README의 4번 기능 설명을 기준으로 확인한다.
이 파일은 7번 조회와 8번 화면 연결을 기록하며, 예약·결제 정책은 각 담당의 문서와 구현을 따른다.
