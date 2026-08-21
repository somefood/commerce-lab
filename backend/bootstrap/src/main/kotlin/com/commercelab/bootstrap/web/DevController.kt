package com.commercelab.bootstrap.web

import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

@Profile("dev")
@RestController
class DevController {

    @PostMapping("/api/dev/reset")
    fun devReset() {

    }
}