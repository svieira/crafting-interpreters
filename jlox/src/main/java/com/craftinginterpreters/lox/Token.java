package com.craftinginterpreters.lox;

record Token(TokenType type, String lexeme, Object literal, int line, int column) {
  static Token artificial(String lexeme) {
    return new Token(TokenType.IDENTIFIER, lexeme, null, -1, -1);
  }

  static Token artificial(TokenType type) {
    return new Token(type, type.keyword(), null, -1, -1);
  }

  public String toString() {
        return "[" + line + ":" + column + "] " + type + " " + lexeme + (literal == null ? "" : " " + literal);
    }
}