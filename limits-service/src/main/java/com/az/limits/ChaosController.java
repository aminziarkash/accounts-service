package com.az.limits;

import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A demo tool to make the service slow or broken. Exists only when app.chaos.enabled is true. */
@RestController
@RequestMapping("/admin/chaos")
@ConditionalOnProperty(name = "app.chaos.enabled", havingValue = "true")
public class ChaosController {

    private final ChaosState state;

    public ChaosController(ChaosState state) {
        this.state = state;
    }

    @GetMapping
    public ChaosSettings current() {
        return new ChaosSettings(state.delay().toMillis(), state.failing());
    }

    @PutMapping
    public ChaosSettings set(@Valid @RequestBody ChaosSettings settings) {
        state.update(Duration.ofMillis(settings.delayMillis()), settings.failing());
        return current();
    }

    @DeleteMapping
    public ResponseEntity<Void> reset() {
        state.reset();
        return ResponseEntity.noContent().build();
    }
}