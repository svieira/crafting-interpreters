package com.craftinginterpreters.lox;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ParserTest {

  public static Stream<Arguments> precedenceCases() {
    return Stream.of(
      arguments("1 + 2 + 3", "(+ (+ 1.0 2.0) 3.0)"),
      arguments("1 + 2 * 3", "(+ 1.0 (* 2.0 3.0))"),
      arguments("a ? b : c", "(? a (: b c))"),
      arguments("a ? b ? c : d : e", "(? a (: (? b (: c d)) e))")
    );
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("precedenceCases")
  void testPrecedence(String sourceCode, String expectedRepresentation) {
    var tokens = new Scanner(sourceCode).scanTokens();
    var results = new Parser(tokens).parse();
    assertNotNull(results, "Failed to parse " + sourceCode);
    var actualRepresentation = results.accept(new AstPrinter());
    assertEquals(actualRepresentation, expectedRepresentation);
  }
}