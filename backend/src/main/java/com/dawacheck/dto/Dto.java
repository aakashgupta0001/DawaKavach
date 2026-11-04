package com.dawacheck.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Everything the API accepts and returns.
 *
 * These shapes are a contract, not a convenience: the frontend in
 * {@code frontend/js} was built against them while the backend was still a
 * browser mock, and each page reads these exact field names. Renaming a field
 * here breaks a page, so the records are grouped in one file where they can be
 * read side by side against {@code frontend/js/api.js}.
 */
public final class Dto {

  private Dto() {
  }

  /* ========================= requests ========================= */

  public record RegisterRequest(
      @NotBlank(message = "Name is required.") String name,
      @NotBlank(message = "Email is required.")
      @Email(message = "That email address does not look right.") String email,
      @NotBlank(message = "Password is required.")
      @Size(min = 8, message = "Password must be at least 8 characters.") String password,
      boolean consent) {
  }

  public record LoginRequest(
      @NotBlank(message = "Enter your email.") String email,
      @NotBlank(message = "Enter your password.") String password) {
  }

  public record MemberRequest(
      @NotBlank(message = "Name is required.") String name,
      @NotNull(message = "Age is required.")
      @Min(value = 0, message = "Enter an age between 0 and 120.")
      @Max(value = 120, message = "Enter an age between 0 and 120.") Integer age,
      String relation,
      String nickname,
      List<String> allergies) {
  }

  /** A PUT may send any subset, so nothing here is required. */
  public record MemberPatch(
      String name, Integer age, String relation, String nickname, List<String> allergies) {
  }

  public record CabinetRequest(
      String medicineId, String schedule, Integer dosesPerDay, String prescribedBy,
      String startDate, String endDate, Boolean longTerm, Boolean active) {
  }

  public record ConfirmItem(
      String itemId, String medicineId, String schedule, Integer dosesPerDay,
      String prescribedBy, boolean skip) {
  }

  public record ConfirmRequest(List<ConfirmItem> items, boolean keepImage) {
  }

  public record ReminderRequest(
      String id, String entryId, String type, String remindAt, String channel, Boolean enabled) {
  }

  public record PredictRequest(String saltA, String saltB) {
  }

  public record MappingResolution(String medicineId) {
  }

  /* ========================= knowledge base ========================= */

  public record UserView(String id, String name, String email, String role) {
  }

  public record AuthResponse(UserView user) {
  }

  public record SaltView(
      String id, String name, String atc, List<String> synonyms, List<String> classes) {
  }

  /** One salt inside a medicine, with the strength printed on that box. */
  public record MedSalt(String id, String name, String strength, List<String> synonyms) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MedicineView(
      String id, String brand, String maker, String form, String pack, Double price,
      boolean priceApproximate, List<MedSalt> salts, Double confidence) {

    /** The same medicine with a match score attached, as search results need. */
    public MedicineView withConfidence(double score) {
      return new MedicineView(id, brand, maker, form, pack, price, priceApproximate, salts,
          Math.round(score * 100) / 100.0);
    }
  }

  /* ========================= family ========================= */

  public record AlertCounts(long red, long yellow, long green, long total) {
  }

  public record MemberView(
      String id, String name, int age, String relation, String nickname,
      List<String> allergies, List<String> allergyNames,
      long medicineCount, AlertCounts alertCounts) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CabinetView(
      String id, String memberId, String medicineId, String schedule, int dosesPerDay,
      String prescribedBy, String startDate, String endDate, boolean active, boolean longTerm,
      String fromScanId, MedicineView medicine) {
  }

  /* ========================= alerts ========================= */

  /** One language of one alert. {@code closing} is never empty (section 14). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Explanation(
      String title, String body, List<String> questions, String closing,
      boolean blocked, String blockedPhrase) {
  }

  public record DailyTotal(double value, String unit, double ceiling) {
  }

  /**
   * The fields after {@code createdAt} are set only for the alert types that
   * have them, which is why this record is serialised without nulls: a
   * dataset-backed alert carries a mechanism and no probability, an AI alert
   * carries a probability and no mechanism, and the frontend tells them apart
   * by which fields are present.
   */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AlertView(
      String id, String type, String severity, String colour, String source, String memberId,
      List<String> salts, List<String> saltNames, List<String> entryIds, List<String> medicines,
      Map<String, Explanation> explanation, String createdAt,
      String datasetName, String mechanism, String model, Double probability, Double threshold,
      DailyTotal dailyTotal, String ruleSet, String ruleKey) {
  }

  /* ========================= scanner ========================= */

  public record ScanItemView(
      String id, String detectedText, String cleanedText, double ocrConfidence,
      boolean lowConfidence, List<MedicineView> suggestions, boolean confirmed,
      String matchedMedicineId) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ScanView(
      String id, String memberId, String status, String createdAt, String imageName,
      String rawText, String fixtureLabel, String ocrEngine, boolean ocrAvailable,
      boolean imageRetained, List<ScanItemView> items) {
  }

  public record ConfirmResult(List<CabinetView> added, List<AlertView> alerts) {
  }

  public record OcrFixtureRow(int index, String label, int lineCount) {
  }

  /* ========================= generics and comparison ========================= */

  public record SaltStrength(String name, String strength) {
  }

  public record GenericView(
      String id, String name, List<SaltStrength> saltNames, String form, String pack, Double mrp,
      String match, double perUnitBrand, double perUnitGeneric, int savePct,
      double monthlySaving) {
  }

  public record CompareSalt(String salt, String saltId, double strength, String unit) {
  }

  /**
   * The comparison panel prints the two boxes side by side, so it needs the raw
   * composition pairs as well as the resolved salt names.
   */
  public record CompareMed(
      String id, String brand, String maker, String form, String pack, Double price,
      List<List<String>> composition, List<MedSalt> salts) {
  }

  public record CompareResult(
      CompareMed a, CompareMed b, List<CompareSalt> saltsA, List<CompareSalt> saltsB,
      boolean sameSalts, boolean sameStrength, boolean sameForm, String verdict) {
  }

  /* ========================= reminders and summary ========================= */

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record ReminderView(
      String id, String entryId, String type, String remindAt, String channel, boolean enabled,
      CabinetView entry) {
  }

  public record SummaryView(
      MemberView member, List<CabinetView> entries, List<AlertView> alerts,
      String generatedAt, String disclaimer) {
  }

  /* ========================= model ========================= */

  public record PredictResult(double probability, String severity) {
  }

  public record ModelCard(
      String serving, double threshold, String features, String target, boolean trainedModel) {
  }

  /* ========================= admin ========================= */

  public record DatasetRow(String id, String name, long rows, String lastImport, String target) {
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record MappingRow(
      String id, String detected, String guess, long count, String note,
      MedicineView guessMedicine) {
  }

  public record Counts(long medicines, long salts, long synonyms, long interactions,
      long generics) {
  }

  public record AdminOverview(
      List<DatasetRow> datasets, Counts counts, List<MappingRow> mappingQueue, ModelCard model) {
  }

  public record ImportResult(
      String target, String fileName, long rowsRead, long rowsImported, long rowsSkipped,
      List<String> warnings, String finishedAt) {
  }

  /* ========================= errors ========================= */

  /** What a non-2xx response carries; api.js reads {@code message}. */
  public record ApiError(String message, int status) {
  }
}
