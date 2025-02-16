package io.squashql.query;

import io.squashql.TestClass;
import io.squashql.query.builder.Query;
import io.squashql.query.dto.*;
import io.squashql.table.Table;
import io.squashql.type.TableTypedField;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.squashql.query.Functions.*;
import static io.squashql.query.database.QueryEngine.GRAND_TOTAL;
import static io.squashql.query.database.QueryEngine.TOTAL;

@TestClass(ignore = TestClass.Type.CLICKHOUSE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ATestBucketing extends ABaseTestQuery {

  private final String storeName = "store" + getClass().getSimpleName().toLowerCase();
  private final TableField ean = new TableField(this.storeName, "ean");
  private final TableField shop = new TableField(this.storeName, "shop");
  private final TableField unitPrice = new TableField(this.storeName, "unitPrice");
  private final TableField qtySold = new TableField(this.storeName, "qtySold");
  private final TableField kvi = new TableField(this.storeName, "kvi");

  private final String sensitivitiesStoreName = "sensitivities";
  private final TableField bucket = new TableField(this.sensitivitiesStoreName, "bucket");
  private final TableField min = new TableField(this.sensitivitiesStoreName, "min");
  private final TableField max = new TableField(this.sensitivitiesStoreName, "max");

  private final String expensivenessStoreName = "expensiveness";
  private final TableField category = new TableField(this.expensivenessStoreName, "category");
  private final TableField minPrice = new TableField(this.expensivenessStoreName, "minPrice");
  private final TableField maxPrice = new TableField(this.expensivenessStoreName, "maxPrice");
  private final Measure sales = Functions.sum("sales", Functions.multiply(this.qtySold, this.unitPrice));
  private final VirtualTableDto sensitivities = new VirtualTableDto(
          this.sensitivitiesStoreName,
          List.of(this.bucket.fieldName, this.min.fieldName, this.max.fieldName),
          List.of(
                  List.of("unsensitive", 0d, 50d),
                  List.of("sensitive", 50d, 80d),
                  List.of("hypersensitive", 80d, 101d)
          ));
  private final VirtualTableDto expensiveness = new VirtualTableDto(
          this.expensivenessStoreName,
          List.of(this.category.fieldName, this.minPrice.fieldName, this.maxPrice.fieldName),
          List.of(
                  List.of("cheap", 0d, 7d),
                  List.of("expensive", 7d, 11d)
          ));

  @Override
  protected Map<String, List<TableTypedField>> getFieldsByStore() {
    TableTypedField ean = new TableTypedField(this.storeName, "ean", int.class);
    TableTypedField shop = new TableTypedField(this.storeName, "shop", String.class);
    TableTypedField unitPrice = new TableTypedField(this.storeName, "unitPrice", double.class);
    TableTypedField qtySold = new TableTypedField(this.storeName, "qtySold", int.class);
    TableTypedField kvi = new TableTypedField(this.storeName, "kvi", double.class);
    return Map.of(this.storeName, List.of(ean, shop, unitPrice, qtySold, kvi));
  }

  @Override
  protected void loadData() {
    List<Object[]> tuples = new ArrayList<>();
    for (int shop = 0; shop < 2; shop++) {
      for (int ean = 0; ean < 10; ean++) {
        tuples.add(new Object[]{ean, String.valueOf(shop), (double) ean, 10, (ean + 1) * 10d});
      }
    }
    this.tm.load(this.storeName, tuples);
  }

  @Test
  void testSingleVirtualTable() {
    CriteriaDto criteria = all(
            criterion(this.kvi, this.min, ConditionType.GE),
            criterion(this.kvi, this.max, ConditionType.LT));
    var query = Query
            .from(this.storeName)
            .join(this.sensitivities, JoinType.INNER)
            .on(criteria)
            .select(List.of(this.shop, this.bucket), List.of(this.sales))
            .build();

    Table result = this.executor.executeQuery(query);
    Assertions.assertThat(result.headers().stream().map(Header::name))
            .containsExactly(this.shop.name(), this.bucket.name(), this.sales.alias());
    Assertions.assertThat(result).containsExactly(
            List.of("0", "hypersensitive", 240d),
            List.of("0", "sensitive", 150d),
            List.of("0", "unsensitive", 60d),
            List.of("1", "hypersensitive", 240d),
            List.of("1", "sensitive", 150d),
            List.of("1", "unsensitive", 60d));

    query = Query
            .from(this.storeName)
            .join(this.sensitivities, JoinType.INNER)
            .on(criteria)
            .select(List.of(this.shop, this.bucket), List.of(this.sales))
            .rollup(this.shop, this.bucket)
            .build();
    result = this.executor.executeQuery(query);
    Assertions.assertThat(result.headers().stream().map(Header::name))
            .containsExactly(this.shop.name(), this.bucket.name(), this.sales.alias());
    Assertions.assertThat(result).containsExactly(
            List.of(GRAND_TOTAL, GRAND_TOTAL, 900d),
            List.of("0", TOTAL, 450d),
            List.of("0", "hypersensitive", 240d),
            List.of("0", "sensitive", 150d),
            List.of("0", "unsensitive", 60d),
            List.of("1", TOTAL, 450d),
            List.of("1", "hypersensitive", 240d),
            List.of("1", "sensitive", 150d),
            List.of("1", "unsensitive", 60d));
  }

  @Test
  void testConditionFieldCombined() {
    CriteriaDto criteria = all(
            criterion(minus(this.kvi, this.min), ge(0)),
            criterion(minus(this.kvi, this.max), lt(0)));
    var query = Query
            .from(this.storeName)
            .join(this.sensitivities, JoinType.INNER)
            .on(criteria)
            .select(List.of(this.shop, this.bucket), List.of(this.sales))
            .build();

    Table result = this.executor.executeQuery(query);
    Assertions.assertThat(result.headers().stream().map(Header::name))
            .containsExactly(this.shop.name(), this.bucket.name(), this.sales.alias());
    Assertions.assertThat(result).containsExactly(
            List.of("0", "hypersensitive", 240d),
            List.of("0", "sensitive", 150d),
            List.of("0", "unsensitive", 60d),
            List.of("1", "hypersensitive", 240d),
            List.of("1", "sensitive", 150d),
            List.of("1", "unsensitive", 60d));
  }

  @Test
  void testMultipleVirtualTables() {
    CriteriaDto criteriaSensi = all(
            criterion(this.kvi, this.min, ConditionType.GE),
            criterion(this.kvi, this.max, ConditionType.LT));
    CriteriaDto criteriaExp = all(
            criterion(this.unitPrice, this.minPrice, ConditionType.GE),
            criterion(this.unitPrice, this.maxPrice, ConditionType.LT));
    var query = Query
            .from(this.storeName)
            .join(this.sensitivities, JoinType.INNER)
            .on(criteriaSensi)
            .join(this.expensiveness, JoinType.INNER)
            .on(criteriaExp)
            .select(List.of(this.bucket, this.category), List.of(CountMeasure.INSTANCE))
            .build();

    Table result = this.executor.executeQuery(query);
    Assertions.assertThat(result.headers().stream().map(Header::name))
            .containsExactly(this.bucket.name(), this.category.name(), CountMeasure.INSTANCE.alias());
    Assertions.assertThat(result).containsExactly(
            List.of("hypersensitive", "expensive", 6L),
            List.of("sensitive", "cheap", 6L),
            List.of("unsensitive", "cheap", 8L));
  }
}
