package egg.client.module.setting;

import java.util.Arrays;
import java.util.List;

public class ModeSetting extends Setting {
    private List<String> modes;
    private int index;

    public ModeSetting(String name, String defaultMode, String description, String... modes) {
        super(name, description);
        this.modes = new java.util.ArrayList<>(Arrays.asList(modes));
        int idx = this.modes.indexOf(defaultMode);
        this.index = (idx != -1) ? idx : 0;
    }

    public void setModes(List<String> newModes) {
        if (newModes == null || newModes.isEmpty()) return;
        String current = get();
        this.modes = new java.util.ArrayList<>(newModes);
        int idx = this.modes.indexOf(current);
        this.index = (idx != -1) ? idx : 0;
    }

    public String get() {
        if (modes.isEmpty()) return "";
        return modes.get(index % modes.size());
    }

    public void set(String mode) {
        int idx = modes.indexOf(mode);
        if (idx != -1) {
            this.index = idx;
        }
    }

    public void cycle() {
        if (modes.isEmpty()) return;
        index = (index + 1) % modes.size();
    }

    public void cyclePrev() {
        if (modes.isEmpty()) return;
        index = (index - 1 + modes.size()) % modes.size();
    }

    public boolean is(String mode) {
        return get().equalsIgnoreCase(mode);
    }

    public List<String> getModes() {
        return modes;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        if (index >= 0 && index < modes.size()) {
            this.index = index;
        }
    }
}
