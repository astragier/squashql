package io.squashql.query.database;

class DuckDBQueryRewriter implements QueryRewriter {

  private final DatabaseQuery query;

  DuckDBQueryRewriter(DatabaseQuery query) {
    this.query = query;
  }

  @Override
  public DatabaseQuery query() {
    return this.query;
  }

  @Override
  public String tableName(String table) {
    return SqlUtils.doubleQuoteEscape(table);
  }

  @Override
  public String cteName(String cteName) {
    return SqlUtils.doubleQuoteEscape(cteName);
  }

  @Override
  public String fieldName(String field) {
    return SqlUtils.doubleQuoteEscape(field);
  }

  @Override
  public String escapeAlias(String alias) {
    return SqlUtils.doubleQuoteEscape(alias);
  }

  @Override
  public boolean usePartialRollupSyntax() {
    return true;
  }
}
