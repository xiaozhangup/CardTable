package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Chunk
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

class WorldTableRenderer(private val plugin: CardTablePlugin) : Listener {
    private data class Target(
        val center: Location, val rotation: Quaternionf, val width: Double, val height: Double,
        val action: String, val argument: String?
    ) {
        // The same plane and dimensions are used to render and pick, at every seat angle.
        fun distance(eye: Location, direction: Vector): Double? {
            val normal = rotation.transform(Vector3f(0f, 0f, -1f))
            val denominator = direction.x * normal.x + direction.y * normal.y + direction.z * normal.z
            if (denominator >= -0.00001) return null
            val offset = center.toVector().subtract(eye.toVector())
            val distance = (offset.x * normal.x + offset.y * normal.y + offset.z * normal.z) / denominator
            if (distance !in 0.0..5.0) return null
            val hit = eye.toVector().add(direction.clone().multiply(distance)).subtract(center.toVector())
            val local = Quaternionf(rotation).conjugate().transform(Vector3f(hit.x.toFloat(), hit.y.toFloat(), hit.z.toFloat()))
            return distance.takeIf { abs(local.x) <= width / 2 && abs(local.y) <= height / 2 }
        }
    }
    private data class Rendered(
        val frame: DisplayFrame,
        val base: MutableList<Entity> = mutableListOf(),
        val pile: MutableMap<String, ItemDisplay> = mutableMapOf(),
        val flights: MutableMap<String, Flight> = mutableMapOf(),
        val draws: MutableList<DrawFlight> = mutableListOf(),
        val skipped: MutableMap<Int, SkipNotice> = mutableMapOf(),
        var visiblePile: Set<String> = emptySet(),
        var lastPlaySequence: Long? = null,
        var lastDrawSequence: Long? = null,
        var lastSkipSequence: Long? = null,
        var active: Boolean = false,
        var turnSeat: Int? = null,
        var turnText: Component = Component.empty(),
        val chunks: MutableList<Chunk> = mutableListOf(),
        val targets: MutableMap<UUID, MutableList<Target>> = mutableMapOf(),
        val status: MutableList<TextDisplay> = mutableListOf(),
        var direction: TextDisplay? = null,
        var surface: Display? = null,
        var snapshot: List<GameView>? = null,
        var headerSnapshot: TableHeaderView? = null,
        var pileSnapshot: List<CardFace>? = null,
        var pileSkin: String? = null
    )
    private data class Flight(
        val entity: ItemDisplay, val start: Location, var end: Location,
        val fromRotation: Quaternionf, var toRotation: Quaternionf,
        var customModel: Boolean, var tick: Int,
        val startScale: Float = TableLayout.CARD_SCALE, val endScale: Float = 0.72f
    )
    private data class DrawFlight(val flight: Flight, val playerId: UUID)
    private data class SkipNotice(val playerId: UUID, var ticks: Int = 30)
    private val rendered = mutableMapOf<String, Rendered>()
    private val tableKey = NamespacedKey(plugin, "table")
    private val ownerKey = NamespacedKey(plugin, "owner")
    private val actionKey = NamespacedKey(plugin, "action")
    private val lastClick = mutableMapOf<UUID, Int>()
    private val handPages = mutableMapOf<UUID, Int>()
    private val chunkUsers = mutableMapOf<String, Int>()
    private val bots = BotMannequins(plugin).also { Bukkit.getPluginManager().registerEvents(it, plugin) }

    fun add(room: GameSession) {
        val table = room.table
        val output = Rendered(DisplayFrame(::prepare))
        val center = table.center
        for (x in ((center.blockX - 6) shr 4)..((center.blockX + 6) shr 4)) {
            for (z in ((center.blockZ - 6) shr 4)..((center.blockZ + 6) shr 4)) {
                val chunk = center.world.getChunkAt(x, z)
                val key = chunkKey(chunk)
                if (key !in chunkUsers) chunk.addPluginChunkTicket(plugin)
                chunkUsers[key] = (chunkUsers[key] ?: 0) + 1
                output.chunks += chunk
            }
        }
        rendered[table.id] = output
        val half = TableLayout.tableHalfSize(room.seatCount)
        // Every piece of furniture is a non-pickable display; only empty seat pads accept joining.
        output.surface = surface(room)
        output.base += output.surface!!
        for (x in listOf(-half + 0.23, half - 0.23)) for (z in listOf(-half + 0.23, half - 0.23)) {
            output.base += block(center.clone().add(x, 0.0, z), Material.DARK_OAK_PLANKS, 0.18f, 0.63f, 0.18f)
        }
        for (seat in 0 until room.seatCount) {
            val point = TableLayout.seatLocation(table, seat, room.seatCount)
            output.base += block(point.clone().add(0.0, TableLayout.SEAT_HEIGHT - 0.11, 0.0), Material.SPRUCE_PLANKS, 0.70f, 0.11f, 0.70f)
            output.base += block(point, Material.DARK_OAK_PLANKS, 0.30f, 0.49f, 0.30f)
            val outward = TableLayout.radial(table, seat, room.seatCount)
            val statusPoint = center.clone().add(outward.clone().multiply(half - 0.35)).add(0.0, tableCardHeight(room), 0.0)
            output.status += tableText(statusPoint, outward)
        }
        output.base += output.status
        // These 6 x 5 logical-pixel glyphs stay centered and retain the previous 0.45-block width.
        output.direction = tableText(center.clone().add(0.0, tableCardHeight(room), 0.0),
            Vector(0.0, 0.0, 1.0), 3.0f, 0.1125f)
        output.base += output.direction!!
    }

    fun refresh(room: GameSession) {
        val output = rendered.getValue(room.table.id)
        bots.refresh(room.table, room.seatCount, room.participants.filter { it.bot })
        val public = room.view(null)
        if (room.active && !output.active) {
            output.draws.forEach { it.flight.entity.remove() }
            output.draws.clear()
            output.skipped.clear()
        }
        output.active = room.active
        refreshPile(room, output, (room as? TableCardPile)?.tableCards() ?: public.publicCards)
        refreshDraws(room, output, public)
        room.participants.filterNot { it.bot }.forEach { participant ->
            Bukkit.getPlayer(participant.id)?.let { owner ->
                val view = room.view(participant.id)
                owner.sendActionBar(Component.text("手牌 ${view.hand.size} 张", NamedTextColor.WHITE)
                    .append(Component.text("  已选 ${view.hand.count { it.selected }} 张", NamedTextColor.GREEN)))
            }
        }
        refreshStatus(room, output, public)
        val views = listOf(public.copy(remainingSeconds = 0)) + room.participants.filterNot { it.bot }.map { room.view(it.id).copy(remainingSeconds = 0) }
        val header = (room as? TableHeader)?.tableHeader() ?: TableHeaderView(
            Component.text(plugin.tables.provider(room.table.game)!!.displayName, TextColor.color(0x9BC7B6)),
            Component.text(if (room.active) "对局中" else "${room.participants.size}/${room.seatCount} 人", NamedTextColor.WHITE)
        )
        if (views == output.snapshot && header == output.headerSnapshot) return
        output.snapshot = views
        output.headerSnapshot = header
        output.frame.begin()
        output.targets.clear()
        val table = room.table
        val radius = TableLayout.radius(room.seatCount)
        headerText(output.frame, table.center.clone().add(0.0, TableLayout.TABLE_HEIGHT + 1.94, 0.0),
            header.title.append(Component.newline()).append(header.detail))
        for (seat in 0 until room.seatCount) {
            val playerView = public.players.firstOrNull { it.participant.seat == seat }
            val outward = TableLayout.radial(table, seat, room.seatCount)
            val tangent = Vector(outward.z, 0.0, -outward.x)
            fun point(distance: Double, height: Double, side: Double = 0.0): Location =
                table.center.clone().add(outward.clone().multiply(distance)).add(tangent.clone().multiply(side)).add(0.0, height, 0.0)
            val seatPoint = TableLayout.seatLocation(table, seat, room.seatCount)
            if (playerView == null) {
                interaction(output.frame, "join:$seat", seatPoint.clone().add(0.0, 0.46, 0.0), table.id, "join", null, 0.70f, 0.20f)
                continue
            }
            val name = "${playerView.participant.name}${if (playerView.participant.bot) "  人机" else ""}\n${playerView.role}  ${playerView.count} 张${if (playerView.ready && !room.active) "  已准备" else ""}"
            text(output.frame, "name:$seat", point(radius + 0.20, 2.15), name, null, 0.46f)
            val back = if (playerView.count > 0) frameCard(output.frame, "back:${playerView.participant.id}", point(radius - 0.75, 0.84),
                CardFace(public.backAsset, "手牌", ""), table.skin, TableLayout.facing(outward, 1.5707964f), null, 0.46f) else null
            if (playerView.participant.bot) continue
            val owner = Bukkit.getPlayer(playerView.participant.id) ?: continue
            if (back != null) owner.hideEntity(plugin, back)
            val handView = room.view(owner.uniqueId)
            val targets = mutableListOf<Target>()
            output.targets[owner.uniqueId] = targets
            val pageCount = ((handView.hand.size + TableLayout.HAND_PAGE_SIZE - 1) / TableLayout.HAND_PAGE_SIZE).coerceAtLeast(1)
            val page = (handPages[owner.uniqueId] ?: 0).coerceAtMost(pageCount - 1)
            handPages[owner.uniqueId] = page
            val hand = handView.hand.drop(page * TableLayout.HAND_PAGE_SIZE).take(TableLayout.HAND_PAGE_SIZE)
            val spacing = if (hand.size > 1) min(0.16, TableLayout.HAND_SPAN / (hand.size - 1)) else 0.0
            hand.forEachIndexed { index, face ->
                val lift = if (face.selected) TableLayout.HAND_LIFT else 0.0
                // A single low row occupies the old control area, outside the table edge.
                // Selection slides in the card plane, leaving the table status and pile visible.
                val depth = radius - TableLayout.HAND_DISTANCE + index * 0.004 - lift * tan(TableLayout.CARD_TILT.toDouble())
                val position = point(depth, TableLayout.HAND_HEIGHT + lift, (index - (hand.size - 1) / 2.0) * spacing)
                val rotation = TableLayout.facing(outward)
                frameCard(output.frame, "hand:${owner.uniqueId}:${face.token}", position, face, plugin.skin(owner.uniqueId, table.skin), rotation, owner)
                targets += Target(position, rotation, TableLayout.CARD_WIDTH, TableLayout.CARD_HEIGHT, "select", face.token)
            }
            if (pageCount > 1) text(output.frame, "page:${owner.uniqueId}", point(radius - 0.60, 0.45), "${page + 1} / $pageCount", owner, 0.30f)
            if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine") && hand.isNotEmpty()) {
                text(output.frame, "fallback:${owner.uniqueId}", point(radius - 1.85, 2.20), hand.joinToString(" ") { if (it.selected) "[${it.name}]" else it.name }, owner, 0.32f)
            }
            val controls = buildList {
                addAll(handView.controls)
                if (!room.active && room.supportsBots && room.table.bet == 0.0 && room.participants.size < room.seatCount) add(GameControl("cardtable:bots", "补齐人机", "doudizhu:table"))
            }
            controls.chunked(3).forEachIndexed { row, buttons ->
                buttons.forEachIndexed { index, control ->
                    val position = point(radius - 1.85, 1.58 + row * 0.47, (index - (buttons.size - 1) / 2.0) * 0.86)
                    button(output, targets, position, TableLayout.facing(outward, 0.35f), control, owner)
                }
            }
            button(output, targets, point(radius - 1.50, 1.72, -1.48), TableLayout.facing(outward, 0f),
                GameControl("cardtable:settings", "设置", ""), owner, 0.60f)
            button(output, targets, point(radius - 1.50, 1.72, 1.48), TableLayout.facing(outward, 0f),
                GameControl("cardtable:leave", "离桌", ""), owner, 0.60f)
            if (pageCount > 1) {
                for ((side, change) in listOf(-1.48 to -1, 1.48 to 1)) {
                    if (page + change !in 0 until pageCount) continue
                    val position = point(radius - 0.85, 0.85, side)
                    val control = GameControl("cardtable:hand_page", if (change < 0) "上一页" else "下一页", "", change.toString())
                    button(output, targets, position, TableLayout.facing(outward, 0.75f), control, owner, 0.52f)
                }
            }
            // Native volumes receive clicks, including attacks. Overlapping bounds never choose the card:
            // pick() intersects the visible planes, so angled seats use the same rules as front seats.
            targets.forEachIndexed { index, target ->
                val horizontal = target.rotation.transform(Vector3f((target.width / 2).toFloat(), 0f, 0f))
                val vertical = target.rotation.transform(Vector3f(0f, (target.height / 2).toFloat(), 0f))
                val width = 2 * max(abs(horizontal.x) + abs(vertical.x), abs(horizontal.z) + abs(vertical.z)) + 0.02f
                val height = 2 * (abs(horizontal.y) + abs(vertical.y)) + 0.02f
                interaction(output.frame, "pick:${owner.uniqueId}:$index", target.center.clone().add(0.0, -height / 2.0, 0.0), table.id, "surface", owner, width, height)
            }
            // Multi-card combinations also face each player so every rank remains readable.
            // Single-card plays are shown only in the shared tabletop pile.
            val recap = handView.publicCards.takeIf { it.size > 1 }.orEmpty()
            val recapRotation = TableLayout.facing(outward, 0.18f)
            recap.forEachIndexed { index, face ->
                frameCard(output.frame, "recap:${owner.uniqueId}:$index", point(radius - 2.95 + index * 0.003, 3.46, (index - (recap.size - 1) / 2.0) * 0.15), face,
                    table.skin, recapRotation, owner, 0.46f)
            }
        }
        val up = Quaternionf().rotationY(1.5707964f).rotateX(1.5707964f)
        public.bottomCards.forEachIndexed { index, face ->
            frameCard(output.frame, "bottom:$index", table.center.clone().add(-0.60,
                tableCardHeight(room), (index - (public.bottomCards.size - 1) / 2.0) * 0.29), face, table.skin, up, null, 0.44f)
        }
        output.frame.end()
    }

    private fun tableCardHeight(room: GameSession): Double =
        TableLayout.TABLE_HEIGHT + max(0.04, TableLayout.tableHalfSize(room.seatCount) / 32.0) + 0.012

    private fun tableText(point: Location, outward: Vector, scale: Float = 0.38f, centerY: Float = 0.25f): TextDisplay = point.world.spawn(point, TextDisplay::class.java) {
        prepare(it)
        it.billboard = Display.Billboard.FIXED
        it.isShadowed = false
        it.backgroundColor = Color.fromARGB(0, 0, 0, 0)
        it.lineWidth = 120
        // Text fronts are +Z: lie flat, face upward, and read from this seat toward the table.
        val rotation = TableLayout.facing(outward, 1.5707964f).rotateY(Math.PI.toFloat())
        it.transformation = Transformation(rotation.transform(Vector3f(-0.0125f * scale, -centerY * scale, 0f)),
            rotation, Vector3f(scale), Quaternionf())
    }

    private fun refreshStatus(room: GameSession, output: Rendered, view: GameView) {
        val current = view.players.firstOrNull { it.current }
        val symbol = when ((room as? TableTurnOrder)?.turnDirection) {
            TurnDirection.CLOCKWISE -> "⟳"
            TurnDirection.COUNTERCLOCKWISE -> "⟲"
            null -> ""
        }
        output.direction!!.text(Component.text(symbol, NamedTextColor.WHITE))
        val skip = (room as? TableSkipEvents)?.lastTableSkip
        if (skip == null) output.skipped.clear()
        else if (skip.sequence != output.lastSkipSequence) {
            val player = view.players.first { it.participant.seat == skip.seat }.participant
            output.skipped[skip.seat] = SkipNotice(player.id)
        }
        output.lastSkipSequence = skip?.sequence
        // If play returns quickly, the current turn takes precedence over an earlier skip notice.
        output.skipped.entries.removeIf { (seat, notice) ->
            seat == current?.participant?.seat || view.players.none { it.participant.seat == seat && it.participant.id == notice.playerId }
        }
        output.turnSeat = current?.participant?.seat
        // The second line lies closer to the seat; the triangle points outward toward the player.
        output.turnText = Component.text("${view.remainingSeconds}s\n▼", NamedTextColor.WHITE)
        paintStatus(output)
    }

    private fun paintStatus(output: Rendered) {
        output.status.forEachIndexed { seat, label ->
            val notice = output.skipped[seat]
            label.text(when {
                notice != null -> Component.text("\n∅", NamedTextColor.WHITE)
                output.turnSeat == seat -> output.turnText
                else -> Component.empty()
            })
            label.textOpacity = (notice?.let { min(it.ticks, 6) * 255 / 6 } ?: 255).toByte()
        }
    }

    private fun refreshDraws(room: GameSession, output: Rendered, view: GameView) {
        val draw = (room as? TableDrawEvents)?.lastTableDraw
        output.draws.removeIf { pending ->
            val remove = draw == null || view.players.none { it.participant.id == pending.playerId }
            if (remove) pending.flight.entity.remove()
            remove
        }
        if (draw?.sequence == output.lastDrawSequence) return
        output.lastDrawSequence = draw?.sequence
        if (draw == null) return
        val player = view.players.first { it.participant.seat == draw.seat }.participant
        val outward = TableLayout.radial(room.table, draw.seat, room.seatCount)
        val tangent = Vector(outward.z, 0.0, -outward.x)
        val up = Quaternionf().rotationY(1.5707964f).rotateX(1.5707964f)
        val facing = TableLayout.facing(outward)
        // Public draw events carry only a count. Every flying card is a back, even for its owner.
        val model = plugin.items.worldCard(CardFace(view.backAsset, "摸牌", ""), room.table.skin)
        repeat(draw.count) { index ->
            val start = room.table.center.clone().add(-0.60, tableCardHeight(room) + 0.006 * (index + 1), 0.0)
            val end = room.table.center.clone().add(outward.clone().multiply(TableLayout.radius(room.seatCount) - TableLayout.HAND_DISTANCE))
                .add(tangent.clone().multiply((index - (draw.count - 1) / 2.0) * 0.035)).add(0.0, TableLayout.HAND_HEIGHT, 0.0)
            val entity = start.world.spawn(start, ItemDisplay::class.java) {
                prepare(it)
                it.setItemStack(model.stack)
                it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                it.transformation = cardTransform(0.44f, up, model.customModel)
                it.teleportDuration = 1
                it.interpolationDuration = 1
            }
            output.draws += DrawFlight(Flight(entity, start, end, up, facing, model.customModel,
                -1 - index * 2, 0.44f, TableLayout.CARD_SCALE), player.id)
        }
    }

    private fun refreshPile(room: GameSession, output: Rendered, cards: List<CardFace>) {
        val play = (room as? TablePlayEvents)?.lastTablePlay
        val newPlay = play?.takeIf { it.sequence != output.lastPlaySequence }
        if (output.pileSnapshot == cards && output.pileSkin == room.table.skin && play?.sequence == output.lastPlaySequence) return
        // A new round can reveal the same opening card as the previous round's final card.
        if (play == null && output.lastPlaySequence != null) output.flights.clear()
        val previous = output.pileSnapshot.orEmpty()
        if (cards.size < previous.size && previous.takeLast(cards.size) == cards) {
            // UNO recycling keeps the top card: changing its pile index must not respawn it
            // or interrupt a flight already in progress. Match the suffix, including empty tokens.
            val offset = previous.size - cards.size
            val oldPile = output.pile.toMutableMap()
            val oldFlights = output.flights.toMap()
            output.pile.clear()
            output.flights.clear()
            cards.forEachIndexed { index, face ->
                val oldKey = "${index + offset}:${face.token}"
                val key = "$index:${face.token}"
                oldPile.remove(oldKey)?.let { output.pile[key] = it }
                oldFlights[oldKey]?.let { output.flights[key] = it }
            }
            oldPile.values.forEach(Entity::remove)
        }
        output.lastPlaySequence = play?.sequence
        output.pileSnapshot = cards.toList()
        output.pileSkin = room.table.skin
        val keys = cards.mapIndexed { index, face -> "$index:${face.token}" }
        val first = (cards.size - 16).coerceAtLeast(0)
        output.visiblePile = keys.drop(first).toSet()
        // A fresh event is only animated when its cards are still at the top of the current public pile.
        // UNO may recycle older discards during a penalty before publishing the final view.
        val animatedStart = if (newPlay != null && cards.takeLast(newPlay.cards.size) == newPlay.cards)
            cards.size - newPlay.cards.size else cards.size
        val liveKeys = keys.toSet()
        output.flights.keys.retainAll(liveKeys)
        output.pile.entries.removeIf { (key, entity) ->
            val remove = key !in output.visiblePile && key !in output.flights
            if (remove) entity.remove()
            remove
        }
        cards.forEachIndexed { index, face ->
            val key = keys[index]
            val animate = index >= animatedStart
            if (key !in output.visiblePile && key !in output.flights && !animate) return@forEachIndexed
            val x = ((index * 3) % 5 - 2) * 0.018
            val z = ((index * 7) % 5 - 2) * 0.014
            val angle = Math.toRadians(((index * 11) % 19 - 9).toDouble()).toFloat()
            val up = Quaternionf().rotationY(angle).rotateX(1.5707964f)
            val position = room.table.center.clone().add(0.60 + x, tableCardHeight(room) + (index - first).coerceAtLeast(0) * 0.005, z)
            val model = plugin.items.worldCard(face, room.table.skin)
            if (animate) {
                val outward = TableLayout.radial(room.table, newPlay!!.seat, room.seatCount)
                val tangent = Vector(outward.z, 0.0, -outward.x)
                val order = index - animatedStart
                val start = room.table.center.clone().add(outward.multiply(TableLayout.radius(room.seatCount) - 0.72))
                    .add(tangent.multiply((order - (newPlay.cards.size - 1) / 2.0) * 0.035)).add(0.0, 0.90, 0.0)
                val rotation = TableLayout.facing(TableLayout.radial(room.table, newPlay.seat, room.seatCount))
                val setup: (ItemDisplay) -> Unit = {
                    it.setItemStack(model.stack)
                    it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
                    it.transformation = cardTransform(TableLayout.CARD_SCALE, rotation, model.customModel)
                    it.teleportDuration = 1
                    it.interpolationDuration = 1
                }
                val entity = output.pile[key]?.also { move(it, start); setup(it) }
                    ?: start.world.spawn(start, ItemDisplay::class.java) { prepare(it); setup(it) }
                        .also { output.pile[key] = it }
                output.flights[key] = Flight(entity, start, position, rotation, up, model.customModel, -1 - order * 2)
            } else {
                val flight = output.flights[key]
                val entity = output.pile.getOrPut(key) { card(position, face, room.table.skin, up, null, 0.72f) }
                entity.setItemStack(model.stack)
                if (flight != null) {
                    flight.end = position
                    flight.toRotation = up
                    flight.customModel = model.customModel
                } else {
                    move(entity, position)
                    entity.transformation = cardTransform(0.72f, up, model.customModel)
                }
            }
        }
    }

    /** Shares the plugin's synchronous tick; removing a room also removes all of its flights. */
    fun tick() {
        rendered.values.forEach { output ->
            val iterator = output.flights.iterator()
            while (iterator.hasNext()) {
                val (key, flight) = iterator.next()
                if (advance(flight)) {
                    iterator.remove()
                    if (key !in output.visiblePile) output.pile.remove(key)?.remove()
                }
            }
            output.draws.removeIf {
                // Allow the last one-tick client interpolation to reach the hand before removal.
                if (it.flight.tick >= 12) {
                    it.flight.entity.remove()
                    true
                } else {
                    advance(it.flight)
                    false
                }
            }
            if (output.skipped.isNotEmpty()) {
                output.skipped.entries.removeIf { --it.value.ticks <= 0 }
                paintStatus(output)
            }
        }
    }

    private fun advance(flight: Flight): Boolean {
        if (++flight.tick <= 0) return false // Spawn metadata precedes the first movement.
        val progress = (flight.tick / 12.0).coerceAtMost(1.0)
        val position = flight.start.clone().add(flight.end.toVector().subtract(flight.start.toVector()).multiply(progress))
            .add(0.0, 4 * 0.34 * progress * (1 - progress), 0.0)
        val rotation = Quaternionf(flight.fromRotation).slerp(flight.toRotation, progress.toFloat())
        move(flight.entity, position)
        flight.entity.transformation = cardTransform((flight.startScale + (flight.endScale - flight.startScale) * progress).toFloat(), rotation, flight.customModel)
        flight.entity.interpolationDelay = 0
        return progress == 1.0
    }

    private fun move(entity: Entity, point: Location) {
        val old = entity.location
        if (old.x != point.x || old.y != point.y || old.z != point.z) entity.teleport(point.clone().apply { yaw = 0f; pitch = 0f })
    }

    private fun button(output: Rendered, targets: MutableList<Target>, point: Location, rotation: Quaternionf, control: GameControl, owner: Player, width: Float = 0.76f) {
        val height = 0.24f
        val material = when (control.action) {
            "ready", "play" -> Material.GREEN_CONCRETE
            "bid", "draw" -> Material.CYAN_CONCRETE
            "hint", "challenge", "catch" -> Material.BROWN_CONCRETE
            "cardtable:leave" -> Material.RED_CONCRETE
            "color" -> when (control.argument) {
                "red" -> Material.RED_CONCRETE
                "yellow" -> Material.YELLOW_CONCRETE
                "green" -> Material.GREEN_CONCRETE
                "blue" -> Material.BLUE_CONCRETE
                else -> Material.GRAY_CONCRETE
            }
            else -> Material.GRAY_CONCRETE
        }
        // The entire visible plate is clickable, including its Chinese label.
        val key = "button:${owner.uniqueId}:${control.action}:${control.argument}"
        output.frame.use("$key:plate", point, BlockDisplay::class.java, owner) {
            it.block = material.createBlockData()
            it.transformation = Transformation(rotation.transform(Vector3f(-width / 2, -height / 2, 0f)),
                rotation, Vector3f(width, height, 0.012f), Quaternionf())
        }
        val labelOffset = rotation.transform(Vector3f(0f, 0f, -0.014f))
        output.frame.use("$key:text", point.clone().add(labelOffset.x.toDouble(), labelOffset.y.toDouble(), labelOffset.z.toDouble()), TextDisplay::class.java, owner) {
            it.text(Component.text(control.title, if (material == Material.YELLOW_CONCRETE) NamedTextColor.BLACK else NamedTextColor.WHITE))
            it.billboard = Display.Billboard.FIXED
            it.backgroundColor = Color.fromARGB(0, 0, 0, 0)
            it.isShadowed = false
            it.lineWidth = 120
            // TextDisplay faces +Z, while cards and input planes face -Z.
            val textRotation = Quaternionf(rotation).rotateY(Math.PI.toFloat())
            // Center the vanilla one-line layout: x offset 0.5px, height 10px, each pixel 0.025 blocks.
            it.transformation = Transformation(textRotation.transform(Vector3f(-0.006875f, -0.06875f, 0f)),
                textRotation, Vector3f(0.55f), Quaternionf())
        }
        targets += Target(point, rotation, width.toDouble(), height.toDouble(), control.action, control.argument)
    }

    fun invalidate(id: String) { rendered[id]?.snapshot = null }
    fun refreshAssets(room: GameSession) {
        val output = rendered.getValue(room.table.id)
        output.surface?.let { output.base.remove(it); it.remove() }
        output.surface = surface(room).also { output.base += it }
        output.pileSnapshot = null
        invalidate(room.table.id)
        refresh(room)
    }

    private fun surface(room: GameSession): Display {
        val model = plugin.items.model("doudizhu:tabletop")
        val point = room.table.center.clone().add(0.0, TableLayout.TABLE_HEIGHT, 0.0)
        val half = TableLayout.tableHalfSize(room.seatCount).toFloat()
        if (!model.customModel) return block(point.subtract(0.0, 0.04, 0.0), Material.GREEN_WOOL, 2 * half, 0.08f, 2 * half)
        return point.world.spawn(point, ItemDisplay::class.java) {
            prepare(it)
            it.setItemStack(model.stack)
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            it.transformation = transform(half)
        }
    }

    private fun block(point: Location, material: Material, width: Float, height: Float, depth: Float): BlockDisplay = point.world.spawn(point, BlockDisplay::class.java) {
        prepare(it)
        it.block = material.createBlockData()
        it.transformation = Transformation(Vector3f(-width / 2, 0f, -depth / 2), Quaternionf(), Vector3f(width, height, depth), Quaternionf())
    }
    private fun card(point: Location, face: CardFace, skin: String, rotation: Quaternionf, owner: Player?, scale: Float = TableLayout.CARD_SCALE): ItemDisplay = point.world.spawn(point, ItemDisplay::class.java) {
        prepare(it, owner)
        val model = plugin.items.worldCard(face, skin)
        it.setItemStack(model.stack)
        it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
        // ItemDisplayRenderer adds a local Y half-turn before drawing the item model.
        // Custom cards have an identity FIXED transform; vanilla PAPER already compensates in item/generated.
        it.transformation = cardTransform(scale, rotation, model.customModel)
    }
    private fun frameCard(frame: DisplayFrame, key: String, point: Location, face: CardFace, skin: String, rotation: Quaternionf, owner: Player?, scale: Float = TableLayout.CARD_SCALE): ItemDisplay =
        frame.use(key, point, ItemDisplay::class.java, owner) {
            val model = plugin.items.worldCard(face, skin)
            it.setItemStack(model.stack)
            it.itemDisplayTransform = ItemDisplay.ItemDisplayTransform.FIXED
            it.transformation = cardTransform(scale, rotation, model.customModel)
        }
    private fun cardTransform(scale: Float, rotation: Quaternionf, customModel: Boolean): Transformation =
        if (customModel) transform(scale, Quaternionf(rotation).rotateY(Math.PI.toFloat())) else
            // Generated PAPER is one pixel thick; match the custom cards' 0.06-pixel thickness.
            Transformation(Vector3f(), rotation, Vector3f(0.625f * scale, 0.9375f * scale, 0.06f * scale), Quaternionf())
    private fun headerText(frame: DisplayFrame, point: Location, value: Component): TextDisplay =
        frame.use("header", point, TextDisplay::class.java) {
            it.text(value.decoration(TextDecoration.ITALIC, false))
            it.billboard = Display.Billboard.CENTER
            it.backgroundColor = null
            it.isDefaultBackground = true
            it.isShadowed = true
            it.transformation = Transformation(Vector3f(-0.0125f, -0.25f, 0f),
                Quaternionf(), Vector3f(1f), Quaternionf())
        }

    private fun text(frame: DisplayFrame, key: String, point: Location, value: String, owner: Player?, scale: Float): TextDisplay = frame.use(key, point, TextDisplay::class.java, owner) {
        it.text(Component.text(value, NamedTextColor.WHITE))
        it.billboard = Display.Billboard.CENTER
        it.backgroundColor = Color.fromARGB(135, 20, 25, 27)
        it.isShadowed = false
        it.lineWidth = 280
        it.transformation = transform(scale)
    }
    private fun interaction(frame: DisplayFrame, key: String, point: Location, id: String, action: String, owner: Player?, width: Float, height: Float): Interaction = frame.use(key, point, Interaction::class.java, owner) {
        it.interactionWidth = width
        it.interactionHeight = height
        it.isResponsive = true
        it.persistentDataContainer.set(tableKey, PersistentDataType.STRING, id)
        it.persistentDataContainer.set(actionKey, PersistentDataType.STRING, action)
        owner?.let { player -> it.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString()) }
    }
    private fun prepare(entity: Entity, owner: Player? = null) {
        entity.isPersistent = false
        entity.isInvulnerable = true
        if (entity is Display) {
            // Spawn locations carry the table/seat yaw; all display orientation lives in the transform.
            entity.setRotation(0f, 0f)
            entity.brightness = Display.Brightness(15, 15)
            entity.viewRange = 0.7f
        }
        if (owner != null) { entity.isVisibleByDefault = false; owner.showEntity(plugin, entity) }
    }

    @EventHandler
    fun airClick(event: PlayerInteractEvent) {
        if (event.hand != EquipmentSlot.HAND || event.action !in setOf(Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK)) return
        val rightClick = event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK
        if (pick(event.player, rightClick)) event.isCancelled = true
    }
    @EventHandler
    fun rightClick(event: PlayerInteractEntityEvent) {
        if (event.hand == EquipmentSlot.HAND && dispatch(event.player, event.rightClicked, true)) event.isCancelled = true
    }
    @EventHandler
    fun rightClickAt(event: PlayerInteractAtEntityEvent) {
        if (event.hand == EquipmentSlot.HAND && dispatch(event.player, event.rightClicked, true)) event.isCancelled = true
    }
    @EventHandler
    fun leftClick(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        if (dispatch(player, event.entity, false)) event.isCancelled = true
    }

    private fun pick(player: Player, rightClick: Boolean): Boolean {
        val room = plugin.tables.tableOf(player.uniqueId) ?: return false
        if (player.world != room.table.center.world) return false
        val targets = rendered[room.table.id]?.targets?.get(player.uniqueId) ?: return false
        val eye = player.eyeLocation
        val direction = eye.direction
        val hit = targets.mapNotNull { target -> target.distance(eye, direction)?.let { target to it } }.minByOrNull { it.second } ?: return false
        val block = player.world.rayTraceBlocks(eye, direction, hit.second, FluidCollisionMode.NEVER, true)
        if (block != null) return false
        if (duplicate(player)) return true
        plugin.attempt(player) {
            when (hit.first.action) {
                "select" -> {
                    val view = room.view(player.uniqueId)
                    val selected = view.hand.any { it.token == hit.first.argument && it.selected }
                    val play = view.controls.firstOrNull { it.action == "play" }
                    if (rightClick && selected && play != null) plugin.tables.act(player, play.action, play.argument)
                    else plugin.tables.act(player, "select", hit.first.argument)
                }
                "cardtable:settings" -> plugin.menus.open(player, room)
                "cardtable:leave" -> plugin.tables.leave(player)
                "cardtable:bots" -> plugin.tables.bots(player, "fill")
                "cardtable:hand_page" -> {
                    handPages[player.uniqueId] = (handPages[player.uniqueId] ?: 0) + hit.first.argument!!.toInt()
                    invalidate(room.table.id)
                    refresh(room)
                }
                else -> plugin.tables.act(player, hit.first.action, hit.first.argument)
            }
        }
        return true
    }
    private fun duplicate(player: Player): Boolean {
        val tick = Bukkit.getCurrentTick()
        if (lastClick[player.uniqueId] == tick) return true
        lastClick[player.uniqueId] = tick
        return false
    }
    private fun dispatch(player: Player, entity: Entity, rightClick: Boolean): Boolean {
        if (pick(player, rightClick)) return true
        val data = entity.persistentDataContainer
        val table = data.get(tableKey, PersistentDataType.STRING) ?: return false
        val owner = data.get(ownerKey, PersistentDataType.STRING)
        if (owner != null && owner != player.uniqueId.toString()) return true
        val action = data.get(actionKey, PersistentDataType.STRING)!!
        if (action == "surface" || duplicate(player)) return true
        plugin.attempt(player) {
            require(player.world == entity.world && player.location.distanceSquared(entity.location) <= 36) { "请走近牌桌再操作。" }
            when (action) {
                "join" -> if (plugin.tables.tableOf(player.uniqueId)?.table?.id != table) plugin.tables.join(player, table)
            }
        }
        return true
    }

    fun remove(id: String) {
        bots.remove(id)
        rendered.remove(id)?.let { room ->
            room.targets.keys.forEach { handPages.remove(it) }
            room.frame.clear()
            (room.base + room.pile.values).forEach(Entity::remove)
            room.flights.clear()
            room.draws.forEach { it.flight.entity.remove() }
            room.draws.clear()
            room.skipped.clear()
            room.chunks.forEach { chunk ->
                val key = chunkKey(chunk)
                val count = chunkUsers.getValue(key) - 1
                if (count == 0) { chunkUsers.remove(key); chunk.removePluginChunkTicket(plugin) } else chunkUsers[key] = count
            }
        }
    }
    fun forget(playerId: UUID) { lastClick.remove(playerId); handPages.remove(playerId) }
    fun shutdown() { rendered.keys.toList().forEach(::remove) }
    private fun transform(scale: Float, rotation: Quaternionf = Quaternionf()) = Transformation(Vector3f(), rotation, Vector3f(scale), Quaternionf())
    private fun chunkKey(chunk: Chunk) = "${chunk.world.uid}:${chunk.x}:${chunk.z}"
}
