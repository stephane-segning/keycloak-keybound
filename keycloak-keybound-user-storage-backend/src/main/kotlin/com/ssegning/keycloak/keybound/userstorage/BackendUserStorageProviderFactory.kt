package com.ssegning.keycloak.keybound.userstorage

import com.ssegning.keycloak.keybound.core.helper.SPI_CORE_INFO
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.component.ComponentModel
import org.keycloak.models.KeycloakSession
import org.keycloak.provider.ServerInfoAwareProviderFactory
import org.keycloak.storage.UserStorageProviderFactory
import org.slf4j.LoggerFactory

class BackendUserStorageProviderFactory :
    UserStorageProviderFactory<BackendUserStorageProvider>,
    ServerInfoAwareProviderFactory {
    companion object {
        private val log = LoggerFactory.getLogger(BackendUserStorageProviderFactory::class.java)
        const val ID = "backend-user-storage"
    }

    override fun create(session: KeycloakSession, model: ComponentModel): BackendUserStorageProvider {
        log.debug("Creating BackendUserStorageProvider for component {}", model.name)
        return BackendUserStorageProvider(
            session = session,
            componentModel = model,
            apiGateway = session.getProvider(ApiGateway::class.java)
        )
    }

    override fun getId(): String = ID

    override fun getHelpText(): String =
        "Connects Keycloak user storage to the external backend user API."

    override fun getOperationalInfo(): Map<String, String> = SPI_CORE_INFO
}
