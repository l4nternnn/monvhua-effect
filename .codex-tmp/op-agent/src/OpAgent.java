import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public final class OpAgent {
    private OpAgent() {
    }

    public static void premain(String args, Instrumentation instrumentation) throws Exception {
        agentmain(args, instrumentation);
    }

    public static void agentmain(String args, Instrumentation instrumentation) throws Exception {
        String[] parts = (args == null ? "" : args).split(",", -1);
        String playerName = parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : "shushuwonie";
        UUID uuid = parts.length > 1 && !parts[1].isBlank() ? UUID.fromString(parts[1].trim()) : null;

        Class<?> serverClass = findLoadedClass(instrumentation, "net.minecraft.server.MinecraftServer");
        if (serverClass == null) {
            throw new IllegalStateException("MinecraftServer class is not loaded");
        }

        Object server = findServerInstance(serverClass);
        if (server == null) {
            throw new IllegalStateException("Could not find running MinecraftServer instance");
        }

        Runnable task = () -> {
            try {
                opPlayer(serverClass, server, playerName, uuid);
            } catch (Throwable throwable) {
                throwable.printStackTrace(System.err);
                throw new RuntimeException(throwable);
            }
        };

        try {
            Method executeSync = serverClass.getMethod("executeSync", Runnable.class);
            executeSync.invoke(server, task);
        } catch (NoSuchMethodException missingExecuteSync) {
            task.run();
        }
    }

    private static Class<?> findLoadedClass(Instrumentation instrumentation, String name) {
        for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
            if (clazz.getName().equals(name)) {
                return clazz;
            }
        }
        return null;
    }

    private static Object findServerInstance(Class<?> serverClass) throws Exception {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (!"Server thread".equals(thread.getName())) {
                continue;
            }

            Object target = getThreadTask(thread);
            Object server = findServerInObjectGraph(target, serverClass, new IdentityHashMap<>(), 0);
            if (server != null) {
                return server;
            }
        }
        return null;
    }

    private static Object getThreadTask(Thread thread) throws Exception {
        try {
            Field targetField = Thread.class.getDeclaredField("target");
            targetField.setAccessible(true);
            return targetField.get(thread);
        } catch (NoSuchFieldException ignored) {
            Field holderField = Thread.class.getDeclaredField("holder");
            holderField.setAccessible(true);
            Object holder = holderField.get(thread);
            if (holder == null) {
                return null;
            }
            Field taskField = holder.getClass().getDeclaredField("task");
            taskField.setAccessible(true);
            return taskField.get(holder);
        }
    }

    private static Object findServerInObjectGraph(
            Object object,
            Class<?> serverClass,
            IdentityHashMap<Object, Boolean> visited,
            int depth
    ) throws Exception {
        if (object == null || depth > 4 || visited.containsKey(object)) {
            return null;
        }
        visited.put(object, Boolean.TRUE);

        if (serverClass.isInstance(object)) {
            return object;
        }

        if (object instanceof AtomicReference<?> reference) {
            Object value = reference.get();
            if (serverClass.isInstance(value)) {
                return value;
            }
        }

        Class<?> clazz = object.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                Class<?> type = field.getType();
                if (type.isPrimitive()) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(object);
                Object server = findServerInObjectGraph(value, serverClass, visited, depth + 1);
                if (server != null) {
                    return server;
                }
            }
            clazz = clazz.getSuperclass();
        }

        return null;
    }

    private static void opPlayer(Class<?> serverClass, Object server, String playerName, UUID uuid) throws Exception {
        Object playerManager = serverClass.getMethod("getPlayerManager").invoke(server);

        Object profile = null;
        try {
            Object player = playerManager.getClass().getMethod("getPlayer", String.class).invoke(playerManager, playerName);
            if (player != null) {
                profile = player.getClass().getMethod("getGameProfile").invoke(player);
            }
        } catch (NoSuchMethodException ignored) {
            // Fall back to a GameProfile built from the UUID passed by the launcher/usercache.
        }

        if (profile == null) {
            if (uuid == null) {
                throw new IllegalStateException("Player is not online and no UUID was provided: " + playerName);
            }
            ClassLoader loader = serverClass.getClassLoader();
            Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile", false, loader);
            profile = gameProfileClass.getConstructor(UUID.class, String.class).newInstance(uuid, playerName);
        }

        Class<?> gameProfileClass = Class.forName("com.mojang.authlib.GameProfile", false, serverClass.getClassLoader());
        Method addToOperators = playerManager.getClass().getMethod("addToOperators", gameProfileClass);
        addToOperators.invoke(playerManager, profile);
        System.out.println("[OpAgent] Granted OP to " + playerName);
    }
}
