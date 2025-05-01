package com.craftinginterpreters.lox;

import java.util.Objects;

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

  private boolean isArtificial() {
    return line == -1 && column == -1 && literal == null;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Token other)) {
      return false;
    }
    if (other == this) {
      return true;
    }
    if (isArtificial() && other.isArtificial()) {
      return Objects.equals(type, other.type) && Objects.equals(lexeme, other.lexeme);
    }
    return Objects.equals(type, other.type)
            && Objects.equals(lexeme, other.lexeme)
            && Objects.equals(literal, other.literal)
            && line == other.line
            && column == other.column;
  }

  @Override
  public int hashCode() {
    return isArtificial()
            ? Objects.hash(type, lexeme)
            : 31 * (31 * Objects.hash(type, lexeme, literal) + line) + column;
  }
}