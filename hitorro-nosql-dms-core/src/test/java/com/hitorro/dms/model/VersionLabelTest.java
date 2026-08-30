/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.model;

import com.hitorro.dms.model.VersionLabel.Kind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionLabelTest {

    @Test
    void parses_simple_release() {
        VersionLabel v = VersionLabel.parse("1.2.3");
        assertThat(v.major).isEqualTo(1);
        assertThat(v.minor).isEqualTo(2);
        assertThat(v.patch).isEqualTo(3);
        assertThat(v.qualifier).isNull();
        assertThat(v.qualNumber).isNull();
        assertThat(v.build).isNull();
        assertThat(v.isStable()).isTrue();
    }

    @Test
    void parses_qualifier_without_number() {
        VersionLabel v = VersionLabel.parse("2.0.0-alpha");
        assertThat(v.qualifier).isEqualTo("alpha");
        assertThat(v.qualNumber).isNull();
        assertThat(v.isStable()).isFalse();
    }

    @Test
    void parses_qualifier_with_number() {
        VersionLabel v = VersionLabel.parse("2.0.0-alpha3");
        assertThat(v.qualifier).isEqualTo("alpha");
        assertThat(v.qualNumber).isEqualTo(3);
    }

    @Test
    void parses_full_form_with_build() {
        VersionLabel v = VersionLabel.parse("2.1.4-hotfix1+812");
        assertThat(v.major).isEqualTo(2);
        assertThat(v.minor).isEqualTo(1);
        assertThat(v.patch).isEqualTo(4);
        assertThat(v.qualifier).isEqualTo("hotfix");
        assertThat(v.qualNumber).isEqualTo(1);
        assertThat(v.build).isEqualTo(812);
    }

    @Test
    void round_trips_via_label() {
        List<String> labels = List.of(
                "0.0.1", "1.0.0", "2.1.3",
                "2.0.0-alpha", "2.0.0-alpha3",
                "1.2.3-hotfix2+45", "1.0.0+7");
        for (String s : labels) {
            assertThat(VersionLabel.parse(s).label()).isEqualTo(s);
        }
    }

    @Test
    void rejects_malformed_label() {
        assertThatThrownBy(() -> VersionLabel.parse("1.2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionLabel.parse("v1.0.0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionLabel.parse("1.0.0-")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionLabel.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VersionLabel.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bump_major_zeroes_minor_and_patch_and_drops_qualifier() {
        VersionLabel v = VersionLabel.parse("1.5.3-alpha2").bump(Kind.MAJOR);
        assertThat(v.label()).isEqualTo("2.0.0");
    }

    @Test
    void bump_minor_zeroes_patch() {
        VersionLabel v = VersionLabel.parse("2.5.3").bump(Kind.MINOR);
        assertThat(v.label()).isEqualTo("2.6.0");
    }

    @Test
    void bump_patch_increments_only_patch() {
        VersionLabel v = VersionLabel.parse("2.5.3").bump(Kind.PATCH);
        assertThat(v.label()).isEqualTo("2.5.4");
    }

    @Test
    void bump_qualifier_requires_qualifier_present() {
        assertThatThrownBy(() -> VersionLabel.parse("2.0.0").bump(Kind.QUALIFIER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bump_qualifier_starts_at_2_when_previous_had_none() {
        // 2.0.0-alpha (unnumbered) bumped to alpha2
        VersionLabel v = VersionLabel.parse("2.0.0-alpha").bump(Kind.QUALIFIER);
        assertThat(v.label()).isEqualTo("2.0.0-alpha2");
    }

    @Test
    void bump_qualifier_increments_the_number() {
        VersionLabel v = VersionLabel.parse("2.0.0-rc3").bump(Kind.QUALIFIER);
        assertThat(v.label()).isEqualTo("2.0.0-rc4");
    }

    @Test
    void bump_with_new_qualifier_enters_prerelease_cycle() {
        VersionLabel v = VersionLabel.parse("2.5.3").bump(Kind.MINOR, "beta");
        assertThat(v.label()).isEqualTo("2.6.0-beta1");
    }

    @Test
    void initial_is_1_0_0() {
        assertThat(VersionLabel.initial().label()).isEqualTo("1.0.0");
    }

    @Test
    void ordering_stable_beats_prerelease_at_same_numeric() {
        // Semver: 1.0.0 > 1.0.0-anything
        assertThat(VersionLabel.parse("1.0.0"))
                .isGreaterThan(VersionLabel.parse("1.0.0-alpha"))
                .isGreaterThan(VersionLabel.parse("1.0.0-rc99"));
    }

    @Test
    void ordering_numeric_dominates() {
        assertThat(VersionLabel.parse("2.0.0-alpha"))
                .isGreaterThan(VersionLabel.parse("1.99.99"));
        assertThat(VersionLabel.parse("1.1.0"))
                .isGreaterThan(VersionLabel.parse("1.0.99"));
    }

    @Test
    void ordering_within_a_qualifier_is_by_number() {
        assertThat(VersionLabel.parse("2.0.0-alpha3"))
                .isGreaterThan(VersionLabel.parse("2.0.0-alpha2"));
    }

    @Test
    void build_number_breaks_ties() {
        assertThat(VersionLabel.parse("1.0.0+2"))
                .isGreaterThan(VersionLabel.parse("1.0.0+1"));
    }

    @Test
    void equals_and_hashCode_consistent() {
        VersionLabel a = VersionLabel.parse("2.1.3-hotfix1+45");
        VersionLabel b = VersionLabel.parse("2.1.3-hotfix1+45");
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
    }

    @Test
    void negative_parts_rejected() {
        assertThatThrownBy(() -> new VersionLabel(-1, 0, 0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
