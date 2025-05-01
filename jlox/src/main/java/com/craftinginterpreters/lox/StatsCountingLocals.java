package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.TreeMap;

final class StatsCountingLocals extends HashMap<Token, Resolver.Coordinates> {
  private int lookups;
  private int misses;
  private int hits;
  private int writes;

//  StatsCountingLocals() {
//    super((x, y) -> {
//      var delta = x.line() - y.line();
//      return delta == 0 ? x.column() - y.column() : delta;
//    });
//  }

  @Override
  public Resolver.Coordinates get(Object key) {
    var result = super.get(key);
    lookups++;
    if (result == null) {
      misses++;
    } else {
      hits++;
    }
    return result;
  }

  @Override
  public Resolver.Coordinates put(Token key, Resolver.Coordinates value) {
    writes++;
    if (containsKey(key) && !get(key).equals(value)) {
      throw new IllegalArgumentException(
                  "Duplicate key: " + key +
                  " existing value: " + get(key) +
                  " new value: " + value);
    }
    return super.put(key, value);
  }

  String asString() {
    return String.format("""
            Local variable resolution table:
              Writes: %d
              Lookups: %d
              Misses: %d
              Hits: %d
            """, writes, lookups, misses, hits);
  }
}
