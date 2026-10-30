package egg.client.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import egg.client.command.impl.*;
import egg.client.module.Module;
import egg.client.module.ModuleManager;
import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.command.CommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;

import java.util.*;

public final class CommandManager {
    private static final CommandManager INSTANCE = new CommandManager();
    public static CommandManager getInstance() {
        return INSTANCE;
    }

    private final Map<String, Command> commands = new HashMap<>();
    private final CommandDispatcher<CommandSource> dispatcher = new CommandDispatcher<>();

    private CommandManager() {
        register(new HelpCommand());
        register(new WaypointCommand());
        register(new FriendCommand());
        register(new BindCommand());
        register(new ToggleCommand());
        register(new ConfigCommand());
// register(new CasesCommand());
        register(new ConfettiCommand());
        register(new GlobalsCommand());

        buildDispatcher();
    }

    private void buildDispatcher() {

        dispatcher.register(literal("help"));

        dispatcher.register(literal("confetti"));
        dispatcher.register(literal("totem"));

        for (String t : new String[]{"toggle", "t"}) {
            dispatcher.register(literal(t)
                    .then(argument("module", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (Module m : ModuleManager.getInstance().getModules()) {
                                    if (m.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                        builder.suggest(m.getName());
                                    }
                                }
                                return builder.buildFuture();
                            }))
            );
        }

        dispatcher.register(literal("bind")
                .then(argument("module", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Module m : ModuleManager.getInstance().getModules()) {
                                if (m.getName().toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                    builder.suggest(m.getName());
                                }
                            }
                            return builder.buildFuture();
                        })
                        .then(argument("key", StringArgumentType.word())))
        );

        dispatcher.register(literal("cases")
                .then(literal("open"))
                .then(literal("list"))
        );

        for (String f : new String[]{"friend", "f"}) {
            dispatcher.register(literal(f)
                    .then(literal("add").then(argument("name", StringArgumentType.word())))
                    .then(literal("del").then(argument("name", StringArgumentType.word())))
                    .then(literal("list"))
            );
        }

        for (String c : new String[]{"config", "cfg"}) {
            dispatcher.register(literal(c)
                    .then(literal("dir"))
                    .then(literal("save").then(argument("name", StringArgumentType.word())))
                    .then(literal("load").then(argument("name", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (String cfg : egg.client.config.ConfigManager.getConfigs()) {
                                    if (cfg.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                        builder.suggest(cfg);
                                    }
                                }
                                return builder.buildFuture();
                            })))
                    .then(literal("delete").then(argument("name", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (String cfg : egg.client.config.ConfigManager.getConfigs()) {
                                    if (cfg.toLowerCase(Locale.ROOT).startsWith(builder.getRemainingLowerCase())) {
                                        builder.suggest(cfg);
                                    }
                                }
                                return builder.buildFuture();
                            })))
                    .then(literal("list"))
            );
        }

        for (String wp : new String[]{"waypoint", "wp"}) {
            dispatcher.register(literal(wp)
                    .then(literal("add").then(argument("name", StringArgumentType.word())))
                    .then(literal("del").then(argument("name", StringArgumentType.word())))
                    .then(literal("list"))
            );
        }
    }

    private static LiteralArgumentBuilder<CommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<CommandSource, T> argument(String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    public CommandDispatcher<CommandSource> getDispatcher() {
        return dispatcher;
    }

    public void register(Command cmd) {
        commands.put(cmd.getName().toLowerCase(), cmd);
        for (String alias : cmd.getAliases()) {
            commands.put(alias.toLowerCase(), cmd);
        }
    }

    public boolean execute(String raw) {
        if (!raw.startsWith(".")) return false;
        String line = raw.substring(1).trim();
        if (line.isEmpty()) return false;

        String[] parts = line.split("\\s+");
        String name = parts[0].toLowerCase();
        Command cmd = commands.get(name);
        if (cmd == null) {
            sendClientMessage("§cНеизвестная команда. Введите §f.help §cдля списка команд.", false);
            return true;
        }

        String[] args = new String[parts.length - 1];
        System.arraycopy(parts, 1, args, 0, args.length);
        cmd.execute(args);
        return true;
    }

    public static void sendClientMessage(String msg) {
        sendClientMessage(msg, true);
    }

    public static void sendClientMessage(String msg, boolean prefix) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud == null) return;

        MutableText fullText = Text.empty();
        if (prefix) {
            int c1 = Theme.getAccent();
            int c2 = Theme.getAccentHover();
            fullText.append(Text.literal("E").styled(s -> s.withColor(TextColor.fromRgb(c1)).withBold(true)));
            fullText.append(Text.literal("g").styled(s -> s.withColor(TextColor.fromRgb(ColorUtil.interpolate(c1, c2, 0.33f))).withBold(true)));
            fullText.append(Text.literal("g").styled(s -> s.withColor(TextColor.fromRgb(ColorUtil.interpolate(c1, c2, 0.66f))).withBold(true)));
            fullText.append(Text.literal("V").styled(s -> s.withColor(TextColor.fromRgb(c2)).withBold(true)));
            fullText.append(Text.literal(" » ").styled(s -> s.withColor(TextColor.fromRgb(0x888888)).withBold(false)));
        }

        fullText.append(parseFormatted(msg));
        mc.inGameHud.getChatHud().addMessage(fullText);
    }

    private static Text parseFormatted(String text) {
        MutableText root = Text.empty();
        String[] parts = text.split("§");
        if (parts.length == 0) return root;

        root.append(Text.literal(parts[0]));
        TextColor currentColor = TextColor.fromRgb(0xFFFFFF);
        boolean bold = false;

        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;

            char code = p.charAt(0);
            String content = p.substring(1);

            switch (code) {
                case '0' -> currentColor = TextColor.fromRgb(0x000000);
                case '1' -> currentColor = TextColor.fromRgb(0x0000AA);
                case '2' -> currentColor = TextColor.fromRgb(0x00AA00);
                case '3' -> currentColor = TextColor.fromRgb(0x00AAAA);
                case '4' -> currentColor = TextColor.fromRgb(0xAA0000);
                case '5' -> currentColor = TextColor.fromRgb(0xAA00AA);
                case '6' -> currentColor = TextColor.fromRgb(0xFFAA00);
                case '7' -> currentColor = TextColor.fromRgb(0xAAAAAA);
                case '8' -> currentColor = TextColor.fromRgb(0x555555);
                case '9' -> currentColor = TextColor.fromRgb(0x5555FF);
                case 'a' -> currentColor = TextColor.fromRgb(0x55FF55);
                case 'b' -> currentColor = TextColor.fromRgb(0x55FFFF);
                case 'c' -> currentColor = TextColor.fromRgb(0xFF5555);
                case 'd' -> currentColor = TextColor.fromRgb(0xFF55FF);
                case 'e' -> currentColor = TextColor.fromRgb(0xFFFF55);
                case 'f' -> currentColor = TextColor.fromRgb(0xFFFFFF);
                case 'l' -> bold = true;
                case 'r' -> {
                    currentColor = TextColor.fromRgb(0xFFFFFF);
                    bold = false;
                }
            }

            if (!content.isEmpty()) {
                TextColor finalColor = currentColor;
                boolean finalBold = bold;
                root.append(Text.literal(content).styled(s -> s.withColor(finalColor).withBold(finalBold)));
            }
        }

        return root;
    }

    public Collection<Command> getCommands() {
        return commands.values();
    }
}
