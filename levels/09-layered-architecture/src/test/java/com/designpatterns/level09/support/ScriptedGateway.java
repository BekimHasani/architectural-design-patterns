package com.designpatterns.level09.support;

import com.designpatterns.level09.integration.gateway.AuthorizeRequest;
import com.designpatterns.level09.integration.gateway.FailureCategory;
import com.designpatterns.level09.integration.gateway.GatewayResult;
import com.designpatterns.level09.integration.gateway.OperationRequest;
import com.designpatterns.level09.integration.gateway.ProviderAdapter;
import com.designpatterns.level09.integration.gateway.ProviderCredentials;
import com.designpatterns.level09.integration.gateway.ProviderDescriptor;
import com.designpatterns.level09.integration.gateway.capability.ActionCompleter;
import com.designpatterns.level09.integration.gateway.capability.Authorizer;
import com.designpatterns.level09.integration.gateway.capability.Canceller;
import com.designpatterns.level09.integration.gateway.capability.Capturer;
import com.designpatterns.level09.integration.gateway.capability.Refunder;
import com.designpatterns.level09.model.Capability;
import com.designpatterns.level09.model.ProviderId;
import com.designpatterns.level09.model.RefundTerms;


import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Test double: answers from a script, records what it was asked. No provider code involved. */
public final class ScriptedGateway implements ProviderAdapter, Authorizer, ActionCompleter, Capturer, Canceller, Refunder {

    public final List<AuthorizeRequest> authorizations = new ArrayList<>();
    public final List<OperationRequest> operations = new ArrayList<>();
    public final List<String> statusLookups = new ArrayList<>();
    private final Deque<GatewayResult> script;
    private GatewayResult status = new GatewayResult.Failed(FailureCategory.NOT_PROCESSED, "scripted");
    private ProviderId id = ProviderId.of("scripted");
    private Consumer<AuthorizeRequest> duringAuthorize = request -> { };

    public ScriptedGateway(GatewayResult... results) {
        this.script = new ArrayDeque<>(List.of(results));
    }

    private ProviderDescriptor descriptor(ProviderId id) {
        return new ProviderDescriptor(id, Set.of(Capability.AUTHORIZE, Capability.CAPTURE, Capability.CANCEL, Capability.REFUND,
                Capability.CUSTOMER_ACTION), Set.of(Currency.getInstance("EUR")), RefundTerms.ANY, ProviderDescriptor.Confirmation.SYNC);
    }

    public ScriptedGateway named(ProviderId newId) {
        this.id = newId;
        return this;
    }

    @Override
    public ProviderDescriptor descriptor() {
        return descriptor(id);
    }

    public ScriptedGateway withStatus(GatewayResult result) {
        this.status = result;
        return this;
    }

    public ScriptedGateway duringAuthorize(Consumer<AuthorizeRequest> spy) {
        this.duringAuthorize = spy;
        return this;
    }

    @Override
    public GatewayResult authorize(AuthorizeRequest request) {
        authorizations.add(request);
        duringAuthorize.accept(request);
        return script.removeFirst();
    }

    @Override
    public GatewayResult completeAction(String providerRef, ProviderCredentials credentials) {
        return script.removeFirst();
    }

    @Override
    public GatewayResult capture(OperationRequest request) {
        operations.add(request);
        return script.removeFirst();
    }

    @Override
    public GatewayResult cancel(OperationRequest request) {
        operations.add(request);
        return script.removeFirst();
    }

    @Override
    public GatewayResult refund(OperationRequest request) {
        operations.add(request);
        return script.removeFirst();
    }

    @Override
    public GatewayResult getStatus(String attemptId, ProviderCredentials credentials) {
        statusLookups.add(attemptId);
        return status;
    }
}
