package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

public class LoxClass extends LoxInstance implements LoxCallable {
  private final String name;
  private final Map<String, LoxFunction> methods;

  public LoxClass(String name, Map<String, LoxFunction> methods, Map<String, LoxFunction> classMethods) {
    super(classMethods.isEmpty() ? null : new LoxClass(name + "::metaclass", classMethods, Map.of()));
    this.name = name;
    this.methods = methods;
  }

  @Override
  public String toString() {
    return "<class " + this.name + ">";
  }

  void initialize(Interpreter interpreter) {
    if (loxClass == null) {
      return;
    }
    LoxFunction initializer = loxClass.findMethod("init");
    if (initializer != null) {
      initializer.bind(this).call(interpreter, List.of());
    }
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
