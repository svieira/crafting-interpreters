package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Environment {
  private final Map<String, Object> values = new HashMap<>();
  private final List<Object> quickValues = new ArrayList<>();
  private final Environment enclosing;
  private final Environment top;
  private final int depth;
  private final static class Stats {
    private int byNameAssignments = 0;
    private int byNameLookups = 0;
    private int byNameMisses = 0;
    private int byCoordinateAssignments = 0;
    private int byCoordinateLookups = 0;
    private int byCoordinateMisses = 0;

    private String asString() {
      return String.format("""
      By name:
        Assignments: %d
        Lookups: %d
        Misses: %d
      By coordinates:
        Assignments: %d
        Lookups: %d
        Misses: %d
      """, byNameAssignments, byNameLookups, byNameMisses, byCoordinateAssignments, byCoordinateLookups, byCoordinateMisses);
    }
  }
  private final Stats stats;

  Environment() {
    enclosing = null;
    top = this;
    depth = 0;
    stats = new Stats();
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
    this.top = enclosing.top;
    this.depth = enclosing.depth + 1;
    this.stats = enclosing.top.stats;
  }

  Object get(Token name) {
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

  void define(Token name, Object value) {
    stats.byNameAssignments++;
    values.put(name.lexeme(), value);
  }

  void define(String name, Object value) {
    stats.byNameAssignments++;
    values.put(name, value);
  }

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

  public Object getAt(Resolver.Coordinates distance, Token name) {
    var scope = ancestor(distance.scope());
    var isQuick = scope.quickValues.size() > distance.id();
    stats.byCoordinateLookups++;
    if (!isQuick) {
      stats.byCoordinateMisses++;
    }
    return isQuick ? scope.quickValues.get(distance.id()) : get(name);
  }

  public void assignAt(Resolver.Coordinates distance, Token name, Object result) {
    var ancestor = ancestor(distance.scope());
    if (ancestor == null) {
      throw new NullPointerException("Expected to find enclosing scope at " + distance + " from " + name);
    }
    stats.byCoordinateAssignments++;
    ancestor.quickValues.add(distance.id(), result);
  }

  Environment ancestor(int distance) {
    Environment environment = this;
    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
      if (environment == null) break;
    }

    return environment;
  }

  void printStats() {
    System.out.println(stats.asString());
  }
}