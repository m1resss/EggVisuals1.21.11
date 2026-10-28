package egg.client.util;

public final class LayoutHelper {
    private LayoutHelper() {}

    private static final String RU = "йцукенгшщзхъфывапролджэячсмитьбю.ёЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ,Ё";
    private static final String EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,./`QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?~";

    public static String toEn(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = RU.indexOf(c);
            if (idx != -1 && idx < EN.length()) {
                sb.append(EN.charAt(idx));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String toRu(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int idx = EN.indexOf(c);
            if (idx != -1 && idx < RU.length()) {
                sb.append(RU.charAt(idx));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
