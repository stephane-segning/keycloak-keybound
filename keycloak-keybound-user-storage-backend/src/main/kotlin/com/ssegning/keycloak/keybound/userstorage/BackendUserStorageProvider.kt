package com.ssegning.keycloak.keybound.userstorage

import com.ssegning.keycloak.keybound.core.helper.noop
import com.ssegning.keycloak.keybound.core.models.BackendUser
import com.ssegning.keycloak.keybound.core.models.BackendUserSearchCriteria
import com.ssegning.keycloak.keybound.core.spi.ApiGateway
import org.keycloak.component.ComponentModel
import org.keycloak.models.GroupModel
import org.keycloak.models.ModelDuplicateException
import org.keycloak.models.RealmModel
import org.keycloak.models.UserModel
import org.keycloak.models.utils.KeycloakModelUtils
import org.keycloak.storage.StorageId
import org.keycloak.storage.UserStorageProvider
import org.keycloak.storage.user.UserLookupProvider
import org.keycloak.storage.user.UserQueryProvider
import org.keycloak.storage.user.UserRegistrationProvider
import org.keycloak.models.KeycloakSession
import java.util.stream.Stream

class BackendUserStorageProvider(
    private val session: KeycloakSession,
    private val componentModel: ComponentModel,
    private val apiGateway: ApiGateway
) : UserStorageProvider, UserLookupProvider, UserQueryProvider, UserRegistrationProvider {
    private val knownSearchParams = setOf(
        UserModel.SEARCH,
        UserModel.FIRST_NAME,
        UserModel.LAST_NAME,
        UserModel.EMAIL,
        UserModel.USERNAME,
        UserModel.EXACT,
        UserModel.EMAIL_VERIFIED,
        UserModel.ENABLED,
        UserModel.IDP_ALIAS,
        UserModel.IDP_USER_ID,
        UserModel.INCLUDE_SERVICE_ACCOUNT,
        UserModel.GROUPS
    )

    override fun close() = noop()

    override fun getUserById(realm: RealmModel, id: String): UserModel? {
        val backendUserId = StorageId.externalId(id)
        val user = apiGateway.getUser(backendUserId) ?: return null
        if (!isInRealm(realm, user)) {
            return null
        }
        return toUserModel(realm, user)
    }

    override fun getUserByUsername(realm: RealmModel, username: String): UserModel? {
        val users = apiGateway.searchUsers(
            realmName = realm.name,
            criteria = BackendUserSearchCriteria(
                username = username,
                exact = true,
                maxResults = 2
            )
        ).orEmpty()
        val user = singleUserOrNull(realm, "username", username, users)
        return user?.let { toUserModel(realm, it) }
    }

    override fun getUserByEmail(realm: RealmModel, email: String): UserModel? {
        val users = apiGateway.searchUsers(
            realmName = realm.name,
            criteria = BackendUserSearchCriteria(
                email = email,
                exact = true,
                maxResults = 2
            )
        ).orEmpty()
        val user = singleUserOrNull(realm, "email", email, users)
        return user?.let { toUserModel(realm, it) }
    }

    override fun addUser(realm: RealmModel, username: String): UserModel? {
        val normalizedUsername = KeycloakModelUtils.toLowerCaseSafe(username)
        val createdUser = apiGateway.createUser(
            realmName = realm.name,
            username = normalizedUsername
        ) ?: return null
        return toUserModel(realm, createdUser)
    }

    override fun removeUser(realm: RealmModel, user: UserModel): Boolean {
        val backendUserId = StorageId.externalId(user.id)
        return apiGateway.deleteUser(backendUserId)
    }

    override fun searchForUserStream(
        realm: RealmModel,
        params: Map<String, String>,
        firstResult: Int?,
        maxResults: Int?
    ): Stream<UserModel> {
        if (params.containsKey(UserModel.IDP_ALIAS) || params.containsKey(UserModel.IDP_USER_ID)) {
            return Stream.empty()
        }

        val criteria = buildSearchCriteria(params, firstResult, maxResults)
        val users = apiGateway.searchUsers(realm.name, criteria).orEmpty()
        return users
            .filter { isInRealm(realm, it) }
            .map { toUserModel(realm, it) }
            .stream()
    }

    override fun searchForUserByUserAttributeStream(
        realm: RealmModel,
        attrName: String,
        attrValue: String
    ): Stream<UserModel> {
        if (attrName == BackendUserAdapter.BACKEND_USER_ID_ATTRIBUTE || attrName == "id" || attrName == "user_id") {
            return getUserById(realm, attrValue)?.let { Stream.of(it) } ?: Stream.empty()
        }

        val users = apiGateway.searchUsers(
            realmName = realm.name,
            criteria = BackendUserSearchCriteria(
                attributes = mapOf(attrName to attrValue),
                exact = true
            )
        ).orEmpty()

        return users
            .filter { isInRealm(realm, it) }
            .map { toUserModel(realm, it) }
            .stream()
    }

    override fun getGroupMembersStream(
        realm: RealmModel,
        group: GroupModel,
        firstResult: Int?,
        maxResults: Int?
    ): Stream<UserModel> = Stream.empty()

    override fun getUsersCount(realm: RealmModel, includeServiceAccount: Boolean): Int {
        return apiGateway.searchUsers(
            realmName = realm.name,
            criteria = BackendUserSearchCriteria()
        )?.size ?: 0
    }

    private fun buildSearchCriteria(
        params: Map<String, String>,
        firstResult: Int?,
        maxResults: Int?
    ): BackendUserSearchCriteria {
        val search = params[UserModel.SEARCH]
        val exact = parseBoolean(params[UserModel.EXACT])
        val enabled = parseBoolean(params[UserModel.ENABLED])
        val emailVerified = parseBoolean(params[UserModel.EMAIL_VERIFIED])
        val customAttributes = params
            .filterKeys { it !in knownSearchParams }
            .filterValues { it.isNotBlank() }
            .ifEmpty { null }

        if (!search.isNullOrBlank()) {
            return BackendUserSearchCriteria(
                search = search,
                firstResult = firstResult,
                maxResults = maxResults
            )
        }

        return BackendUserSearchCriteria(
            username = params[UserModel.USERNAME],
            firstName = params[UserModel.FIRST_NAME],
            lastName = params[UserModel.LAST_NAME],
            email = params[UserModel.EMAIL],
            enabled = enabled,
            emailVerified = emailVerified,
            exact = exact,
            attributes = customAttributes,
            firstResult = firstResult,
            maxResults = maxResults
        )
    }

    private fun parseBoolean(value: String?): Boolean? = when (value?.lowercase()) {
        "true" -> true
        "false" -> false
        else -> null
    }

    private fun toUserModel(realm: RealmModel, backendUser: BackendUser): UserModel =
        BackendUserAdapter(session, realm, componentModel, apiGateway, backendUser)

    private fun isInRealm(realm: RealmModel, backendUser: BackendUser): Boolean =
        backendUser.realm == null || backendUser.realm == realm.name

    private fun singleUserOrNull(
        realm: RealmModel,
        fieldName: String,
        fieldValue: String,
        users: List<BackendUser>
    ): BackendUser? {
        val filteredUsers = users.filter { isInRealm(realm, it) }
        if (filteredUsers.size > 1) {
            throw ModelDuplicateException("Duplicate users for $fieldName=$fieldValue in realm ${realm.name}")
        }
        return filteredUsers.firstOrNull()
    }

}
