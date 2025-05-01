package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

class EnvironmentSimple extends Environment {
  private final Map<String, Object> values = new HashMap<>();
  private final EnvironmentSimple enclosing;
  private final EnvironmentSimple top;
  private final int depth;
  private final Stats stats;

  EnvironmentSimple() {
    enclosing = null;
    top = this;
    depth = 0;
    stats = new Stats();
  }

  EnvironmentSimple(EnvironmentSimple enclosing) {
    this.enclosing = enclosing;
    this.top = enclosing.top;
    this.depth = enclosing.depth + 1;
    this.stats = enclosing.top.stats;
  }

  @Override
  Environment pushScope(Object identifier) {
    return new EnvironmentSimple(this);
  }

  @Override
  public Object get(Token name) {
    if (values.containsKey(name.lexeme())) {
      stats.byNameLookups++;
      return values.get(name.lexeme());
    }

    if (enclosing != null) {
      stats.byNameMisses++;
      return enclosing.get(name);
    }

    throw new EvaluationError(name,
            "Undefined variable '" + name.lexeme() + "'.");
  }

  @Override
  public Environment getEnvironmentOf(Token name) {
    if (values.containsKey(name.lexeme())) {
      stats.byNameLookups++;
      return this;
    }

    if (enclosing != null) {
      stats.byNameMisses++;
      return enclosing.getEnvironmentOf(name);
    }

    throw new EvaluationError(name,
            "Undefined variable '" + name.lexeme() + "'.");
  }

  @Override
  public void define(Token name, Object value) {
    stats.byNameAssignments++;
    values.put(name.lexeme(), value);
  }

  @Override
  public void define(String name, Object value) {
    stats.byNameAssignments++;
    values.put(name, value);
  }

  @Override
  public void assign(Token name, Object value) {
    stats.byNameLookups++;
    if (values.containsKey(name.lexeme())) {
      stats.byNameAssignments++;
      values.put(name.lexeme(), value);
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new EvaluationError(name,
            "Cannot assign to undefined variable '" + name.lexeme() + "'");
  }

  @Override
  Environment parent() {
    return enclosing;
  }

  @Override
  public void printStats() {
    System.out.println(stats.asString());
  }


  private final static class Stats {
    private int byNameAssignments = 0;
    private int byNameLookups = 0;
    private int byNameMisses = 0;

    private String asString() {
      return String.format("""
      By name:
        Assignments: %d
        Lookups: %d
        Misses: %d
      """, byNameAssignments, byNameLookups, byNameMisses);
    }
  }
}