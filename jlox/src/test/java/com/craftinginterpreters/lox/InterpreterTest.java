package com.craftinginterpreters.lox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterTest {
  @Test
  void debug() {
    switch(new Scanner("""
            var i = 0; while (i < 3) { print i; i = i + 1; }
            """).scanTokens()) {
      case Scanner.LexError lexError -> {
        fail(lexError.getMessage());
      }
      case Scanner.TokenList tokens -> {
        switch(new Parser(tokens).parse()) {
          case ParseError parseError -> {
            fail(parseError.getMessage());
          }
          case Expr expr -> {
            var result = expr.accept(new Interpreter());
            assertTrue(result != null);
          }
          case Program program -> {
            new Interpreter().interpret(program, Assertions::assertNull);
          }
        }
      }
    }
  }

}