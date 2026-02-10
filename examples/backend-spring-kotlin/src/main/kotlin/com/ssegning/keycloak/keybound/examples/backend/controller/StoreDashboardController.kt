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
            "Serving store dashboard with {} users, {} devices, {} approvals, {} sms challenges",
            snapshot.users.size,
            snapshot.devices.size,
            snapshot.approvals.size,
            snapshot.smsChallenges.size
        )
        model.addAttribute("users", snapshot.users)
        model.addAttribute("usernameIndex", snapshot.usernameIndex)
        model.addAttribute("emailIndex", snapshot.emailIndex)
        model.addAttribute("devices", snapshot.devices)
        model.addAttribute("devicesByJkt", snapshot.devicesByJkt)
        model.addAttribute("approvals", snapshot.approvals)
        model.addAttribute("smsChallenges", snapshot.smsChallenges)
        return "store-dashboard"
    }
}
