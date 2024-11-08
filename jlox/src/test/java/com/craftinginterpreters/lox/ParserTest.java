package com.craftinginterpreters.lox;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class ParserTest {

  public static Stream<Arguments> precedenceCases() {
    return Stream.of(
      testCase("comments", "1 + 2 // Spicy", "(+ 1.0 2.0)"),
      testCase("comments", "1 /* oh boy */ + 2", "(+ 1.0 2.0)"),
      testCase("comments", "1 /* nested /* magic */ */ + 2", "(+ 1.0 2.0)"),
      testCase("precedence", "1 + 2 + 3", "(+ (+ 1.0 2.0) 3.0)"),
      testCase("precedence", "1 + 2 * 3", "(+ 1.0 (* 2.0 3.0))"),
      testCase("precedence", "a ? b : c", "(? a (: b c))"),
      testCase("precedence", "a ? b ? c : d : e", "(? a (: (? b (: c d)) e))"),
      testCase("precedence", "nil and false or true", "(or (and nil false) true)"),
      testCase("precedence", "first and second ? x : y", "(and first (? second (: x y)))"),
      testCase("unary", "-3", "(- 3.0)")
    );
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("precedenceCases")
  void testPrecedence(String sourceCode, String expectedRepresentation) {
    var scanResults = new Scanner(sourceCode).scanTokens();
    if (scanResults instanceof Scanner.LexError lexError) {
      fail("Failed to lex", lexError);
    }
    var tokens = (Scanner.TokenList) scanResults;
    switch(new Parser(tokens).parse()) {
      case Program p -> {
        var actualRepresentation = p.accept(new AstPrinter()).collect(Collectors.joining("\n"));
        assertEquals(expectedRepresentation, actualRepresentation);
      }
      case Expr expression -> {
        var actualRepresentation = expression.accept(new AstPrinter());
        assertEquals(expectedRepresentation, actualRepresentation);
      }
      case ParseError failure -> fail("Failed to parse " + sourceCode, failure);
    }
  }

  public static Stream<Arguments> errorCases() {
    return Stream.of(
      testCase("binaryOps", "+ 2 + 3", "Binary operator missing left-hand side"),
      testCase("ternary", "? missing : something", "Ternary operator missing test condition"),
      testCase("ternary", "missing ? something", "Expecting COLON following QUESTION_MARK"),
      testCase("ternary", "missing : something", "Failed to parse (next viable token is [1:19] IDENTIFIER something)"),
      testCase("ternary", ": lots_missing", "Unexpected COLON. Are you missing a QUESTION_MARK?"),
      testCase("elvis", "?: missing_test", "Ternary operator missing test condition"),
      testCase("lex",
              """
              "Oh no, an unclosed quote ...
              """, "Unterminated string."),
      testCase("lex", "/* it's unclosed you see ...", "Unclosed block comment detected"),
      testCase("lex", "✘_is_not_valid_id", "Unexpected character: '✘' (HEAVY BALLOT X)"),
      testCase("loops", "break;", "Unable to handle token of type BREAK")
    );
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("errorCases")
  void testErrors(String sourceCode, String expectedError) {
    var scanResults = new Scanner(sourceCode).scanTokens();
    switch (scanResults) {
      case Scanner.LexError lexError -> assertEquals(expectedError, lexError.getMessage());
      case Scanner.TokenList tokens -> {
        switch(new Parser(tokens).parse()) {
          case Program p -> {
            var program = p.accept(new AstPrinter()).collect(Collectors.joining("\n"));
            fail("Did not expect " + sourceCode + " to successfully parse, but parsed as " + program);
          }
          case Expr expression -> fail("Did not expect " + sourceCode + " to successfully parse, but parsed as " + expression.accept(new AstPrinter()));
          case ParseError failure -> assertEquals(expectedError, failure.earlierError().message());
        }
      }
    }

  }

  private static Arguments testCase(String sourceCode, String expectedRepresentation) {
    return arguments(sourceCode, expectedRepresentation);
  }

  private static Arguments testCase(String tag, String sourceCode, String expectedRepresentation) {
    var title = "#" + tag + " " + sourceCode;
    return arguments(named(title, sourceCode), expectedRepresentation);
  }
}