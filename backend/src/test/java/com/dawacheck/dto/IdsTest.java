package com.dawacheck.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The prefixed ids exist to stop one kind of id being used where another
 * belongs, so the tests that matter here are the ones that reject.
 */
class IdsTest {

  @Test
  void encodesAndDecodesRoundTrip() {
    assertThat(Ids.encode(Ids.MEMBER, 1L)).isEqualTo("fm1");
    assertThat(Ids.encode(Ids.MEDICINE, 26L)).isEqualTo("m26");
    assertThat(Ids.decode(Ids.MEMBER, "fm1")).isEqualTo(1L);
    assertThat(Ids.decode(Ids.ENTRY, "c417")).isEqualTo(417L);
  }

  @Test
  void encodingNullGivesNull() {
    // A cabinet entry that came from no scan has no scan id to report.
    assertThat(Ids.encode(Ids.SCAN, null)).isNull();
  }

  @Test
  void rejectsAnIdOfTheWrongKind() {
    // The whole point: a medicine id sent where an entry id belongs fails here
    // rather than quietly reading row 12 of the wrong table.
    assertThatThrownBy(() -> Ids.decode(Ids.ENTRY, "m12"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cabinet entry");
  }

  @Test
  void rejectsAPrefixWithNoNumber() {
    assertThatThrownBy(() -> Ids.decode(Ids.MEMBER, "fm"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ids.decode(Ids.MEMBER, "fmx"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsBlankAndNull() {
    assertThatThrownBy(() -> Ids.decode(Ids.MEMBER, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("required");
    assertThatThrownBy(() -> Ids.decode(Ids.MEMBER, "   "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void doesNotConfuseTheScanAndScanItemPrefixes() {
    // "si" and "s" both begin with s, and "scan" begins with s too.
    assertThat(Ids.decode(Ids.SCAN_ITEM, "si7")).isEqualTo(7L);
    assertThat(Ids.decode(Ids.SCAN, "scan7")).isEqualTo(7L);
    assertThat(Ids.decode(Ids.SALT, "s7")).isEqualTo(7L);
    assertThatThrownBy(() -> Ids.decode(Ids.SALT, "si7"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Ids.decode(Ids.SALT, "scan7"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void decodeOrNullAcceptsAnAbsentValue() {
    assertThat(Ids.decodeOrNull(Ids.MEDICINE, null)).isNull();
    assertThat(Ids.decodeOrNull(Ids.MEDICINE, "")).isNull();
    assertThat(Ids.decodeOrNull(Ids.MEDICINE, "m3")).isEqualTo(3L);
  }
}
