package com.designpatterns.level08.providers.paypal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/** PayPal's JSON shapes. Package-private: they never leave this package. */
final class PayPalApi {

    private PayPalApi() {
    }

    record AccessToken(@JsonProperty("access_token") String accessToken, @JsonProperty("expires_in") long expiresIn) {
    }

    record CreateOrder(String intent, @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits) {
    }

    record PurchaseUnit(@JsonProperty("reference_id") String referenceId, Amount amount) {
    }

    /** {@code value} is a DECIMAL STRING ("48.59"), unlike our integer minor units. */
    record Amount(@JsonProperty("currency_code") String currencyCode, String value) {
    }

    record CaptureOrder(Amount amount) {
    }

    record Refund(Amount amount) {
    }

    record CaptureResponse(@JsonProperty("purchase_units") List<CapturedUnit> purchaseUnits) {

        Optional<String> captureId() {
            return purchaseUnits == null || purchaseUnits.isEmpty() ? Optional.empty()
                    : Optional.of(purchaseUnits.get(0).payments().captures().get(0).id());
        }
    }

    record CapturedUnit(Payments payments) {
    }

    record Payments(List<Capture> captures) {
    }

    record Capture(String id) {
    }

    record Order(String id, String status, List<Link> links) {

        Optional<URI> approveLink() {
            return links == null ? Optional.empty() : links.stream()
                    .filter(l -> "approve".equals(l.rel()))
                    .map(l -> URI.create(l.href()))
                    .findFirst();
        }
    }

    record OrderList(List<Order> orders) {
    }

    record Link(String rel, String href) {
    }

    record Error(String name, List<Detail> details) {

        String firstIssue() {
            return details == null || details.isEmpty() ? "" : details.get(0).issue();
        }
    }

    record Detail(String issue) {
    }
}
