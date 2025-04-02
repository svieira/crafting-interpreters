package com.craftinginterpreters.lox;

abstract class Environment {
  /** Construct a new environment with this one as the enclosing one */
  abstract Environment pushScope();

  /** Lookup a token in the environment in an optimizable way */
  abstract Object get(Token name);

  /** Define (declare and assign in a single operation) a name to a value in an optimizable way */
  abstract void define(Token name, Object value);

  /** Attempt to assign a value to a previously declared variable slot in an optimizable way */
  abstract void assign(Token name, Object value);

  /** Globally define a name in a way that we're not going to optimize yet */
  abstract void define(String name, Object value);

  /** Exists only for a hack to get the superclass */
  abstract Environment getEnvironmentOf(Token name);

  /** Exists only for a hack to get the superclass */
  abstract Environment parent();

  /** Implementations should track some stats */
  abstract void printStats();
}
