package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.List;

public class LoxClass implements LoxCallable {
  private final String name;
  private final HashMap<String, LoxFunction> methods;

  public LoxClass(String name, HashMap<String, LoxFunction> methods) {
    this.name = name;
    this.methods = methods;
  }

  @Override
  public String toString() {
    return "<class " + this.name + ">";
  }

  @Override
  public Object call(Interpreter interpreter, List<Object> arguments) {
    var instance = new LoxInstance(this);
    LoxFunction initializer = findMethod("init");
    if (initializer != null) {
      initializer.bind(instance).call(interpreter, arguments);
    }
    return instance;
  }

  @Override
  public int arity() {
    LoxFunction initializer = findMethod("init");
    if (initializer == null) return 0;
    return initializer.arity();
  }

  public String name() {
    return name;
  }

  public LoxFunction findMethod(String methodName) {
    if (methods.containsKey(methodName)) {
      return methods.get(methodName);
    }

    return null;
  }
}
