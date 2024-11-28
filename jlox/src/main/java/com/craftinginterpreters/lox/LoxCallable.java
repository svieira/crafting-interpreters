package com.craftinginterpreters.lox;

import java.util.List;

public interface LoxCallable {
  Object call(Interpreter interpreter, List<Object> arguments);
  /** The total number of arguments this function requires */
  int arity();
}
