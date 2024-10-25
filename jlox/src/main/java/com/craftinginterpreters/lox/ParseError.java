package com.craftinginterpreters.lox;

public final class ParseError extends RuntimeException implements ParseResult {
  private final Token token;
  private final String message;

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
}
