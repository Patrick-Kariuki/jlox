package lox.parser;

import static lox.scanner.TokenType.*;

import java.util.List;
import lox.Lox;
import lox.ast.Expr;
import lox.scanner.Token;
import lox.scanner.TokenType;

/**
 * <p>
 *   A recursive descent parser that converts a flat list of tokens into a hierarchical
 *   abstract syntax tree representation for the Lox language. Implements grammar rules
 *   for expressions including arithmetic, comparisons, grouping, and literals.
 * </p>
 * Uses panic mode error recovery to handle syntax errors gracefully and continue parsing.
 */
public class Parser {
  // ParseError (sentinel) class to unwind the parser
  private static class ParseError extends RuntimeException {}

  private final List<Token> tokens;
  private int current = 0;  // next token waiting to be parsed

  /**
   * Parser constructor.
   *
   * @param tokens the list of tokens to parse
   */
  public Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  /**
   * Parses the tokens into an expression tree.
   *
   * @return the parsed expression, or null if a syntax error occurred
   */
  public Expr parse() {
    try {
      return expression();
    } catch (ParseError error) {
      return null;
    }
  }

  // expression     → equality ;
  private Expr expression() {
    return equality();
  }

  // equality       → comparison ( ( "!=" | "==" ) comparison )* ;
  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  private boolean check(TokenType type) {
    if (isAtEnd()) {
      return false;
    }
    return peek().type == type;
  }

  private Token advance() {
    if (!isAtEnd()) {
      current++;
    }
    return previous();
  }

  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  private Token peek() {
    return tokens.get(current);
  }

  private Token previous() {
    return tokens.get(current - 1);
  }

  // comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // term           → factor ( ( "-" | "+" ) factor )* ;
  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // factor         → unary ( ( "/" | "*" ) unary )* ;
  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  // unary          → ( "!" | "-" ) unary | primary ;
  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return primary();
  }

  // primary        → NUMBER | STRING | "true" | "false" | "nil" | "(" expression ")" ;
  private Expr primary() {
    if (match(FALSE)) {
      return new Expr.Literal(false);
    }
    if (match(TRUE)) {
      return new Expr.Literal(true);
    }
    if (match(NIL)) {
      return new Expr.Literal(null);
    }

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    // After matching '(', parse expression inside it and expect ')'.
    // If we don't, that's an error.
    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    throw error(peek(), "Expect expression."); // token cannot start expression
  }

  private Token consume(TokenType type, String message) {
    if (check(type)) {
      return advance();
    }

    throw error(peek(), message);
  }

  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) {
        return;
      }

      switch (peek().type) {
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
