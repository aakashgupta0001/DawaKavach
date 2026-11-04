package com.dawacheck.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves the plain HTML/CSS/JS frontend from the sibling {@code frontend/}
 * folder during development, so the pages and the API share an origin and the
 * frontend needs no CORS and no API_BASE. In a packaged build the same files
 * sit in {@code src/main/resources/static} and the classpath entry below picks
 * them up instead.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

  private final AppProperties props;

  public WebConfig(AppProperties props) {
    this.props = props;
  }

  /**
   * Serving the frontend from a {@code /**} resource handler replaces Spring's
   * own welcome-page handling, so the landing page has to be pointed at by hand.
   */
  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/").setViewName("forward:/index.html");
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    List<String> locations = new ArrayList<>();
    if (props.getStaticDir() != null && !props.getStaticDir().isBlank()) {
      locations.add(props.getStaticDir());
    }
    locations.add("classpath:/static/");
    registry.addResourceHandler("/**")
        .addResourceLocations(locations.toArray(String[]::new));
  }

  @Bean
  RestClient restClient() {
    return RestClient.create();
  }
}
