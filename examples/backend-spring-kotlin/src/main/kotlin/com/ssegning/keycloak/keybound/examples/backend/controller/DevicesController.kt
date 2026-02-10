package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.api.DevicesApi
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceLookupRequest
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceLookupResponse
import com.ssegning.keycloak.keybound.examples.backend.model.DeviceRecord
import com.ssegning.keycloak.keybound.examples.backend.model.UserDevicesResponse
import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class DevicesController(private val store: BackendDataStore) : DevicesApi {
    override fun disableUserDevice(userId: String, deviceId: String): ResponseEntity<DeviceRecord> {
        val record = store.disableDevice(userId, deviceId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found for user")
        return ResponseEntity.ok(record)
    }

    override fun listUserDevices(userId: String, includeRevoked: Boolean): ResponseEntity<UserDevicesResponse> {
        val devices = store.listUserDevices(userId, includeRevoked)
        val response = UserDevicesResponse()
            .userId(userId)
            .devices(devices)
        return ResponseEntity.ok(response)
    }

    override fun lookupDevice(deviceLookupRequest: DeviceLookupRequest): ResponseEntity<DeviceLookupResponse> {
        val response = store.lookupDevice(deviceLookupRequest.deviceId, deviceLookupRequest.jkt)
        return ResponseEntity.ok(response)
    }
}
