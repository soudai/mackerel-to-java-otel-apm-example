package com.example.spring;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ProductRepository {
  private final JdbcTemplate jdbcTemplate;

  public ProductRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<Product> findAll() {
    return jdbcTemplate.query(
        "SELECT id, name FROM products ORDER BY id",
        (rs, rowNum) -> new Product(rs.getInt("id"), rs.getString("name"))
    );
  }
}
