package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.module.ModuleManager;
import egg.client.module.render.Lyrics;
import egg.client.util.MediaTracker;

import java.util.Arrays;

public class LyricsCommand extends Command {
    public LyricsCommand() {
        super("lyrics", "Управление модулем отображения текста песен в мире", ".lyrics <play/stop/pos/demo/status>", "lyr", "lrc");
    }

    @Override
    public void execute(String[] args) {
        Lyrics lyrics = ModuleManager.getInstance().getModule(Lyrics.class);
        if (lyrics == null) {
            CommandManager.sendClientMessage("§cМодуль Lyrics не найден!");
            return;
        }

        MediaTracker mt = MediaTracker.getInstance();

        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            CommandManager.sendClientMessage("§d=== Lyrics Status ===");
            CommandManager.sendClientMessage("§7Модуль: " + (lyrics.isEnabled() ? "§aВключен" : "§cВыключен"));
            CommandManager.sendClientMessage("§7Плеер GSMTC / Окно: " + (mt.isPlaying() ? "§aИграет" : "§eОстановлен"));
            CommandManager.sendClientMessage("§7Трек: §f" + (mt.getTrackTitle().isEmpty() ? "§8(не определен)" : (mt.getTrackArtist().isEmpty() ? "" : mt.getTrackArtist() + " - ") + mt.getTrackTitle()));
            CommandManager.sendClientMessage("§7Позиция: §f" + (mt.getTrackElapsedMs() / 1000) + " сек / " + mt.getTrackDurationSec() + " сек");
            CommandManager.sendClientMessage("§7Загружено строк: §f" + (lyrics.getActiveLyrics() != null ? lyrics.getActiveLyrics().size() : 0));
            CommandManager.sendClientMessage("§7Команды: §d.lyrics play <автор> - <трек> §7| §d.lyrics demo §7| §d.lyrics stop §7| §d.lyrics pos <сек>");
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "demo" -> {
                if (!lyrics.isEnabled()) {
                    lyrics.toggle();
                }
                lyrics.playDemo();
            }
            case "stop" -> {
                lyrics.stopManual();
            }
            case "pos", "seek" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("§cИспользование: .lyrics pos <секунды>");
                    return;
                }
                try {
                    long sec = Long.parseLong(args[1]);
                    lyrics.seekManual(sec);
                } catch (NumberFormatException e) {
                    CommandManager.sendClientMessage("§cУкажите число секунд!");
                }
            }
            case "play" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("§cИспользование: .lyrics play <исполнитель> - <трек>");
                    return;
                }
                String fullQuery = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                String artist = "";
                String title = fullQuery;

                if (fullQuery.contains(" - ")) {
                    String[] parts = fullQuery.split(" - ", 2);
                    artist = parts[0].trim();
                    title = parts[1].trim();
                } else if (fullQuery.contains(" — ")) {
                    String[] parts = fullQuery.split(" — ", 2);
                    artist = parts[0].trim();
                    title = parts[1].trim();
                }

                if (!lyrics.isEnabled()) {
                    lyrics.toggle();
                }
                lyrics.playManual(artist, title);
            }
            default -> {
                String fullQuery = String.join(" ", args).trim();
                String artist = "";
                String title = fullQuery;
                if (fullQuery.contains(" - ")) {
                    String[] parts = fullQuery.split(" - ", 2);
                    artist = parts[0].trim();
                    title = parts[1].trim();
                }

                if (!lyrics.isEnabled()) {
                    lyrics.toggle();
                }
                lyrics.playManual(artist, title);
            }
        }
    }
}
