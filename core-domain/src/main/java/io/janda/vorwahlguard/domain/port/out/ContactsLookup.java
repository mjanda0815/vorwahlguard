package io.janda.vorwahlguard.domain.port.out;

import io.janda.vorwahlguard.domain.model.PhoneNumber;

/**
 * Yes/no contact membership for one number. The adapter (`:app`, {@code ContactsContract})
 * never hands a raw contact list into the domain — only this single boolean answer
 * (PROJECT.md §4). Must fail closed ({@code false}) if {@code READ_CONTACTS} was revoked or
 * never granted (ADR 0006).
 */
public interface ContactsLookup {

    boolean isKnownContact(PhoneNumber number);
}
