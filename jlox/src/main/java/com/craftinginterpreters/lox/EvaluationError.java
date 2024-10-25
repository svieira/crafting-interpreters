package com.craftinginterpreters.lox;

class EvaluationError extends RuntimeException {
  private final Token token;

  EvaluationError(Token operator, String failureMessage) {
    super(failureMessage);
    this.token = operator;
  }

  Token getToken() {
    return token;
  }
}
