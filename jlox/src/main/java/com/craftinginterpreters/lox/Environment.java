package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Environment {
  private final Map<String, Object> values = new HashMap<>();
  private final List<Object> quickValues = new ArrayList<>();
  private final Environment enclosing;

  Environment() {
    enclosing = null;
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  Object get(Token name) {
    if (values.containsKey(name.lexeme())) {
      return values.get(name.lexeme());
    }

    if (enclosing != null) return enclosing.get(name);

    throw new EvaluationError(name,
            "Undefined variable '" + name.lexeme() + "'.");
  }

  void define(Token name, Object value) {
    values.put(name.lexeme(), value);
  }

  void define(String name, Object value) {
    values.put(name, value);
  }

  public void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme())) {
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
    return scope.quickValues.size() > distance.id() ? scope.quickValues.get(distance.id()) : get(name);
  }

  public void assignAt(Resolver.Coordinates distance, Token name, Object result) {
    var ancestor = ancestor(distance.scope());
    if (ancestor == null) {
      throw new NullPointerException("Expected to find enclosing scope at " + distance + " from " + name);
    }
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
}