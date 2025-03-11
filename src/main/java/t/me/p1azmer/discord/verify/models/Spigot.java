package t.me.p1azmer.discord.verify.models;

import lombok.Cleanup;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Objects;

@Slf4j
@UtilityClass
public class Spigot {
    private static final OkHttpClient CLIENT = new OkHttpClient();
    public static final int INVALID_ID = -1;

    public int fetchSpigotUserId(@NotNull String nickname) throws Exception {
        String url = "https://api.spigotmc.org/simple/0.2/index.php?action=findAuthor&name=" + nickname
                + "&t=" + System.currentTimeMillis();
        String response = makeApiRequest(url);
        if (response == null) return INVALID_ID;

        JSONObject json = new JSONObject(response);
        return json.has("id") ? json.getInt("id") : INVALID_ID;
    }

    public @Nullable String fetchSpigotUserDiscord(int userId) throws Exception {
        if (userId == INVALID_ID) return null;

        String url = "https://api.spigotmc.org/simple/0.2/index.php?action=getAuthor&id=" + userId
                + "&t=" + System.currentTimeMillis();
        String response = makeApiRequest(url);
        if (response == null) return null;

        JSONObject json = new JSONObject(response);
        return json.has("identities") && json.getJSONObject("identities").has("discord")
                ? json.getJSONObject("identities").getString("discord")
                : null;
    }

    public @Nullable String makeApiRequest(@NotNull String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build();

        try (@Cleanup Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("API request failed: HTTP {}", response.code());
                return null;
            }

            @Cleanup ResponseBody responseBody = response.body();
            return Objects.requireNonNull(responseBody, "response body is null!").string();
        }
    }
}
