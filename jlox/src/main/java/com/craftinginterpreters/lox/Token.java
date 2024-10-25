package com.craftinginterpreters.lox;

record Token(TokenType type, String lexeme, Object literal, int line, int column) {
  public String toString() {
        return "[" + line + ":" + column + "] " + type + " " + lexeme + (literal == null ? "" : " " + literal);
    }
}