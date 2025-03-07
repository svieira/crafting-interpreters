package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.stream.Stream;

final class Program extends ArrayList<Stmt> implements ParseResult.Success {
  public <R> Stream<R> accept(Stmt.Visitor<R> interpreter) {
    return this.stream().map(stmt -> stmt.accept(interpreter));
  }
}
