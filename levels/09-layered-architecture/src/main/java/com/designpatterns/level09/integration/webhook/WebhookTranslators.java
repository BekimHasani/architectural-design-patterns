package com.designpatterns.level09.integration.webhook;

import com.designpatterns.level09.model.ProviderId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * REGISTRY of translators by provider id. Spring hands over every {@link ProviderEventTranslator}
 * bean, so a provider's translator registers itself just by being a {@code @Component}.
 */
@Component
public class WebhookTranslators {

    private final Map<ProviderId, ProviderEventTranslator> byProvider;

    public WebhookTranslators(List<ProviderEventTranslator> translators) {
        this.byProvider = translators.stream()
                .collect(Collectors.toUnmodifiableMap(ProviderEventTranslator::provider, Function.identity()));
    }

    public Optional<ProviderEventTranslator> forProvider(ProviderId providerId) {
        return Optional.ofNullable(byProvider.get(providerId));
    }
}
