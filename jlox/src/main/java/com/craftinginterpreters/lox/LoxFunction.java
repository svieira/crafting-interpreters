package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
  private final Stmt.Function declaration;
  private final Environment scope;

  LoxFunction(Stmt.Function declaration, Environment scope) {
    this.declaration = declaration;
    this.scope = scope;
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    var environment = new Environment(scope);
    for (int i = 0; i < declaration.params().size(); i++) {
      environment.define(
              declaration.params().get(i),
              arguments.get(i));
    }

    try {
      interpreter.executeBlock(declaration.body(), environment);
    } catch (ReturnSignal signal) {
      return signal.value;
    }

    return null;
  }

  @Override
  public int arity() {
    return declaration.params().size();
  }

  @Override
  public String toString() {
    return "<fn " + declaration.name().lexeme() + ">";
  }
}
