package com.ssegning.keycloak.keybound.spi

import org.keycloak.provider.Provider
import org.keycloak.provider.ProviderFactory
import org.keycloak.provider.Spi

open class ApiGatewaySpi : Spi {
    override fun isInternal(): Boolean = true

    override fun getName(): String = "backend-spi"

    override fun getProviderClass(): Class<out Provider> = ApiGateway::class.java

    override fun getProviderFactoryClass(): Class<out ProviderFactory<out ApiGateway>> =
        ApiGatewayProviderFactory::class.java

    override fun isEnabled(): Boolean = true
}