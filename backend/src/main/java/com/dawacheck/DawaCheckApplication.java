package com.dawacheck;

import com.dawacheck.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DawaCheck version 1.
 *
 * Reads a family's medicines, maps Indian brand names to their salts, and warns
 * when two medicines share a salt or form a risky combination. The application
 * never tells anyone to start, stop or change a medicine: every alert ends by
 * asking the user to confirm with their doctor or pharmacist.
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class DawaCheckApplication {
  public static void main(String[] args) {
    SpringApplication.run(DawaCheckApplication.class, args);
  }
}
