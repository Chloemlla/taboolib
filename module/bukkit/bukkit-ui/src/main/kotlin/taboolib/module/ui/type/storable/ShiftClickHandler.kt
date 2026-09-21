package taboolib.module.ui.type.storable

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import taboolib.platform.util.isAir
import taboolib.platform.util.removeMeta
import taboolib.platform.util.setMeta

/**
 * Shift 点击操作处理器
 */
class ShiftClickHandler : BaseActionHandler() {
    
    /**
     * 从玩家背包 Shift 点击到 UI
     */
    fun shiftClickToUI(ctx: StorableActionContext, autoStack: Boolean): StorableActionResult {
        val currentItem = ctx.event.currentItem ?: return StorableActionResult.DENIED
        if (currentItem.isAir) return StorableActionResult.DENIED
        return if (autoStack) {
            shiftClickWithAutoStack(ctx, currentItem)
        } else {
            shiftClickWithoutAutoStack(ctx, currentItem)
        }
    }
    
    private fun shiftClickWithAutoStack(ctx: StorableActionContext, currentItem: ItemStack): StorableActionResult {
        val inventory = ctx.inventory
        val remainingItem = currentItem.clone()
        val slots = ctx.rule.getMergeSlots(inventory, remainingItem) ?: (0 until inventory.size).toList()
        // 第一步：尝试合并到所有相同物品的槽位
        for (slot in slots) {
            if (remainingItem.amount <= 0) break
            // 合并目标同样受放入校验约束，否则 Shift 连点可绕过 checkSlot 堆进禁放槽位
            if (!ctx.rule.canPlace(inventory, remainingItem, slot)) continue
            val slotItem = ctx.rule.getItem(inventory, slot)
            if (slotItem != null && !slotItem.isAir && slotItem.isSimilar(remainingItem)) {
                val maxStack = ctx.rule.getItemStacker().getMaxStackSize(slotItem)
                if (slotItem.amount < maxStack) {
                    val toAdd = minOf(maxStack - slotItem.amount, remainingItem.amount)
                    ctx.rule.setItem(inventory, slotItem.clone().apply { amount = slotItem.amount + toAdd }, slot, ctx.clickType)
                    remainingItem.amount -= toAdd
                }
            }
        }
        // 第二步：如果还有剩余，找空槽位放入
        if (remainingItem.amount > 0) {
            val firstSlot = ctx.rule.getFirstSlot(inventory, remainingItem)
            // 落位同样受放入校验约束，未通过时剩余物品留在玩家背包
            if (firstSlot >= 0 && ctx.rule.getItem(inventory, firstSlot).isAir && ctx.rule.canPlace(inventory, remainingItem, firstSlot)) {
                ctx.rule.setItem(inventory, remainingItem.clone(), firstSlot, ctx.clickType)
                remainingItem.amount = 0
            }
        }
        // 第三步：更新玩家背包中的物品
        if (remainingItem.amount <= 0) {
            ctx.event.currentItem?.type = Material.AIR
            ctx.event.currentItem = null
        } else {
            currentItem.amount = remainingItem.amount
        }
        return StorableActionResult.HANDLED
    }
    
    private fun shiftClickWithoutAutoStack(ctx: StorableActionContext, currentItem: ItemStack): StorableActionResult {
        val inventory = ctx.inventory
        val firstSlot = ctx.rule.getFirstSlot(inventory, currentItem)
        if (firstSlot >= 0) {
            // Shift 放入同样受放入校验约束，未通过时直接阻断（调用方已取消事件，物品留在玩家背包）
            if (!ctx.rule.canPlace(inventory, currentItem, firstSlot)) return StorableActionResult.HANDLED
            if (ctx.rule.canShiftSwap(inventory, currentItem, firstSlot)) {
                // 调用方已取消事件，经事件设置来源槽不会生效，必须直接改玩家背包，否则来源不清、背包与页面各留一份
                // rawSlot 是合成视图全局下标，主栏换算到 PlayerInventory 会错位 18 格，必须用 Bukkit 已换算的 slot
                val playerInventory = ctx.player.inventory
                val playerSlot = ctx.event.clickEvent().slot
                val oldTopItem = ctx.rule.getItem(inventory, firstSlot)?.takeIf { !it.isAir }
                if (playerSlot in 0 until playerInventory.size) {
                    playerInventory.setItem(playerSlot, oldTopItem ?: ItemStack(Material.AIR))
                }
                // 调用方已取消事件时此行无实际效果，仅维持事件状态一致
                ctx.event.currentItem = oldTopItem
                ctx.rule.setItem(inventory, currentItem, firstSlot, ctx.clickType)
            } else if (ctx.rule.getItem(inventory, firstSlot).isAir) {
                ctx.rule.setItem(inventory, currentItem, firstSlot, ctx.clickType)
                ctx.event.currentItem?.type = Material.AIR
                ctx.event.currentItem = null
            }
        }
        return StorableActionResult.HANDLED
    }
    
    /**
     * 从 UI Shift 点击到玩家背包
     */
    fun shiftClickFromUI(ctx: StorableActionContext): StorableActionResult {
        val slotItem = ctx.slotItem ?: return StorableActionResult.DENIED
        if (slotItem.isAir) return StorableActionResult.DENIED
        if (!canPickup(ctx, slotItem)) return StorableActionResult.DENIED
        clearSlot(ctx)
        ctx.player.setMeta("ui:shiftClickFromUI", ctx)
        try {
            val remaining = ctx.player.inventory.addItem(slotItem.clone())
            // 如果背包满了，把剩余物品放回槽位
            if (remaining.isNotEmpty()) {
                writeSlot(ctx, remaining.values.first())
            }
        } finally {
            ctx.player.removeMeta("ui:shiftClickFromUI")
        }
        return StorableActionResult.HANDLED
    }
}
