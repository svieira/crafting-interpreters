package com.craftinginterpreters.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {
  private final Stmt.Function declaration;
  private final Environment scope;
  private final Type type;

  enum Type {
    FUNCTION, INITIALIZER;
  }

  static LoxFunction method(Stmt.Function declaration, Environment scope) {
    var type = declaration.name().lexeme().equals("init") ? Type.INITIALIZER : Type.FUNCTION;
    return new LoxFunction(declaration, scope, type);
  }

  LoxFunction(Stmt.Function declaration, Environment scope) {
    this(declaration, scope, Type.FUNCTION);
  }

  LoxFunction(Stmt.Function declaration, Environment scope, Type functionType) {
    this.declaration = declaration;
    this.scope = scope;
    this.type = functionType;
  }

  public boolean isGetter() {
    return declaration.isGetter();
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
      if (Type.INITIALIZER.equals(type)) {
        return environment.get(new Token(TokenType.THIS, "this", null, -1, -1));
      }

      return signal.value;
    }

    if (Type.INITIALIZER.equals(type)) {
      return environment.get(new Token(TokenType.THIS, "this", null, -1, -1));
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

  public LoxFunction bind(LoxInstance loxInstance) {
    var environment = new Environment(scope);
    environment.define("this", loxInstance);
    return new LoxFunction(declaration, environment, type);
  }
}
