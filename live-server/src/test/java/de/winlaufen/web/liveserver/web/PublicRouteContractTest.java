package de.winlaufen.web.liveserver.web;

import de.winlaufen.web.liveserver.state.PublishedStartListStore;
import de.winlaufen.web.liveserver.state.PublishedStateStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public surface of the live server, pinned so it cannot grow or shrink unnoticed.
 *
 * <p>An endpoint nobody documented is an endpoint nobody can use and nobody can retire, so this
 * test holds the list from two sides: every route named here has to answer, and every route named
 * here has to appear in {@code docs/API.md}. The check is a plain containment test against the
 * documented endpoint table, not an attempt to parse prose.
 *
 * <p>The reach of that is limited and worth stating. For the live server the list is verified
 * against the running server: a route that stops working fails here, and one that is added without
 * being added to {@link #PUBLIC_ROUTES} is not covered until someone does. The bridge-control paths
 * are only checked for presence in the documentation — this test lives in the live-server module
 * and must not reach into the bridge. A new bridge-control route therefore does not fail this test
 * on its own.
 */
class PublicRouteContractTest {

    /** Every path the live server answers on, other than the WebSocket port. */
    private static final List<String> PUBLIC_ROUTES = List.of(
            "/", "/viewer", "/renderer", "/assets/viewer.css", "/assets/viewer.js",
            "/api/v1/state", "/api/v1/startlist", "/api/v1/runtime");

    private PublicHttpServer server;

    @BeforeEach
    void start() throws Exception {
        server = new PublicHttpServer("127.0.0.1", 0, 44441, new PublishedStateStore("local"),
                new PublishedStartListStore("local"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void everyPublicRouteAnswers() throws Exception {
        for (String route : PUBLIC_ROUTES) {
            int status = send(route).statusCode();
            assertTrue(status == 200 || status == 302, route + " antwortete mit " + status);
        }
    }

    @Test
    void anythingElseIsNotFound() throws Exception {
        for (String route : List.of("/api/v1/config", "/api/v1/status", "/api/v2/state",
                "/dashboard", "/bridge/v1/channels/local", "/live/v1")) {
            assertEquals(404, send(route).statusCode(), route);
        }
    }

    /** A new public route must not stay undocumented. */
    @Test
    void everyPublicRouteIsDocumented() throws Exception {
        String api = Files.readString(repositoryRoot().resolve("docs/API.md"));
        for (String route : PUBLIC_ROUTES) {
            if (route.equals("/")) {
                continue;   // named as "GET /" in the table, too short to match usefully
            }
            assertTrue(api.contains(route), "docs/API.md nennt " + route + " nicht");
        }
        for (String documented : List.of("/live/v1", "/bridge/v1/channels/", "/api/v1/config",
                "/api/v1/status", "44440", "44441", "44442")) {
            assertTrue(api.contains(documented), "docs/API.md nennt " + documented + " nicht");
        }
    }

    private HttpResponse<String> send(String path) throws Exception {
        return HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
                .send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                        .GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        return Files.exists(here.resolve("docs/API.md")) ? here : here.getParent();
    }
}
