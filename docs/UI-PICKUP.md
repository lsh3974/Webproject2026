# 동네 장터 UI — 11번 QR·매장 수령 반영

## 기준

- 최신 main `3b08215` (11번 수령 기능 및 정합성 보강 병합)에서 시작한 `codex/market-ui-pickup` 브랜치다.
- 기존 `feature/market-ui` 디자인을 합치고 최신 결제·환불·수령 화면에 그린·크림 스타일을 적용했다.
- 운영 Java, SQL, application.properties, pom.xml은 기준 main과 동일하다. 상태·권한·수량 정책은 기존 서버를 따른다.
- 최초 8번 디자인에 대한 과거 검증 기록은 현재 기준으로 사용하지 않는다.

## 구매자 화면

내 주문의 **수령 안내·QR**에서 기존 `/orders/{id}`로 이동한다.

| 수령 상태 | 화면 |
|---|---|
| PREPARING | 준비 안내, 새로고침. QR·수령 코드 숨김 |
| READY이며 `pickup.canComplete()` | 큰 QR, 수령 코드, 코드 복사, 매장 안내 |
| PICKED_UP | 수령 완료 시각 표시, QR 숨김 |
| 취소·환불 | 수령용 QR 숨김 |

실제 수령은 main 정책대로 PAID 주문, SUCCESS 모집, 판매자 READY 처리가 필요하다. 화면에서 상태를 임의로 만들지 않는다.
결제 확인 중·환불 처리 중 배지도 주문 목록에 추가했다. 결제·환불 상세 액션과 기존 countdown은 유지한다.

## 판매자 화면

- 내 매장, 공통 판매자 메뉴, 상품 관리 탭에서 **매장 수령**으로 진입한다.
- 수령 관리에서 준비중/수령대기/수령완료/취소 필터와 건수를 표시한다. 필터는 현재 가져온 목록을 화면에서 분류한다.
- 카메라 QR 또는 수령 코드 조회 → 상품·고객·수량 확인 → 수령 확정 순서로 사용한다.
- 기존 `canMarkReady()`, `canComplete()`를 유지하고, 스캔 모달도 조회 API의 `canComplete`에 따라 확정 버튼을 활성화한다.
- 스캔 확인 모달을 닫으면 일시정지된 스캐너를 재개한다.
- 기존 시연용 수령 생성과 QR 보기 기능은 시연용으로 표시하고 유지한다. 운영 전환 제거 지침은 기존 README를 따른다.

## 유지한 연결

| 기능 | 연결 |
|---|---|
| 코드 검색 | `GET /seller/stores/{storeId}/pickups?searchCode=...` |
| QR 조회 | `GET .../pickups/api/lookup?code=...` |
| 준비완료 | `POST .../pickups/{pickupId}/ready` |
| 목록 수령 확정 | `POST .../pickups/{pickupId}/complete` |
| 스캔 수령 확정 | `POST .../pickups/scan-complete`, `pickupCode` 필드 |
| 시연 수령 생성 | 기존 `POST .../pickups/mock-create` 및 필드 |
| 보안 | SELLER/매장 소유권, POST CSRF, 수령·환불 중복 처리 검사 |

`payment/checkout.html`도 공통 디자인과 상품 요약을 적용했다. 기존 payment-page와 데이터 속성, pay-button/retry-button/payment-message, CSRF meta, payment.js를 유지한다.
초기·성공·실패 리다이렉트는 기존처럼 같은 결제 템플릿을 사용한다. 외부 토스 인증창 자체를 수정하지 않는다.

## 담당 파일

- `templates/fragments/ui.html`: 공통 메뉴·헤더·푸터·리소스.
- `static/css/market.css`: 장터 스타일, 모바일 수령 목록, QR 수령증, 모달, 결제 패널.
- `static/js/pickup-ticket.js`: 로컬 QR 생성, 코드 복사/실패 안내.
- `static/js/pickup-dashboard.js`: 상태 필터·건수 및 카메라 연결 환경 안내.
- `templates/seller/pickup/index.html`: 기존 수령 폼·스캔 연결과 새 배치.
- `templates/order/detail.html`: 결제·환불·수령 안내 및 QR.
- `templates/order/list.html`: 결제·수령 진입, 새 상태 표시.

Bootstrap 5.3.8, qrcodejs 1.0.0, html5-qrcode 2.3.8을 로컬에서 제공한다.
QR 생성용 외부 이미지 API에 수령 코드를 전송하지 않는다. 생성 실패 시 표시된 수령 코드를 사용한다.
QR 라이브러리 라이선스는 `static/js/vendor/*.LICENSE`에 포함했다.

## 검증

- 최신 main 전체 + 기존 디자인 + 수령 UI 테스트 **142개 통과**. 추가 상태 샘플 확장 후 수령 UI 테스트 3개 재검증 통과.
- 새 통합 테스트는 실제 Thymeleaf 렌더링, QR 노출 조건, CSRF, 판매자 접근, 조회 API 전화번호 마스킹, 준비완료→스캔 수령완료를 확인한다.
- 브라우저: 41개 샘플 × 390/768/1440px = **123개 화면**, 가로 넘침·누락 이미지/CSS·JS 오류 없음.
- 생성된 구매자/판매자 QR을 디코딩해 표시된 코드와 일치하는지 확인했다. 필터·시연 폼 토글·코드 복사 핸들러·상태별 모달 버튼·스캔 재개/중지도 검증했다.
- 화면/백엔드 테스트는 H2 샘플과 기존 mock Toss gateway를 사용한다. 실제 MySQL/고객 데이터에 시연 주문을 넣지 않는다.
- 카메라 콜백과 클립보드 기록은 모의 객체로 검사한다. 실제 휴대폰 카메라 인식·실제 클립보드 권한·파일 업로드·실제 Toss 결제는 팀 PC에서 확인한다.

```powershell
.\mvnw.cmd test
```

샘플 원본 HTML과 조회 JSON을 내보내려면:

```powershell
.\mvnw.cmd "-Dtest=MarketUiIntegrationTest,PickupUiIntegrationTest" "-Dmarket.previewDir=target/market-preview" "-Dpickup.previewDir=target/pickup-preview" test
```

ZIP의 `preview/index.html`은 리소스 경로와 전송을 변환한 디자인 미리보기다. 결제·저장·수령 확정을 실제 서버로 보내지 않으며 QR은 시연용 코드다.

## 팀 PC에서 적용·확인

기존 Java 17·MySQL·DB/Toss 환경 변수를 유지한다. 11번 DB가 아직 없다면 기존 `database/012-create-pickups.sql`을 로컬 DB에 한 번 적용한다. 이미 적용했다면 다시 실행하지 않는다.
디자인 적용을 위해 DB를 초기화하거나 기존 데이터를 삭제할 필요는 없다.

1. 내 매장 → 매장 수령 메뉴 확인.
2. 테스트 결제 주문의 준비중 상태에서는 QR이 없는지 확인.
3. 모집 SUCCESS 후 판매자 준비완료 → 구매자 QR 표시.
4. QR/코드 조회로 같은 수령증을 찾고 상품 인도 후 수령 확정.
5. 수령 완료 시각과 QR 숨김, 이미 수령/환불된 코드의 확정 차단 확인.
6. 모바일에서 필터·긴 코드·조회 결과·모달 확인.

카메라는 localhost 또는 HTTPS와 카메라 권한이 필요하다. 휴대폰에서 PC의 HTTP 내부 IP로 접속하면 카메라가 제한될 수 있으므로 코드 조회를 대안으로 제공한다.
팀원 검토 후 새 브랜치에서 main으로 PR한다. 이전 디자인 브랜치를 별도로 다시 병합할 필요는 없다.
