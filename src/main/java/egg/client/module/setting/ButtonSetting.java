package egg.client.module.setting;

public class ButtonSetting extends Setting {
    private final Runnable action;

    public ButtonSetting(String name, String description, Runnable action) {
        super(name, description);
        this.action = action;
    }

    public void run() {
        if (action != null) {
            action.run();
        }
    }
}
