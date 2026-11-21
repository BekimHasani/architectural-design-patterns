# Adding provider #21

No change to `payments/`, `api/`, `platform/` or `config/`. A team that doesn't know the core can do this.

1. Create `providers/<name>/`.
2. Write `<Name>Gateway`: implement `ProviderAdapter` plus **only** the capability interfaces the
   provider really has. Keep its JSON records and mapper package-private.
3. Declare its `ProviderDescriptor`: capabilities, currencies, refund terms, confirmation style.
4. Add a `<Name>Factory` `@Component` implementing `ProviderFactory`.
5. Add one test class extending `GatewayContractTest`. The kit generates the right tests from your
   descriptor.
6. Webhooks, if any: `providers/<name>/webhook/`, mapping to `RecordProviderPaymentEvent`.
7. In `application.yml`: add it to `payment.enabled`, set `base-urls`, add merchant credentials, and
   optionally routing rules.

The app won't start if the descriptor and the interfaces disagree.
