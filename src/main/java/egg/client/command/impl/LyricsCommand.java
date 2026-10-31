package egg.client.command.impl;

import egg.client.command.Command;
import egg.client.command.CommandManager;
import egg.client.module.ModuleManager;
import egg.client.module.render.Lyrics;

import java.util.Arrays;

public class LyricsCommand extends Command {
    public LyricsCommand() {
        super("lyrics", "Управление модулем отображения текста песен в скайбоксе", ".lyrics <play/stop/pos/demo>", "lyr", "lrc");
    }

    @Override
    public void execute(String[] args) {
        Lyrics lyrics = ModuleManager.getInstance().getModule(Lyrics.class);
        if (lyrics == null) {
            CommandManager.sendClientMessage("§cМодуль Lyrics не найден!");
            return;
        }

        if (args.length == 0) {
            CommandManager.sendClientMessage("§d.lyrics play <исполнитель> - <трек> §7- запуск текста для любого трека");
            CommandManager.sendClientMessage("§d.lyrics demo §7- включить демонстрационный трек");
            CommandManager.sendClientMessage("§d.lyrics stop §7- остановить ручной трек");
            CommandManager.sendClientMessage("§d.lyrics pos <секунды> §7- перемотка позиции трека");
            return;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "demo" -> {
                if (!lyrics.isEnabled()) {
                    lyrics.toggle();
                }
                lyrics.playDemo();
                CommandManager.sendClientMessage("§aДемонстрационный трек Queen - Bohemian Rhapsody запущен в скайбоксе!");
            }
            case "stop" -> {
                lyrics.stopManual();
                CommandManager.sendClientMessage("§eВоспроизведение текста остановлено.");
            }
            case "pos", "seek" -> {
                if (args.length < 2) {
                    CommandManager.sendClientMessage("§cИспользование: .lyrics pos <секунды>");
                    return;
                }
                try {
                    long sec = Long.parseLong(args[1]);
                    lyrics.seekManual(sec);
                    CommandManager.sendClientMessage("§aПозиция воспроизведения установлена на: §f" + sec + " сек.");
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
                CommandManager.sendClientMessage("§aЗапущен поиск и показ текста для: §f" + (artist.isEmpty() ? "" : artist + " — ") + title);
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
                CommandManager.sendClientMessage("§aЗапущен поиск текста для: §f" + fullQuery);
            }
        }
    }
}
