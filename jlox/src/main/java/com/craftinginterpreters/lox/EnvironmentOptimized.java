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
  private final Object identifier;

  EnvironmentOptimized(Map<Token, Resolver.Coordinates> locals) {
    enclosing = null;
    global = new EnvironmentGlobal();
    top = this;
    stats = new Stats();
    this.locals = locals;
    identifier = "TOP";
  }

  EnvironmentOptimized(EnvironmentOptimized enclosing, Object identifier) {
    this.enclosing = enclosing;
    this.top = enclosing.top;
    this.stats = enclosing.top.stats;
    this.locals = null;
    global = null;
    this.identifier = identifier;
  }

  @Override
  Environment pushScope(Object identifier) {
    return new EnvironmentOptimized(this, identifier);
  }

  public Object get(Token name) {
    var distance = top.locals.get(name);
    if (distance == null) {
      return top.global.get(name);
    }
    var scope = ancestor(distance.scope());
    stats.byCoordinateLookups++;
    if (scope == null || scope.values.size() <= distance.id()) {
      throw new EvaluationError(name, "Unable to lookup variable:\n\t'" + name + "'\nin scope:\n'" + this + "'" + "\n\tat distance " + distance);
    }
    return scope.values.get(distance.id());
  }

  public Environment getEnvironmentOf(Token name) {
    var distance = top.locals.get(name);
    if (distance == null) {
      throw new EvaluationError(name, "Unable to resolve for lookup of environment for '" + name + "' due to earlier miss");
    }
    var env = ancestor(distance.scope());
    if (env != null) return env;

    throw new EvaluationError(name,
            "Undefined variable '" + name.lexeme() + "'.");
  }

  void define(Token name, Object value) {
    var distance = top.locals.get(name);
    if (distance == null) {
      //values.add(value);
      throw new EvaluationError("Unable to find scope for '" + name + "'.");
    } else {
      var scope = ancestor(distance.scope());
      stats.byCoordinateLookups++;
      scope.values.add(distance.id(), value);
    }
  }

  void define(String name, Object value) {
    throw new UnsupportedOperationException("Cannot define by string name for optimized environment.");
  }

  public void assign(Token name, Object value) {
    var distance = top.locals.get(name);
    if (distance == null) {
      throw new EvaluationError(name, "Unable to resolve '" + name + "' for assignment due to earlier miss");
    }
    var ancestor = ancestor(distance.scope());
    if (ancestor == null) {
      throw new EvaluationError(name,
              "Cannot assign to undefined variable '" + name.lexeme() + "'");
    }
    stats.byCoordinateAssignments++;
    ancestor.values.set(distance.id(), value);
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

  @Override
  public String toString() {
    return toSelfString() + (enclosing != null ? enclosing.toString(1) : "");
  }

  private String toSelfString() {
    return hashCode() + ":" + identifier + "@" + values;
  }

  private String toString(int depth) {
    return '\n' + "\t".repeat(depth) + toSelfString() + (enclosing != null ? enclosing.toString(depth + 1) : "");
  }

  private final static class Stats {
    private int byCoordinateAssignments = 0;
    private int byCoordinateLookups = 0;

    private String asString() {
      return String.format("""
      By coordinates:
        Assignments: %d
        Lookups: %d
      """, byCoordinateAssignments, byCoordinateLookups);
    }
  }
}