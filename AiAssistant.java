package dev.aurora.core;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional AI helper. Needs your own Anthropic API key in config/aurora_ai_key.txt
 * (or the ANTHROPIC_API_KEY environment variable). Without a key the offline Advisor still works.
 */
public final class AiAssistant {
    private AiAssistant() {}

    private static final String MODEL = "claude-haiku-5-5";
    private static final Path KEY_FILE = FabricLoader.getInstance().getConfigDir().resolve("aurora_ai_key.txt");

    /** {role, text}; role is "user" or "assistant". */
    public static final List<String[]> chat = new CopyOnWriteArrayList<>();
    public static volatile boolean busy;
    public static volatile String error = "";

    private static String key() {
        try {
            if (Files.exists(KEY_FILE)) {
                String k = Files.readString(KEY_FILE, StandardCharsets.UTF_8).trim();
                if (!k.isEmpty()) return k;
            }
        } catch (Exception ignored) {}
        String env = System.getenv("ANTHROPIC_API_KEY");
        return env == null ? "" : env.trim();
    }

    public static boolean hasKey() { return !key().isEmpty(); }

    private static String system() {
        return """
            Ты помощник по настройке клиента Aurora для Minecraft 1.21.11 (Fabric). Клиент рассчитан на одиночную игру и собственные серверы.
            Отвечай по-русски, коротко (до 8 предложений), называй конкретные модули и конкретные значения настроек.
            Помогай с интерфейсом, виджетами, косметикой, плавностью, FPS, расположением элементов и комфортом.
            Не давай советов по обходу античитов, маскировке читов или тому, как избежать бана на публичных серверах.
            Если спрашивают об этом, коротко скажи, что с этим не поможешь, и предложи настроить комфорт и визуальную часть.
            Вот текущее состояние клиента (модули, включены ли они, настройки и допустимые диапазоны):
            """ + Advisor.snapshot();
    }

    public static void ask(String question) {
        if (busy) return;
        error = "";
        chat.add(new String[]{"user", question});
        if (!hasKey()) {
            error = "Нет API-ключа. Создай файл config/aurora_ai_key.txt и вставь туда свой ключ Anthropic (console.anthropic.com).";
            return;
        }
        busy = true;
        final String key = key();
        final String sys = system();
        Thread t = new Thread(() -> {
            try {
                JsonArray msgs = new JsonArray();
                int from = Math.max(0, chat.size() - 8);
                for (int i = from; i < chat.size(); i++) {
                    JsonObject m = new JsonObject();
                    m.addProperty("role", chat.get(i)[0]);
                    m.addProperty("content", chat.get(i)[1]);
                    msgs.add(m);
                }
                JsonObject body = new JsonObject();
                body.addProperty("model", MODEL);
                body.addProperty("max_tokens", 700);
                body.addProperty("system", sys);
                body.add("messages", msgs);

                HttpURLConnection c = (HttpURLConnection) URI.create("https://api.anthropic.com/v1/messages").toURL().openConnection();
                c.setRequestMethod("POST");
                c.setConnectTimeout(10_000);
                c.setReadTimeout(60_000);
                c.setDoOutput(true);
                c.setRequestProperty("content-type", "application/json");
                c.setRequestProperty("x-api-key", key);
                c.setRequestProperty("anthropic-version", "2023-06-01");
                try (OutputStream os = c.getOutputStream()) { os.write(body.toString().getBytes(StandardCharsets.UTF_8)); }

                int code = c.getResponseCode();
                InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
                String resp = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonObject o = JsonParser.parseString(resp).getAsJsonObject();
                if (code >= 400) {
                    error = o.has("error") && o.getAsJsonObject("error").has("message")
                            ? o.getAsJsonObject("error").get("message").getAsString() : "Ошибка HTTP " + code;
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (JsonElement e : o.getAsJsonArray("content")) {
                        JsonObject b = e.getAsJsonObject();
                        if (b.has("text")) sb.append(b.get("text").getAsString());
                    }
                    chat.add(new String[]{"assistant", sb.toString().trim()});
                }
            } catch (Exception e) {
                error = "Не удалось связаться с API: " + e.getMessage();
            } finally {
                busy = false;
            }
        }, "aurora-ai");
        t.setDaemon(true);
        t.start();
    }
}
