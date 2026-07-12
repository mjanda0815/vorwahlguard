package io.janda.vorwahlguard.domain.model;

/** The four grammar tokens a {@link Pattern} can parse to (CLAUDE.md §4). */
public enum PatternKind {
    /** A syntactically valid E.164 number with no wildcard: matches that number only. */
    EXACT,
    /** An E.164 prefix followed by exactly one trailing {@code *}. */
    PREFIX,
    /** The bare {@code *} token: matches every known number, never {@link PhoneNumber#UNKNOWN}. */
    ANY,
    /** The reserved literal {@code PRIVATE}: matches only {@link PhoneNumber#UNKNOWN}. */
    PRIVATE
}
