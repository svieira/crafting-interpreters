package com.craftinginterpreters.lox;

/**
 * The AST for the following Lox grammar

 * <pre><code>
 * expression     → discarded ;
 * discarded      → ternary ( "," ternary )* ;
 * ternary        → equality (( "?" ternary ":" ternary ) | "?:" ternary )* ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                | coalesce ;
 * coalesce       → primary ( "??" primary )* ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                | "(" expression ")" ;
 * </code></pre>
 */
non-sealed interface Expr extends ParseResult {
  interface Visitor<R> {
    R visit(Trinary trinary);
    R visit(Binary binary);
    R visit(Unary unary);
    R visit(Grouping grouping);
    R visit(Literal literal);
  }

  <R> R accept(Visitor<R> visitor);

  record Trinary(Expr head, Token firstOp, Expr left, Token secondOp, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }

  record Binary(Expr left, Token operator, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Grouping(Expr expression) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Literal(Object value) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Unary(Token operator, Expr right) implements Expr {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
}