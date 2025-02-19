package com.craftinginterpreters.lox;

record Token(TokenType type, String lexeme, Object literal, int line, int column) {
  static Token artificial(String lexeme) {
    return new Token(TokenType.IDENTIFIER, lexeme, null, -1, -1);
  }
  public String toString() {
        return "[" + line + ":" + column + "] " + type + " " + lexeme + (literal == null ? "" : " " + literal);
    }
}