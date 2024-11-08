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
 *                | forStmt
 *                | ifStmt
 *                | printStmt ;
 *                | whileStmt
 *                | block ;
 *
 * whileStmt      → "while" "(" expression ")" statement ;
 *
 * forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
 *                  expression? ";"
 *                  expression? ")" statement ;
 *
 * exprStmt       → expression ";" ;
 *
 * ifStmt         → "if" "(" expression ")" statement
 *                ( "else" statement )? ;
 *
 * printStmt      → "print" expression ";" ;
 * loopCtrlStmt   → ("break" | "continue") ";" ;
 *
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
    R visit(If anIf);
    R visit(While aWhile);
    R visit(LoopControl loopControl);
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
  record If(Expr condition, Stmt whenTrue, Stmt whenFalse) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record While(Expr condition, Stmt body) implements Stmt {
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visit(this);
    }
  }
  record LoopControl(Token token, LoopControl.Type type) implements Stmt {
    enum Type { BREAK, CONTINUE }

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
    Block(Stmt... statements) {
      this(List.of(statements));
    }
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
