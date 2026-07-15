package io.janda.vorwahlguard.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The shape of one recorded screening result (PROJECT.md §4). Plain data — no behaviour.
 * {@code numberOrHash} is either the E.164 number or {@code sha256(number + local salt)},
 * depending on {@link Settings#pseudonymiseNumbers()}; that choice is made by the {@code :app}
 * adapter, not here (CLAUDE.md §12).
 *
 * <p>{@code matchedRuleId} is nullable: it is only set when {@code reason ==
 * DecisionReason.RULE_MATCH}. A contact bypass or a no-matching-rule allow (issue #59,
 * ADR 0014 — logged only when {@link Settings#logAllowedCalls()} is on) carries a {@code null}
 * {@code matchedRuleId} and the corresponding {@link DecisionReason}.
 *
 * <p>Plain class, not a {@code record} — see {@link Settings}'s class doc for why.
 */
public final class CallEvent {

    private final String id;
    private final Instant occurredAt;
    private final String numberOrHash;
    private final String regionCode;
    private final String matchedRuleId;
    private final RuleAction action;
    private final DecisionReason reason;

    public CallEvent(
            String id,
            Instant occurredAt,
            String numberOrHash,
            String regionCode,
            String matchedRuleId,
            RuleAction action,
            DecisionReason reason) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.numberOrHash = numberOrHash;
        this.regionCode = regionCode;
        this.matchedRuleId = matchedRuleId;
        this.action = action;
        this.reason = reason;
    }

    public String id() {
        return id;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String numberOrHash() {
        return numberOrHash;
    }

    public String regionCode() {
        return regionCode;
    }

    /** Nullable — see class doc. */
    public String matchedRuleId() {
        return matchedRuleId;
    }

    public RuleAction action() {
        return action;
    }

    public DecisionReason reason() {
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallEvent other)) return false;
        return Objects.equals(id, other.id)
                && Objects.equals(occurredAt, other.occurredAt)
                && Objects.equals(numberOrHash, other.numberOrHash)
                && Objects.equals(regionCode, other.regionCode)
                && Objects.equals(matchedRuleId, other.matchedRuleId)
                && action == other.action
                && reason == other.reason;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, occurredAt, numberOrHash, regionCode, matchedRuleId, action, reason);
    }

    @Override
    public String toString() {
        // numberOrHash is redacted (CLAUDE.md §1/§12): it may be a raw E.164 number, and a
        // toString() must never be the path a number leaks into a log or exception message.
        return "CallEvent[id=" + id
                + ", occurredAt=" + occurredAt
                + ", numberOrHash=" + (numberOrHash == null ? "null" : "<redacted>")
                + ", regionCode=" + regionCode
                + ", matchedRuleId=" + matchedRuleId
                + ", action=" + action
                + ", reason=" + reason
                + "]";
    }
}
