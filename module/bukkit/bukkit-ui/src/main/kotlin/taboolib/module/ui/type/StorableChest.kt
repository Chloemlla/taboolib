package taboolib.module.ui.type

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.module.ui.ItemStacker

/**
 * 可储存容器
 */
interface StorableChest : Chest {

    /**
     * 定义页面规则
     */
    fun rule(rule: Rule.() -> Unit)

    /**
     * 设置是否自动堆叠物品
     * @param enabled true 为自动合并模式（默认），false 为交换模式
     */
    fun autoStack(enabled: Boolean = true)

    /**
     * 页面规则
     */
    interface Rule {

        /**
         * 定义判定位置
         * 玩家是否可以将物品放入
         */
        fun checkSlot(intRange: Int, checkSlot: (inventory: Inventory, itemStack: ItemStack) -> Boolean)

        /**
         * 定义判定位置
         * 玩家是否可以将物品放入
         */
        fun checkSlot(intRange: IntRange, callback: (inventory: Inventory, itemStack: ItemStack) -> Boolean)

        /**
         * 定义判定位置
         * 玩家是否可以将物品放入
         */
        fun checkSlot(callback: (inventory: Inventory, itemStack: ItemStack, slot: Int) -> Boolean)

        /**
         * 获取页面中首个有效的位置
         * 用于玩家 SHIFT 点击快速放入物品
         * 注意：Shift 放入的最终落位仍会经过 checkSlot 校验，不可放入的槽位会被阻断
         */
        fun firstSlot(firstSlot: (inventory: Inventory, itemStack: ItemStack) -> Int)

        /**
         * 物品写入回调
         */
        fun writeItem(writeItem: (inventory: Inventory, itemStack: ItemStack, slot: Int) -> Unit)

        /**
         * 物品写入回调
         */
        fun writeItem(writeItem: (inventory: Inventory, itemStack: ItemStack, slot: Int, type: BukkitClickType) -> Unit)

        /**
         * 读取物品回调
         */
        fun readItem(readItem: (inventory: Inventory, slot: Int) -> ItemStack?)

        /**
         * 是否允许 Shift 交换物品
         */
        fun shiftSwap(shiftSwap: (inventory: Inventory, itemStack: ItemStack, slot: Int) -> Boolean)

        /**
         * 设置物品堆叠器
         */
        fun itemStacker(itemStacker: ItemStacker)

        /**
         * 获取可合并的槽位列表
         * 用于 Shift 点击时自动合并到这些槽位
         */
        fun mergeSlots(mergeSlots: (inventory: Inventory, itemStack: ItemStack) -> List<Int>)

        /**
         * 原子消费顶层容器指定槽位
         * 同步取出并清空 Bukkit 侧槽位，返回取出前的物品（经 readItem，与玩家看到的一致）。
         * 用于结算（分解/销毁/提交）场景：先基于自有状态算奖，再调此方法，最后收尾重开。
         * 消费经由 writeItem 回调逐槽下发，页面的影子状态随之自动同步，无需再手清。
         * 消费是系统行为，不经过 checkSlot/canPickup；放入合法性由 Shift/放置门禁保证。
         * 调用前后不得切换线程，必须在 Bukkit 主线程事件处理内同步执行；重复调用幂等。
         *
         * @param inventory 顶层容器
         * @param slots 待消费槽位，越界自动跳过
         * @param clickType 透传给 writeItem 的点击类型，按钮回调无事件时用默认 LEFT
         * @return 按 slots 顺序的取出物，空槽为 null
         */
        fun consumeSlots(
            inventory: Inventory,
            slots: Collection<Int>,
            clickType: BukkitClickType = BukkitClickType.LEFT,
        ): List<ItemStack?>
    }
}

typealias BukkitClickType = org.bukkit.event.inventory.ClickType