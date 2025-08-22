package dev.skillter.synaxic.security;

import dev.skillter.synaxic.model.entity.ApiKey;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final ApiKey apiKey;

    public ApiKeyAuthentication(ApiKey apiKey) {
        super(AuthorityUtils.createAuthorityList("ROLE_API_USER"));
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return apiKey.getUser();
    }

    public ApiKey getApiKey() {
        return apiKey;
    }
}