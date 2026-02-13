package taboolib.module.ui

import org.bukkit.entity.HumanEntity
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryAction.*
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import taboolib.module.ui.ClickType.*
import taboolib.platform.util.giveItem
import taboolib.platform.util.isAir
import java.util.WeakHashMap

/** 副手快捷键编号（F键） */
private const val OFFHAND_HOTBAR_BUTTON = -1

/** 记录本次 ClickEvent 内各槽位的限量配置（用于 Shift+点击时统一处理） */
private val amountConditionLimits = WeakHashMap<ClickEvent, MutableMap<Int, Int>>()
/** 记录本次 ClickEvent 内各槽位的 condition 规则（用于 Shift+点击时统一处理） */
private val conditionSlotRules = WeakHashMap<ClickEvent, MutableMap<Int, ConditionRule>>()
private val lockSlotRules = WeakHashMap<ClickEvent, MutableList<LockRule>>()
/** 暂存 Shift+点击需要移动的物品信息（等待收集完限量/条件配置） */
private val pendingShiftMove = WeakHashMap<ClickEvent, PendingShiftMove>()
/** 限量放入的处理结果 */
private enum class LimitedPlaceResult { CONTINUE, BLOCKED, PARTIAL }

/**
 * 在页面关闭时返还物品
 */
fun InventoryCloseEvent.returnItems(slots: List<Int>) = slots.forEach { slot ->
    val item = inventory.getItem(slot)
    if (item != null && !item.isAir) {
        player.giveItem(item)
    }
}

/**
 * 创建点击事件条件格
 *
 * 用于创造物品的放入和取出条件
 *
 * 注意：如需拦截 Shift+点击的条件判定，请在 onClick 的最后调用
 * [applyAmountConditionShiftIfNeeded] 以执行实际移动。
 *
 * @param rawSlot 原始格子
 * @param condition 条件
 * @param failedCallback 条件检测失败后执行回调
 * */
fun ClickEvent.conditionSlot(rawSlot: Int, condition: (put: ItemStack?, out: ItemStack?) -> Boolean, failedCallback: () -> Unit = {}): Boolean {
    conditionSlotRules.getOrPut(this) { mutableMapOf() }[rawSlot] = ConditionRule(condition, failedCallback)
    if (isCancelled) return false
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                SWAP_WITH_CURSOR, PICKUP_ALL, PLACE_ALL -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.cursor
                        val out = event.clickedInventory?.getItem(event.slot)
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_HALF -> {
                    if (rawSlot == event.rawSlot) {
                        val put = null
                        val old = event.clickedInventory?.getItem(event.slot)
                        // 右键取出向上取整的一半
                        val out = pickupHalfOut(old)
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val put = null
                        val out = pickupOneOut(event.clickedInventory?.getItem(event.slot))
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_SOME -> {
                    // 暂时不清楚原理
                    if (rawSlot == event.rawSlot) {
                        event.isCancelled = true
                        failedCallback()
                        return false
                    }
                }
                PLACE_SOME -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        val cursor = event.cursor
                        // 放入的数量是 cursor 数量和剩余空间的较小值
                        val put = placeSomePut(old, cursor)
                        val out = null
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PLACE_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val put = placeOnePut(event.cursor)
                        val out = null
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                MOVE_TO_OTHER_INVENTORY -> {
                    // 玩家在下方背包 Shift+点击，物品会尝试移入上方容器
                    if (event.rawSlot >= event.view.topInventory.size) {
                        // 点击的是下方背包，需要判断物品会移入哪个槽位
                        val clickedItem = event.currentItem
                        if (clickedItem != null && !clickedItem.isAir) {
                            if (pendingShiftMove[this] == null) {
                                pendingShiftMove[this] = PendingShiftMove(
                                    who = event.whoClicked,
                                    topInv = event.view.topInventory,
                                    sourceInv = event.clickedInventory,
                                    sourceSlot = event.slot,
                                    item = clickedItem.clone(),
                                )
                            }
                        }
                    } else if (rawSlot == event.rawSlot) {
                        // 点击的是上方容器的目标槽位，物品移出
                        val out = event.currentItem
                        if (!condition(null, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                COLLECT_TO_CURSOR -> {
                    if (event.cursor?.isSimilar(event.view.getItem(rawSlot)) == true) {
                        val put = null
                        val cursor = event.cursor!!
                        val slotItem = event.view.getItem(rawSlot)
                        // 计算实际会收集的数量
                        val out = collectToCursorOut(cursor, slotItem)
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    if (rawSlot == event.rawSlot) {
                        val playerInv = event.whoClicked.inventory as PlayerInventory
                        // 获取快捷栏或副手物品（F键的 hotbarButton 为 -1）
                        val put = if (event.hotbarButton == OFFHAND_HOTBAR_BUTTON) {
                            playerInv.itemInOffHand
                        } else {
                            playerInv.getItem(event.hotbarButton)
                        }
                        val out = event.currentItem
                        if (!condition(put, out)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                else -> {
                    if (rawSlot == event.rawSlot) {
                        event.isCancelled = true
                        failedCallback()
                        return false
                    }
                }
            }
        }
        DRAG -> {
            val event = dragEvent()
            if (rawSlot in event.rawSlots) {
                val put = event.newItems[rawSlot]
                if (!condition(put, null)) {
                    event.isCancelled = true
                    failedCallback()
                    return false
                }
            }
        }
        VIRTUAL -> {}
    }
    return true
}

/**
 * 限制槽位最大物品堆叠数量
 *
 * 注意：如需拦截 Shift+点击的限量移动，请在 onClick 的最后调用
 * [applyAmountConditionShiftIfNeeded] 以执行实际移动。
 * */
fun ClickEvent.amountCondition(rawSlot: Int, amount: Int, failedCallback: () -> Unit = {}): Boolean {
    amountConditionLimits.getOrPut(this) { mutableMapOf() }[rawSlot] = amount
    if (isCancelled) return false
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                SWAP_WITH_CURSOR -> {
                    if (rawSlot == event.rawSlot) {
                        if (shouldBlockSwapWithCursor(event.cursor, event.clickedInventory?.getItem(event.slot), amount)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PLACE_ALL, PLACE_SOME -> {
                    if (rawSlot == event.rawSlot) {
                        when (handleLimitedPlace(event, amount, failedCallback)) {
                            LimitedPlaceResult.BLOCKED -> return false
                            LimitedPlaceResult.PARTIAL -> return true
                            LimitedPlaceResult.CONTINUE -> {}
                        }
                    }
                }
                PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, COLLECT_TO_CURSOR -> {}
                MOVE_TO_OTHER_INVENTORY -> {
                    // Shift+点击从下方背包移入上方容器
                    if (event.rawSlot >= event.view.topInventory.size) {
                        val clickedItem = event.currentItem
                        if (clickedItem != null && !clickedItem.isAir) {
                            if (pendingShiftMove[this] == null) {
                                pendingShiftMove[this] = PendingShiftMove(
                                    who = event.whoClicked,
                                    topInv = event.view.topInventory,
                                    sourceInv = event.clickedInventory,
                                    sourceSlot = event.slot,
                                    item = clickedItem.clone(),
                                )
                            }
                        }
                    }
                }
                PLACE_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        val oldAmount = old?.amount ?: 0
                        if (oldAmount + 1 > minOf(amount, old?.maxStackSize ?: amount)) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    if (rawSlot == event.rawSlot) {
                        val playerInv = event.whoClicked.inventory as PlayerInventory
                        // F键的 hotbarButton 为 -1
                        val put = if (event.hotbarButton == OFFHAND_HOTBAR_BUTTON) {
                            playerInv.itemInOffHand
                        } else {
                            playerInv.getItem(event.hotbarButton)
                        }
                        if ((put?.amount ?: 0) > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                else -> {
                    if (rawSlot == event.rawSlot) {
                        event.isCancelled = true
                        failedCallback()
                        return false
                    }
                }
            }
        }
        DRAG -> {
            val event = dragEvent()
            if (rawSlot in event.rawSlots) {
                if (dragWouldExceedLimit(event.view.getItem(rawSlot), event.newItems[rawSlot], amount)) {
                    event.isCancelled = true
                    failedCallback()
                    return false
                }
            }
        }
        VIRTUAL -> {}
    }
    return true
}

private data class PendingShiftMove(
    val who: HumanEntity,
    val topInv: Inventory,
    val sourceInv: Inventory?,
    val sourceSlot: Int,
    val item: ItemStack,
)

private data class ConditionRule(
    val condition: (put: ItemStack?, out: ItemStack?) -> Boolean,
    val failedCallback: () -> Unit,
)

private data class LockRule(
    val rawSlots: Set<Int>,
    val reverse: Boolean,
)

/**
 * 处理本次 ClickEvent 中的 Shift+点击（需要在所有 conditionSlot/amountCondition 调用之后执行）。
 * 未触发 Shift+点击时不会有任何副作用。
 */
fun ClickEvent.applyAmountConditionShiftIfNeeded() {
    val pending = pendingShiftMove.remove(this) ?: run {
        amountConditionLimits.remove(this)
        conditionSlotRules.remove(this)
        lockSlotRules.remove(this)
        return
    }
    val limits = amountConditionLimits.remove(this) ?: mutableMapOf()
    val rules = conditionSlotRules.remove(this) ?: mutableMapOf()
    val locks = lockSlotRules.remove(this) ?: mutableListOf()

    val event = clickEvent()
    if (event.isCancelled) {
        return
    }
    event.isCancelled = true
    applyShiftMoveWithConstraints(pending.who, pending.topInv, pending.sourceInv, pending.sourceSlot, pending.item, limits, rules, locks)
}

/** SWAP_WITH_CURSOR 时，交换上来的物品是否超出限制 */
private fun shouldBlockSwapWithCursor(cursor: ItemStack?, old: ItemStack?, amount: Int): Boolean {
    if (cursor == null || cursor.isAir || old == null || old.isAir) return false
    if (old.isSimilar(cursor)) return false
    return cursor.amount > amount
}

/** 右键取出向上取整的一半 */
private fun pickupHalfOut(old: ItemStack?): ItemStack? {
    return old?.clone()?.apply { amount = (old.amount + 1) / 2 }
}

/** 右键取出 1 个 */
private fun pickupOneOut(old: ItemStack?): ItemStack? {
    return old?.clone()?.apply { amount = 1 }
}

/** PLACE_SOME 实际放入数量（cursor 与剩余空间取较小值） */
private fun placeSomePut(old: ItemStack?, cursor: ItemStack?): ItemStack? {
    val putAmount = if (old != null && cursor != null) {
        minOf(cursor.amount, old.maxStackSize - old.amount)
    } else 0
    return cursor?.clone()?.apply { amount = putAmount }
}

/** PLACE_ONE 实际放入数量（固定 1 个） */
private fun placeOnePut(cursor: ItemStack?): ItemStack? {
    return cursor?.clone()?.apply { amount = 1 }
}

/** 双击收集时实际取出的数量 */
private fun collectToCursorOut(cursor: ItemStack, slotItem: ItemStack?): ItemStack? {
    val collectAmount = minOf(cursor.maxStackSize - cursor.amount, slotItem?.amount ?: 0)
    return slotItem?.clone()?.apply { amount = collectAmount }
}

/**
 * PLACE_ALL/PLACE_SOME 的限量处理。
 * - 允许全部放入：CONTINUE
 * - 不允许放入：BLOCKED（取消事件并回调）
 * - 只允许部分放入：PARTIAL（取消事件并手动改动光标/槽位）
 */
private fun handleLimitedPlace(
    event: InventoryClickEvent,
    amount: Int,
    failedCallback: () -> Unit,
): LimitedPlaceResult {
    val cursor = event.cursor ?: return LimitedPlaceResult.CONTINUE
    if (cursor.isAir) return LimitedPlaceResult.CONTINUE

    val old = event.clickedInventory?.getItem(event.slot)
    if (old != null && !old.isAir && !old.isSimilar(cursor)) {
        return LimitedPlaceResult.CONTINUE
    }

    val oldAmount = old?.amount ?: 0
    val limit = minOf(amount, cursor.maxStackSize)
    val desiredAdd = minOf(cursor.amount, cursor.maxStackSize - oldAmount)
    val allowedAdd = (limit - oldAmount).coerceAtLeast(0)

    if (allowedAdd <= 0) {
        event.isCancelled = true
        failedCallback()
        return LimitedPlaceResult.BLOCKED
    }

    if (desiredAdd > allowedAdd) {
        event.isCancelled = true
        val newItem = (old?.clone() ?: cursor.clone()).apply { this.amount = oldAmount + allowedAdd }
        event.clickedInventory?.setItem(event.slot, newItem)
        val remaining = cursor.amount - allowedAdd
        val newCursor = if (remaining > 0) cursor.clone().apply { this.amount = remaining } else null
        event.whoClicked.setItemOnCursor(newCursor)
        return LimitedPlaceResult.PARTIAL
    }

    return LimitedPlaceResult.CONTINUE
}

/** 拖拽时计算实际放入后的数量是否超过限制 */
private fun dragWouldExceedLimit(old: ItemStack?, put: ItemStack?, amount: Int): Boolean {
    val newAmount = if (old != null && !old.isAir && put != null && put.isSimilar(old)) {
        old.amount + put.amount
    } else {
        put?.amount ?: 0
    }
    return newAmount > amount
}

/**
 * 计算 Shift+点击的实际填充计划（按 Minecraft 顺序）。
 *
 * 返回值为 (slot, moveAmount) 列表，顺序即填充顺序。
 */
private fun computeShiftMovePlan(
    topInv: Inventory,
    clickedItem: ItemStack,
    slotLimit: (slot: Int, current: ItemStack?) -> Int,
    slotAllows: (slot: Int, current: ItemStack?, moveAmount: Int) -> Boolean = { _, _, _ -> true },
): List<Pair<Int, Int>> {
    val plan = mutableListOf<Pair<Int, Int>>()
    var remaining = clickedItem.amount
    val topSize = topInv.size

    // 先填充已有相同物品的槽位（按槽位顺序）
    for (slot in 0 until topSize) {
        if (remaining <= 0) break
        val item = topInv.getItem(slot)
        if (item != null && !item.isAir && item.isSimilar(clickedItem)) {
            val cap = slotLimit(slot, item) - item.amount
            if (cap > 0) {
                val move = minOf(remaining, cap)
                if (slotAllows(slot, item, move)) {
                    plan.add(slot to move)
                    remaining -= move
                }
            }
        }
    }

    // 再填充空槽位（按槽位顺序）
    for (slot in 0 until topSize) {
        if (remaining <= 0) break
        val item = topInv.getItem(slot)
        if (item == null || item.isAir) {
            val cap = slotLimit(slot, null)
            if (cap > 0) {
                val move = minOf(remaining, cap)
                if (slotAllows(slot, null, move)) {
                    plan.add(slot to move)
                    remaining -= move
                }
            }
        }
    }

    return plan
}

/**
 * 使用限量与条件规则执行 Shift+点击的物品移动（只影响 topInv）。
 *
 * sourceInv/sourceSlot 会被更新为剩余物品。
 */
private fun applyShiftMoveWithConstraints(
    who: HumanEntity,
    topInv: Inventory,
    sourceInv: Inventory?,
    sourceSlot: Int,
    clickedItem: ItemStack,
    slotLimits: Map<Int, Int>,
    conditionRules: Map<Int, ConditionRule>,
    lockRules: List<LockRule>,
) {
    if (sourceInv == null || clickedItem.isAir) {
        return
    }

    var failedCallback: (() -> Unit)? = null
    val plan = computeShiftMovePlan(
        topInv,
        clickedItem,
        { slot, current ->
            val limit = slotLimits[slot] ?: clickedItem.maxStackSize
            val maxStack = current?.maxStackSize ?: clickedItem.maxStackSize
            minOf(limit, maxStack)
        },
        { slot, _, moveAmount ->
            val allowedByLocks = lockRules.all { rule ->
                if (rule.reverse) {
                    slot in rule.rawSlots
                } else {
                    slot !in rule.rawSlots
                }
            }
            if (!allowedByLocks) {
                return@computeShiftMovePlan false
            }

            val rule = conditionRules[slot] ?: return@computeShiftMovePlan true
            val putItem = clickedItem.clone().apply { amount = moveAmount }
            val allow = rule.condition(putItem, null)
            if (!allow && failedCallback == null) {
                failedCallback = rule.failedCallback
            }
            allow
        },
    )
    if (plan.isEmpty()) {
        failedCallback?.invoke()
        return
    }

    var remaining = clickedItem.amount
    for ((slot, move) in plan) {
        val current = topInv.getItem(slot)
        if (current != null && !current.isAir && current.isSimilar(clickedItem)) {
            current.amount += move
            topInv.setItem(slot, current)
        } else {
            val newItem = clickedItem.clone().apply { amount = move }
            topInv.setItem(slot, newItem)
        }
        remaining -= move
    }

    if (remaining <= 0) {
        sourceInv.setItem(sourceSlot, null)
    } else {
        val newSource = clickedItem.clone().apply { amount = remaining }
        sourceInv.setItem(sourceSlot, newSource)
    }

    if (who is Player) {
        who.updateInventory()
    }
}

/**
 * 锁定 [rawSlots] 格子的交互
 *
 * @param rawSlots 原始格子列表
 * @param reverse 反向锁定，仅保留 rawSlots 格子可交互
 * */
fun ClickEvent.lockSlots(rawSlots: List<Int>, reverse: Boolean = false) {
    lockSlotRules.getOrPut(this) { mutableListOf() }.add(
        LockRule(
            rawSlots = rawSlots.toSet(),
            reverse = reverse,
        )
    )

    if (isCancelled) return
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                MOVE_TO_OTHER_INVENTORY -> {
                    // Shift+点击时，检查目标位置
                    val topSize = event.view.topInventory.size
                    if (event.rawSlot >= topSize) {
                        // 从下方背包移入上方容器，检查是否会影响锁定槽位
                        val topInv = event.view.topInventory
                        val clickedItem = event.currentItem
                        if (clickedItem != null && !clickedItem.isAir) {
                            // 仅检查 Shift+点击实际会填充的槽位，避免空锁定槽位误拦截
                            val affectedSlots = computeShiftMovePlan(topInv, clickedItem, { _, current ->
                                current?.maxStackSize ?: clickedItem.maxStackSize
                            }).map { it.first }

                            val wouldAffectLocked = if (reverse) {
                                affectedSlots.any { it !in rawSlots }
                            } else {
                                affectedSlots.any { it in rawSlots }
                            }
                            if (wouldAffectLocked) {
                                event.isCancelled = true
                            }
                        }
                    } else {
                        // 从上方容器移出，检查点击的槽位
                        if ((reverse && event.rawSlot !in rawSlots) || (!reverse && event.rawSlot in rawSlots)) {
                            event.isCancelled = true
                        }
                    }
                }
                COLLECT_TO_CURSOR -> {
                    // 双击收集时，检查是否会从不允许的槽位收集
                    val cursor = event.cursor
                    if (cursor != null) {
                        val topSize = event.view.topInventory.size
                        // 检查上方容器中是否有不允许交互的槽位会被收集
                        val wouldCollectFromDisallowed = (0 until topSize).any { slot ->
                            val item = event.view.getItem(slot)
                            val isDisallowed = if (reverse) slot !in rawSlots else slot in rawSlots
                            item != null && !item.isAir && cursor.isSimilar(item) && isDisallowed
                        }
                        if (wouldCollectFromDisallowed) {
                            event.isCancelled = true
                        }
                    }
                }
                HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    // 数字键/F键交换
                    if ((reverse && event.rawSlot !in rawSlots) || (!reverse && event.rawSlot in rawSlots)) {
                        event.isCancelled = true
                    }
                }
                else -> {
                    if ((reverse && event.rawSlot !in rawSlots) || (!reverse && event.rawSlot in rawSlots)) {
                        event.isCancelled = true
                    }
                }
            }
        }
        DRAG -> {
            val event = dragEvent()
            val check = if (reverse) {
                event.rawSlots.all { it in rawSlots }
            } else {
                event.rawSlots.intersect(rawSlots.toSet()).isEmpty()
            }
            if (!check) {
                event.isCancelled = true
            }
        }
        VIRTUAL -> {}
    }
}
