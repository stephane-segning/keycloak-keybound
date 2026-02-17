package com.ssegning.keycloak.keybound.core.spi

import org.keycloak.provider.ProviderFactory
import org.keycloak.provider.ServerInfoAwareProviderFactory

interface ApiGatewayProviderFactory :
    ProviderFactory<ApiGateway>,
    ServerInfoAwareProviderFactory
