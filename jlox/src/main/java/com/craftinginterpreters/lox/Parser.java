package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {

  private static final TokenType[] EMPTY_TYPES = new TokenType[0];
  private static final TokenType[] UNAMBIGUOUS_TERM_TOKENS = { PLUS };
  private static final TokenType[] AMBIGUOUS_TERM_TOKENS = { MINUS };

  private final List<Token> tokens;
  private int current = 0;

  private enum Context { IN_LOOP; }

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  ParseResult parse() {
    var program = new Program();
    try {
      while (!isAtEnd()) {
        program.add(declaration(EnumSetQueue.empty(Context.class)));
      }
      return program;
    } catch (ParseError error) {
      if (!program.isEmpty()) {
        // If we successfully parsed at least one statement
        // then we're not being asked to evaluate an expression in a REPL-like environment
        // but are instead parsing a larger program. This is a programming error.
        return error;
      }
      try {
        // Otherwise, it may be a single expression in a REPL-like environment
        // Rewind and try to parse as an expression
        current = 0;
        var expression = expression();
        if (!isAtEnd()) {
          return new ParseError(peek(), "Failed to fully parse expression", error);
        }
        return expression;
      } catch (ParseError expressionParseError) {
        return new ParseError(peek(), "Failed to parse as expression", expressionParseError);
      }
    }
  }

  private Stmt declaration(EnumSetQueue<Context> context) {
    try {
      if (match(VAR)) return varDeclaration(context);
      return statement(context);
    } catch (ParseError e) {
      var initialToken = peek();
      synchronize();
      throw error(initialToken, "Failed to parse (next viable token is " + previous() + ")");
    }
  }

  private Stmt varDeclaration(EnumSetQueue<Context> context) {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt statement(EnumSetQueue<Context> context) {
    if (match(FOR)) return forStatement(context);
    if (match(IF)) return ifStatement(context);
    if (match(PRINT)) return printStatement();
    if (match(WHILE)) return whileStatement(context);
    if (match(LEFT_BRACE)) return block(context);
    if (match(BREAK, CONTINUE)) return loopControl(context);

    return expressionStatement();
  }

  private Stmt forStatement(EnumSetQueue<Context> context) {
    // Desugaring for to a while
    consume(LEFT_PAREN, "Expect '(' after 'for'.");
    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration(context);
    } else {
      initializer = expressionStatement();
    }
    Expr condition = !check(SEMICOLON) ? expression() : new Expr.Literal(true);
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = !check(RIGHT_PAREN) ? expression() : null;
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");
    Stmt body = statement(EnumSetQueue.push(context, Context.IN_LOOP));

    if (increment != null) {
      body = new Stmt.Block(body, new Stmt.Expression(increment));
    }

    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(initializer, body);
    }

    return body;
  }

  private Stmt whileStatement(EnumSetQueue<Context> context) {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after while condition.");
    Stmt body = statement(EnumSetQueue.push(context, Context.IN_LOOP));
    return new Stmt.While(condition, body);
  }

  private Stmt ifStatement(EnumSetQueue<Context> context) {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt whenTrue = statement(context);
    Stmt whenFalse = null;
    if (match(ELSE)) {
      whenFalse = statement(context);
    }

    return new Stmt.If(condition, whenTrue, whenFalse);
  }

  private Stmt loopControl(EnumSetQueue<Context> context) {
    var token = previous();
    consume(SEMICOLON, "Expect ';' after loop control.");

    if (!context.contains(Context.IN_LOOP)) {
      throw new ParseError(token, "loop control must be inside of loop");
    }

    var type = switch (token.type()) {
      case BREAK -> Stmt.LoopControl.Type.BREAK;
      case CONTINUE -> Stmt.LoopControl.Type.CONTINUE;
      default -> throw new ParseError(token, "Not a valid loop control keyword");
    };
    return new Stmt.LoopControl(token, type);
  }

  private Stmt block(EnumSetQueue<Context> context) {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration(context));
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return new Stmt.Block(statements);
  }

  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  private Expr expression() {
    return assignment();
  }

  private Expr assignment() {
    var leftHandSide = or();
    if (match(EQUAL)) {
      var equals = previous();
      var rightHandSide = assignment();
      if (leftHandSide instanceof Expr.Variable variable) {
        return new Expr.Assignment(variable.name(), rightHandSide);
      }
      throw error(equals, "Invalid assignment target");
    }
    return leftHandSide;
  }

  private Expr or() {
    // If we had more of these it might make sense to duplicate the method or copy and paste the impl
    var e = binaryOp(this::and, OR);
    return e instanceof Expr.Binary b && b.operator().type().equals(OR) ? new Expr.Logical(b.left(), b.operator(), b.right()) : e;
  }

  private Expr and() {
    var e = binaryOp(this::discardedExpression, AND);
    return e instanceof Expr.Binary b && b.operator().type().equals(AND) ? new Expr.Logical(b.left(), b.operator(), b.right()) : e;
  }

  private Expr discardedExpression() {
    return binaryOp(this::ternary, COMMA);
  }

  private Expr ternary() {
    if (match(QUESTION_MARK, ELVIS)) {
      // Error: Leading ternary operator
      var operator = previous();
      var _right = ternary(); // Go ahead and attempt to recover
      throw error(operator, "Ternary operator missing test condition");
    } else if (match(COLON)) {
      // Error: Leading ternary operator
      var operator = previous();
      var _right = ternary(); // Go ahead and attempt to recover
      throw error(operator, "Unexpected " + COLON + ". Are you missing a " + QUESTION_MARK + "?");
    }
    var expr = equality();
    while (match(QUESTION_MARK, ELVIS)) {
      var firstOp = previous();
      var left = ternary();
      if (firstOp.type() == ELVIS) {
        expr = new Expr.Binary(expr, firstOp, left);
      } else if (match(COLON)) {
        var secondOp = previous();
        var right = ternary();
        expr = new Expr.Trinary(expr, firstOp, left, secondOp, right);
      } else {
        throw error(previous(), "Expecting " + COLON + " following " + QUESTION_MARK);
      }
    }
    return expr;
  }

  private Expr equality() {
    return binaryOp(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
  }

  private Expr comparison() {
    return binaryOp(this::term, GREATER_EQUAL, GREATER, LESS_EQUAL, LESS);
  }

  private Expr term() {
    return binaryOp(this::factor, UNAMBIGUOUS_TERM_TOKENS, AMBIGUOUS_TERM_TOKENS);
  }

  private Expr factor() {
    return binaryOp(this::unary, STAR, SLASH);
  }

  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }
    return coalesce();
  }

  private Expr coalesce() {
    return binaryOp(this::primary, COALESCE);
  }

  private Expr primary() {
    var token = advance();
    return switch (token.type()) {
      case TRUE -> new Expr.Literal(true);
      case FALSE -> new Expr.Literal(false);
      case NIL -> new Expr.Literal(null);
      case NUMBER, STRING -> new Expr.Literal(token.literal());
      case LEFT_PAREN -> {
        var group = expression();
        consume(RIGHT_PAREN, "Expect ')' after expression.");
        yield new Expr.Grouping(group);
      }
      case IDENTIFIER -> new Expr.Variable(token);
      case EOF -> throw error(token, "Unexpected end of file");
      default -> {
        throw error(token, "Unable to handle token of type " + token.type());
      }
    };
  }

  /** Parse a left-associative binary operation */
  private Expr binaryOp(Supplier<Expr> target, TokenType... opTokens) {
    return binaryOp(target, opTokens, EMPTY_TYPES);
  }

  /** Parse a left-associative binary operator where some of the operators are also unary operators */
  private Expr binaryOp(Supplier<Expr> target, TokenType[] unambiguousTokens, TokenType[] ambiguousTokens) {
    if (match(unambiguousTokens)) {
      // Error: Leading binary operator
      var operator = previous();
      var _right = target.get(); // Go ahead and attempt to recover
      throw error(operator, "Binary operator missing left-hand side");
    }
    var expr = target.get();
    while (match(unambiguousTokens) || match(ambiguousTokens)) {
      Token operator = previous();
      Expr right = target.get();
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  private boolean match(TokenType... types) {
    while (check(COMMENT)) advance();
    for (TokenType type: types) {
      if (check(type)) {
        advance();
        return true;
      }
    }
    return false;
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type() == type;
  }

  private Token advance() {
    if (isAtEnd()) return peek();
    do current += 1; while (check(COMMENT));
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type() == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  private ParseError error(Token token, String message) {
    return new ParseError(token, message);
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type() == SEMICOLON) return;

      switch (peek().type()) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}