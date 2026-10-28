package delta.discord;


import delta.lib.jsoup.Connection;
import java.io.IOException;

@FunctionalInterface
public interface ConnectionFactory {
    Connection create(String str) throws IOException;
}
