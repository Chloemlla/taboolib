package taboolib.module.ui

import org.bukkit.event.inventory.InventoryAction.*
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import taboolib.module.ui.ClickType.*
import taboolib.platform.util.giveItem

/** 副手槽位索引 */
private const val OFFHAND_SLOT = 40

/**
 * 在页面关闭时返还物品
 */
fun InventoryCloseEvent.returnItems(slots: List<Int>) = slots.forEach { player.giveItem(inventory.getItem(it)) }

/**
 * 创建点击事件条件格
 *
 * 用于创造物品的放入和取出条件
 *
 * @param rawSlot 原始格子
 * @param condition 条件
 * @param failedCallback 条件检测失败后执行回调
 * */
fun ClickEvent.conditionSlot(rawSlot: Int, condition: (put: ItemStack?, out: ItemStack?) -> Boolean, failedCallback: () -> Unit = {}): Boolean {
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
                        val out = old?.clone()?.apply { amount = old.amount/2 }
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
                        val out = event.clickedInventory?.getItem(event.slot)?.clone()?.apply { amount = 1 }
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
                        val put = old?.clone()?.apply { amount = old.maxStackSize - old.amount }
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
                        val put = event.cursor?.clone()?.apply { amount = 1 }
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
                        // 点击的是下方背包，检查物品是否会移入目标槽位
                        val clickedItem = event.currentItem
                        if (clickedItem != null && !clickedItem.type.isAir) {
                            val topInv = event.view.topInventory
                            val targetItem = topInv.getItem(rawSlot)
                            // 如果目标槽位为空或可堆叠，物品可能移入
                            if (targetItem == null || targetItem.type.isAir) {
                                if (!condition(clickedItem, null)) {
                                    event.isCancelled = true
                                    failedCallback()
                                    return false
                                }
                            } else if (targetItem.isSimilar(clickedItem) && targetItem.amount < targetItem.maxStackSize) {
                                val canPut = minOf(clickedItem.amount, targetItem.maxStackSize - targetItem.amount)
                                val putItem = clickedItem.clone().apply { amount = canPut }
                                if (!condition(putItem, null)) {
                                    event.isCancelled = true
                                    failedCallback()
                                    return false
                                }
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
                        val collectAmount = minOf(cursor.maxStackSize - cursor.amount, slotItem?.amount ?: 0)
                        val out = slotItem?.clone()?.apply { amount = collectAmount }
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
                        // 获取快捷栏或副手物品
                        val put = if (event.hotbarButton == OFFHAND_SLOT) {
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
 * */
fun ClickEvent.amountCondition(rawSlot: Int, amount: Int, failedCallback: () -> Unit = {}): Boolean {
    if (isCancelled) return false
    when(clickType) {
        CLICK -> {
            val event = clickEvent()
            when(event.action) {
                SWAP_WITH_CURSOR, PLACE_ALL -> {
                    if (rawSlot == event.rawSlot) {
                        val put = event.cursor
                        if ((put?.amount ?: 0) > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PICKUP_ALL, PICKUP_HALF, PICKUP_ONE, PICKUP_SOME, MOVE_TO_OTHER_INVENTORY, COLLECT_TO_CURSOR -> {}
                PLACE_SOME -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        if ((old?.maxStackSize ?: 0) > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                PLACE_ONE -> {
                    if (rawSlot == event.rawSlot) {
                        val old = event.clickedInventory?.getItem(event.slot)
                        if ((old?.amount ?: 0) + 1 > amount) {
                            event.isCancelled = true
                            failedCallback()
                            return false
                        }
                    }
                }
                HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> {
                    if (rawSlot == event.rawSlot) {
                        val playerInv = event.whoClicked.inventory as PlayerInventory
                        val put = if (event.hotbarButton == OFFHAND_SLOT) {
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
                val put = event.newItems[rawSlot]
                if ((put?.amount ?: 0) > amount) {
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
 * 锁定 [rawSlots] 格子的交互
 *
 * @param rawSlots 原始格子列表
 * @param reverse 反向锁定，仅保留 rawSlots 格子可交互
 * */
fun ClickEvent.lockSlots(rawSlots: List<Int>, reverse: Boolean = false) {
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
                        if (clickedItem != null && !clickedItem.type.isAir) {
                            // 检查上方容器中是否有锁定槽位会被影响
                            val affectedSlots = (0 until topSize).filter { slot ->
                                val item = topInv.getItem(slot)
                                (item == null || item.type.isAir || (item.isSimilar(clickedItem) && item.amount < item.maxStackSize))
                            }
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
                    // 双击收集时，检查是否会从锁定槽位收集
                    val cursor = event.cursor
                    if (cursor != null) {
                        val wouldCollectFromLocked = rawSlots.any { slot ->
                            val item = event.view.getItem(slot)
                            item != null && cursor.isSimilar(item)
                        }
                        if ((reverse && !wouldCollectFromLocked) || (!reverse && wouldCollectFromLocked)) {
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