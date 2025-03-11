package t.me.p1azmer.discord.verify;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;
import t.me.p1azmer.discord.verify.config.Config;
import t.me.p1azmer.discord.verify.models.Spigot;
import t.me.p1azmer.discord.verify.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j(topic = "[Bot]")
public class Main extends ListenerAdapter {

    private static final Cache<String, String> VERIFICATION_CODES = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private static final ThreadLocalRandom RANDOM = ThreadLocalRandom.current();
    private static final String GUILD_ID_KEY = "guild.id";
    private static final String CHANNEL_ID_KEY = "channel.id";
    private static final String ADMIN_ROLE_ID_KEY = "admin.role.id";
    private static final String VERIFY_ROLE_ID_KEY = "verify.role.id";
    private static final String ASSIGN_ROLES_KEY = "assign.roles";
    private static final String BOT_TOKEN_KEY = "bot.token";
    private static final String CODE_FORMAT_KEY = "generation.code.name";
    private static final String DELETE_MESSAGES_KEY = "delete-messages-in-channel";
    private static final int CODE_LENGTH = 6;
    private static JDA jda;

    public static void main(String[] args) {
        try {
            loadConfig();
            jda = initializeJDA();
            registerCommands(jda);
            addShutdownHook();
            log.info("Bot successfully started!");
        } catch (Exception exception) {
            log.error("Got an exception while starting the bot", exception);
        }
    }

    private static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(Main::unload, "Shutdown Thread"));
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

    private static void unload() {
        shutdownJDA();
        log.info("Bot has been unloaded!");
    }

    private static void loadConfig() {
        File file = new File("config/config.yml");
        FileUtils.createFile(file);

        try (InputStream input = Main.class.getResourceAsStream("/config.yml")) {
            if (input != null) {
                FileUtils.copyFile(input, file);
                log.info("Successfully copied default config.yml");
            } else {
                log.error("Resource 'config.yml' not found in src/main/resources");
            }
        } catch (Exception exception) {
            log.error("Got an exception while copying default config.yml", exception);
        }

        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            Config.setConfigMap(yaml.loadAs(fileInputStream, Map.class));
            log.info("Successfully loaded config.yml");
        } catch (Exception exception) {
            log.error("Got an exception while loading config.yml", exception);
        }
    }

    private static @NotNull JDA initializeJDA() throws Exception {
        String token = Config.getConfigString(BOT_TOKEN_KEY);
        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("Bot token not specified or empty in config.yml");
        }

        token = token.trim();
        log.info("Using bot token: '{}'", token);
        return JDABuilder.createDefault(token, EnumSet.of(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER,
                        CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS)
                .addEventListeners(new Main())
                .build()
                .awaitReady();
    }

    private static void registerCommands(@NotNull JDA jda) {
        String guildId = Config.getConfigString(GUILD_ID_KEY);
        if (guildId != null && !guildId.isEmpty()) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null) {
                guild.updateCommands().addCommands(
                        Commands.slash("verify", "Start verifying your SpigotMC account")
                                .addOption(OptionType.STRING, "username", "Your username on SpigotMC", true),
                        Commands.slash("done", "Complete your SpigotMC verification"),
                        Commands.slash("reload", "Reload bot configuration")
                ).queue();
            } else {
                log.warn("Guild with ID {} not found. Commands not registered.", guildId);
            }
        } else {
            log.warn("Guild ID not specified in config.yml. Commands not registered.");
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (!isCorrectGuild(event.getGuild()) || !isCorrectChannel(event)) return;

        event.deferReply(true).queue();
        String commandName = event.getName();
        User user = event.getUser();

        switch (commandName) {
            case "verify" -> handleVerify(event, user);
            case "done" -> handleDone(event, user);
            case "reload" -> handleReload(event);
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        boolean enabledDeleteMessages = Config.getConfigBoolean(DELETE_MESSAGES_KEY);
        if (!enabledDeleteMessages) return;

        if (!isCorrectGuild(event.getGuild()) || !isCorrectChannel(event.getChannel().getId())) {
            return;
        }

        Message message = event.getMessage();
        String content = message.getContentRaw();
        User author = event.getAuthor();
        if (author.isBot() || content.startsWith("/") || hasAdminRole(event)) return;

        message.delete().queue(
                success -> log.info("Deleted message from {}: {}", author.getName(), content),
                failure -> log.error("Failed to delete message from {}", author.getName(), failure)
        );
    }

    private static boolean isCorrectGuild(@Nullable Guild guild) {
        String guildId = Config.getConfigString(GUILD_ID_KEY);
        return guild != null && guild.getId().equals(guildId);
    }

    private static boolean isCorrectChannel(@NotNull SlashCommandInteractionEvent event) {
        return event.getChannel().getId().equals(Config.getConfigString(CHANNEL_ID_KEY));
    }

    private static boolean isCorrectChannel(@NotNull String channelId) {
        return channelId.equals(Config.getConfigString(CHANNEL_ID_KEY));
    }

    private static boolean hasAdminRole(@NotNull MessageReceivedEvent event) {
        if (event.getMember() == null) return false;

        String adminRoleId = Config.getConfigString(ADMIN_ROLE_ID_KEY);
        return event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
    }

    private static void handleVerify(@NotNull SlashCommandInteractionEvent event, @NotNull User user) {
        if (isAlreadyVerified(event)) {
            replyEphemeral(event, "messages.verify.already-verified");
            return;
        }

        String nickname = getUsernameOption(event);
        if (nickname == null) {
            replyEphemeral(event, "messages.verify.usage");
            return;
        }

        String code = generateCode();
        VERIFICATION_CODES.put(user.getId(), nickname + "#" + code);
        replyEphemeral(event, "messages.verify.instruction", "{code}", code);
    }

    private static void handleDone(@NotNull SlashCommandInteractionEvent event, @NotNull User user) {
        if (isAlreadyVerified(event)) {
            replyEphemeral(event, "messages.verify.already-verified");
            return;
        }

        String data = VERIFICATION_CODES.getIfPresent(user.getId());
        if (data == null) {
            replyEphemeral(event, "messages.done.no-verify");
            return;
        }

        verifyUser(event, user, data);
    }

    private static void handleReload(@NotNull SlashCommandInteractionEvent event) {
        if (!isAdmin(event)) return;
        loadConfig();
        replyEphemeral(event, "messages.reload.success");
    }

    private static boolean isAdmin(@NotNull SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return false;

        String adminRoleId = Config.getConfigString(ADMIN_ROLE_ID_KEY);
        boolean isAdmin = event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(adminRoleId));
        if (!isAdmin) {
            replyEphemeral(event, "messages.reload.no-permission");
        }
        return isAdmin;
    }

    private static boolean isAlreadyVerified(@NotNull SlashCommandInteractionEvent event) {
        if (event.getMember() == null) return false;
        String verifyRoleId = Config.getConfigString(VERIFY_ROLE_ID_KEY);
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
        return Objects.requireNonNull(Config.getConfigString(CODE_FORMAT_KEY), "message " + CODE_FORMAT_KEY + " not found in config!").replace("{generated_code}", verificationCode);
    }

    private static void verifyUser(@NotNull SlashCommandInteractionEvent event, @NotNull User user, @NotNull String data) {
        String[] parts = data.split("#");
        String nickname = parts[0].replaceAll("\\s+", "");
        String code = parts[1].replaceAll("\\s+", "");

        try {
            int userId = Spigot.fetchSpigotUserId(nickname);
            if (userId == Spigot.INVALID_ID) {
                replyEphemeral(event, "messages.verify.discord.identifier.user-not-found", "{username}", nickname);
                return;
            }

            String discordTag = Spigot.fetchSpigotUserDiscord(userId);
            if (discordTag == null) {
                replyEphemeral(event, "messages.verify.discord.identifier.tag-not-found", "{code}", code);
                return;
            }

            if (discordTag.equals(code)) {
                replyEphemeral(event, "messages.verify.success");
                assignVerificationRoles(event);
                VERIFICATION_CODES.invalidate(user.getId());
            } else {
                replyEphemeral(event, "messages.verify.failure", "{actual_info}", discordTag, "{code}", code);
            }
        } catch (Exception e) {
            log.error("Error verifying user", e);
            replyEphemeral(event, "messages.verify.error");
        }
    }

    private static void assignVerificationRoles(@NotNull SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            log.warn("Cannot assign roles: Guild is null");
            return;
        }

        assignRole(guild, event, Objects.requireNonNull(Config.getConfigString(VERIFY_ROLE_ID_KEY), "message " + VERIFY_ROLE_ID_KEY + " not found in config!"));
        Objects.requireNonNull(Config.getConfigStringList(ASSIGN_ROLES_KEY), "list " + ASSIGN_ROLES_KEY + " not found!").forEach(roleId -> assignRole(guild, event, roleId));
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
        String message = Objects.requireNonNull(Config.getConfigString(messageKey), "Message not found in config: " + messageKey);
        for (int i = 0; i < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        event.getHook().sendMessage(message).setEphemeral(true).queue();
    }
}