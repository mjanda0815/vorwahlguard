# ProGuard/R8 rules for :app.
# Hilt and Room ship their own consumer rules; libphonenumber does not — it loads its metadata
# (region and number-format tables; the geocoder/carrier artifacts CLAUDE.md §2 forbids aren't
# on the classpath) at runtime by resource name, which R8's static analysis cannot see, so a
# missing keep rule here shows up as an empty country picker or a normalization crash — only in
# the release build, only on a real device (docs/RELEASE.md §4). This is a starting point, not a
# verified fix: it has not yet been confirmed against an actual release build tested on-device
# (see the manual test in docs/RELEASE.md §6).
-keep class com.google.i18n.phonenumbers.** { *; }
