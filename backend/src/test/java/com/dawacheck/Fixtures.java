package com.dawacheck;

import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.Salt;
import com.dawacheck.service.KnowledgeBase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Builds a family and a medicine list in memory.
 *
 * {@code SafetyCheckService.check} takes a member and a list of entries and
 * returns alerts without touching the database, so the engine can be tested as
 * the function it is: one medicine list in, one set of findings out. That makes
 * each case readable — the test says which boxes are on the shelf and which
 * warnings should come back — and it keeps the engine tests independent of the
 * seeded demo family, which exists to demonstrate one particular case rather
 * than every case.
 *
 * The medicines themselves are the real seeded rows, looked up by the brand name
 * printed on the box, because a test that invented its own compositions would
 * stop telling you anything about the catalogue.
 */
public final class Fixtures {

  private final KnowledgeBase kb;
  private long nextEntryId = 1;

  public Fixtures(KnowledgeBase kb) {
    this.kb = kb;
  }

  /** A member with no allergies recorded. */
  public FamilyMember member(String name, int age) {
    return member(name, age, List.of());
  }

  /** A member who reacts to the named salts. */
  public FamilyMember member(String name, int age, List<String> allergySalts) {
    FamilyMember m = new FamilyMember();
    m.id = 900L + name.hashCode() % 90;
    m.name = name;
    m.age = age;
    m.relation = "Family member";
    m.nickname = "";
    m.allergySaltIds = allergySalts.stream()
        .map(this::salt)
        .map(s -> String.valueOf(s.id))
        .collect(Collectors.joining(","));
    return m;
  }

  /** One box on the shelf, taken the given number of times a day. */
  public CabinetEntry taking(FamilyMember member, String brand, int dosesPerDay) {
    return taking(member, brand, dosesPerDay, false);
  }

  /** As above, with {@code longTerm} set, which the STOPP-style rules look at. */
  public CabinetEntry taking(FamilyMember member, String brand, int dosesPerDay,
      boolean longTerm) {
    CabinetEntry e = new CabinetEntry();
    e.id = nextEntryId++;
    e.member = member;
    e.medicine = medicine(brand);
    e.schedule = "As advised";
    e.dosesPerDay = dosesPerDay;
    e.prescribedBy = "Dr. Test";
    e.startDate = LocalDate.of(2026, 1, 1);
    e.active = true;
    e.longTerm = longTerm;
    return e;
  }

  /** A box that is on the list but marked stopped. */
  public CabinetEntry stopped(FamilyMember member, String brand, int dosesPerDay) {
    CabinetEntry e = taking(member, brand, dosesPerDay);
    e.active = false;
    return e;
  }

  public Medicine medicine(String brand) {
    for (Medicine m : kb.allMedicines()) {
      if (m.brand.equalsIgnoreCase(brand)) {
        return m;
      }
    }
    throw new AssertionError("No medicine named \"" + brand + "\" in the seeded catalogue");
  }

  public Salt salt(String name) {
    for (Salt s : kb.allSalts()) {
      if (s.name.equalsIgnoreCase(name)) {
        return s;
      }
    }
    throw new AssertionError("No salt named \"" + name + "\" in the seeded catalogue");
  }

  public static List<CabinetEntry> list(CabinetEntry... entries) {
    return new ArrayList<>(List.of(entries));
  }
}
