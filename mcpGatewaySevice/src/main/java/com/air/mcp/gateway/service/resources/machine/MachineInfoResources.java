package com.air.mcp.gateway.service.resources.machine;

import com.air.mcp.gateway.annotation.meta.CustomerMcpResourceMeta;
import com.air.mcp.gateway.annotation.permission.RequireMcpResourcePermission;
import com.air.mcp.gateway.mapper.TableSchemaMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @McpResource中的uri必须全局唯一
 * MCP 协议中设计 Resource（资源）的初衷，可以用一句话概括：为 AI 大模型提供一个安全、只读的“数据字典”或“文件系统”，让它在思考和行动之前，能够先充分了解当前系统的背景上下文。
 */
@Service
@RequiredArgsConstructor
public class MachineInfoResources {

    private final TableSchemaMapper tableSchemaMapper;
    private final JsonMapper jsonMapper;

    @McpResource(uri = "db://identity/machine/schema", name = "read_machine_table_schema_info", description = "获取数据苦衷有关于机器的表结构信息")
    @CustomerMcpResourceMeta(domainCode= "120000000",sensitivityType = "read",elementCode = "db://identity/machine/schema")
    @RequireMcpResourcePermission(resourceCode = "db://identity/machine/schema",resourceName = "readMachineTableSchemaInfo")
    public McpSchema.ReadResourceResult readMachineTableSchemaInfo(McpTransportContext ctx) {
        //这里先模拟
        String tablePrefix = "machine_";
        String uri = "db://identity/machine/schema"; // 必须与注解中的 uri 保持一致
        Boolean choiceJsonStructFlag = true;

        // 从数据库查出列信息
        List<Map<String, Object>> allColumns  = tableSchemaMapper.getAllTablesColumnsByTablePrefix(tablePrefix);
        if (allColumns.isEmpty()) {
            return buildResult(uri, "[application/json] or [text/x-sql]", "未找到以 " + tablePrefix + " 开头的表。");
        }

        if (choiceJsonStructFlag) {
            String jsonText = getJsonDDlInfo(allColumns,this.jsonMapper);
            return buildResult(uri, "application/json", jsonText);
        }

        StringBuilder allDdlString = getBigDDlStringInfo(allColumns, tablePrefix);
        // 返回统一的大字符串
        return buildResult(uri, "text/x-sql", allDdlString.toString());
    }

    private static @NonNull String getJsonDDlInfo(List<Map<String, Object>> allColumns,JsonMapper jsonMapper) {
        // 按表名进行分组
        Map<String, List<Map<String, Object>>> groupedByTable = allColumns.stream()
                .collect(Collectors.groupingBy(
                        col -> (String) col.get("table_name"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(groupedByTable);
    }

    private static @NonNull StringBuilder getBigDDlStringInfo(List<Map<String, Object>> allColumns, String tablePrefix) {
        // 按表名进行分组（保持 LinkedHashMap 顺序，因为 SQL 里已经 ORDER BY table_name 了）
        Map<String, List<Map<String, Object>>> groupedByTable = allColumns.stream()
                .collect(Collectors.groupingBy(
                        col -> (String) col.get("table_name"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        // 3. 循环拼装每一张表的 DDL
        StringBuilder allDdl = new StringBuilder();
        splicingDDLStringInfo(tablePrefix,allDdl, groupedByTable);
        return allDdl;
    }

    private static void splicingDDLStringInfo(String tablePrefix,StringBuilder allDdl, Map<String, List<Map<String, Object>>> groupedByTable) {
        allDdl.append("-- ==========================================\n")
                .append("-- 以"+tablePrefix+"为前缀的所有表结构汇总\n")
                .append("-- ==========================================\n\n");
        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedByTable.entrySet()) {
            String tableName = entry.getKey();
            List<Map<String, Object>> columns = entry.getValue();

            allDdl.append("CREATE TABLE public.").append(tableName).append(" (\n");

            for (int i = 0; i < columns.size(); i++) {
                Map<String, Object> col = columns.get(i);
                String colName = (String) col.get("column_name");
                String dataType = (String) col.get("data_type");
                Object maxLength = col.get("character_maximum_length");
                String isNullable = (String) col.get("is_nullable");

                allDdl.append("  ").append(colName).append(" ").append(dataType);

                if (maxLength != null && Integer.parseInt(maxLength.toString()) > 0) {
                    allDdl.append("(").append(maxLength).append(")");
                }
                if ("NO".equals(isNullable)) {
                    allDdl.append(" NOT NULL");
                }
                if (i < columns.size() - 1) {
                    allDdl.append(",\n");
                }
            }
            // 每张表结束加个空行，方便大模型区分
            allDdl.append("\n);\n\n");
        }
    }

    /**
     * 辅助方法：快速构建 ReadResourceResult
     */
    private McpSchema.ReadResourceResult buildResult(String uri, String mimeType, String text) {
        McpSchema.TextResourceContents contents = new McpSchema.TextResourceContents(uri, mimeType, text);
        return new McpSchema.ReadResourceResult(List.of(contents));
    }
}
