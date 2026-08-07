/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 * Copyright (C) 2026 Dream Server contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.dream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/// Downloads and validates a Dream Server client manifest before handing its
/// standard modpack archive to HMCL's existing installation wizard.
///
/// The service deliberately owns no Minecraft installation logic. It only
/// validates a small HTTPS manifest and its SHA-256-pinned archive, then lets
/// the upstream HMCL implementation create and maintain the game instance.
@NotNullByDefault
public final class DreamManifestService {
    /// Command-line option that points to an HTTPS Dream manifest.
    public static final String ARGUMENT = "--dream-manifest";

    /// Maximum accepted manifest size in bytes.
    private static final int MAXIMUM_MANIFEST_BYTES = 1024 * 1024;

    /// HTTP client that refuses redirects so a trusted HTTPS URL cannot be
    /// silently redirected to a less secure endpoint.
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /// Utility class; instances are not needed.
    private DreamManifestService() {
    }

    /// Finds a Dream manifest option in application arguments.
    ///
    /// @param arguments raw application arguments
    /// @return the supplied manifest URL, or `null` when no complete option is present
    public static @Nullable String findManifestUrl(List<String> arguments) {
        for (int index = 0; index + 1 < arguments.size(); index++) {
            if (ARGUMENT.equals(arguments.get(index))) {
                return arguments.get(index + 1);
            }
        }
        return null;
    }

    /// Starts downloading and importing the bundle referenced by a manifest.
    ///
    /// @param manifestUrl HTTPS URL of a Dream Server manifest
    public static void install(String manifestUrl) {
        Task.supplyAsync(Schedulers.io(), () -> downloadBundle(manifestUrl))
                .whenComplete(Schedulers.javafx(), (archive, exception) -> {
                    if (exception != null) {
                        Controllers.dialog("Dream server synchronization failed.\n" + describeFailure(exception),
                                "Dream Launcher", MessageDialogPane.MessageType.ERROR);
                        return;
                    }

                    ModpackInstallWizardProvider provider = new ModpackInstallWizardProvider(
                            GameDirectoryManager.getSelectedRepository(), archive);
                    Controllers.getDecorator().startWizard(provider, "Install Dream server client environment");
                })
                .start();
    }

    /// Downloads, parses, validates, and stores the bundle declared by one manifest.
    ///
    /// @param manifestUrl HTTPS URL of a Dream Server manifest
    /// @return the verified modpack archive, retained until JVM shutdown for the install wizard
    /// @throws IOException if the manifest or archive cannot be downloaded or validated
    private static Path downloadBundle(String manifestUrl) throws IOException {
        URI manifestUri = requireHttpsUri(manifestUrl);
        byte @NotNull [] rawManifest = request(manifestUri, MAXIMUM_MANIFEST_BYTES);
        DreamBundle bundle = parseBundle(rawManifest);
        Path archive = Files.createTempFile("dream-server-", "." + bundle.extension());
        try {
            downloadArchive(bundle, archive);
            archive.toFile().deleteOnExit();
            return archive;
        } catch (IOException exception) {
            Files.deleteIfExists(archive);
            throw exception;
        }
    }

    /// Sends one GET request without following redirects.
    ///
    /// @param uri HTTPS resource to request
    /// @param maximumBytes inclusive size cap, or a negative value for no cap
    /// @return response bytes
    /// @throws IOException if the request fails, redirects, or exceeds the cap
    private static byte @NotNull [] request(URI uri, int maximumBytes) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json, application/octet-stream")
                .GET()
                .build();
        try {
            HttpResponse<byte @NotNull []> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Dream endpoint returned HTTP " + response.statusCode() + ".");
            }
            byte @NotNull [] body = response.body();
            if (maximumBytes >= 0 && body.length > maximumBytes) {
                throw new IOException("Dream manifest is larger than the allowed size.");
            }
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Dream synchronization was interrupted.", exception);
        }
    }

    /// Streams one bundle to disk while enforcing its declared size and SHA-256 digest.
    ///
    /// @param bundle validated bundle metadata
    /// @param destination temporary archive location
    /// @throws IOException if the download fails or differs from the manifest
    private static void downloadArchive(DreamBundle bundle, Path destination) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(bundle.url())
                .header("Accept", "application/octet-stream")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream input = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Dream endpoint returned HTTP " + response.statusCode() + ".");
                }
                long advertisedLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
                if (advertisedLength >= 0 && advertisedLength != bundle.size()) {
                    throw new IOException("Dream bundle length does not match the manifest.");
                }

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                long downloaded = 0;
                try (var output = Files.newOutputStream(destination)) {
                    byte @NotNull [] buffer = new byte[32 * 1024];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        downloaded += read;
                        if (downloaded > bundle.size()) {
                            throw new IOException("Dream bundle is larger than declared in the manifest.");
                        }
                        digest.update(buffer, 0, read);
                        output.write(buffer, 0, read);
                    }
                }
                if (downloaded != bundle.size() || !HexFormat.of().formatHex(digest.digest()).equals(bundle.sha256())) {
                    throw new IOException("The downloaded modpack does not match the Dream manifest.");
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Dream synchronization was interrupted.", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable in this Java runtime.", exception);
        }
    }

    /// Parses the minimum v1 bundle fields from a manifest document.
    ///
    /// @param rawManifest UTF-8 JSON manifest bytes
    /// @return the validated bundle location and integrity metadata
    /// @throws IOException if the document does not conform to Dream manifest v1
    private static DreamBundle parseBundle(byte @NotNull [] rawManifest) throws IOException {
        try {
            JsonObject manifest = JsonParser.parseString(new String(rawManifest, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!"dream.server-manifest".equals(requiredString(manifest, "format")) || manifest.get("schema").getAsInt() != 1) {
                throw new IOException("Unsupported Dream manifest format.");
            }
            JsonElement bundleElement = manifest.get("bundle");
            if (bundleElement == null || !bundleElement.isJsonObject()) {
                throw new IOException("Dream manifest does not contain a bundle.");
            }

            JsonObject bundle = bundleElement.getAsJsonObject();
            String format = requiredString(bundle, "format");
            String extension = switch (format) {
                case "mrpack" -> "mrpack";
                case "curseforge" -> "zip";
                default -> throw new IOException("Unsupported Dream bundle format: " + format + ".");
            };
            String sha256 = requiredString(bundle, "sha256").toLowerCase();
            if (!sha256.matches("[0-9a-f]{64}")) {
                throw new IOException("Dream bundle SHA-256 is malformed.");
            }
            JsonElement sizeElement = bundle.get("size");
            if (sizeElement == null || !sizeElement.isJsonPrimitive() || sizeElement.getAsLong() <= 0) {
                throw new IOException("Dream bundle size is missing or invalid.");
            }
            return new DreamBundle(requireHttpsUri(requiredString(bundle, "url")), sha256, extension, sizeElement.getAsLong());
        } catch (RuntimeException exception) {
            throw new IOException("Dream manifest is not valid JSON.", exception);
        }
    }

    /// Reads a mandatory non-blank JSON string field.
    ///
    /// @param object JSON object that owns the field
    /// @param name field name
    /// @return the non-blank value
    /// @throws IOException if the field is missing or invalid
    private static String requiredString(JsonObject object, String name) throws IOException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new IOException("Dream manifest field '" + name + "' is missing.");
        }
        String string = value.getAsString().trim();
        if (string.isEmpty()) {
            throw new IOException("Dream manifest field '" + name + "' is empty.");
        }
        return string;
    }

    /// Validates that a text value is an absolute HTTPS URI with a host.
    ///
    /// @param value URI text
    /// @return parsed HTTPS URI
    /// @throws IOException if the URI is unsuitable for a remotely supplied bundle
    private static URI requireHttpsUri(String value) throws IOException {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IOException("Dream downloads must use an absolute HTTPS URL.");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IOException("Dream manifest contains an invalid URL.", exception);
        }
    }

    /// Produces a concise error message suitable for a launcher dialog.
    ///
    /// @param exception asynchronous task failure
    /// @return a human-readable failure description
    private static String describeFailure(Throwable exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    /// Immutable description of a modpack bundle accepted by the v1 protocol.
    ///
    /// @param url HTTPS URL of the archive
    /// @param sha256 lowercase SHA-256 digest expected for the archive
    /// @param extension file extension used by the upstream importer
    /// @param size exact expected archive size in bytes
    @NotNullByDefault
    private record DreamBundle(URI url, String sha256, String extension, long size) {
    }
}
