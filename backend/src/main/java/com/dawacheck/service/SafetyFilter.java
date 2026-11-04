package com.dawacheck.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * The one rule the whole application is built around: DawaCheck never tells
 * anyone to start, stop or change a medicine.
 *
 * Every explanation passes through here before it leaves the server, whether it
 * came from a template or from a language model. A blocked explanation is
 * replaced rather than cleaned up, because a sentence that trips this filter has
 * already failed and guessing at a safe rewrite would be worse than saying
 * nothing.
 */
@Service
public class SafetyFilter {

  private static final List<String> BANNED_PHRASES = List.of(
      "stop taking", "stop the medicine", "do not take", "discontinue",
      "reduce the dose", "increase the dose", "double the dose",
      "बंद कर दें", "बंद कर दीजिए", "खुराक बढ़ा", "खुराक घटा");

  public record Verdict(boolean safe, String phrase) {
  }

  public Verdict check(String text) {
    if (text == null) {
      return new Verdict(true, null);
    }
    String low = text.toLowerCase(Locale.ROOT);
    for (String phrase : BANNED_PHRASES) {
      if (low.contains(phrase.toLowerCase(Locale.ROOT))) {
        return new Verdict(false, phrase);
      }
    }
    return new Verdict(true, null);
  }

  public List<String> bannedPhrases() {
    return BANNED_PHRASES;
  }
}
