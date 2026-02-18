package com.ssegning.keycloak.keybound.examples.backend.controller

import com.ssegning.keycloak.keybound.examples.backend.store.BackendDataStore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@Controller
@RequestMapping("/admin/stores")
class StoreDashboardController(private val store: BackendDataStore) {
    companion object {
        private val log = LoggerFactory.getLogger(StoreDashboardController::class.java)
    }

    @GetMapping
    fun dashboard(model: Model): String {
        val snapshot = store.snapshot()
        log.info(
            "Serving store dashboard with {} users and {} devices",
            snapshot.users.size,
            snapshot.devices.size
        )
        model.addAttribute("users", snapshot.users)
        model.addAttribute("usernameIndex", snapshot.usernameIndex)
        model.addAttribute("emailIndex", snapshot.emailIndex)
        model.addAttribute("devices", snapshot.devices)
        model.addAttribute("devicesByJkt", snapshot.devicesByJkt)
        return "store-dashboard"
    }
}
