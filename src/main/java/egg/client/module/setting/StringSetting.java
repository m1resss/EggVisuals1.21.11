package egg.client.module.setting;

public class StringSetting extends Setting {
    private String value;

    public StringSetting(String name, String defaultValue, String description) {
        super(name, description);
        this.value = defaultValue;
    }

    public String get() {
        return value;
    }

    public void set(String value) {
        this.value = value != null ? value : "";
    }
}
