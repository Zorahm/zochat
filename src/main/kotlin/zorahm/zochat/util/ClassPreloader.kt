package zorahm.zochat.util

import java.io.File
import java.util.jar.JarFile
import java.util.logging.Logger

/**
 * Eagerly loads the plugin's classes during onEnable. The JVM loads classes lazily on first use,
 * so PluginClassLoader ends up reading the shaded multi-MB jar from disk on the MAIN thread
 * mid-tick — on hosts with slow I/O the watchdog caught exactly this stalling the server for 10+
 * seconds (first bubble spawn after startup). Paying the disk reads once at startup, when a pause
 * is harmless and the jar is still in the OS page cache, removes the mid-game stall entirely.
 */
object ClassPreloader {

    fun preload(jar: File, loader: ClassLoader, logger: Logger) {
        val start = System.nanoTime()
        var loaded = 0
        try {
            JarFile(jar).use { jf ->
                for (entry in jf.entries()) {
                    val name = entry.name
                    if (!name.endsWith(".class") || !name.startsWith("zorahm/zochat/")) continue
                    // MySQL/protobuf are only touched from the async DB thread — a lazy load there
                    // can't freeze the server, so don't slow startup with their ~3000 classes.
                    if (name.startsWith("zorahm/zochat/libs/mysql/")) continue
                    if (name.startsWith("zorahm/zochat/libs/protobuf/")) continue

                    val className = name.removeSuffix(".class").replace('/', '.')
                    try {
                        // initialize=false: read+define the bytes (the expensive disk part) without
                        // running static initializers, so preloading has no side effects.
                        Class.forName(className, false, loader)
                        loaded++
                    } catch (_: Throwable) {
                        // Classes against optional compileOnly deps (PlaceholderAPI) fail to load
                        // when the dep is absent — first real use is already guarded elsewhere.
                    }
                }
            }
        } catch (e: Exception) {
            logger.warning("Class preload skipped: ${e.message}")
            return
        }
        logger.info("Preloaded $loaded classes in ${(System.nanoTime() - start) / 1_000_000} ms")
    }
}
