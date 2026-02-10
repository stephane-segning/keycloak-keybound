package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.UsersApi
import com.ssegning.keycloak.keybound.examples.backend.model.UserRecord
import com.ssegning.keycloak.keybound.examples.backend.model.UserSearchRequest
import com.ssegning.keycloak.keybound.examples.backend.model.UserSearchResponse
import com.ssegning.keycloak.keybound.examples.backend.model.UserUpsertRequest
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class UsersController(private val store: BackendDataStore) : UsersApi {
    override fun createUser(userUpsertRequest: UserUpsertRequest): ResponseEntity<UserRecord> {
        val user = store.createUser(userUpsertRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(user)
    }

    override fun deleteUser(userId: String): ResponseEntity<Void> {
        if (!store.deleteUser(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return ResponseEntity.noContent().build()
    }

    override fun getUser(userId: String): ResponseEntity<UserRecord> {
        val user = store.getUser(userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        return ResponseEntity.ok(user)
    }

    override fun searchUsers(userSearchRequest: UserSearchRequest): ResponseEntity<UserSearchResponse> {
        val results = store.searchUsers(userSearchRequest)
        val response = UserSearchResponse()
            .users(results)
            .totalCount(results.size)
        return ResponseEntity.ok(response)
    }

    override fun updateUser(userId: String, userUpsertRequest: UserUpsertRequest): ResponseEntity<UserRecord> {
        val updated = store.updateUser(userId, userUpsertRequest)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        return ResponseEntity.ok(updated)
    }
}
