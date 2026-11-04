package com.dawacheck.service;

import com.dawacheck.dto.Dto;
import com.dawacheck.repo.LlmCacheRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Module 7, the explanation assistant.
 *
 * Each alert is explained twice, once in English and once in Hindi, and both
 * languages travel in the same response so the language toggle in the browser
 * needs no round trip. Every explanation carries three questions the family can
 * ask at the clinic, and ends with the same closing line.
 *
 * The wording is produced from templates rather than from a language model. That
 * is a deliberate choice for the parts of the text that must never drift: a
 * template cannot invent a dose, cannot recommend stopping a medicine, and says
 * the same thing every time the same facts come in. Section 11 allows a model to
 * phrase the body text instead, and {@link #llmReady()} is where that would be
 * switched on; when it is, only the salt names, the alert type and the language
 * may be sent, never a name, an age or a photo (section 14). Either way the
 * result passes through {@link SafetyFilter} before it leaves the server.
 */
@Service
public class ExplanationService {

  private static final Map<String, String> CLOSING = Map.of(
      "en", "Confirm with your doctor or pharmacist.",
      "hi", "अपने डॉक्टर या दवा वाले से पुष्टि करें।");

  private static final Map<String, String> SEVERITY_HI = Map.of(
      "major", "गंभीर", "moderate", "मध्यम", "minor", "हल्का");

  private final SafetyFilter filter;
  private final LlmCacheRepository cache;

  public ExplanationService(SafetyFilter filter, LlmCacheRepository cache) {
    this.filter = filter;
    this.cache = cache;
  }

  /**
   * True when a language model is configured to phrase explanations. It is not,
   * so the {@code llm_cache} table stays empty by design: there are no model
   * calls to cache, and template output is cheaper to render than to look up.
   */
  public boolean llmReady() {
    return false;
  }

  public long cachedCount() {
    return cache.count();
  }

  /** Both languages of one alert, keyed {@code "en"} and {@code "hi"}. */
  public Map<String, Dto.Explanation> explain(String type, Facts f) {
    Map<String, Dto.Explanation> out = new LinkedHashMap<>();
    out.put("en", finish("en", render(type, "en", f)));
    out.put("hi", finish("hi", render(type, "hi", f)));
    return out;
  }

  /* ---------------------------------------------------------------- */

  private record Draft(String title, String body, List<String> questions) {
  }

  private Dto.Explanation finish(String lang, Draft d) {
    SafetyFilter.Verdict v = filter.check(d.body());
    String body = v.safe() ? d.body()
        : "en".equals(lang)
            ? "(This explanation was blocked by the safety filter and is being regenerated.)"
            : "(यह जानकारी सुरक्षा जाँच में रोक दी गई; इसे फिर से तैयार किया जा रहा है।)";
    return new Dto.Explanation(d.title(), body, d.questions(), CLOSING.get(lang),
        !v.safe(), v.phrase());
  }

  private Draft render(String type, String lang, Facts f) {
    boolean hi = "hi".equals(lang);
    return switch (type) {
      case "duplicate" -> hi ? duplicateHi(f) : duplicateEn(f);
      case "total" -> hi ? totalHi(f) : totalEn(f);
      case "interaction" -> hi ? interactionHi(f) : interactionEn(f);
      case "predicted" -> hi ? predictedHi(f) : predictedEn(f);
      case "elderly" -> hi ? elderlyHi(f) : elderlyEn(f);
      case "allergy" -> hi ? allergyHi(f) : allergyEn(f);
      default -> throw new IllegalArgumentException("No explanation template for " + type);
    };
  }

  /* ---------------- 1. the same salt in more than one medicine ---------------- */

  private Draft duplicateEn(Facts f) {
    // The same medicine entered twice is a different conversation from two
    // different boxes that happen to share a salt, so it gets its own wording.
    if (f.distinctBrands == 1) {
      return new Draft(
          "%s is on the list %d times".formatted(f.brand, f.count),
          ("%s contains %s, and it appears %d times on this list. Unless two of them are meant to "
              + "be taken together, the same salt is going into the body more than once.")
              .formatted(f.brand, f.salt, f.count),
          List.of(
              "Is this medicine meant to be taken more than once a day?",
              "Are these two entries the same prescription written twice?",
              "How much %s in a day is safe for me?".formatted(f.salt)));
    }
    return new Draft(
        "%s is in %d medicines".formatted(f.salt, f.count),
        ("%s %s contain %s. Taking them together means the same salt is going into the body "
            + "more than once, which can add up to more than the body can handle in a day, even "
            + "though the boxes look different.")
            .formatted(f.brandsEn, f.count == 2 ? "both" : "all", f.salt),
        List.of(
            f.count == 2 ? "Do I need both of these medicines together?"
                : "Do I need all %d of these medicines together?".formatted(f.count),
            "How much %s in a day is safe for me?".formatted(f.salt),
            "Can one of these be replaced by something without this salt?"));
  }

  private Draft duplicateHi(Facts f) {
    if (f.distinctBrands == 1) {
      return new Draft(
          "%s सूची में %d बार है".formatted(f.brand, f.count),
          ("%s में %s है, और यह इस सूची में %d बार दर्ज है। जब तक दोनों साथ लेने को कहा न गया हो, "
              + "एक ही सॉल्ट शरीर में एक से ज़्यादा बार जा रहा है।")
              .formatted(f.brand, f.salt, f.count),
          List.of(
              "क्या यह दवा दिन में एक से ज़्यादा बार लेनी है?",
              "क्या ये दोनों एक ही पर्चे की दो बार लिखी गई दवा हैं?",
              "एक दिन में %s की कितनी मात्रा मेरे लिए ठीक है?".formatted(f.salt)));
    }
    return new Draft(
        "%s %d दवाओं में है".formatted(f.salt, f.count),
        ("%s — इन %s में %s है। ये साथ लेने से एक ही सॉल्ट शरीर में कई बार जाता है, और दिन भर में "
            + "वह मात्रा ज़्यादा हो सकती है, भले डिब्बे अलग-अलग दिखते हों।")
            .formatted(brandsHi(f), f.count == 2 ? "दोनों" : "सब", f.salt),
        List.of(
            f.count == 2 ? "क्या मुझे ये दोनों दवाएँ साथ लेनी ज़रूरी हैं?"
                : "क्या मुझे ये सभी %d दवाएँ साथ लेनी ज़रूरी हैं?".formatted(f.count),
            "एक दिन में %s की कितनी मात्रा मेरे लिए ठीक है?".formatted(f.salt),
            "क्या इनमें से कोई दवा इस सॉल्ट के बिना वाली दवा से बदली जा सकती है?"));
  }

  /* ---------------- 2. how much of one salt adds up in a day ---------------- */

  private Draft totalEn(Facts f) {
    long total = Math.round(f.total);
    return new Draft(
        "%s: about %d %s a day".formatted(f.salt, total, f.unit),
        ("Adding up every medicine on the list, roughly %d %s of %s is taken in a day, from %s. "
            + "A commonly used upper limit for adults is about %s %s a day, so this total is "
            + "worth showing to the doctor.")
            .formatted(total, f.unit, f.salt, f.brandsEn, trim(f.ceiling), f.unit),
        List.of(
            "Is %d %s of %s a day right for me?".formatted(total, f.unit, f.salt),
            "Which of these should be taken only when needed rather than daily?",
            "Should any test be done to check how my body is handling this?"));
  }

  private Draft totalHi(Facts f) {
    long total = Math.round(f.total);
    return new Draft(
        "%s: दिन में लगभग %d %s".formatted(f.salt, total, f.unit),
        ("सूची की सभी दवाएँ जोड़ने पर दिन में %s की मात्रा लगभग %d %s बनती है — %s से। बड़ों के लिए "
            + "आम तौर पर ऊपरी सीमा करीब %s %s प्रतिदिन मानी जाती है, इसलिए यह जोड़ डॉक्टर को "
            + "दिखाना ठीक रहेगा।")
            .formatted(f.salt, total, f.unit, brandsHi(f), trim(f.ceiling), f.unit),
        List.of(
            "क्या मेरे लिए दिन में %s की %d %s मात्रा ठीक है?".formatted(f.salt, total, f.unit),
            "इनमें से कौन सी दवा रोज़ नहीं, सिर्फ़ ज़रूरत पर लेनी चाहिए?",
            "क्या इसकी जाँच के लिए कोई टेस्ट कराना चाहिए?"));
  }

  /* ---------------- 3. a pair recorded in the interaction dataset ---------------- */

  private Draft interactionEn(Facts f) {
    return new Draft(
        "%s with %s".formatted(f.saltA, f.saltB),
        ("%s contains %s and %s contains %s. %s This pair is graded %s in the interaction "
            + "dataset.").formatted(f.brandA, f.saltA, f.brandB, f.saltB, f.mechanismEn,
            f.severity),
        List.of(
            "Is it safe for me to take these two together?",
            "Should they be taken at different times of the day?",
            "Is there a sign or symptom I should watch for?"));
  }

  private Draft interactionHi(Facts f) {
    String mech = f.mechanismHi == null || f.mechanismHi.isBlank() ? f.mechanismEn : f.mechanismHi;
    String grade = f.severityHi == null || f.severityHi.isBlank()
        ? SEVERITY_HI.getOrDefault(f.severity, f.severity) : f.severityHi;
    return new Draft(
        "%s और %s साथ में".formatted(f.saltA, f.saltB),
        "%s में %s है और %s में %s। %s डेटासेट में इस जोड़ी का दर्जा %s है।"
            .formatted(f.brandA, f.saltA, f.brandB, f.saltB, mech, grade),
        List.of(
            "क्या ये दोनों दवाएँ मेरे लिए साथ लेना ठीक है?",
            "क्या इन्हें दिन के अलग-अलग समय पर लेना चाहिए?",
            "कोई लक्षण जिस पर मुझे ध्यान रखना चाहिए?"));
  }

  /* ---------------- 4. a pair the model flagged but the dataset does not list ---- */

  private Draft predictedEn(Facts f) {
    return new Draft(
        "Possible risk: %s with %s (AI prediction)".formatted(f.saltA, f.saltB),
        ("This pair is not listed in the interaction dataset, so there is no recorded fact either "
            + "way. The model scored it %d%% likely to interact because both salts work in "
            + "similar ways. A missing entry is not proof of safety, and a prediction is not "
            + "proof of harm.").formatted(f.pct),
        List.of(
            "Are these two known to cause any problem together?",
            "Was this combination meant to be taken at the same time?",
            "Should one of them be reviewed at the next visit?"));
  }

  private Draft predictedHi(Facts f) {
    return new Draft(
        "संभावित जोखिम: %s और %s (AI अनुमान)".formatted(f.saltA, f.saltB),
        ("यह जोड़ी डेटासेट में दर्ज नहीं है, इसलिए इसके बारे में कोई पक्की जानकारी नहीं है। मॉडल ने "
            + "इसे %d%% संभावना दी है, क्योंकि दोनों सॉल्ट मिलते-जुलते तरीके से काम करते हैं। डेटा "
            + "में न होना सुरक्षित होने का सबूत नहीं है, और अनुमान नुकसान का सबूत नहीं है।")
            .formatted(f.pct),
        List.of(
            "क्या इन दोनों से साथ में कोई दिक्कत होती है?",
            "क्या इन्हें एक ही समय पर लेना तय किया गया था?",
            "अगली बार दिखाने पर इनमें से किसी पर फिर से विचार करना चाहिए?"));
  }

  /* ---------------- 5. the STOPP-style checks for older adults ---------------- */

  private Draft elderlyEn(Facts f) {
    return new Draft(f.titleEn,
        f.bodyEn + " This is a STOPP version 3 style check that applies to people aged 65 and "
            + "above, and it is a prompt for review, not a verdict about this medicine.",
        f.questionsEn);
  }

  private Draft elderlyHi(Facts f) {
    return new Draft(f.titleHi,
        f.bodyHi + " यह 65 वर्ष और उससे ऊपर के लोगों के लिए STOPP वर्जन 3 जैसी जाँच है — यह दवा पर "
            + "फिर से विचार करने का संकेत है, कोई फ़ैसला नहीं।",
        f.questionsHi);
  }

  /* ---------------- 6. a salt the member is recorded as reacting to ---------- */

  private Draft allergyEn(Facts f) {
    return new Draft(
        "Recorded allergy: %s".formatted(f.salt),
        ("%s contains %s, which is saved in this member's allergy list. The medicine is still "
            + "shown here so nothing is hidden, but the pharmacist should know about the allergy "
            + "before it is taken.").formatted(f.brand, f.salt),
        List.of(
            "Does my record show an allergy to %s?".formatted(f.salt),
            "Is there an alternative without this salt?",
            "What reaction should we expect if it was taken by mistake?"));
  }

  private Draft allergyHi(Facts f) {
    return new Draft(
        "दर्ज एलर्जी: %s".formatted(f.salt),
        ("%s में %s है, जो इस सदस्य की एलर्जी सूची में दर्ज है। दवा यहाँ छिपाई नहीं गई है, लेकिन "
            + "लेने से पहले दवा वाले को एलर्जी के बारे में बताना चाहिए।").formatted(f.brand, f.salt),
        List.of(
            "क्या मेरे रिकॉर्ड में %s से एलर्जी लिखी है?".formatted(f.salt),
            "इस सॉल्ट के बिना कोई दूसरी दवा है?",
            "गलती से ले लिया तो क्या प्रतिक्रिया हो सकती है?"));
  }

  /* ---------------------------------------------------------------- */

  private static String brandsHi(Facts f) {
    return f.brandsHi == null || f.brandsHi.isBlank() ? f.brandsEn : f.brandsHi;
  }

  /** 3000.0 reads as 3000, 0.25 stays 0.25. */
  private static String trim(double n) {
    return n == Math.floor(n) ? String.valueOf((long) n) : String.valueOf(n);
  }

  /** "A, B and C" in English, "A, B और C" in Hindi. */
  public static String listToText(List<String> items, String lang) {
    if (items.isEmpty()) {
      return "";
    }
    if (items.size() == 1) {
      return items.get(0);
    }
    String separator = "hi".equals(lang) ? " और " : " and ";
    return String.join(", ", items.subList(0, items.size() - 1))
        + separator + items.get(items.size() - 1);
  }

  public static String severityHi(String severity) {
    return SEVERITY_HI.getOrDefault(severity, severity);
  }

  public static String closing(String lang) {
    return CLOSING.getOrDefault(lang, CLOSING.get("en"));
  }
}
