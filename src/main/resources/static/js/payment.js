(() => {
    const page = document.getElementById("payment-page");
    if (!page) {
        return;
    }

    const orderId = page.dataset.orderId;
    const mode = page.dataset.mode;
    const initialStatus = page.dataset.status;

    const message = document.getElementById("payment-message");
    const payButton = document.getElementById("pay-button");
    const retryButton = document.getElementById("retry-button");

    const csrfToken =
        document.querySelector('meta[name="_csrf"]').content;
    const csrfHeader =
        document.querySelector('meta[name="_csrf_header"]').content;

    const endpoint = `/orders/${orderId}/payment`;

    function showMessage(text, type = "secondary") {
        message.textContent = text;
        message.className = `alert alert-${type}`;
    }

    function setBusy(busy) {
        payButton.disabled = busy;
        retryButton.disabled = busy;
    }

    async function post(path, body) {
        const response = await fetch(endpoint + path, {
            method: "POST",
            credentials: "same-origin",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(body ?? {})
        });

        const contentType = response.headers.get("content-type") || "";

        if (response.redirected || !contentType.includes("application/json")) {
            throw new Error(
                "로그인 상태를 확인한 뒤 주문 상세에서 다시 진행해주세요."
            );
        }

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "요청 처리에 실패했습니다.");
        }

        return data;
    }

    function applyResult(result) {
        payButton.hidden = true;
        retryButton.hidden = result.status !== "PAYMENT_PENDING";

        if (result.status === "PAID") {
            showMessage(result.message, "success");
        } else if (result.status === "PAYMENT_PENDING") {
            showMessage(result.message, "warning");
        } else {
            showMessage(result.message, "danger");
        }
    }

    payButton.addEventListener("click", async () => {
        setBusy(true);

        try {
            const prepared = await post("/prepare");

            const tossPayments = TossPayments(page.dataset.clientKey);
            const payment = tossPayments.payment({
                customerKey: page.dataset.customerKey
            });

            await payment.requestPayment({
                method: "CARD",
                amount: {
                    currency: "KRW",
                    value: prepared.amount
                },
                orderId: prepared.orderRef,
                orderName: prepared.orderName,
                successUrl: page.dataset.successUrl,
                failUrl: page.dataset.failUrl
            });

        } catch (error) {
            showMessage(
                error.message || "결제를 시작하지 못했습니다.",
                "danger"
            );
        } finally {
            setBusy(false);
        }
    });

    retryButton.addEventListener("click", async () => {
        setBusy(true);

        try {
            const result = await post("/retry");
            applyResult(result);
        } catch (error) {
            showMessage(error.message, "danger");
        } finally {
            setBusy(false);
        }
    });

    async function confirmFromRedirect() {
        const params = new URLSearchParams(window.location.search);
        const rawAmount = params.get("amount");
        const amount = rawAmount === null ? NaN : Number(rawAmount);

        const body = {
            paymentKey: params.get("paymentKey"),
            orderId: params.get("orderId"),
            amount
        };

        if (!body.paymentKey
            || !body.orderId
            || !Number.isSafeInteger(amount)
            || amount <= 0) {
            showMessage("결제 응답 정보가 올바르지 않습니다.", "danger");
            return;
        }

        setBusy(true);
        showMessage("결제 승인 결과를 확인하고 있습니다.");

        try {
            const result = await post("/confirm", body);

            // 처리한 결제 키를 주소창에 계속 남기지 않습니다.
            history.replaceState(null, "", endpoint);

            applyResult(result);
        } catch (error) {
            showMessage(error.message, "danger");

            // 서버 저장 후 응답만 유실됐을 수도 있습니다.
            retryButton.hidden = false;
        } finally {
            setBusy(false);
        }
    }

    if (initialStatus === "PAID") {
        showMessage("이미 결제가 완료된 주문입니다.", "success");

    } else if (mode === "success") {
        confirmFromRedirect();

    } else if (initialStatus === "PAYMENT_PENDING") {
        showMessage(
            "승인 결과를 확인해야 합니다. 결제 결과 다시 확인을 눌러주세요.",
            "warning"
        );
        retryButton.hidden = false;

    } else if (initialStatus === "PENDING") {
        payButton.hidden = false;

        if (mode === "fail") {
            showMessage(
                "결제가 취소되었거나 인증에 실패했습니다. 예약 시간 안에 다시 시도해주세요.",
                "warning"
            );
        } else {
            showMessage("금액을 확인하고 테스트 결제를 진행해주세요.");
        }

    } else {
        showMessage("현재 상태에서는 결제할 수 없습니다.", "warning");
    }
})();