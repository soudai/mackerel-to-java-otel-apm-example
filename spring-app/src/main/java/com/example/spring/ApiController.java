package com.example.spring;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ApiController {
  private final ProductRepository repo;
  private final ManualClient manual;

  public ApiController(ProductRepository repo, ManualClient manual) {
    this.repo = repo;
    this.manual = manual;
  }

  @GetMapping("/products")
  public List<Product> products() {
    return repo.findAll();
  }

  @GetMapping("/manual/hello")
  public String manualHello() {
    return manual.hello();
  }

  @GetMapping("/manual/db")
  public String manualDb() {
    return manual.db();
  }

  @GetMapping("/health")
  public String health() {
    return "ok";
  }
}
