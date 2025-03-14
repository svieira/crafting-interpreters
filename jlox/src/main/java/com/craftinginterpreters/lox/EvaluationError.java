package com.craftinginterpreters.lox;

class EvaluationError extends RuntimeException {
  private final Token token;

  EvaluationError(Token operator, String failureMessage) {
    super(failureMessage);
    this.token = operator;
  }

  EvaluationError(Throwable cause) {
    super(cause);
    token = Token.artificial("<Unknown source location>");
  }

  Token getToken() {
    return token;
  }
}
