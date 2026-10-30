package dev.myclient.fake;

import dev.myclient.Settings;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * With "Fake /pay" on, typing /pay <player> <amount> is NOT sent to the server. A message appears at the
 * bottom of YOUR screen (action bar) and your fake scoreboard money goes down. Nobody else sees anything.
 */
public final class FakePay {
    private FakePay() {}

    public static void show(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Text t = FakeScoreboard.parse(text);
        mc.inGameHud.setOverlayMessage(t, false);
        if (Settings.payInChat) mc.player.sendMessage(t, false);
    }

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!Settings.fakePay) return true;
            String[] p = command.trim().split("\\s+");
            if (p.length < 3 || !p[0].equalsIgnoreCase("pay")) return true;
            if (MinecraftClient.getInstance().player == null) return true;
            String amount = p[2].replaceFirst("^\\$", "");
            show(Settings.payFormat.replace("{player}", p[1]).replace("{amount}", amount));
            if (Settings.payDeduct) {
                Double v = FakeScoreboard.parseAmount(amount);
                if (v != null) Settings.fakeMoney = Math.max(0, Settings.fakeMoney - v);
            }
            return false;
        });
    }
}
