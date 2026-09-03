package vn.rikkei.exam.equipmentloan.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ToolExecutionContext {
    private static final ThreadLocal<List<String>> TOOLS_USED = ThreadLocal.withInitial(ArrayList::new);

    public static void recordTool(String toolName) {
        if (!TOOLS_USED.get().contains(toolName)) {
            TOOLS_USED.get().add(toolName);
        }
    }

    public static List<String> getToolsUsed() {
        return new ArrayList<>(TOOLS_USED.get());
    }

    public static void clear() {
        TOOLS_USED.get().clear();
    }
}
