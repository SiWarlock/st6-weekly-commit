package com.st6.wc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * {@code wc-api} Spring Boot entry point (task 0.4). At package {@code com.st6.wc}, its default
 * component scan covers {@code com.st6.wc.config} — picking up the {@code :shared} {@code
 * ClockConfig} and the api {@code OrgTimeBindingConfig} (flag 6). No domain endpoints / auth / CORS
 * / SNS yet — those land in later phases.
 */
@SpringBootApplication
public class WcApiApplication {

  public static void main(String[] args) {
    SpringApplication.run(WcApiApplication.class, args);
  }
}
