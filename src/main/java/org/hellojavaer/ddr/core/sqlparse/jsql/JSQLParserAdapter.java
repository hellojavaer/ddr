/*
 * Copyright 2016-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hellojavaer.ddr.core.sqlparse.jsql;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.*;
import net.sf.jsqlparser.expression.operators.relational.*;
import net.sf.jsqlparser.parser.CCJSqlParser;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SubSelect;
import net.sf.jsqlparser.statement.update.Update;
import net.sf.jsqlparser.util.JSQLBaseVisitor;
import org.hellojavaer.ddr.core.exception.DDRException;
import org.hellojavaer.ddr.core.sharding.ShardingInfo;
import org.hellojavaer.ddr.core.sharding.ShardingRouteParamContext;
import org.hellojavaer.ddr.core.sharding.ShardingRouter;

import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 *
 * @author <a href="mailto:hellojavaer@gmail.com">zoukaiming[邹凯明]</a>,created on 12/11/2016.
 */
public class JSQLParserAdapter extends JSQLBaseVisitor {

    private String               sql;
    private Map<Integer, Object> jdbcParam;
    private ShardingRouter       shardingRouter;
    private Statement            statement;

    public JSQLParserAdapter(String sql, Map<Integer, Object> jdbcParam, ShardingRouter shardingRouter) {
        this.sql = sql;
        this.jdbcParam = jdbcParam;
        this.shardingRouter = shardingRouter;
        try {
            CCJSqlParser parser = new CCJSqlParser(new StringReader(sql));
            try {
                statement = parser.Statement();
            } catch (Exception ex) {
                throw new JSQLParserException(ex);
            }
        } catch (JSQLParserException e) {
            throw new DDRException(e);
        }
    }

    public String parse() {
        if (statement instanceof Select) {
            parse((Select) statement);
        } else if (statement instanceof Update) {
            parse((Update) statement);
        } else if (statement instanceof Insert) {
            parse((Insert) statement);
        } else if (statement instanceof Delete) {
            parse((Delete) statement);
        } else {
            throw new DDRException("sql[" + sql
                                   + "] is not supported. Only support select, insert, update or delete statement");
        }
        return statement.toString();

    }

    private void after() {
        StackContext context = this.getContext().getStack().pop();
        for (Map.Entry<String, TableWapper> entry : context.entrySet()) {
            TableWapper tableWapper = entry.getValue();
            if (!entry.getValue().isConverted()) {
                ShardingInfo info = this.shardingRouter.route(this.getContext().getTableRouterContext(),
                                                              tableWapper.getScName(), tableWapper.getTbName(), null);
                tableWapper.getTable().setSchemaName(info.getScName());
                tableWapper.getTable().setName(info.getTbName());
                tableWapper.setConverted(true);
            }
        }
    }

    @Override
    public void visit(JdbcParameter jdbcParameter) {
        // jdbcParameter.setIndex(jdbcParameterIndex);//DEBUG
        // jdbcParameterIndex++;
    }

    // === 一级语句
    @Override
    public void visit(Insert insert) {
        this.getContext().getStack().push(new StackContext());
        if (this.shardingRouter.isRoute(this.getContext().getTableRouterContext(), insert.getTable().getSchemaName(),
                                        insert.getTable().getName())) {
            putTableToContext(insert.getTable(), false);
            List<Column> columns = insert.getColumns();
            if (columns != null) {
                ExpressionList expressionList = (ExpressionList) insert.getItemsList();
                List<Expression> valueList = expressionList.getExpressions();
                for (int i = 0; i < columns.size(); i++) {
                    Column column = columns.get(i);
                    TableWapper tableWapper = getTableFromContext(column);
                    if (tableWapper != null) {
                        Expression expression = valueList.get(i);
                        routeTable(tableWapper, expression);
                    }
                }
            }
        }
        super.visit(insert);
        after();
    }

    @Override
    public void visit(Delete delete) {
        this.getContext().getStack().push(new StackContext());
        if (this.shardingRouter.isRoute(this.getContext().getTableRouterContext(), delete.getTable().getSchemaName(),
                                        delete.getTable().getName())) {
            putTableToContext(delete.getTable(), false);
        }
        super.visit(delete);
        after();
    }

    @Override
    public void visit(Update update) {
        this.getContext().getStack().push(new StackContext());
        super.visit(update);
        for (Table table : update.getTables()) {
            if (this.shardingRouter.isRoute(this.getContext().getTableRouterContext(), table.getSchemaName(),
                                            table.getName())) {
                putTableToContext(table, true);
                List<Column> columns = update.getColumns();
                if (columns != null) {
                    int count = -1;
                    for (Column col : columns) {
                        count++;
                        TableWapper tableWapper = getTableFromContext(col);
                        if (tableWapper != null) {
                            List<Expression> expressions = update.getExpressions();
                            routeTable(tableWapper, expressions.get(count));
                        }
                    }
                }
            }
        }
        after();
    }

    @Override
    public void visit(Select select) {
        this.getContext().getStack().push(new StackContext());
        super.visit(select);
        after();
    }

    // ===
    @Override
    public void visit(SubSelect subSelect) {
        this.getContext().getStack().push(new StackContext());
        subSelect.getSelectBody().accept(this);
        StackContext context = this.getContext().getStack().pop();
        for (Map.Entry<String, TableWapper> entry : context.entrySet()) {
            TableWapper tableWapper = entry.getValue();
            if (!entry.getValue().isConverted()) {
                ShardingInfo info = shardingRouter.route(this.getContext().getTableRouterContext(),
                                                         tableWapper.getScName(), tableWapper.getTbName(), null);
                tableWapper.getTable().setSchemaName(info.getScName());
                tableWapper.getTable().setName(info.getTbName());
                tableWapper.setConverted(true);
            }
        }
        // super.visit(subSelect);////TOCHECK
    }

    // not supprot == begin==
    @Override
    public void visit(IsNullExpression isNullExpression) {
        if (getTableFromContext((Column) isNullExpression.getLeftExpression()) != null) {
            throw new DDRException("IsNullExpression for sharding colum[" + ""
                                   + "] is not supproted, only support '=', 'in' and 'between and' operation");
        }
        super.visit(isNullExpression);
    }

    @Override
    public void visit(GreaterThan greaterThan) {
        if (getTableFromContext((Column) greaterThan.getLeftExpression()) != null) {
            // TODO
            throw null;
        }
        super.visit(greaterThan);
    }

    @Override
    public void visit(GreaterThanEquals greaterThanEquals) {
        if (getTableFromContext((Column) greaterThanEquals.getLeftExpression()) != null) {
            // TODO
            throw null;
        }
        super.visit(greaterThanEquals);
    }

    @Override
    public void visit(MinorThan minorThan) {
        if (getTableFromContext((Column) minorThan.getLeftExpression()) != null) {
            // TODO
            throw null;
        }
        super.visit(minorThan);
    }

    @Override
    public void visit(MinorThanEquals minorThanEquals) {
        if (getTableFromContext((Column) minorThanEquals.getLeftExpression()) != null) {
            // TODO
            throw null;
        }
        super.visit(minorThanEquals);
    }

    @Override
    public void visit(NotEqualsTo notEqualsTo) {
        if (getTableFromContext((Column) notEqualsTo.getLeftExpression()) != null) {
            // TODO
            throw null;
        }
        super.visit(notEqualsTo);
    }

    @Override
    public void visit(LikeExpression likeExpression) {
        if (getTableFromContext((Column) likeExpression.getLeftExpression()) != null) {
            // TODO
            throw null;
        }
        super.visit(likeExpression);
    }

    // not supprot == end ==
    private TableWapper getTableFromContext(Column col) {
        ConverterContext converterContext = this.getContext();
        StackContext stackContext = converterContext.getStack().peek();
        String colFullName = col.toString();
        colFullName = colFullName.trim().toLowerCase();
        return stackContext.get(colFullName);
    }

    private void putTableToContext(Table table) {
        putTableToContext(table, true);
    }

    private void putTableToContext(Table table, boolean appendAlias) {
        ConverterContext converterContext = this.getContext();
        StackContext stackContext = converterContext.getStack().peek();
        String tbName = table.getName();
        String columnName = shardingRouter.getRouteColName(converterContext.getTableRouterContext(),
                                                           table.getSchemaName(), tbName);
        String tbAliasName = tbName;
        if (table.getAlias() != null && table.getAlias().getName() != null) {
            tbAliasName = table.getAlias().getName();
        } else {
            if (appendAlias) {
                table.setAlias(new Alias(tbName, true));
            }
        }
        if (table.getSchemaName() != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(table.getSchemaName().trim().toLowerCase());
            sb.append('.');
            sb.append(tbAliasName.trim().toLowerCase());
            sb.append('.');
            sb.append(columnName.trim().toLowerCase());
            String key = sb.toString();
            TableWapper tableWapper = new TableWapper(table);
            putIntoContext(stackContext, key, tableWapper);
            putIntoContext(stackContext, key.substring(table.getSchemaName().length() + 1), tableWapper);
            putIntoContext(stackContext, key.substring(table.getSchemaName().length() + 2 + tbAliasName.length()),
                           tableWapper);
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(tbAliasName.trim().toLowerCase());
            sb.append('.');
            sb.append(columnName.trim().toLowerCase());
            String key = sb.toString();
            TableWapper tableWapper = new TableWapper(table);
            putIntoContext(stackContext, key, tableWapper);
            putIntoContext(stackContext, key.substring(tbAliasName.length() + 1), tableWapper);
        }
    }

    private Long getLongValue(Expression obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LongValue) {
            return ((LongValue) obj).getValue();
        } else if (obj instanceof StringValue) {
            return Long.valueOf(((StringValue) obj).getValue());
        } else if (obj instanceof HexValue) {
            return Long.parseLong(((HexValue) obj).getValue(), 16);
        } else if (obj instanceof JdbcParameter) {
            JdbcParameter jdbcParameterObj = (JdbcParameter) obj;
            if (jdbcParam == null) {
                return null;
            } else {
                Object val = jdbcParam.get(jdbcParameterObj.getIndex());
                if (val == null) {
                    return null;
                } else {
                    if (val instanceof Byte) {
                        return ((Byte) val).longValue();
                    } else if (val instanceof Short) {
                        return ((Short) val).longValue();
                    } else if (val instanceof Integer) {
                        return ((Integer) val).longValue();
                    } else if (val instanceof Long) {
                        return ((Long) val).longValue();
                    } else if (val instanceof String) {
                        return Long.valueOf((String) val);
                    } else {
                        // TODO
                        throw null;
                    }
                }
            }
        } else {
            // TODO
            throw null;
        }
    }

    // support
    @Override
    public void visit(InExpression inExpression) {
        TableWapper tableWapper = getTableFromContext((Column) inExpression.getLeftExpression());
        if (tableWapper == null) {
            super.visit(inExpression);
            return;
        }
        if (inExpression.isNot()) {
            throw new DDRException("sharding cloumn['" + inExpression.getLeftExpression()
                                   + "'] not support not operation");
        }
        // 普通in模式
        ItemsList itemsList0 = inExpression.getRightItemsList();
        if (itemsList0 instanceof ExpressionList) {
            ExpressionList itemsList = (ExpressionList) itemsList0;
            List<Expression> list = itemsList.getExpressions();
            if (list == null || list.isEmpty()) {
                throw new DDRException("sharding cloumn['" + inExpression.getLeftExpression()
                                       + "'] in operation must have more than one items");
            }
            for (Expression exp : list) {
                routeTable(tableWapper, exp);
            }
        } else {
            throw new DDRException("");
        }
    }

    @Override
    public void visit(Between between) {
        TableWapper tableWapper = getTableFromContext((Column) between.getLeftExpression());
        if (tableWapper == null) {
            super.visit(between);
            return;
        }
        if (between.isNot()) {
            throw new DDRException("sharding cloumn['" + between.getLeftExpression() + "'] not support not operation");
        }
        Expression begin = between.getBetweenExpressionStart();
        Expression end = between.getBetweenExpressionEnd();
        long s = getLongValue(begin);
        long e = getLongValue(end);
        for (long s0 = s; s0 <= e; s0++) {
            routeTable(tableWapper, s0);
        }
    }

    @Override
    public void visit(EqualsTo equalsTo) {
        Column column = (Column) equalsTo.getLeftExpression();
        String fullColumnName = column.toString();
        TableWapper tableWapper = (TableWapper) this.getContext().getStack().peek().get(fullColumnName.trim().toLowerCase());
        if (tableWapper == null) {// 不需要要路由的表
            super.visit(equalsTo);
            return;
        } else {
            routeTable(tableWapper, equalsTo.getRightExpression());
        }
    }

    private void routeTable(TableWapper tableWapper, Expression routeValueExpression) {
        Long val = getLongValue(routeValueExpression);//
        routeTable(tableWapper, val);
    }

    private void routeTable(TableWapper tableWapper, Long val) {
        if (tableWapper == null) {//
            return;
        }
        if (tableWapper == AMBIGUOUS_TABLE) {
            throw new RuntimeException("Column '" + null + "' in where clause is ambiguous");
        }
        ShardingInfo info = shardingRouter.route(this.getContext().getTableRouterContext(), tableWapper.getScName(),
                                                 tableWapper.getTbName(), val);
        if (tableWapper.isConverted()) {
            if (!equalsIgnoreCase(info.getScName(), tableWapper.getTable().getSchemaName())
                || !equalsIgnoreCase(info.getTbName(), tableWapper.getTable().getName())) {
                throw new RuntimeException("多重路由");// 多重路由
            }
        } else {
            tableWapper.getTable().setSchemaName(info.getScName());
            tableWapper.getTable().setName(info.getTbName());
            tableWapper.setConverted(true);
        }
    }

    private boolean equalsIgnoreCase(String str0, String str1) {
        if (str0 == null && str1 == null) {
            return true;
        } else if (str0 != null) {
            return str0.equals(str1);
        } else {
            return str1.equals(str0);
        }
    }

    @Override
    public void visit(Table table) {
        ConverterContext converterContext = this.getContext();
        String tbName = table.getName();
        if (!shardingRouter.isRoute(converterContext.getTableRouterContext(), table.getSchemaName(), tbName)) {
            return;
        }
        putTableToContext(table);
    }

    private void putIntoContext(StackContext stackContext, String key, TableWapper tableWapper) {
        TableWapper tableWapper0 = (TableWapper) stackContext.get(key);
        if (tableWapper0 == null) {
            stackContext.put(key, tableWapper);
        } else {
            stackContext.put(key, AMBIGUOUS_TABLE);
        }
    }

    private static final TableWapper             AMBIGUOUS_TABLE = new TableWapper(null, true);

    private static ThreadLocal<ConverterContext> context         = new ThreadLocal<ConverterContext>();

    private class StackContext extends HashMap<String, TableWapper> {
    }

    private static class TableWapper {

        private boolean converted;
        private String  scName;
        private String  tbName;
        private Table   table;

        public TableWapper(Table table) {
            this.converted = false;
            this.table = table;
            this.scName = table.getSchemaName();
            this.tbName = table.getName();
        }

        public TableWapper(Table table, boolean converted) {
            this.converted = converted;
            this.table = table;
        }

        public String getScName() {
            return scName;
        }

        public void setScName(String scName) {
            this.scName = scName;
        }

        public String getTbName() {
            return tbName;
        }

        public void setTbName(String tbName) {
            this.tbName = tbName;
        }

        public Table getTable() {
            return table;
        }

        public void setTable(Table table) {
            this.table = table;
        }

        public boolean isConverted() {
            return converted;
        }

        public void setConverted(boolean converted) {
            this.converted = converted;
        }
    }

    private class ConverterContext {

        private Stack<StackContext>       stack              = null;
        private ShardingRouteParamContext tableRouterContext = null;

        private class TableRouterContextImpl extends HashMap<String, Object> implements ShardingRouteParamContext {
        }

        public ConverterContext() {
            this.stack = new Stack<StackContext>();
            this.tableRouterContext = new TableRouterContextImpl();
        }

        public ConverterContext(Stack<StackContext> stack, ShardingRouteParamContext tableRouterContext) {
            this.stack = stack;
            this.tableRouterContext = tableRouterContext;
        }

        public Stack<StackContext> getStack() {
            return stack;
        }

        public ShardingRouteParamContext getTableRouterContext() {
            return tableRouterContext;
        }
    }

    private void parse(Insert insert) {
        try {
            ConverterContext converterContext = new ConverterContext();
            context.set(converterContext);
            insert.accept(this);
        } finally {
            context.remove();
        }
    }

    private void parse(Delete delete) {
        try {
            ConverterContext converterContext = new ConverterContext();
            context.set(converterContext);
            delete.accept(this);
        } finally {
            context.remove();
        }
    }

    private void parse(Update update) {
        try {
            ConverterContext converterContext = new ConverterContext();
            context.set(converterContext);
            update.accept(this);
        } finally {
            context.remove();
        }
    }

    private void parse(Select select) {
        try {
            ConverterContext converterContext = new ConverterContext();
            context.set(converterContext);
            select.accept(this);
        } finally {
            context.remove();
        }
    }

    private ConverterContext getContext() {
        return context.get();
    }

}