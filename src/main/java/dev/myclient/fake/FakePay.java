package dev.myclient.fake;

import dev.myclient.Settings;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * With "Fake /pay" on, typing /pay <player> <amount> is NOT sent to the server. Only a chat line appears
 * on your screen (and optionally your fake scoreboard money goes down). Nobody else sees anything.
 */
public final class FakePay {
    private FakePay() {}

    public static void register() {
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
            if (!Settings.fakePay) return true;
            String[] p = command.trim().split("\\s+");
            if (p.length < 3 || !p[0].equalsIgnoreCase("pay")) return true;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return true;
            String msg = Settings.payFormat.replace("{player}", p[1]).replace("{amount}", p[2]);
            mc.player.sendMessage(Text.literal(msg.replace('&', '\u00a7')), false);
            if (Settings.payDeduct) {
                Double v = FakeScoreboard.parseAmount(p[2]);
                if (v != null) Settings.fakeMoney = Math.max(0, Settings.fakeMoney - v);
            }
            return false;
        });
    }
}
