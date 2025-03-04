package t.me.p1azmer.discord.verify;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "[Bot]")
public class Main extends ListenerAdapter {

    static final Properties CONFIG = new Properties();
    static final Cache<String, String> VERIFICATION_CODES = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    static final OkHttpClient CLIENT = new OkHttpClient();
    static final Random RANDOM = new Random();
    static final String CHANNEL_ID_KEY = "channel.id";
    static final String ADMIN_ROLE_ID_KEY = "admin.role.id";
    static final String VERIFY_ROLE_ID_KEY = "verify.role.id";
    static final String ASSIGN_ROLE_IDS_KEY = "assign.role.ids";
    static final String BOT_TOKEN_KEY = "bot.token";
    static final String CODE_FORMAT_KEY = "generation.code.name";
    static final int CODE_LENGTH = 6;
    static final int INVALID_ID = -1;
    static JDA jda;

    public static void main(String[] args) {
        try {
            loadConfig();
            jda = initializeJDA();
            registerCommands(jda);
            addShutdownHook();
            log.info("Bot is ready!");
        } catch (Exception exception) {
            log.error("Got an exception while starting the bot", exception);
        }
    }

    private static void unload() {
        shutdownJDA();
        log.info("Bot has been unloaded!");
    }

    private static void shutdownJDA() {
        if (jda == null) return;

        jda.shutdown();
        try {
            if (!jda.awaitShutdown(5, TimeUnit.SECONDS)) {
                jda.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::unload, "Shutdown Thread"));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!isCorrectChannel(event)) {
            return;
        }

        event.deferReply(true).queue();
        String commandName = event.getName();
        User user = event.getUser();

        switch (commandName) {
            case "verify" -> handleVerify(event, user);
            case "done" -> handleDone(event, user);
            case "reload" -> handleReload(event);
        }
    }

    private static @NotNull JDA initializeJDA() throws Exception {
        String token = CONFIG.getProperty(BOT_TOKEN_KEY);
        return JDABuilder.create(token, EnumSet.of(GatewayIntent.GUILD_MEMBERS))
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER,
                        CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(new Main())
                .build()
                .awaitReady();
    }

    private static void registerCommands(@NotNull JDA jda) {
        jda.updateCommands().addCommands(
                Commands.slash("verify", "Start verifying your SpigotMC account")
                        .addOption(OptionType.STRING, "username", "Your username on SpigotMC", true),
                Commands.slash("done", "Complete your SpigotMC verification"),
                Commands.slash("reload", "Reload bot configuration")
        ).queue();
    }

    private static boolean isCorrectChannel(@NotNull SlashCommandInteractionEvent event) {
        return event.getChannel().getId().equals(CONFIG.getProperty(CHANNEL_ID_KEY));
    }

    private static void handleVerify(@NotNull SlashCommandInteractionEvent event, @NotNull User user) {
        if (isAlreadyVerified(event)) {
            replyEphemeral(event, "message.verify.already_verified");
            return;
        }

        String nickname = getUsernameOption(event);
        if (nickname == null) {
            replyEphemeral(event, "message.verify.usage");
            return;
        }

        String code = generateCode();
        VERIFICATION_CODES.put(user.getId(), nickname + "#" + code);
        replyEphemeral(event, "message.verify.instruction", "{code}", code);
    }

    private static void handleDone(@NotNull SlashCommandInteractionEvent event, @NotNull User user) {
        if (isAlreadyVerified(event)) {
            replyEphemeral(event, "message.verify.already_verified");
            return;
        }

        String data = VERIFICATION_CODES.getIfPresent(user.getId());
        if (data == null) {
            replyEphemeral(event, "message.done.no_verify");
            return;
        }

        verifyUser(event, user, data);
    }

    private static void handleReload(@NotNull SlashCommandInteractionEvent event) {
        if (!isAdmin(event)) {
            return;
        }
        loadConfig();
        replyEphemeral(event, "message.reload.success");
    }

    private static boolean isAdmin(@NotNull SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return false;

        String adminRoleId = CONFIG.getProperty(ADMIN_ROLE_ID_KEY);
        boolean isAdmin = event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        if (!isAdmin) {
            replyEphemeral(event, "message.reload.no_permission");
        }
        return isAdmin;
    }

    private static boolean isAlreadyVerified(@NotNull SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return false;
        String verifyRoleId = CONFIG.getProperty(VERIFY_ROLE_ID_KEY);
        return event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(verifyRoleId));
    }

    private static @Nullable String getUsernameOption(@NotNull SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption("username");
        return option != null ? option.getAsString() : null;
    }

    private static @NotNull String generateCode() {
        int code = RANDOM.nextInt((int) Math.pow(10, CODE_LENGTH));
        String verificationCode = String.format("%0" + CODE_LENGTH + "d", code);
        return CONFIG.getProperty(CODE_FORMAT_KEY).replace("{generated_code}", verificationCode);
    }

    private static void verifyUser(@NotNull SlashCommandInteractionEvent event, @NotNull User user, @NotNull String data) {
        String[] parts = data.split("#");
        String nickname = parts[0].replaceAll("\\s+", "");
        String code = parts[1].replaceAll("\\s+", "");

        try {
            int userId = fetchSpigotUserId(nickname);
            if (userId == INVALID_ID) {
                replyEphemeral(event, "message.verify.discord.identifier.user_not_found", "{username}", nickname);
                return;
            }

            String discordTag = fetchSpigotUserDiscord(userId);
            if (discordTag == null) {
                replyEphemeral(event, "message.verify.discord.identifier.tag_not_found", "{code}", code);
                return;
            }

            if (discordTag.equals(code)) {
                replyEphemeral(event, "message.verify.success");
                assignVerificationRoles(event);
                VERIFICATION_CODES.invalidate(user.getId());
            } else {
                replyEphemeral(event, "message.verify.failure", "{actual_info}", discordTag, "{code}", code);
            }
        } catch (Exception e) {
            log.error("Error verifying user", e);
            replyEphemeral(event, "message.verify.error");
        }
    }

    private static int fetchSpigotUserId(@NotNull String nickname) throws Exception {
        String url = "https://api.spigotmc.org/simple/0.2/index.php?action=findAuthor&name=" + nickname
                + "&t=" + System.currentTimeMillis();
        String response = makeApiRequest(url);
        if (response == null) return INVALID_ID;

        JSONObject json = new JSONObject(response);
        return json.has("id") ? json.getInt("id") : INVALID_ID;
    }

    private static @Nullable String fetchSpigotUserDiscord(int userId) throws Exception {
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

    private static @Nullable String makeApiRequest(@NotNull String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("API request failed: HTTP {}", response.code());
                return null;
            }
            @Cleanup ResponseBody body = response.body();
            if (body == null) return null;

            return body.string();
        }
    }

    private static void assignVerificationRoles(@NotNull SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            log.warn("Cannot assign roles: Guild is null");
            return;
        }

        assignRole(guild, event, CONFIG.getProperty(VERIFY_ROLE_ID_KEY));
        String roleIdsString = CONFIG.getProperty(ASSIGN_ROLE_IDS_KEY);
        if (roleIdsString != null && !roleIdsString.isEmpty()) {
            Arrays.stream(roleIdsString.split(","))
                    .map(String::trim)
                    .forEach(roleId -> assignRole(guild, event, roleId));
        }
    }

    private static void assignRole(@NotNull Guild guild, @NotNull SlashCommandInteractionEvent event, @NotNull String roleId) {
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            log.warn("Role with ID {} not found in guild", roleId);
            return;
        }

        guild.addRoleToMember(event.getMember(), role).queue(
                success -> log.info("Assigned role {} to {}", role.getName(), event.getUser().getName()),
                failure -> log.error("Failed to assign role {}", roleId, failure)
        );
    }

    private static void replyEphemeral(@NotNull SlashCommandInteractionEvent event, @NotNull String messageKey, @NotNull String... replacements) {
        String message = CONFIG.getProperty(messageKey);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        event.getHook().sendMessage(message).setEphemeral(true).queue();
    }

    private static void loadConfig() {
        try (InputStream input = Main.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                log.error("Can't find config.properties");
                return;
            }
            CONFIG.load(input);
            log.info("Configuration loaded successfully!");
        } catch (Exception e) {
            log.error("Got an exception while loading config.properties", e);
        }
    }
}