import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.winlaufen.web.bridge.config.*;
import de.winlaufen.web.bridge.output.WebSocketOutputAdapter;
import de.winlaufen.web.bridge.source.winlaufen.WinLaufenProtocolReader;
import de.winlaufen.web.bridge.startlist.*;
import de.winlaufen.web.bridge.state.CanonicalStateStore;
import de.winlaufen.web.contract.*;
import de.winlaufen.web.liveserver.state.*;
import de.winlaufen.web.liveserver.web.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Isolated original protocol -> bridge WS -> live server -> HTTP/browser, no installed services. */
public class FinishHistorySmoke {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String[] HEADERS = {"Rang", "StNr", "Laufzeit"};
    static final String[][][] TABLES = {
        {{"1", "12", "0:30:00,1"}},
        {{"1", "A12", "0:29:00,0"}, {"2", "12", "0:30:00,1"}},
        {{"1", "A12", "0:29:00,0"}, {"2", "0012", "0:29:30,0"}, {"3", "12", "0:30:00,1"}}
    };
    static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    static void await(BooleanSupplier condition) throws Exception {
        long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > limit) throw new AssertionError("Delivery timeout");
            Thread.sleep(10);
        }
    }
    static void feed(CanonicalStateStore store, String[][][] tables) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) {
            for (int i = 0; i < tables.length; i++) {
                for (Object field : new Object[]{"Standardwettkampf", 1, 1,
                        new String[]{"Testklasse"}, new int[]{0}, 0, 0, 0, i == 2 ? 1 : 0})
                    output.writeObject(field);
                for (String[] row : tables[i]) output.writeObject(row);
                output.writeObject("tabelle"); output.writeObject(HEADERS); output.writeObject("ende");
            }
        }
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var reader = new WinLaufenProtocolReader(input, ignored -> {}, store::result);
            for (int i = 0; i < tables.length; i++) reader.readNext();
        }
    }
    static JsonNode read(PublicHttpServer server, String path) throws Exception {
        try (var http = HttpClient.newBuilder().proxy(new ProxySelector() {
            public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
            public void connectFailed(URI uri, SocketAddress address, IOException error) { }
        }).build()) {
            return JSON.readTree(http.send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + server.port() + path)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body());
        }
    }
    static void save(String[] args, String name, JsonNode value) throws Exception {
        if (args.length == 1) Files.writeString(Path.of(args[0], name + ".json"), value.toString());
    }
    static class Browser extends WebSocketClient {
        final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        Browser(int port) { super(URI.create("ws://127.0.0.1:" + port + "/live/v1"),
                Map.of("Origin", "http://127.0.0.1")); }
        public void onOpen(ServerHandshake h) { }
        public void onClose(int code, String reason, boolean remote) { }
        public void onError(Exception error) { }
        public void onMessage(String message) { messages.add(message); }
    }
    public static void main(String[] args) throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        var published = new PublishedStateStore("local");
        var startlists = new PublishedStartListStore("local");
        var live = new LiveWebSocketServer("127.0.0.1", port, published, startlists, "local", "synthetic-secret");
        var store = new CanonicalStateStore(PresentationConfig.defaults(), null);
        var config = new OutputTargetConfig("test", OutputTargetType.LOCAL, true,
                URI.create("ws://127.0.0.1:" + port + "/bridge/v1/channels/local"), "local", "synthetic-secret");
        live.start(); live.awaitStart();
        try (var http = new PublicHttpServer("127.0.0.1", 0, port, published, startlists);
             var adapter = new WebSocketOutputAdapter(config, "smoke-stream", store.get())) {
            http.start();
            store.addListener(adapter::publish);
            store.health(SourceHealth.CONNECTED);
            adapter.publishStartList(new CanonicalStartList(1, StartListSource.IMPORT_CSV, "synthetic.csv",
                    List.of("0012", "12", "A12").stream().map(bib -> new StartListEntry(bib,
                            "Testklasse", "10:00:00", "Test" + bib, "Test", "", "", "", "", "", "")).toList()));
            adapter.start();
            await(() -> published.get().sourceRevision() == 1 && startlists.get().present());
            save(args, "baseline", read(http, "/api/v1/state"));
            save(args, "startlist", read(http, "/api/v1/startlist"));
            // Hold the live store: the input continues while ingest/ACK is deliberately slow.
            synchronized (published) {
                long before = System.nanoTime();
                feed(store, TABLES);
                check(System.nanoTime() - before < TimeUnit.SECONDS.toNanos(1), "Three results in one second");
                Thread.sleep(150);
            }
            await(() -> published.get().sourceRevision() == store.get().sourceRevision());
            JsonNode state = read(http, "/api/v1/state");
            var hints = state.at("/state/finishHistory/hints");
            check(hints.size() == 3, "All three hints survive slow output and slow live ingest");
            check(hints.get(0).get("row").get(1).asText().equals("12"), "First original pointer");
            check(hints.get(1).get("row").get(1).asText().equals("A12"), "Second original pointer");
            check(hints.get(2).get("row").get(1).asText().equals("0012"), "Third original pointer");
            save(args, "burst", state);
            live.shutdown();
            feed(store, new String[][][]{{{"9", "12", "0:30:00,1"}, {"1", "A12", "0:29:00,0"}, {"2", "0012", "0:29:30,0"}}});
            live = new LiveWebSocketServer("127.0.0.1", port, published, startlists, "local", "synthetic-secret");
            live.start(); live.awaitStart();
            await(() -> published.get().sourceRevision() == store.get().sourceRevision()
                    && published.get().bridgeLinkConnected());
            state = read(http, "/api/v1/state");
            check(state.at("/state/finishHistory/lastSequence").asLong() == 4, "Reconnect retains original correction hint");
            save(args, "reconnect", state);
            var browser = new Browser(port);
            try {
                check(browser.connectBlocking(3, TimeUnit.SECONDS), "Browser reconnect");
                JsonNode snapshot = JSON.readTree(browser.messages.poll(3, TimeUnit.SECONDS));
                check(snapshot.at("/state/finishHistory/hints").size() == 4, "Browser reconnect gets history");
            } finally { browser.closeBlocking(); }
            System.out.println("PASS: original burst, slow ingest, HTTP history, bridge/browser reconnect, original correction");
        } finally { live.shutdown(); }
    }
}
