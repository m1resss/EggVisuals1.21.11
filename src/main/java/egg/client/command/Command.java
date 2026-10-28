package egg.client.command;

public abstract class Command {
    private final String name;
    private final String description;
    private final String syntax;
    private final String[] aliases;

    public Command(String name, String description, String syntax, String... aliases) {
        this.name = name;
        this.description = description;
        this.syntax = syntax;
        this.aliases = aliases != null ? aliases : new String[0];
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSyntax() {
        return syntax;
    }

    public String[] getAliases() {
        return aliases;
    }

    public abstract void execute(String[] args);
}
