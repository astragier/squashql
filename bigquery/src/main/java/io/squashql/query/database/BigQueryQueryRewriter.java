package io.squashql.query.database;

import io.squashql.query.BinaryOperator;
import io.squashql.query.date.DateFunctions;
import io.squashql.type.FunctionTypedField;
import io.squashql.type.TypedField;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class BigQueryQueryRewriter implements QueryRewriter {

  private final String projectId;
  private final String datasetName;

  @Override
  public String functionExpression(FunctionTypedField ftf) {
    if (DateFunctions.SUPPORTED_DATE_FUNCTIONS.contains(ftf.function())) {
      // https://cloud.google.com/bigquery/docs/reference/standard-sql/date_functions#extract
      return String.format("EXTRACT(%s FROM %s)", ftf.function(), ftf.field().sqlExpression(this));
    } else {
      throw new IllegalArgumentException("Unsupported function " + ftf);
    }
  }

  @Override
  public String fieldName(String field) {
    return SqlUtils.backtickEscape(field);
  }

  @Override
  public String tableName(String table) {
    return SqlUtils.backtickEscape(this.projectId + "." + this.datasetName + "." + table);
  }

  /**
   * See <a href="https://cloud.google.com/bigquery/docs/schemas#column_names">https://cloud.google.com/bigquery/docs/schemas#column_names</a>.
   * A column name must contain only letters (a-z, A-Z), numbers (0-9), or underscores (_), and it must start with a
   * letter or underscore. The maximum column name length is 300 characters.
   * FIXME must used a regex instead to replace incorrect characters.
   */
  @Override
  public String escapeAlias(String alias) {
    return SqlUtils.backtickEscape(alias)
            .replace("(", "_")
            .replace(")", "_")
            .replace(" ", "_");
  }

  @Override
  public boolean usePartialRollupSyntax() {
    // Not supported https://issuetracker.google.com/issues/35905909
    return false;
  }

  @Override
  public String escapeSingleQuote(String s) {
    return SqlUtils.escapeSingleQuote(s, "\\'");
  }

  @Override
  public String grouping(TypedField f) {
    // BQ does not support using the alias
    return _select(f, false);
  }

  @Override
  public String binaryOperation(BinaryOperator operator, String leftOperand, String rightOperand) {
    return switch (operator) {
      case PLUS, MINUS, MULTIPLY -> QueryRewriter.super.binaryOperation(operator, leftOperand, rightOperand);
      // https://cloud.google.com/bigquery/docs/reference/standard-sql/functions-and-operators#safe_divide
      case DIVIDE -> new StringBuilder()
              .append("SAFE_DIVIDE")
              .append("(")
              .append(leftOperand)
              .append(", ")
              .append(rightOperand)
              .append(")")
              .toString();
    };
  }
}
