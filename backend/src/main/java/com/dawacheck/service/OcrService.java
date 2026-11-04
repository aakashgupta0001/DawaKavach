package com.dawacheck.service;

import com.dawacheck.config.AppProperties;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Module 3, the reading half: Tesseract through Tess4J, as section 11 specifies.
 *
 * A photo of a pharmacy bill taken on a phone is not a scan. It is dim, angled
 * and compressed, so the image is prepared before Tesseract sees it: upscaled if
 * it is small, flattened to greyscale, and thresholded to black and white.
 * Contrast is what Tesseract is sensitive to, far more than resolution.
 *
 * Tesseract has to be installed separately with its language data, which is why
 * {@link #available()} exists and why the scanner offers the sample bills when
 * it returns false. The alternative would be to let the upload fail with
 * something unreadable, or worse, to show invented text as though it had been
 * read off the photo.
 */
@Service
public class OcrService {

  private static final Logger log = LoggerFactory.getLogger(OcrService.class);

  /** Tesseract reports 0 to 100 per line; the rest of the app works in 0 to 1. */
  private static final double CONFIDENCE_SCALE = 100.0;

  /** Below this width, characters are too few pixels tall for Tesseract. */
  private static final int MIN_WIDTH = 1000;

  private final AppProperties props;

  public OcrService(AppProperties props) {
    this.props = props;
  }

  public record Line(String text, double confidence) {
  }

  public record Result(String rawText, List<Line> lines, String engine) {
  }

  public boolean available() {
    return props.getOcr().isAvailable();
  }

  public String engineName() {
    return available() ? "Tesseract (" + props.getOcr().getLanguage() + ")" : "not configured";
  }

  /**
   * Reads one photo.
   *
   * @throws BadRequestException when the bytes are not a readable image, or when
   *     Tesseract is not configured on this machine
   */
  public Result read(byte[] bytes) {
    if (!available()) {
      throw new BadRequestException(
          "Reading photos is not switched on for this installation. Set dawacheck.ocr.tessdata "
              + "to a Tesseract language folder, or use one of the sample bills.");
    }
    BufferedImage image = decode(bytes);
    BufferedImage prepared = prepare(image);

    Tesseract tesseract = new Tesseract();
    tesseract.setDatapath(props.getOcr().getTessdata());
    tesseract.setLanguage(props.getOcr().getLanguage());

    try {
      List<Word> words = tesseract.getWords(prepared, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
      List<Line> lines = new ArrayList<>();
      StringBuilder raw = new StringBuilder();
      for (Word w : words) {
        String text = w.getText() == null ? "" : w.getText().strip();
        if (text.isEmpty()) {
          continue;
        }
        raw.append(text).append('\n');
        lines.add(new Line(text, clamp(w.getConfidence() / CONFIDENCE_SCALE)));
      }
      if (lines.isEmpty()) {
        throw new BadRequestException(
            "No text could be read from that photo. Try again in better light, with the bill "
                + "flat and the whole of it in the frame.");
      }
      return new Result(raw.toString().strip(), lines, engineName());
    } catch (BadRequestException e) {
      throw e;
    } catch (RuntimeException | Error e) {
      // A missing native library surfaces as UnsatisfiedLinkError, which is an
      // Error rather than an Exception, so it is caught here too: the cause is a
      // broken installation, not a bad photo, and saying so saves a lot of time.
      log.error("Tesseract failed while reading an upload", e);
      throw new BadRequestException(
          "The OCR engine could not run. Check that Tesseract and its language data are "
              + "installed, then try again.");
    }
  }

  private static BufferedImage decode(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new BadRequestException("That file is not a JPEG, PNG or WebP image.");
      }
      return image;
    } catch (IOException e) {
      throw new BadRequestException("That photo could not be opened. Please choose it again.");
    }
  }

  /** Upscale, flatten to grey, then threshold. */
  private static BufferedImage prepare(BufferedImage source) {
    BufferedImage scaled = source;
    if (source.getWidth() < MIN_WIDTH) {
      double factor = (double) MIN_WIDTH / source.getWidth();
      int width = MIN_WIDTH;
      int height = (int) Math.round(source.getHeight() * factor);
      scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = scaled.createGraphics();
      g.drawImage(source, 0, 0, width, height, null);
      g.dispose();
    }

    BufferedImage grey = new BufferedImage(scaled.getWidth(), scaled.getHeight(),
        BufferedImage.TYPE_BYTE_GRAY);
    Graphics2D g = grey.createGraphics();
    g.drawImage(scaled, 0, 0, null);
    g.dispose();

    // A fixed threshold is enough for a printed bill under reasonable light. A
    // phone photo of a crumpled bill would want an adaptive threshold per block,
    // which is what the image-cleaning step in section 10 describes.
    BufferedImage bw = new BufferedImage(grey.getWidth(), grey.getHeight(),
        BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < grey.getHeight(); y++) {
      for (int x = 0; x < grey.getWidth(); x++) {
        int value = grey.getRaster().getSample(x, y, 0);
        bw.setRGB(x, y, value < 140 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
      }
    }
    return bw;
  }

  private static double clamp(double value) {
    return Math.max(0, Math.min(1, Math.round(value * 100.0) / 100.0));
  }
}
