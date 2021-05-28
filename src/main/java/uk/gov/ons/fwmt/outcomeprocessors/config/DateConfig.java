package uk.gov.ons.fwmt.outcomeprocessors.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

@Configuration
public class DateConfig {
  @Bean
  DateFormat dateFormat() {
    return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
  }
}
