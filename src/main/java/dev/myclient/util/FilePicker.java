package dev.myclient.util;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Consumer;

/** Opens the system "choose a file" dialog for PNGs (via LWJGL tinyfd, which Minecraft ships). */
public final class FilePicker {
    private FilePicker() {}

    public static void pickPng(String title, Consumer<Path> onPicked, Runnable onUnavailable) {
        Thread th = new Thread(() -> {
            String res = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(1);
                filters.put(stack.UTF8("*.png"));
                filters.flip();
                Class<?> cls = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
                Method m = cls.getMethod("tinyfd_openFileDialog",
                    CharSequence.class, CharSequence.class, PointerBuffer.class, CharSequence.class, boolean.class);
                res = (String) m.invoke(null, title, "", filters, "PNG images", false);
            } catch (Throwable t) {
                MinecraftClient.getInstance().execute(onUnavailable);
                return;
            }
            final String r = res;
            if (r != null) MinecraftClient.getInstance().execute(() -> onPicked.accept(Path.of(r)));
        }, "myclient-filepicker");
        th.setDaemon(true);
        th.start();
    }
}
