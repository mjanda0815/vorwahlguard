package io.janda.vorwahlguard.domain.port.out;

import io.janda.vorwahlguard.domain.model.Rule;
import java.util.List;

/**
 * The in-memory rule snapshot the screening hot path reads from (CLAUDE.md §3 rule 1). The
 * `:app` adapter warms this cache on service bind and refreshes it by observing the DAO; it
 * must never touch disk on the calling thread.
 */
public interface RuleRepository {

    List<Rule> activeRules();
}
