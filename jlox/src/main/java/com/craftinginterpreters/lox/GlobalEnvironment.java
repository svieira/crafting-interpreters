package com.craftinginterpreters.lox;

import java.util.List;

public class GlobalEnvironment extends Environment {
  public GlobalEnvironment() {
    define("clock", new LoxCallable() {
      @Override
      public Object call(Interpreter interpreter, List<Object> arguments) {
        return System.currentTimeMillis() / 1_000d;
      }

      @Override
      public int arity() {
        return 0;
      }

      @Override
      public String toString() {
        return "<native fn>";
      }
    });
  }
}
