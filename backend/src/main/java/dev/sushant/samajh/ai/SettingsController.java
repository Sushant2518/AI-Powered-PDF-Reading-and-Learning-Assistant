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

/** App settings (monthly budget) and this month's spend. */
@RestController
@RequestMapping("/api")
class SettingsController {

    private final AiService ai;
    private final JdbcTemplate jdbc;
    private final String claudeModel;
    private final List<String> trustedDomains;

    SettingsController(AiService ai, JdbcTemplate jdbc,
                       @Value("${samajh.ai.model}") String claudeModel,
                       @Value("${samajh.ai.trusted-domains}") List<String> trustedDomains) {
        this.ai = ai;
        this.jdbc = jdbc;
        this.claudeModel = claudeModel;
        this.trustedDomains = trustedDomains;
    }

    @GetMapping("/settings")
    Map<String, Object> get() {
        return Map.of("monthlyBudgetUsd", ai.monthlyBudget(), "claudeModel", claudeModel, "trustedDomains", trustedDomains);
    }

    @PutMapping("/settings")
    Map<String, Object> update(@RequestBody AiService.Settings req) {
        if (req.monthlyBudgetUsd() < 0 || req.monthlyBudgetUsd() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Budget must be between $0 and $1000");
        }
        jdbc.update("UPDATE settings SET monthly_budget_usd = ? WHERE id = 1", req.monthlyBudgetUsd());
        return get();
    }

    @GetMapping("/usage")
    Map<String, Double> usage() {
        return Map.of("spentUsd", ai.spentThisMonth(), "budgetUsd", ai.monthlyBudget());
    }
}
