/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed representation of a document version label.
 *
 * <h3>Grammar</h3>
 * <pre>
 *   label ::= MAJOR "." MINOR "." PATCH ( "-" QUALIFIER QUAL_NUMBER? )? ( "+" BUILD )?
 * </pre>
 *
 * Examples:
 * <ul>
 *   <li>{@code 1.0.0}</li>
 *   <li>{@code 2.1.3}</li>
 *   <li>{@code 3.0.0-alpha}</li>
 *   <li>{@code 3.0.0-alpha3}</li>
 *   <li>{@code 2.1.4-hotfix1+build812}</li>
 * </ul>
 *
 * <p>The DMS uses the parsed components as separate Lucene fields so
 * queries like {@code WHERE version_major = 2 AND version_qualifier IS NULL}
 * work at index time — see the design doc.</p>
 *
 * <p>This class is intentionally immutable + comparable. Use
 * {@link #bump} to derive the next label for a check-in.</p>
 */
public final class VersionLabel implements Comparable<VersionLabel> {

    // 1.2.3
    // 1.2.3-alpha
    // 1.2.3-alpha3
    // 1.2.3-alpha3+45
    private static final Pattern PATTERN = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z]+)(\\d+)?)?(?:\\+(\\d+))?$");

    /** Semantic bump kinds for {@link #bump}. */
    public enum Kind { MAJOR, MINOR, PATCH, QUALIFIER }

    public final long major;
    public final long minor;
    public final long patch;
    /** null when stable (no pre-release). */
    public final String qualifier;
    /** null when qualifier absent or unnumbered. */
    public final Long qualNumber;
    /** null when no build suffix. */
    public final Long build;

    public VersionLabel(long major, long minor, long patch,
                        String qualifier, Long qualNumber, Long build) {
        if (major < 0 || minor < 0 || patch < 0)
            throw new IllegalArgumentException("version parts must be non-negative");
        if (qualNumber != null && qualifier == null)
            throw new IllegalArgumentException("qualNumber requires a qualifier");
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.qualifier = qualifier;
        this.qualNumber = qualNumber;
        this.build = build;
    }

    /** {@code 1.0.0} — the label for a brand-new document. */
    public static VersionLabel initial() {
        return new VersionLabel(1, 0, 0, null, null, null);
    }

    /** Parse a canonical label string. Throws on malformed input. */
    public static VersionLabel parse(String label) {
        if (label == null) throw new IllegalArgumentException("null label");
        Matcher m = PATTERN.matcher(label.trim());
        if (!m.matches())
            throw new IllegalArgumentException("bad version label: " + label);
        return new VersionLabel(
                Long.parseLong(m.group(1)),
                Long.parseLong(m.group(2)),
                Long.parseLong(m.group(3)),
                m.group(4),
                m.group(5) == null ? null : Long.parseLong(m.group(5)),
                m.group(6) == null ? null : Long.parseLong(m.group(6))
        );
    }

    /** Canonical rendering. Round-trips with {@link #parse}. */
    public String label() {
        StringBuilder b = new StringBuilder();
        b.append(major).append('.').append(minor).append('.').append(patch);
        if (qualifier != null) {
            b.append('-').append(qualifier);
            if (qualNumber != null) b.append(qualNumber);
        }
        if (build != null) b.append('+').append(build);
        return b.toString();
    }

    /** Alias for {@link #label} — String.valueOf-friendly. */
    @Override public String toString() { return label(); }

    /**
     * Compute the next label for a check-in. Rules:
     * <ul>
     *   <li>{@link Kind#MAJOR}    → major++, minor=0, patch=0, drops qualifier</li>
     *   <li>{@link Kind#MINOR}    → minor++, patch=0, drops qualifier</li>
     *   <li>{@link Kind#PATCH}    → patch++, drops qualifier</li>
     *   <li>{@link Kind#QUALIFIER}→ same numeric, qualNumber++ within the qualifier
     *       (requires {@code qualifier} to be set)</li>
     * </ul>
     * The {@code build} suffix is never carried across — every bump is a
     * distinct build; callers stamp {@code build} independently.
     */
    public VersionLabel bump(Kind kind) {
        // Single-arg bump goes to STABLE by default — matches semver's "1.0.0-alpha → 1.0.0"
        // release convention. To stay in a pre-release cycle across a major/minor/patch
        // bump, use bump(kind, "alpha") explicitly.
        return bump(kind, kind == Kind.QUALIFIER ? this.qualifier : null);
    }

    /**
     * Same as {@link #bump(Kind)} but allows changing the qualifier as
     * part of the bump. Useful for entering / leaving the pre-release
     * lifecycle: {@code bump(MINOR, "beta")} promotes to the next
     * minor and starts a beta cycle.
     */
    public VersionLabel bump(Kind kind, String newQualifier) {
        switch (kind) {
            case MAJOR:
                return new VersionLabel(major + 1, 0, 0, newQualifier,
                        newQualifier == null ? null : 1L, null);
            case MINOR:
                return new VersionLabel(major, minor + 1, 0, newQualifier,
                        newQualifier == null ? null : 1L, null);
            case PATCH:
                return new VersionLabel(major, minor, patch + 1, newQualifier,
                        newQualifier == null ? null : 1L, null);
            case QUALIFIER:
                if (qualifier == null)
                    throw new IllegalStateException("cannot bump qualifier on a stable version");
                long next = (qualNumber == null ? 1L : qualNumber) + 1;
                return new VersionLabel(major, minor, patch, qualifier, next, null);
            default:
                throw new IllegalArgumentException("unknown kind: " + kind);
        }
    }

    /**
     * Ordering. Stable releases sort above any qualifier at the same
     * numeric level (semver convention). Qualifier ordering within a
     * (major.minor.patch) is alphabetical, then by qualNumber.
     * Build number breaks ties.
     */
    @Override
    public int compareTo(VersionLabel o) {
        int c = Long.compare(major, o.major);
        if (c != 0) return c;
        c = Long.compare(minor, o.minor);
        if (c != 0) return c;
        c = Long.compare(patch, o.patch);
        if (c != 0) return c;
        // Stable > pre-release at the same numeric level.
        if (qualifier == null && o.qualifier != null) return 1;
        if (qualifier != null && o.qualifier == null) return -1;
        if (qualifier != null) {
            c = qualifier.compareTo(o.qualifier);
            if (c != 0) return c;
            long a = qualNumber == null ? 0 : qualNumber;
            long b = o.qualNumber == null ? 0 : o.qualNumber;
            c = Long.compare(a, b);
            if (c != 0) return c;
        }
        long ab = build == null ? 0 : build;
        long bb = o.build == null ? 0 : o.build;
        return Long.compare(ab, bb);
    }

    /** True when no pre-release qualifier is present. */
    public boolean isStable() { return qualifier == null; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionLabel v)) return false;
        return major == v.major && minor == v.minor && patch == v.patch
                && Objects.equals(qualifier, v.qualifier)
                && Objects.equals(qualNumber, v.qualNumber)
                && Objects.equals(build, v.build);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, qualifier, qualNumber, build);
    }
}
