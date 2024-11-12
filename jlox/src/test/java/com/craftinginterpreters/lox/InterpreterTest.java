package com.craftinginterpreters.lox;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

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
            var env = new Environment();
            var prints = new ByteArrayOutputStream();
            var output = new PrintStream(prints, true);
            new Interpreter(env, output).interpret(program, Assertions::assertNull);
            assertEquals("0\n1\n2\n", prints.toString(StandardCharsets.UTF_8));
          }
        }
      }
    }
  }

}