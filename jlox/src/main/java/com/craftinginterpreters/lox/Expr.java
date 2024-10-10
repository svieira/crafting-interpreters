package com.craftinginterpreters.lox;

/**
 * The AST for the following Lox grammar

 * <pre><code>
 * expression     → literal
 *                | unary
 *                | binary
 *                | grouping ;
 *
 * literal        → NUMBER | STRING | "true" | "false" | "nil" ;
 * grouping       → "(" expression ")" ;
 * unary          → ( "-" | "!" ) expression ;
 * binary         → expression operator expression ;
 * operator       → "==" | "!=" | "<" | "<=" | ">" | ">="
 *                | "+"  | "-"  | "*" | "/" ;
 * </code></pre>
 */
interface Expr {
  interface Visitor<R> {
    R visit(Binary binary);
    R visit(Grouping grouping);
    R visit(Literal literal);
    R visit(Unary unary);
  }

  <R> R accept(Visitor<R> visitor);

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