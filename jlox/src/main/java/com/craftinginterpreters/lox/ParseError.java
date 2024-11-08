package com.craftinginterpreters.lox;

public final class ParseError extends RuntimeException implements ParseResult {
  private final Token token;
  private final String message;
  private ParseError earlierError;

  ParseError(Token token, String message, ParseError earlierError) {
    this(token, message);
    this.earlierError = earlierError;
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

  public ParseError earlierError() {
    return earlierError;
  }
}
