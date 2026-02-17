package com.ssegning.keycloak.keybound.userstorage

import com.ssegning.keycloak.keybound.core.models.BackendUser
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.component.ComponentModel
import org.keycloak.models.KeycloakSession
import org.keycloak.models.ModelException
import org.keycloak.models.RealmModel
import org.keycloak.models.SubjectCredentialManager
import org.keycloak.models.UserModel
import org.keycloak.storage.StorageId
import org.keycloak.storage.adapter.AbstractUserAdapter
import org.slf4j.LoggerFactory
import java.util.stream.Stream

class BackendUserAdapter(
    session: KeycloakSession,
    realm: RealmModel,
    private val componentModel: ComponentModel,
    private val apiGateway: ApiGateway,
    backendUser: BackendUser,
) : AbstractUserAdapter(session, realm, componentModel) {
    companion object {
        const val BACKEND_USER_ID_ATTRIBUTE = "backend_user_id"
    }

    private val log = LoggerFactory.getLogger(BackendUserAdapter::class.java)
    private var user: BackendUser = backendUser
    private var createdTimestamp = backendUser.createdAt?.toEpochMilliseconds()
    private val keycloakStorageId = StorageId.keycloakId(componentModel, backendUser.userId)

    override fun getId(): String = keycloakStorageId

    override fun getUsername(): String = user.username

    override fun setUsername(username: String) {
        persistUser(user.copy(username = username))
    }

    override fun getFirstName(): String? = user.firstName

    override fun setFirstName(firstName: String?) {
        persistUser(user.copy(firstName = firstName))
    }

    override fun getLastName(): String? = user.lastName

    override fun setLastName(lastName: String?) {
        persistUser(user.copy(lastName = lastName))
    }

    override fun getEmail(): String? = user.email

    override fun setEmail(email: String?) {
        persistUser(user.copy(email = email?.lowercase()))
    }

    override fun isEnabled(): Boolean = user.enabled

    override fun setEnabled(enabled: Boolean) {
        persistUser(user.copy(enabled = enabled))
    }

    override fun isEmailVerified(): Boolean = user.emailVerified

    override fun setEmailVerified(verified: Boolean) {
        persistUser(user.copy(emailVerified = verified))
    }

    override fun getCreatedTimestamp(): Long? = createdTimestamp

    override fun setCreatedTimestamp(timestamp: Long?) {
        createdTimestamp = timestamp
    }

    override fun getFirstAttribute(name: String): String? =
        when (name) {
            UserModel.USERNAME -> user.username
            UserModel.FIRST_NAME -> user.firstName
            UserModel.LAST_NAME -> user.lastName
            UserModel.EMAIL -> user.email
            UserModel.ENABLED -> user.enabled.toString()
            UserModel.EMAIL_VERIFIED -> user.emailVerified.toString()
            BACKEND_USER_ID_ATTRIBUTE -> user.userId
            else -> user.attributes[name]
        }

    override fun getAttributeStream(name: String): Stream<String> =
        getFirstAttribute(name)
            ?.let { Stream.of(it) }
            ?: Stream.empty()

    override fun getAttributes(): MutableMap<String, MutableList<String>> {
        val attributes = linkedMapOf<String, MutableList<String>>()
        attributes[UserModel.USERNAME] = mutableListOf(user.username)
        user.firstName?.let { attributes[UserModel.FIRST_NAME] = mutableListOf(it) }
        user.lastName?.let { attributes[UserModel.LAST_NAME] = mutableListOf(it) }
        user.email?.let { attributes[UserModel.EMAIL] = mutableListOf(it) }
        attributes[UserModel.ENABLED] = mutableListOf(user.enabled.toString())
        attributes[UserModel.EMAIL_VERIFIED] = mutableListOf(user.emailVerified.toString())
        attributes[BACKEND_USER_ID_ATTRIBUTE] = mutableListOf(user.userId)
        user.attributes.forEach { (key, value) ->
            attributes[key] = mutableListOf(value)
        }
        return attributes
    }

    override fun setSingleAttribute(
        name: String,
        value: String?,
    ) {
        when (name) {
            UserModel.USERNAME -> {
                if (value.isNullOrBlank()) {
                    throw ModelException("Username cannot be null or blank")
                }
                persistUser(user.copy(username = value))
            }

            UserModel.FIRST_NAME -> persistUser(user.copy(firstName = value))
            UserModel.LAST_NAME -> persistUser(user.copy(lastName = value))
            UserModel.EMAIL -> persistUser(user.copy(email = value?.lowercase()))
            UserModel.ENABLED -> persistUser(user.copy(enabled = parseBooleanValue(name, value, true)))
            UserModel.EMAIL_VERIFIED -> persistUser(user.copy(emailVerified = parseBooleanValue(name, value, false)))
            BACKEND_USER_ID_ATTRIBUTE -> Unit
            else -> {
                val updatedAttributes = user.attributes.toMutableMap()
                if (value == null) {
                    updatedAttributes.remove(name)
                } else {
                    updatedAttributes[name] = value
                }
                persistUser(user.copy(attributes = updatedAttributes))
            }
        }
    }

    override fun setAttribute(
        name: String,
        values: MutableList<String>?,
    ) {
        setSingleAttribute(name, values?.firstOrNull())
    }

    override fun removeAttribute(name: String) {
        when (name) {
            UserModel.USERNAME -> throw ModelException("Username cannot be removed")
            UserModel.FIRST_NAME -> persistUser(user.copy(firstName = null))
            UserModel.LAST_NAME -> persistUser(user.copy(lastName = null))
            UserModel.EMAIL -> persistUser(user.copy(email = null))
            UserModel.ENABLED -> persistUser(user.copy(enabled = true))
            UserModel.EMAIL_VERIFIED -> persistUser(user.copy(emailVerified = false))
            BACKEND_USER_ID_ATTRIBUTE -> Unit
            else -> {
                val updatedAttributes = user.attributes.toMutableMap()
                updatedAttributes.remove(name)
                persistUser(user.copy(attributes = updatedAttributes))
            }
        }
    }

    override fun credentialManager(): SubjectCredentialManager = session.users().getUserCredentialManager(this)

    private fun parseBooleanValue(
        attribute: String,
        value: String?,
        defaultValue: Boolean,
    ): Boolean =
        when (value?.lowercase()) {
            null -> defaultValue
            "true" -> true
            "false" -> false
            else -> throw ModelException("Attribute $attribute must be boolean but was $value")
        }

    private fun persistUser(updatedUser: BackendUser) {
        log.debug("Persisting backend user {} attribute change", user.userId)
        val persistedUser =
            apiGateway.updateUser(
                userId = user.userId,
                realmName = realm.name,
                username = updatedUser.username,
                firstName = updatedUser.firstName,
                lastName = updatedUser.lastName,
                email = updatedUser.email,
                enabled = updatedUser.enabled,
                emailVerified = updatedUser.emailVerified,
                attributes = updatedUser.attributes,
            ) ?: run {
                log.error("Failed to persist backend user {}", user.userId)
                throw ModelException("Failed to update backend user ${user.userId}")
            }

        user = persistedUser
        createdTimestamp = persistedUser.createdAt
            ?.toEpochMilliseconds()
            ?: createdTimestamp
    }
}
