package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.CardFace
import me.xiaozhangup.crab.util.itemStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack

class ItemProvider(private val plugin: CardTablePlugin) {
    data class ModelItem(val stack: ItemStack, val customModel: Boolean)

    fun model(id: String, fallback: Material = Material.PAPER): ModelItem {
        val custom = if (id.isNotEmpty() && Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) CraftEngineBridge.build(id) else null
        return ModelItem(custom ?: ItemStack(fallback), custom != null)
    }

    fun worldCard(face: CardFace, skin: String): ModelItem = model(plugin.settings.skins.getValue(skin) + face.asset).apply {
        stack.editMeta {
            it.displayName(text(face.name, NamedTextColor.WHITE))
            it.setEnchantmentGlintOverride(face.selected)
        }
    }

    fun card(face: CardFace, skin: String, selectable: Boolean = true): ItemStack = icon(
        plugin.settings.skins.getValue(skin) + face.asset, face.name,
        if (selectable) buildList {
            add(parameter("状态", if (face.selected) "已选中" else "未选中"))
            if (face.token.isNotEmpty()) add(parameter("编号", face.token))
            add(description(""))
            add(hint(if (face.selected) "单击取消选择" else "单击选择这张牌"))
        } else listOf(description("公开牌  仅供查看")), Material.PAPER,
        if (face.selected && selectable) NamedTextColor.GREEN else NamedTextColor.WHITE
    ).apply {
        editMeta { it.setEnchantmentGlintOverride(face.selected && selectable) }
    }

    fun item(id: String, title: String, lore: List<String> = emptyList(), fallback: Material = Material.PAPER): ItemStack =
        icon(id, title, lore.map(::description), fallback)

    fun icon(id: String, title: String, lore: List<Component> = emptyList(), fallback: Material = Material.PAPER,
             titleColor: NamedTextColor = NamedTextColor.WHITE): ItemStack =
        formatIcon(model(id, fallback).stack, title, lore, titleColor)

    fun menuIcon(material: Material, title: String, lore: List<Component> = emptyList(),
                 titleColor: NamedTextColor = NamedTextColor.WHITE): ItemStack =
        formatIcon(itemStack(material), title, lore, titleColor)

    private fun formatIcon(result: ItemStack, title: String, lore: List<Component>, titleColor: NamedTextColor): ItemStack {
        result.editMeta { meta ->
            meta.displayName(text(title, titleColor))
            meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
            meta.addItemFlags(*ItemFlag.values())
        }
        return result
    }

    fun decoration(material: Material): ItemStack = itemStack(material) { hideTooltip() }
    fun description(value: String): Component = text(value, NamedTextColor.GRAY)
    fun parameter(label: String, value: Any): Component = description("$label: ").append(text(value.toString(), NamedTextColor.WHITE))
    fun hint(value: String): Component = text(value, NamedTextColor.YELLOW)
    private fun text(value: String, color: NamedTextColor): Component = Component.text(value, color).decoration(TextDecoration.ITALIC, false)
}

/** Only loaded when CraftEngine is present; the shared plugin remains usable without it. */
private object CraftEngineBridge {
    fun build(id: String): ItemStack? = net.momirealms.craftengine.bukkit.api.CraftEngineItems.byId(id)?.buildBukkitItem()
}
