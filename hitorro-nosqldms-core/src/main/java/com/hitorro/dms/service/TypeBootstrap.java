/*
 * Copyright (c) 2006-2026 Chris Collins
 */
package com.hitorro.dms.service;

import com.hitorro.jsontypesystem.JsonTypeSystem;
import com.hitorro.jsontypesystem.Type;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts the DMS's bundled JVS type JSONs from the classpath onto
 * disk (under {@code $home/config/types/}) and points
 * {@link JsonTypeSystem} at that directory via the {@code HT_BIN}
 * system property.
 *
 * <p>Why extract to disk? {@link JsonTypeSystem} discovers types
 * through {@code EnvBaseFiles.getBinConfigBaseFile()} which resolves
 * from {@code HT_BIN} on the filesystem — it doesn't have a
 * classpath scanner. Rather than fork the type system, we materialise
 * the bundle at boot so JVS can find both the core types
 * ({@code core_sysobject}, {@code core_id}, {@code core_mls}, …)
 * and the DMS-specific ones ({@code dms_document}, {@code dms_task},
 * …).</p>
 *
 * <p>Idempotent: running twice with the same home is a no-op that
 * re-copies the classpath resources (so upgrading the jar picks up
 * the new bundled versions on next boot).</p>
 */
public final class TypeBootstrap {

    private static final String[] BUNDLED = {
            // Core types (transitively needed by the DMS types below).
            "core_sysobject.json",
            "core_id.json",
            "core_mls.json",
            "core_mlselem.json",
            "core_dates.json",
            "core_date.json",
            "core_string.json",
            "core_long.json",
            "core_double.json",
            "core_boolean.json",
            "core_url.json",
            // DMS domain types — the reason this project exists.
            "dms_content_ref.json",
            "dms_document.json",
            "dms_wiki_page.json",
            "dms_task.json",
            "dms_contact.json",
            "dms_folder.json",
    };

    private TypeBootstrap() { }

    /**
     * Extract classpath types under {@code $home/config/types/} and
     * set {@code HT_BIN} so {@link JsonTypeSystem#getMe()} finds
     * them. Returns the list of extracted type names.
     */
    public static List<String> bootstrap(Path home) {
        Path typesDir = home.resolve("config").resolve("types");
        try { Files.createDirectories(typesDir); }
        catch (IOException e) { throw new UncheckedIOException(e); }

        List<String> names = new ArrayList<>();
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = TypeBootstrap.class.getClassLoader();
        for (String file : BUNDLED) {
            String cp = "config/types/" + file;
            try (InputStream in = cl.getResourceAsStream(cp)) {
                if (in == null) {
                    System.err.println("[TypeBootstrap] missing classpath resource: " + cp);
                    continue;
                }
                Files.copy(in, typesDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                names.add(file.replace(".json", ""));
            } catch (IOException e) {
                System.err.println("[TypeBootstrap] failed to extract " + file + ": " + e.getMessage());
            }
        }

        // Point JsonTypeSystem at our on-disk types dir.
        System.setProperty("HT_BIN", home.toString());
        System.setProperty("ht.bin", home.toString());
        return names;
    }

    /** Discover a Type by name via JsonTypeSystem. Convenience wrapper. */
    public static Type type(String name) {
        return JsonTypeSystem.getMe().getType(name);
    }
}
