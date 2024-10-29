package com.craftinginterpreters.lox;

public final class ParseError extends RuntimeException implements ParseResult {
  private final Token token;
  private final String message;
  private ParseError statementParseException;

  ParseError(Token token, String message, ParseError statementParseException) {
    this(token, message);
    this.statementParseException = statementParseException;
  }

  ParseError(Token token, String message) {
    super("[" + token.line() + ":" + token.column() + "] " + message);
    this.token = token;
    this.message = message;
  }

  Token token() {
    return token;
  }

  public String message() {
    return message;
  }

  public ParseError statementParseException() {
    return statementParseException;
  }
}
