package com.craftinginterpreters.lox;

record Token(TokenType type, String lexeme, Object literal, int line) {
  public String toString() {
        return type + " " + lexeme + (literal == null ? "" : " " + literal);
    }
}