package dev.sushant.samajh.ai;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** App settings (AI provider, Claude budget) and usage. */
@RestController
@RequestMapping("/api")
class SettingsController {

    private final AiService ai;
    private final JdbcTemplate jdbc;
    private final String claudeModel;
    private final String geminiModel;
    private final List<String> trustedDomains;

    SettingsController(AiService ai, JdbcTemplate jdbc,
                       @Value("${samajh.ai.model}") String claudeModel,
                       @Value("${samajh.ai.gemini.model}") String geminiModel,
                       @Value("${samajh.ai.trusted-domains}") List<String> trustedDomains) {
        this.ai = ai;
        this.jdbc = jdbc;
        this.claudeModel = claudeModel;
        this.geminiModel = geminiModel;
        this.trustedDomains = trustedDomains;
    }

    @GetMapping("/settings")
    Map<String, Object> get() {
        AiService.Settings s = ai.settings();
        return Map.of("provider", s.provider(), "monthlyBudgetUsd", s.monthlyBudgetUsd(),
                "claudeModel", claudeModel, "geminiModel", geminiModel, "trustedDomains", trustedDomains);
    }

    @PutMapping("/settings")
    Map<String, Object> update(@RequestBody AiService.Settings req) {
        if (!"gemini".equals(req.provider()) && !"claude".equals(req.provider())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider must be gemini or claude");
        }
        if (req.monthlyBudgetUsd() < 0 || req.monthlyBudgetUsd() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must be between $0 and $1000");
        }
        jdbc.update("UPDATE settings SET provider = ?, monthly_budget_usd = ? WHERE id = 1", req.provider(), req.monthlyBudgetUsd());
        return get();
    }

    @GetMapping("/usage")
    Map<String, Object> usage() {
        AiService.Settings s = ai.settings();
        return Map.of("provider", s.provider(), "spentUsd", ai.spentThisMonth(), "budgetUsd", s.monthlyBudgetUsd(),
                "geminiRequestsToday", ai.geminiRequestsToday());
    }
}
