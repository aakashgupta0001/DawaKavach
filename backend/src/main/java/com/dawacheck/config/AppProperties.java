package com.dawacheck.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Everything configurable about DawaCheck, under the {@code dawacheck.} prefix. */
@ConfigurationProperties(prefix = "dawacheck")
public class AppProperties {

  private String staticDir = "file:../frontend/";
  private List<String> corsOrigins = List.of();
  private boolean seed = true;
  private final Ocr ocr = new Ocr();
  private final Predict predict = new Predict();

  public static class Ocr {
    /** Path to a Tesseract tessdata folder. Empty means OCR is not available. */
    private String tessdata = "";
    private String language = "eng";

    public String getTessdata() { return tessdata; }
    public void setTessdata(String tessdata) { this.tessdata = tessdata; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public boolean isAvailable() { return tessdata != null && !tessdata.isBlank(); }
  }

  public static class Predict {
    /** FastAPI service exposing POST /predict. Empty means use the built-in heuristic. */
    private String url = "";

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public boolean isRemote() { return url != null && !url.isBlank(); }
  }

  public String getStaticDir() { return staticDir; }
  public void setStaticDir(String staticDir) { this.staticDir = staticDir; }
  public List<String> getCorsOrigins() { return corsOrigins; }
  public void setCorsOrigins(List<String> corsOrigins) { this.corsOrigins = corsOrigins; }
  public boolean isSeed() { return seed; }
  public void setSeed(boolean seed) { this.seed = seed; }
  public Ocr getOcr() { return ocr; }
  public Predict getPredict() { return predict; }
}
