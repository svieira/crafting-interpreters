package com.craftinginterpreters.lox;

import java.util.List;

/**
 * <p>The AST for the statement portion of the Lox grammar:</p>
 * <pre><code>
 * program        → declaration* EOF ;
 *
 * declaration    → varDecl
 *                | statement ;
 *
 * varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
 *
 * statement      → exprStmt
 *                | printStmt ;
 *                | block ;
 *
 * exprStmt       → expression ";" ;
 * printStmt      → "print" expression ";" ;
 * block          → "{" declaration* "}" ;
 * </code></pre>
 *
 * {@link Expr see also <code>Expr</code>}
 */
interface Stmt {
  interface Visitor<R> {
    R visit(Expression expression);
    R visit(Print print);
    R visit(Var declaration);
    R visit(Block block);
    default R visit(Unparsable errorNode) {
      throw new UnsupportedOperationException("Did not expect to have to handle unparsable statements");
    }
  }

  <R> R accept(Visitor<R> visitor);

  record Expression(Expr expression) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Print(Expr expression) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Var(Token name, Expr initializer) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record Block(List<Stmt> statements) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  // TODO: Separate out into its own super-tree using an object algebra.
  record Unparsable(Token start, Token end) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
}
