package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;

public class LoxInstance {
  private final LoxClass loxClass;
  private final Map<String, Object> fields = new HashMap<>();

  public LoxInstance(LoxClass loxClass) {
    this.loxClass = loxClass;
  }

  Object get(Token fieldName) {
    var name = fieldName.lexeme();
    if (fields.containsKey(name)) {
      return fields.get(name);
    }
    LoxFunction method = loxClass.findMethod(fieldName.lexeme());
    if (method != null) return method.bind(this);

    throw new EvaluationError(fieldName, "Undefined property " + name + ".");
  }

  public void set(Token field, Object value) {
    fields.put(field.lexeme(), value);
  }

  @Override
  public String toString() {
    return "<" + loxClass.name() + " instance>";
  }
}
