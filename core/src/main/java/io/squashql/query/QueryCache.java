package io.squashql.query;

import io.squashql.query.compiled.CompiledMeasure;
import io.squashql.query.dto.CacheStatsDto;
import io.squashql.table.ColumnarTable;
import io.squashql.table.Table;

import java.util.Set;

public interface QueryCache {

  ColumnarTable createRawResult(QueryCacheKey scope);

  boolean contains(CompiledMeasure measure, QueryCacheKey scope);

  void contributeToCache(Table result, Set<CompiledMeasure> measures, QueryCacheKey scope);

  void contributeToResult(Table result, Set<CompiledMeasure> measures, QueryCacheKey scope);

  /**
   * Invalidates the cache associated to the given user.
   *
   * @param user the user identifier
   */
  void clear(SquashQLUser user);

  /**
   * Invalidate the whole cache.
   */
  void clear();

  CacheStatsDto stats(SquashQLUser user);

  record QueryCacheKey(QueryExecutor.QueryScope scope, SquashQLUser user) {
  }
}
