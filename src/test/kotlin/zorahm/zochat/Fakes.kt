package zorahm.zochat

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.Style
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Minimal Player/World stand-ins for tests. paper-api is compileOnly and there's no mocking library,
 * so these are JDK dynamic proxies: only the handful of methods the code under test calls are answered,
 * everything else returns null/false/0.
 */
object Fakes {

    fun world(name: String): World = proxy(World::class.java, name) { method, _ ->
        if (method.name == "getName") name else null
    }

    fun player(
        name: String,
        world: World? = null,
        x: Double = 0.0,
        y: Double = 64.0,
        z: Double = 0.0,
        permissions: Set<String> = emptySet(),
    ): Player = proxy(Player::class.java, name) { method, args ->
        when (method.name) {
            "getName" -> name
            "getWorld" -> world
            "getLocation" -> Location(world, x, y, z)
            "hasPermission" -> (args?.getOrNull(0) as? String)?.let { it in permissions }
            else -> null
        }
    }

    /** Every text leaf of [component] paired with its effective (inherited + own) style, in order. */
    fun leaves(component: Component, parent: Style = Style.empty()): List<Pair<String, Style>> {
        val style = parent.merge(component.style())
        val own = if (component is TextComponent && component.content().isNotEmpty()) {
            listOf(component.content() to style)
        } else {
            emptyList()
        }
        return own + component.children().flatMap { leaves(it, style) }
    }

    private fun <T> proxy(type: Class<T>, label: String, answer: (Method, Array<out Any?>?) -> Any?): T {
        val handler = InvocationHandler { self, method, args ->
            when {
                method.name == "equals" && method.parameterCount == 1 -> self === args!![0]
                method.name == "hashCode" && method.parameterCount == 0 -> System.identityHashCode(self)
                method.name == "toString" && method.parameterCount == 0 -> "${type.simpleName}($label)"
                else -> answer(method, args) ?: defaultFor(method.returnType)
            }
        }
        return type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
    }

    // A proxy returning null for a primitive return type throws NPE, so hand back the zero value.
    private fun defaultFor(type: Class<*>): Any? = when (type) {
        Boolean::class.javaPrimitiveType -> false
        Int::class.javaPrimitiveType -> 0
        Long::class.javaPrimitiveType -> 0L
        Double::class.javaPrimitiveType -> 0.0
        Float::class.javaPrimitiveType -> 0f
        Short::class.javaPrimitiveType -> 0.toShort()
        Byte::class.javaPrimitiveType -> 0.toByte()
        Char::class.javaPrimitiveType -> 0.toChar()
        else -> null
    }
}
