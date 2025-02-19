package com.craftinginterpreters.lox;

import java.util.List;
import java.util.Map;

public class LoxClass extends LoxInstance implements LoxCallable {
  private final String name;
  private final Map<String, LoxFunction> methods;
  private final LoxClass superclass;

  public LoxClass(String name, LoxClass superclass, Map<String, LoxFunction> methods, Map<String, LoxFunction> classMethods) {
    super(classMethods.isEmpty() ? null : new LoxClass(name + "::metaclass", null, classMethods, Map.of()));
    this.name = name;
    this.methods = methods;
    this.superclass = superclass;
  }

  @Override
  public String toString() {
    var hasSuper = superclass != null;
    return "<class " + this.name + (hasSuper ? " extends " + superclass.name : "") + ">";
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

    if (superclass != null) {
      return superclass.findMethod(methodName);
    }

    return null;
  }
}
