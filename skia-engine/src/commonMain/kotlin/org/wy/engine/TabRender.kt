package org.wy.engine

/** 显示层 tab 展开工具：集中在段落构建与索引映射入口，各节点复用。 */
internal object TabRender {
    /** 单个 tab 展开的等效空格数（固定 4）。 */
    const val SPACES_PER_TAB = 4

    /** 显示层 tab 展开：逐字符累加。 */
    fun expandTabs(text: String): String = text.replace("\t", " ".repeat(SPACES_PER_TAB))

    /** 逻辑索引 → 显示索引：每个 '\t' 贡献 [SPACES_PER_TAB] 个显示位，其余 1 个。 */
    fun logicToDisplay(text: String, logicPos: Int): Int {
        val limit = logicPos.coerceIn(0, text.length)
        var display = 0
        for (i in 0 until limit) {
            display += if (text[i] == '\t') SPACES_PER_TAB else 1
        }
        return display
    }

    /** 显示索引 → 逻辑索引：反向扫描；落在展开区间中间时吸附到 tab 之后。 */
    fun displayToLogic(text: String, displayPos: Int): Int {
        val len = text.length
        var logic = 0
        var disp = 0
        while (logic < len && disp < displayPos) {
            disp += if (text[logic] == '\t') SPACES_PER_TAB else 1
            logic++
        }
        return logic
    }
}
