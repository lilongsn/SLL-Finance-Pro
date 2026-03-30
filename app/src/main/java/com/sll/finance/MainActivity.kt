package com.sll.finance

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// --- 1. 数据模型 ---
data class Transaction(
    val id: Long = System.currentTimeMillis() + (0..9999).random(),
    val amount: Double,
    val note: String,
    val type: String,
    val owner: String,
    val category: String,  // 作为手动选择的标签名
    val color: Color,
    val date: LocalDate = LocalDate.now(),
    val isDeleted: Boolean = false // 是否已移入回收站
)

object ThemeColors {
    val Food = Color(0xFFFF9800); val Trans = Color(0xFF2196F3); val Shop = Color(0xFFE91E63)
    val Fun = Color(0xFF9C27B0); val Health = Color(0xFF4CAF50); val Life = Color(0xFF00BCD4)
    val IncomeGreen = Color(0xFF4CAF50) // 收入绿
    val ExpenseRed = Color(0xFFF44336)  // 支出红

    // 为自定义标签生成随机但固定的颜色
    private val TagPalette = listOf(
        Color(0xFFFF9800), Color(0xFF2196F3), Color(0xFFE91E63),
        Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFF009688),
        Color(0xFF8BC34A), Color(0xFFFFC107), Color(0xFF795548)
    )

    fun getColorForTag(tag: String): Color {
        return TagPalette[kotlin.math.abs(tag.hashCode()) % TagPalette.size]
    }
}

// --- 2. 仓库逻辑 ---
class FinanceRepo(context: Context) {
    private val prefs = context.getSharedPreferences("sll_pro_v9_stats", Context.MODE_PRIVATE)

    fun saveLogin(u: String) = prefs.edit().putString("LAST_USER", u).apply()
    fun getAutoLoginUser() = prefs.getString("LAST_USER", null)
    fun clearLogin() = prefs.edit().remove("LAST_USER").apply()
    fun saveUser(n: String, p: String) = prefs.edit().putString("USER_PWD_$n", p).apply()
    fun checkUser(n: String, p: String) = (n == "admin" && p == "123456") || prefs.getString("USER_PWD_$n", null) == p

    fun saveTransactions(list: List<Transaction>) {
        val data = list.joinToString("###") { "${it.id}|${it.amount}|${it.note}|${it.type}|${it.owner}|${it.category}|${it.color.toArgb()}|${it.date}|${it.isDeleted}" }
        prefs.edit().putString("ALL_TRANS", data).apply()
    }

    fun loadTransactions(): List<Transaction> {
        val data = prefs.getString("ALL_TRANS", "") ?: ""
        if (data.isEmpty()) return emptyList()
        return try {
            data.split("###").map {
                val p = it.split("|")
                val isDel = if (p.size > 8) p[8].toBoolean() else false
                Transaction(p[0].toLong(), p[1].toDouble(), p[2], p[3], p[4], p[5], Color(p[6].toInt()), LocalDate.parse(p[7]), isDel)
            }
        } catch (e: Exception) { emptyList() }
    }

    fun saveTags(u: String, list: List<String>) {
        prefs.edit().putString("TAGS_$u", list.joinToString(",")).apply()
    }

    fun loadTags(u: String): List<String> {
        val str = prefs.getString("TAGS_$u", "") ?: ""
        if (str.isEmpty()) return listOf("餐饮", "交通", "购物", "居家", "娱乐", "工资")
        return str.split(",")
    }
}

// --- 3. 业务状态 ---
class FinanceState(val repo: FinanceRepo) {
    val allTransactions = mutableStateListOf<Transaction>()
    var currentUser by mutableStateOf<String?>(null)
    val userTags = mutableStateListOf<String>()

    init {
        allTransactions.addAll(repo.loadTransactions())
        currentUser = repo.getAutoLoginUser()
        if (currentUser != null) {
            userTags.addAll(repo.loadTags(currentUser!!))
        }
    }

    fun addNewTag(tag: String) {
        val u = currentUser ?: return
        if (tag.isNotBlank() && !userTags.contains(tag)) {
            userTags.add(tag)
            repo.saveTags(u, userTags)
        }
    }

    fun add(amt: Double, note: String, type: String, category: String, date: LocalDate) {
        val u = currentUser ?: return
        val col = ThemeColors.getColorForTag(category)
        allTransactions.add(0, Transaction(amount = amt, note = note, type = type, owner = u, category = category, color = col, date = date))
        repo.saveTransactions(allTransactions)
    }

    fun update(id: Long, newAmt: Double, newNote: String, newCat: String, newDate: LocalDate) {
        val idx = allTransactions.indexOfFirst { it.id == id }
        if (idx != -1) {
            val old = allTransactions[idx]
            val col = ThemeColors.getColorForTag(newCat)
            allTransactions[idx] = old.copy(amount = newAmt, note = newNote, category = newCat, color = col, date = newDate)
            repo.saveTransactions(allTransactions)
        }
    }

    fun softDelete(id: Long) {
        val idx = allTransactions.indexOfFirst { it.id == id }
        if (idx != -1) {
            allTransactions[idx] = allTransactions[idx].copy(isDeleted = true)
            repo.saveTransactions(allTransactions)
        }
    }

    fun restore(id: Long) {
        val idx = allTransactions.indexOfFirst { it.id == id }
        if (idx != -1) {
            allTransactions[idx] = allTransactions[idx].copy(isDeleted = false)
            repo.saveTransactions(allTransactions)
        }
    }

    fun hardDelete(id: Long) {
        allTransactions.removeAll { it.id == id }
        repo.saveTransactions(allTransactions)
    }

    fun login(u: String, p: String): Boolean {
        if (repo.checkUser(u, p)) {
            currentUser = u; repo.saveLogin(u)
            userTags.clear()
            userTags.addAll(repo.loadTags(u))
            if (u == "admin" && allTransactions.none { it.owner == "admin" }) injectData()
            return true
        }
        return false
    }

    private fun injectData() {
        val items = listOf("午餐" to "支出" to "餐饮", "打车" to "支出" to "交通", "买衣服" to "支出" to "购物", "发工资" to "收入" to "工资", "兼职" to "收入" to "工资")
        repeat(50) { i ->
            val triple = items[i % items.size]
            val year = if (i % 2 == 0) 2026 else 2025
            val day = LocalDate.of(year, (i % 12) + 1, (i % 28) + 1)
            // 收入数额稍微大一点，保证有盈余的月份
            val amt = if(triple.first.second == "收入") (2000..8000).random().toDouble() else (10..300).random().toDouble()
            add(amt, triple.first.first, triple.first.second, triple.second, day)
        }
    }
}

// --- 4. 界面视图 ---
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainView(state: FinanceState, onLogout: () -> Unit) {
    var showRecycleBin by remember { mutableStateOf(false) }

    if (showRecycleBin) {
        RecycleBinScreen(state, onClose = { showRecycleBin = false })
        return
    }

    // 🌟 1. 新增了“统计” Tab
    val tabs = listOf("支出", "收入", "统计")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<Transaction?>(null) }
    var isChartExpanded by remember { mutableStateOf(false) }
    var filterDate by remember { mutableStateOf<LocalDate?>(null) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("SLL 记账 Pro", fontWeight = FontWeight.Black) },
                navigationIcon = {
                    IconButton(onClick = { showRecycleBin = true }) { Icon(Icons.Default.Delete, "回收站") }
                },
                actions = {
                    IconButton(onClick = onLogout) { Icon(Icons.Default.ExitToApp, "退出") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Color.Black) { Icon(Icons.Default.Add, null, tint = Color.White) }
        }
    ) { p ->
        Column(Modifier.padding(p).fillMaxSize()) {
            TabRow(pagerState.currentPage, containerColor = Color.White, contentColor = Color.Black) {
                tabs.forEachIndexed { i, t -> Tab(pagerState.currentPage == i, { scope.launch { pagerState.animateScrollToPage(i) } }, text = { Text(t) }) }
            }

            HorizontalPager(state = pagerState) { pageIdx ->
                // 🌟 2. 独立处理第三个 Tab “统计”
                if (pageIdx == 2) {
                    StatisticsScreen(state)
                } else {
                    // 原有的支出/收入页面逻辑
                    val type = tabs[pageIdx]
                    val filtered = state.allTransactions
                        .filter { it.owner == state.currentUser && it.type == type && !it.isDeleted && (filterDate == null || it.date == filterDate) }
                        .sortedByDescending { it.date }

                    val groupedData = filtered.groupBy { "${it.date.year}年 ${it.date.monthValue}月" }
                    val totalAmount = filtered.sumOf { it.amount }
                    val amountColor = if (type == "支出") ThemeColors.ExpenseRed else ThemeColors.IncomeGreen

                    Column(Modifier.fillMaxSize()) {
                        // 顶部总额卡片
                        Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "累计${type}", fontSize = 14.sp, color = Color.Gray)
                                Spacer(Modifier.height(8.dp))
                                Text(text = "¥${"%.2f".format(totalAmount)}", fontSize = 42.sp, fontWeight = FontWeight.Black, color = amountColor)
                            }
                        }

                        // 日期筛选条
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(onClick = { filterDate = null }, color = if(filterDate == null) Color.Black else Color(0xFFE0E0E0), shape = CircleShape, modifier = Modifier.height(36.dp)) {
                                Box(Modifier.padding(horizontal = 16.dp), Alignment.Center) { Text("全部记录", color = if(filterDate == null) Color.White else Color.Gray, fontSize = 12.sp) }
                            }
                            Spacer(Modifier.width(12.dp))
                            Surface(
                                onClick = {
                                    val now = LocalDate.now()
                                    DatePickerDialog(context, { _, y, m, d -> filterDate = LocalDate.of(y, m + 1, d) }, now.year, now.monthValue - 1, now.dayOfMonth).show()
                                },
                                color = if(filterDate != null) Color.Black else Color(0xFFE0E0E0), shape = CircleShape, modifier = Modifier.height(36.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.DateRange, null, Modifier.size(16.dp), tint = if(filterDate != null) Color.White else Color.Gray)
                                    Text(filterDate?.toString() ?: "按日期筛选", color = if(filterDate != null) Color.White else Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { isChartExpanded = !isChartExpanded }) { Text(if(isChartExpanded) "收起图表" else "饼状图", color = Color.Gray) }
                        }

                        // 饼图
                        AnimatedVisibility(isChartExpanded) {
                            if (filtered.isNotEmpty()) {
                                Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(16.dp)) {
                                    Column(Modifier.padding(16.dp)) { PieChart(filtered) }
                                }
                            }
                        }

                        // 列表展示
                        if (filtered.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, null, Modifier.size(48.dp), tint = Color(0xFFE0E0E0))
                                    Text("没有找到相关记录", color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
                                }
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                                groupedData.forEach { (monthLabel, monthItems) ->
                                    stickyHeader {
                                        Row(Modifier.fillMaxWidth().background(Color(0xFFF7F8FA)).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(monthLabel, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 13.sp)
                                            Text("小计: ¥${"%.2f".format(monthItems.sumOf { it.amount })}", color = amountColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    items(monthItems) { item -> TransactionRow(item) { editingItem = item } }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 添加和编辑弹窗
        val defaultAddType = if (pagerState.currentPage < 2) tabs[pagerState.currentPage] else "支出"
        if (showAdd) TransactionDialog("新增$defaultAddType", null, state, { showAdd = false }) { a, n, c, d -> state.add(a,n,defaultAddType, c, d); showAdd = false }
        if (editingItem != null) {
            TransactionDialog("编辑记录", editingItem, state, { editingItem = null },
                onDelete = { state.softDelete(editingItem!!.id); editingItem = null },
                onConfirm = { a, n, c, d -> state.update(editingItem!!.id, a, n, c, d); editingItem = null }
            )
        }
    }
}

// --- 🌟 5. 全新宏观统计大盘 (Statistics Screen) ---
@Composable
fun StatisticsScreen(state: FinanceState) {
    var timeMode by remember { mutableStateOf("月度") } // "年度" or "月度"

    // 获取有效账单并按日期倒序
    val validTransactions = state.allTransactions
        .filter { it.owner == state.currentUser && !it.isDeleted }
        .sortedByDescending { it.date }

    // 动态分组核心逻辑
    val groupedData = remember(validTransactions, timeMode) {
        if (timeMode == "年度") {
            validTransactions.groupBy { "${it.date.year}年度" }
        } else {
            // 补齐两位数月份，保证排序和视觉美观
            validTransactions.groupBy { "${it.date.year}年 ${String.format("%02d", it.date.monthValue)}月" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 月度/年度 切换开关
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            Surface(
                onClick = { timeMode = "月度" },
                color = if (timeMode == "月度") Color.Black else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                modifier = Modifier.height(36.dp).weight(1f)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("按月统计", color = if (timeMode == "月度") Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
            }
            Surface(
                onClick = { timeMode = "年度" },
                color = if (timeMode == "年度") Color.Black else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier.height(36.dp).weight(1f)
            ) {
                Box(contentAlignment = Alignment.Center) { Text("按年统计", color = if (timeMode == "年度") Color.White else Color.Gray, fontWeight = FontWeight.Bold) }
            }
        }

        if (groupedData.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无数据可统计", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
                groupedData.forEach { (period, items) ->
                    item {
                        StatSummaryCard(period, items)
                    }
                }
            }
        }
    }
}

@Composable
fun StatSummaryCard(period: String, items: List<Transaction>) {
    val income = items.filter { it.type == "收入" }.sumOf { it.amount }
    val expense = items.filter { it.type == "支出" }.sumOf { it.amount }
    val balance = income - expense

    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(period, fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.DarkGray)
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Color(0xFFF0F0F0))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("总收入", fontSize = 12.sp, color = Color.Gray)
                    Text("+¥${"%.2f".format(income)}", fontWeight = FontWeight.Bold, color = ThemeColors.IncomeGreen, fontSize = 18.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("总支出", fontSize = 12.sp, color = Color.Gray)
                    Text("-¥${"%.2f".format(expense)}", fontWeight = FontWeight.Bold, color = ThemeColors.ExpenseRed, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                color = Color(0xFFF7F8FA),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("净结余", fontWeight = FontWeight.Bold, color = Color.DarkGray)
                    val balanceColor = if (balance > 0) ThemeColors.IncomeGreen else if (balance < 0) ThemeColors.ExpenseRed else Color.Black
                    val prefix = if (balance > 0) "+" else ""
                    Text("$prefix¥${"%.2f".format(balance)}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = balanceColor)
                }
            }
        }
    }
}

// --- 6. 弹窗修改：加入日历和手动选标签 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDialog(
    title: String,
    item: Transaction?,
    state: FinanceState,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onConfirm: (Double, String, String, LocalDate) -> Unit
) {
    var a by remember { mutableStateOf(item?.amount?.toString() ?: "") }
    var n by remember { mutableStateOf(item?.note ?: "") }
    var cat by remember { mutableStateOf(item?.category ?: "") }
    var d by remember { mutableStateOf(item?.date ?: LocalDate.now()) }
    var newTag by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (item != null && onDelete != null) {
                    TextButton(onClick = onDelete) { Text("移至回收站", color = Color.Red) }
                } else { Spacer(Modifier.width(1.dp)) }

                Button(onClick = {
                    val v = a.toDoubleOrNull()
                    if(v != null && cat.isNotBlank()) onConfirm(v, n, cat, d)
                    else Toast.makeText(ctx, "请填写金额并选择一个标签！", Toast.LENGTH_SHORT).show()
                }, colors = ButtonDefaults.buttonColors(Color.Black)) { Text("保存") }
            }
        },
        title = { Text(title, fontWeight = FontWeight.ExtraBold) },
        text = {
            Column {
                // 日期选择按钮
                Surface(onClick = {
                    DatePickerDialog(ctx, { _, y, m, day -> d = LocalDate.of(y, m + 1, day) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                }, color = Color(0xFFF0F0F0), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DateRange, null, tint = Color.DarkGray)
                        Spacer(Modifier.width(8.dp))
                        Text(d.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                OutlinedTextField(a, {a=it}, label = {Text("金额")}, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(n, {n=it}, label = {Text("备注详情 (选填)")}, modifier = Modifier.fillMaxWidth())

                // 标签选择区域
                Text("选择标签 (必选)", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.userTags) { tag ->
                        FilterChip(selected = cat == tag, onClick = { cat = tag }, label = { Text(tag) })
                    }
                }

                // 新建标签区域
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(newTag, {newTag=it}, label = {Text("新建标签")}, modifier = Modifier.weight(1f).height(55.dp))
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if(newTag.isNotBlank()) { state.addNewTag(newTag); cat = newTag; newTag = "" } }, modifier = Modifier.height(55.dp), colors = ButtonDefaults.buttonColors(Color.DarkGray)) { Text("添加") }
                }
            }
        }
    )
}

// --- 7. 回收站页面 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(state: FinanceState, onClose: () -> Unit) {
    val deletedItems = state.allTransactions.filter { it.owner == state.currentUser && it.isDeleted }.sortedByDescending { it.date }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("回收站", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "返回") } }
            )
        }
    ) { p ->
        if (deletedItems.isEmpty()) {
            Box(Modifier.padding(p).fillMaxSize(), contentAlignment = Alignment.Center) { Text("回收站空空如也", color = Color.Gray) }
        } else {
            LazyColumn(Modifier.padding(p).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(deletedItems) { item ->
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF0F0)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${item.category} (${item.type})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("${item.date} · ¥${item.amount}", fontSize = 13.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { state.restore(item.id) }) { Icon(Icons.Default.Refresh, "恢复", tint = Color(0xFF4CAF50)) }
                            IconButton(onClick = { state.hardDelete(item.id) }) { Icon(Icons.Default.Delete, "彻底删除", tint = Color.Red) }
                        }
                    }
                }
            }
        }
    }
}

// --- 基础组件 ---
@Composable
fun TransactionRow(item: Transaction, onClick: () -> Unit) {
    val amountColor = if (item.type == "支出") ThemeColors.ExpenseRed else ThemeColors.IncomeGreen
    val symbol = if (item.type == "支出") "-" else "+"
    Surface(Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp), color = Color.White) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(item.color.copy(alpha = 0.1f), CircleShape), contentAlignment = Alignment.Center) {
                Text(item.category.take(1), color = item.color, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if(item.note.isEmpty()) item.category else item.note, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${item.date} · 标签: ${item.category}", fontSize = 11.sp, color = Color.Gray)
            }
            Text(text = "$symbol¥${item.amount}", fontSize = 18.sp, fontWeight = FontWeight.Black, color = amountColor)
        }
    }
}

@Composable
fun PieChart(data: List<Transaction>) {
    val groups = data.groupBy { it.category }.mapValues { it.value.sumOf { t -> t.amount } }
    val total = groups.values.sum().coerceAtLeast(1.0)
    val colors = data.distinctBy { it.category }.associate { it.category to it.color }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(100.dp)) {
            var s = -90f
            groups.forEach { (cat, amt) ->
                val sweep = (amt / total * 360f).toFloat()
                drawArc(colors[cat] ?: Color.Gray, s, sweep, false, style = Stroke(30f))
                s += sweep
            }
        }
        Spacer(Modifier.width(24.dp))
        Column {
            groups.keys.take(4).forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(colors[cat] ?: Color.Gray, CircleShape))
                    Text(" $cat ${(groups[cat]!!/total*100).toInt()}%", fontSize = 11.sp)
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = FinanceState(FinanceRepo(this))
        setContent {
            var screen by remember { mutableStateOf(if (state.currentUser != null) "main" else "login") }
            MaterialTheme(colorScheme = lightColorScheme(primary = Color.Black)) {
                Surface(color = Color(0xFFF7F8FA)) {
                    Crossfade(screen, label = "") { s ->
                        when (s) {
                            "login" -> LoginView(state, { screen = "main" }, { screen = "reg" })
                            "reg" -> RegisterView(state) { screen = "login" }
                            "main" -> MainView(state) { state.currentUser = null; state.repo.clearLogin(); screen = "login" }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginView(state: FinanceState, onOk: () -> Unit, onReg: () -> Unit) {
    var u by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center) {
        Text("SLL 记账 Pro", fontSize = 42.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(40.dp)); OutlinedTextField(u, {u=it}, label = {Text("账号")}, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(p, {p=it}, label = {Text("密码")}, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(30.dp))
        Button({ if(state.login(u,p)) onOk() else Toast.makeText(ctx,"身份验证失败",Toast.LENGTH_SHORT).show() }, Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(Color.Black)) { Text("登录") }
        TextButton(onReg, Modifier.align(Alignment.CenterHorizontally)) { Text("还没账号？立即注册") }
    }
}

@Composable
fun RegisterView(state: FinanceState, onBack: () -> Unit) {
    var u by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(40.dp), verticalArrangement = Arrangement.Center) {
        Text("注册账户", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp)); OutlinedTextField(u, {u=it}, label = {Text("用户名")}, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(p, {p=it}, label = {Text("密码")}, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(30.dp)); Button({ if(u.isNotBlank()){ state.repo.saveUser(u,p); onBack() } }, Modifier.fillMaxWidth().height(55.dp), colors = ButtonDefaults.buttonColors(Color.Black)) { Text("确认注册") }
    }
}