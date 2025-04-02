package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class EnvironmentOptimized extends Environment {
  private final Map<Token, Resolver.Coordinates> locals;
  private final List<Object> values = new ArrayList<>();
  private final EnvironmentOptimized enclosing;
  private final EnvironmentOptimized top;
  private final Environment global;
  private final Stats stats;

  EnvironmentOptimized(Map<Token, Resolver.Coordinates> locals) {
    enclosing = null;
    global = new EnvironmentGlobal();
    top = this;
    stats = new Stats();
    this.locals = locals;
  }

  EnvironmentOptimized(EnvironmentOptimized enclosing) {
    this.enclosing = enclosing;
    this.top = enclosing.top;
    this.stats = enclosing.top.stats;
    this.locals = null;
    global = null;
  }

  @Override
  Environment pushScope() {
    return new EnvironmentOptimized(this);
  }

  public Object get(Token name) {
    var distance = top.locals.get(name);
    if (distance == null) {
      return top.global.get(name);
    }
    var scope = ancestor(distance.scope());
    stats.byCoordinateLookups++;
    return scope.values.get(distance.id());
  }

  public Environment getEnvironmentOf(Token name) {
    var distance = top.locals.get(name);
    if (distance == null) {
      throw new EvaluationError(name, "Unable to resolve for lookup of environment due to earlier miss");
    }
    var env = ancestor(distance.scope());
    if (env != null) return env;

    throw new EvaluationError(name,
            "Undefined variable '" + name.lexeme() + "'.");
  }

  void define(Token name, Object value) {
    assign(name, value);
  }

  void define(String name, Object value) {
    throw new UnsupportedOperationException("Cannot define by string name for optimized environment.");
  }

  public void assign(Token name, Object value) {
    var distance = top.locals.get(name);
    if (distance == null) {
      throw new EvaluationError(name, "Unable to resolve for assignment due to earlier miss");
    }
    var ancestor = ancestor(distance.scope());
    if (ancestor == null) {
      throw new EvaluationError(name,
              "Cannot assign to undefined variable '" + name.lexeme() + "'");
    }
    stats.byCoordinateAssignments++;
    ancestor.values.add(distance.id(), value);
  }

  @Override
  Environment parent() {
    return ancestor(1);
  }

  EnvironmentOptimized ancestor(int distance) {
    EnvironmentOptimized environment = this;
    for (int i = 0; i < distance; i++) {
      environment = environment.enclosing;
    }

    return environment;
  }

  void printStats() {
    System.out.println(stats.asString());
  }


  private final static class Stats {
    private int byCoordinateAssignments = 0;
    private int byCoordinateLookups = 0;
    private int byCoordinateMisses = 0;

    private String asString() {
      return String.format("""
      By coordinates:
        Assignments: %d
        Lookups: %d
        Misses: %d
      """, byCoordinateAssignments, byCoordinateLookups, byCoordinateMisses);
    }
  }
}