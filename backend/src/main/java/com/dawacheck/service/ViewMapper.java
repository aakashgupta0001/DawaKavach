package com.dawacheck.service;

import com.dawacheck.domain.AppUser;
import com.dawacheck.domain.CabinetEntry;
import com.dawacheck.domain.FamilyMember;
import com.dawacheck.domain.GenericProduct;
import com.dawacheck.domain.Medicine;
import com.dawacheck.domain.MedicineSalt;
import com.dawacheck.domain.Reminder;
import com.dawacheck.domain.Salt;
import com.dawacheck.dto.Dto;
import com.dawacheck.dto.Ids;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Turns entities into the JSON the frontend reads.
 *
 * Nothing above the service layer sees an entity. That keeps a lazily-loaded
 * association from being serialised by accident, and it keeps the password hash,
 * the stored image bytes and the owner id off the wire: a view only carries what
 * a page actually renders.
 */
@Service
public class ViewMapper {

  private final KnowledgeBase kb;

  public ViewMapper(KnowledgeBase kb) {
    this.kb = kb;
  }

  public Dto.UserView user(AppUser u) {
    return new Dto.UserView(Ids.encode(Ids.USER, u.id), u.name, u.email, u.role);
  }

  public Dto.SaltView salt(Salt s) {
    return new Dto.SaltView(Ids.encode(Ids.SALT, s.id), s.name, s.atc,
        kb.synonyms(s.id), List.copyOf(RiskPredictionService.classes(s)));
  }

  public Dto.MedicineView medicine(Medicine m) {
    List<Dto.MedSalt> salts = new ArrayList<>();
    for (MedicineSalt ms : m.composition) {
      salts.add(new Dto.MedSalt(Ids.encode(Ids.SALT, ms.salt.id), ms.salt.name, ms.strength,
          kb.synonyms(ms.salt.id)));
    }
    // Prices come from public datasets, may be years out of date, and are
    // labelled approximate everywhere they are shown.
    return new Dto.MedicineView(Ids.encode(Ids.MEDICINE, m.id), m.brand, m.maker, m.form, m.pack,
        m.price, true, salts, null);
  }

  public Dto.MemberView member(FamilyMember m, long medicineCount, Dto.AlertCounts counts) {
    List<String> allergyIds = new ArrayList<>();
    List<String> allergyNames = new ArrayList<>();
    for (Long saltId : SafetyCheckService.allergySaltIds(m)) {
      allergyIds.add(Ids.encode(Ids.SALT, saltId));
      Salt s = kb.salt(saltId);
      allergyNames.add(s == null ? "Unknown salt" : s.name);
    }
    return new Dto.MemberView(Ids.encode(Ids.MEMBER, m.id), m.name, m.age, m.relation,
        m.nickname == null ? "" : m.nickname, allergyIds, allergyNames, medicineCount, counts);
  }

  public Dto.CabinetView entry(CabinetEntry e) {
    return new Dto.CabinetView(
        Ids.encode(Ids.ENTRY, e.id),
        Ids.encode(Ids.MEMBER, e.member.id),
        Ids.encode(Ids.MEDICINE, e.medicine.id),
        e.schedule,
        e.dosesPerDay,
        e.prescribedBy,
        e.startDate == null ? null : e.startDate.toString(),
        e.endDate == null ? null : e.endDate.toString(),
        e.active,
        e.longTerm,
        Ids.encode(Ids.SCAN, e.fromScanId),
        medicine(e.medicine));
  }

  public Dto.ReminderView reminder(Reminder r) {
    return new Dto.ReminderView(
        Ids.encode(Ids.REMINDER, r.id),
        Ids.encode(Ids.ENTRY, r.entry.id),
        r.type,
        r.remindAt,
        r.channel,
        r.enabled,
        entry(r.entry));
  }

  /** Salt ids this member reacts to, encoded for the API. */
  public static String allergyColumn(List<String> apiSaltIds) {
    if (apiSaltIds == null || apiSaltIds.isEmpty()) {
      return "";
    }
    List<String> raw = new ArrayList<>();
    for (String id : apiSaltIds) {
      raw.add(String.valueOf(Ids.decode(Ids.SALT, id)));
    }
    return String.join(",", raw);
  }

  /** Reads a comma separated column into a list, ignoring blanks. */
  public static List<String> csv(String column) {
    if (column == null || column.isBlank()) {
      return List.of();
    }
    return Arrays.stream(column.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  public Dto.GenericView generic(GenericProduct g, String match, double perUnitBrand) {
    List<Dto.SaltStrength> salts = new ArrayList<>();
    for (String part : csv(g.saltKey.replace('|', ','))) {
      String[] bits = part.split("@");
      Salt s = kb.salt(Long.valueOf(bits[0]));
      String strength = bits.length > 1 ? trimStrength(bits[1]) : "";
      salts.add(new Dto.SaltStrength(s == null ? "Unknown salt" : s.name, strength));
    }
    double perUnitGeneric = (g.mrp == null ? 0 : g.mrp) / Strengths.packCount(g.pack);
    int savePct = perUnitBrand <= 0 ? 0
        : (int) Math.round((1 - perUnitGeneric / perUnitBrand) * 100);
    return new Dto.GenericView(
        Ids.encode(Ids.GENERIC, g.id), g.name, salts, g.form, g.pack, g.mrp, match,
        Strengths.round2(perUnitBrand), Strengths.round2(perUnitGeneric),
        Math.max(0, savePct), Strengths.round2((perUnitBrand - perUnitGeneric) * 30));
  }

  /** "650.0" reads back as "650 mg". */
  private static String trimStrength(String value) {
    double n = Double.parseDouble(value);
    String number = n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
    return number + " mg";
  }
}
