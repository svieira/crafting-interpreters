package com.craftinginterpreters.lox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.craftinginterpreters.lox.TokenType.*;

class Parser {

  private static final TokenType[] EMPTY_TYPES = new TokenType[0];
  private static final TokenType[] UNAMBIGUOUS_TERM_TOKENS = { PLUS };
  private static final TokenType[] AMBIGUOUS_TERM_TOKENS = { MINUS };

  private final List<Token> tokens;
  private int current = 0;

  private enum StatementContext { IN_FUNCTION, IN_INIT, IN_LOOP, IN_CLASS_DECLARATION; }
  private enum ExpressionContext { IN_CALL, IN_CLASS_DECLARATION; }

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  ParseResult parse() {
    var program = new Program();
    try {
      while (!isAtEnd()) {
        program.add(declaration(EnumSetQueue.empty(StatementContext.class)));
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

  private Program program(EnumSetQueue<StatementContext> context) {
    var program = new Program();
    while (!isAtEnd() && !(context.containsAtHead(StatementContext.IN_FUNCTION) && check(RIGHT_BRACE))) {
      program.add(declaration(context));
    }
    return program;
  }

  private Stmt declaration(EnumSetQueue<StatementContext> context) {
    try {
      if (match(FUN)) return callable("function", context);
      if (match(VAR)) return varDeclaration(context);
      return statement(context);
    } catch (ParseError e) {
      var initialToken = peek();
      synchronize();
      throw new ParseError(initialToken, "Failed to parse (next viable token is " + previous() + ") due to " + e.message(), e);
    }
  }

  private Stmt.Function callable(String callableType, EnumSetQueue<StatementContext> context) {
    boolean isMethod = context.containsAtHead(StatementContext.IN_CLASS_DECLARATION);
    var name = consume(IDENTIFIER, "Expected " + callableType + " name");
    var parameters = new ArrayList<Token>();
    // Methods are not required to have parentheses for getters
    var isGetter = isMethod && !check(LEFT_PAREN);
    if (!isGetter) {
      consume(LEFT_PAREN, "Expected '(' after " + callableType + " name.");
      if (!check(RIGHT_PAREN)) {
        do {
          if (parameters.size() >= 255) {
            throw error(peek(), "Can't have more than 255 parameters.");
          }

          parameters.add(consume(IDENTIFIER, "Expect parameter name."));
        } while (match(COMMA));
      }
      consume(RIGHT_PAREN, "Expect ')' after parameters.");
    }

    consume(LEFT_BRACE, "Expect '{' before " + callableType + " body.");

    if (context.containsAtHead(StatementContext.IN_CLASS_DECLARATION) && name.lexeme().equals("init")) {
      context = EnumSetQueue.push(context, StatementContext.IN_FUNCTION, StatementContext.IN_INIT);
    } else {
      context = EnumSetQueue.push(context, StatementContext.IN_FUNCTION);
    }

    var body = block(context);
    return new Stmt.Function(name, parameters, body, isGetter);
  }

  private Stmt varDeclaration(EnumSetQueue<StatementContext> context) {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression(fromStatementContext(context));
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  private Stmt statement(EnumSetQueue<StatementContext> context) {
    if (match(FOR)) return forStatement(context);
    if (match(IF)) return ifStatement(context);
    if (match(PRINT)) return printStatement(context);
    if (match(RETURN)) return returnStatement(context);
    if (match(WHILE)) return whileStatement(context);
    if (match(CLASS)) return classDeclaration(context);
    if (match(LEFT_BRACE)) return new Stmt.Block(block(context));
    if (match(BREAK, CONTINUE)) return loopControl(context);

    return expressionStatement(context);
  }

  private Stmt classDeclaration(EnumSetQueue<StatementContext> context) {
    var name = consume(IDENTIFIER, "Expect class name.");
    consume(LEFT_BRACE, "Expect '{' before class body.");
    var methods = new ArrayList<Stmt.Function>();
    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      methods.add(callable("method", EnumSetQueue.push(context, StatementContext.IN_CLASS_DECLARATION)));
    }
    consume(RIGHT_BRACE, "Expect '}' after class body.");
    return new Stmt.ClassDeclaration(name, methods);
  }

  private Stmt forStatement(EnumSetQueue<StatementContext> context) {
    // Desugaring for to a while
    consume(LEFT_PAREN, "Expect '(' after 'for'.");
    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration(context);
    } else {
      initializer = expressionStatement(context);
    }
    Expr condition = !check(SEMICOLON) ? expression(fromStatementContext(context)) : new Expr.Literal(true);
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = !check(RIGHT_PAREN) ? expression(fromStatementContext(context)) : null;
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");
    Stmt body = statement(EnumSetQueue.push(context, StatementContext.IN_LOOP));

    if (increment != null) {
      body = new Stmt.Block(body, new Stmt.Expression(increment));
    }

    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(initializer, body);
    }

    return body;
  }

  private Stmt whileStatement(EnumSetQueue<StatementContext> context) {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression(fromStatementContext(context));
    consume(RIGHT_PAREN, "Expect ')' after while condition.");
    Stmt body = statement(EnumSetQueue.push(context, StatementContext.IN_LOOP));
    return new Stmt.While(condition, body);
  }

  private Stmt ifStatement(EnumSetQueue<StatementContext> context) {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression(fromStatementContext(context));
    consume(RIGHT_PAREN, "Expect ')' after if condition.");

    Stmt whenTrue = statement(context);
    Stmt whenFalse = null;
    if (match(ELSE)) {
      whenFalse = statement(context);
    }

    return new Stmt.If(condition, whenTrue, whenFalse);
  }

  private Stmt loopControl(EnumSetQueue<StatementContext> context) {
    var token = previous();
    consume(SEMICOLON, "Expect ';' after loop control.");

    if (!context.contains(StatementContext.IN_LOOP)) {
      throw new ParseError(token, "loop control must be inside of loop");
    }

    var type = switch (token.type()) {
      case BREAK -> Stmt.LoopControl.Type.BREAK;
      case CONTINUE -> Stmt.LoopControl.Type.CONTINUE;
      default -> throw new ParseError(token, "Not a valid loop control keyword");
    };
    return new Stmt.LoopControl(token, type);
  }

  private List<Stmt> block(EnumSetQueue<StatementContext> context) {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration(context));
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  private Stmt printStatement(EnumSetQueue<StatementContext> context) {
    Expr value = expression(fromStatementContext(context));
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  private Stmt returnStatement(EnumSetQueue<StatementContext> context) {
    Token keyword = previous();
    if (!context.contains(StatementContext.IN_FUNCTION)) {
      throw error(keyword, "May only use return inside of function");
    }
    Expr value = null;
    if (!check(SEMICOLON)) {
      value = expression(fromStatementContext(context));
    }

    if (value != null && context.containsAtHead(StatementContext.IN_INIT)) {
      throw error(keyword, "Cannot return value from init");
    }

    consume(SEMICOLON, "Expect ';' after return value.");
    return new Stmt.Return(keyword, value);
  }

  private Stmt expressionStatement(EnumSetQueue<StatementContext> context) {
    var exprContext = fromStatementContext(context);
    Expr expr = expression(exprContext);
    consume(SEMICOLON, "Expect ';' after expression.");
    if (expr instanceof Expr.Function) {
      throw error(previous(), "Function expression in statement position");
    }
    return new Stmt.Expression(expr);
  }

  private static EnumSetQueue<ExpressionContext> fromStatementContext(EnumSetQueue<StatementContext> context) {
    var exprContext = EnumSetQueue.empty(ExpressionContext.class);
    if (context.contains(StatementContext.IN_CLASS_DECLARATION)) {
      exprContext = EnumSetQueue.push(exprContext, ExpressionContext.IN_CLASS_DECLARATION);
    }
    return exprContext;
  }

  private Expr expression() {
    return assignment(EnumSetQueue.empty(ExpressionContext.class));
  }

  private Expr expression(EnumSetQueue<ExpressionContext> context) {
    return assignment(context);
  }

  private Expr assignment(EnumSetQueue<ExpressionContext> context) {
    var leftHandSide = or(context);
    if (match(EQUAL)) {
      var equals = previous();
      var rightHandSide = assignment(context);
      if (leftHandSide instanceof Expr.Variable(Token name)) {
        return new Expr.Assignment(name, rightHandSide);
      } else if (leftHandSide instanceof Expr.Select(Expr target, Token field)) {
        return new Expr.Update(target, field, rightHandSide);
      }
      throw error(equals, "Invalid assignment target");
    }
    return leftHandSide;
  }

  private Expr or(EnumSetQueue<ExpressionContext> context) {
    // If we had more of these it might make sense to duplicate the method or copy and paste the impl
    var e = binaryOp(this::and, context, OR);
    return e instanceof Expr.Binary b && b.operator().type().equals(OR) ? new Expr.Logical(b.left(), b.operator(), b.right()) : e;
  }

  private Expr and(EnumSetQueue<ExpressionContext> context) {
    var e = binaryOp(this::discardedExpression, context, AND);
    return e instanceof Expr.Binary b && b.operator().type().equals(AND) ? new Expr.Logical(b.left(), b.operator(), b.right()) : e;
  }

  private Expr discardedExpression(EnumSetQueue<ExpressionContext> context) {
    // We want to allow discards in groups in calls - e. g. `odd((but, why.not), indeed)`
    if (context.containsAtHead(ExpressionContext.IN_CALL)) {
      return ternary(context);
    }
    return binaryOp(this::ternary, context, COMMA);
  }

  private Expr ternary(EnumSetQueue<ExpressionContext> context) {
    if (match(QUESTION_MARK, ELVIS)) {
      // Error: Leading ternary operator
      var operator = previous();
      var _right = ternary(context); // Go ahead and attempt to recover
      throw error(operator, "Ternary operator missing test condition");
    } else if (match(COLON)) {
      // Error: Leading ternary operator
      var operator = previous();
      var _right = ternary(context); // Go ahead and attempt to recover
      throw error(operator, "Unexpected " + COLON + ". Are you missing a " + QUESTION_MARK + "?");
    }
    var expr = equality(context);
    while (match(QUESTION_MARK, ELVIS)) {
      var firstOp = previous();
      var left = ternary(context);
      if (firstOp.type() == ELVIS) {
        expr = new Expr.Binary(expr, firstOp, left);
      } else if (match(COLON)) {
        var secondOp = previous();
        var right = ternary(context);
        expr = new Expr.Trinary(expr, firstOp, left, secondOp, right);
      } else {
        throw error(previous(), "Expecting " + COLON + " following " + QUESTION_MARK);
      }
    }
    return expr;
  }

  private Expr equality(EnumSetQueue<ExpressionContext> context) {
    return binaryOp(this::comparison, context, BANG_EQUAL, EQUAL_EQUAL);
  }

  private Expr comparison(EnumSetQueue<ExpressionContext> context) {
    return binaryOp(this::term, context, GREATER_EQUAL, GREATER, LESS_EQUAL, LESS);
  }

  private Expr term(EnumSetQueue<ExpressionContext> context) {
    return binaryOp(this::factor, context, UNAMBIGUOUS_TERM_TOKENS, AMBIGUOUS_TERM_TOKENS);
  }

  private Expr factor(EnumSetQueue<ExpressionContext> context) {
    return binaryOp(this::unary, context, STAR, SLASH);
  }

  private Expr unary(EnumSetQueue<ExpressionContext> context) {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary(context);
      return new Expr.Unary(operator, right);
    }
    return coalesce(context);
  }

  private Expr coalesce(EnumSetQueue<ExpressionContext> context) {
    return binaryOp(this::call, context, COALESCE);
  }

  private Expr call(EnumSetQueue<ExpressionContext> context) {
    Expr expr = primary(context);

    while (true) {
      var args = arguments(this::expression, EnumSetQueue.push(context, ExpressionContext.IN_CALL));
      if (args != null) {
        expr = new Expr.Call(expr, previous(), args);
      } else if (match(DOT)) {
        var field = consume(IDENTIFIER, "Expected property name after '.'");
        expr = new Expr.Select(expr, field);
      } else {
        break;
      }
    }

    return expr;
  }

  /** Matches an argument list from the opening '(' to the closing ')'. Returns `null` on missing. */
  private <T> List<T> arguments(Function<EnumSetQueue<ExpressionContext>, T> op, EnumSetQueue<ExpressionContext> context) {
    if (!match(LEFT_PAREN)) {
      return null;
    }

    List<T> arguments = new ArrayList<>();
    if (!check(RIGHT_PAREN)) {
      do {
        if (arguments.size() >= 255) {
          // TODO: Re-add fail-parse-and-continue
          throw error(peek(), "Cannot have more than 255 arguments");
        }
        arguments.add(op.apply(context));
      } while (match(COMMA));
    }
    consume(RIGHT_PAREN, "Expect ')' after arguments.");
    return arguments;
  }

  private Expr primary(EnumSetQueue<ExpressionContext> context) {
    var token = advance();
    return switch (token.type()) {
      case TRUE -> new Expr.Literal(true);
      case FALSE -> new Expr.Literal(false);
      case NIL -> new Expr.Literal(null);
      case NUMBER, STRING -> new Expr.Literal(token.literal());
      case FUN -> function(context);
      case LEFT_PAREN -> {
        var group = expression(context);
        consume(RIGHT_PAREN, "Expect ')' after expression.");
        yield new Expr.Grouping(group);
      }
      case IDENTIFIER -> new Expr.Variable(token);
      case THIS -> {
        if (context.contains(ExpressionContext.IN_CLASS_DECLARATION)) yield new Expr.This(token);
        throw error(token, "'this' used outside of a class declaration");
      }
      case EOF -> throw error(token, "Unexpected end of file");
      default -> {
        throw error(token, "Unable to handle token of type " + token.type());
      }
    };
  }

  private Token identifier(EnumSetQueue<ExpressionContext> context) {
    var token = advance();
    if (token.type() != IDENTIFIER) {
      throw error(token, "Expect identifier");
    }
    return token;
  }

  private Expr function(EnumSetQueue<ExpressionContext> context) {
    var keyword = previous();
    var token = peek();
    Token name;
    boolean isAnonymous = true;
    if (token.type() == IDENTIFIER) {
      name = token;
      advance();
      token = peek();
      isAnonymous = false;
    } else {
      name = new Token(IDENTIFIER, "<anonymous>", null, keyword.line(), keyword.column());
    }
    if (!token.type().equals(LEFT_PAREN)) {
      throw error(token, "Expected '(' after function keyword for function expression.");
    }
    var args = arguments(this::identifier, context);
    consume(LEFT_BRACE, "Expect '{' after function header");
    var body = program(EnumSetQueue.push(StatementContext.IN_FUNCTION));
    consume(RIGHT_BRACE, "Expect '}' after function body");
    return new Expr.Function(keyword, name, args, body, isAnonymous);
  }

  /** Parse a left-associative binary operation */
  private Expr binaryOp(Function<EnumSetQueue<ExpressionContext>, Expr> target, EnumSetQueue<ExpressionContext> context, TokenType... opTokens) {
    return binaryOp(target, context, opTokens, EMPTY_TYPES);
  }

  /** Parse a left-associative binary operator where some of the operators are also unary operators */
  private Expr binaryOp(Function<EnumSetQueue<ExpressionContext>, Expr> target, EnumSetQueue<ExpressionContext> context, TokenType[] unambiguousTokens, TokenType[] ambiguousTokens) {
    if (match(unambiguousTokens)) {
      // Error: Leading binary operator
      var operator = previous();
      var _right = target.apply(context); // Go ahead and attempt to recover
      throw error(operator, "Binary operator missing left-hand side");
    }
    var expr = target.apply(context);
    while (match(unambiguousTokens) || match(ambiguousTokens)) {
      Token operator = previous();
      Expr right = target.apply(context);
      expr = new Expr.Binary(expr, operator, right);
    }
    return expr;
  }

  /**
   * <p>Advance the stream past the provided token(s) if they are of the provided types.</p>
   *
   * <p>Also skips past comments.</p>
  */
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

  /** Check the type of the next token without advancing the stream */
  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type() == type;
  }

  /** Advance to the next non-comment token */
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

  /** Assert the type of the current token and discard it if the type matches. */
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