package com.designpatterns.level08.providers.paypal;

import com.designpatterns.level08.payments.application.ports.AuthorizeRequest;
import com.designpatterns.level08.payments.application.ports.GatewayResult;
import com.designpatterns.level08.payments.domain.DeclineReason;
import com.designpatterns.level08.payments.application.ports.FailureCategory;
import com.designpatterns.level08.payments.domain.Money;
import com.designpatterns.level08.providers.support.ProviderHttp;
import com.designpatterns.level08.providers.support.ProviderHttp.Reply;

import java.math.BigDecimal;
import java.util.List;

/** PayPal ⇄ our model. Pure functions: tested with recorded JSON, no network. */
final class PayPalMapper {

    private PayPalMapper() {
    }

    static PayPalApi.CreateOrder toCreateOrder(AuthorizeRequest request) {
        return new PayPalApi.CreateOrder("CAPTURE", List.of(new PayPalApi.PurchaseUnit(
                request.attemptId(), // our reference: lets us find the order after a lost response
                toAmount(request.amount()))));
    }

    static PayPalApi.Amount toAmount(Money money) {
        return new PayPalApi.Amount(money.currency().getCurrencyCode(), toDecimal(money));
    }

    /**
     * 4859 EUR-cents → "48.59"; 4859 JPY → "4859". Uses the currency's own number of decimals.
     * NOT {@code amount / 100.0}: that is wrong for JPY and loses precision with doubles.
     */
    static String toDecimal(Money money) {
        return BigDecimal.valueOf(money.amountMinor(), money.currency().getDefaultFractionDigits()).toPlainString();
    }

    /** Maps a create / get / capture answer: all three return an order. */
    static GatewayResult toChargeResult(Reply reply) {
        if (reply.status() == 200 || reply.status() == 201) {
            return ProviderHttp.parse(reply.body(), PayPalApi.Order.class)
                    .map(PayPalMapper::fromOrder)
                    .orElseGet(() -> ProviderHttp.unreadable(reply));
        }
        if (reply.status() == 422 && ProviderHttp.parse(reply.body(), PayPalApi.Error.class)
                .filter(e -> "INSTRUMENT_DECLINED".equals(e.firstIssue())).isPresent()) {
            return new GatewayResult.Declined(DeclineReason.GENERIC);
        }
        return ProviderHttp.failure(reply);
    }

    /** Lookup by our reference: no order means PayPal never processed the attempt. */
    static GatewayResult toStatusResult(Reply reply) {
        if (reply.status() != 200) {
            return ProviderHttp.failure(reply);
        }
        return ProviderHttp.parse(reply.body(), PayPalApi.OrderList.class)
                .map(found -> found.orders() == null || found.orders().isEmpty()
                        ? ProviderHttp.notProcessed()
                        : fromOrder(found.orders().get(0)))
                .orElseGet(() -> ProviderHttp.unreadable(reply));
    }

    /** Capture: the refund will need PayPal's CAPTURE id, not the order id. */
    static GatewayResult toCaptureResult(Reply reply, String orderId) {
        if (reply.status() / 100 == 2) {
            String captureId = ProviderHttp.parse(reply.body(), PayPalApi.CaptureResponse.class)
                    .flatMap(PayPalApi.CaptureResponse::captureId)
                    .orElse(orderId);
            return new GatewayResult.Done(captureId);
        }
        return toOperationFailure(reply);
    }

    static GatewayResult toOperationResult(Reply reply, String providerRef) {
        return reply.status() / 100 == 2 ? new GatewayResult.Done(providerRef) : toOperationFailure(reply);
    }

    private static GatewayResult toOperationFailure(Reply reply) {
        return reply.status() == 422 && ProviderHttp.parse(reply.body(), PayPalApi.Error.class)
                .filter(e -> "INSTRUMENT_DECLINED".equals(e.firstIssue())).isPresent()
                ? new GatewayResult.Declined(DeclineReason.GENERIC)
                : ProviderHttp.failure(reply);
    }

    private static GatewayResult fromOrder(PayPalApi.Order order) {
        return switch (order.status()) {
            case "APPROVED" -> new GatewayResult.Authorized(order.id()); // the buyer agreed: funds can be captured
            case "COMPLETED" -> new GatewayResult.Charged(order.id());
            case "CREATED" -> order.approveLink()
                    .<GatewayResult>map(link -> new GatewayResult.RequiresAction(order.id(), link))
                    .orElseGet(() -> new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "PayPal order without approve link"));
            default -> new GatewayResult.Failed(FailureCategory.OUTCOME_UNKNOWN, "Unexpected PayPal status: " + order.status());
        };
    }
}
