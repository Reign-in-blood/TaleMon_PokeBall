package com.reigninblood.TaleMon_PokeBall.util;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PokemonList {

    private static final String RESOURCE_PATH = "Server/NPC/Groups/Pokemon.json";

    private static volatile Set<String> cached = null;

    private PokemonList() {}

    public static boolean isPokemon(String roleName) {
        if (roleName == null || roleName.isEmpty()) return false;
        Set<String> set = getOrLoad();
        return set.contains(roleName);
    }

    public static void reload() {
        cached = null;
    }

    private static Set<String> getOrLoad() {
        if (cached != null) return cached;

        Set<String> result = new HashSet<>();
        try {
            ClassLoader cl = PokemonList.class.getClassLoader();
            InputStream in = cl.getResourceAsStream(RESOURCE_PATH);

            if (in == null) {
                HytaleLogger.getLogger().at(Level.INFO).log(
                        "[TaleMon_PokeBall] PokemonList: resource not found: %s",
                        RESOURCE_PATH
                );
                cached = result;
                return cached;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }

            Pattern p = Pattern.compile("\"IncludeRoles\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL);
            Matcher m = p.matcher(sb.toString());
            if (!m.find()) {
                HytaleLogger.getLogger().at(Level.INFO).log(
                        "[TaleMon_PokeBall] PokemonList: IncludeRoles not found in %s",
                        RESOURCE_PATH
                );
                cached = result;
                return cached;
            }

            String inside = m.group(1);
            Pattern s = Pattern.compile("\"(.*?)\"");
            Matcher ms = s.matcher(inside);
            while (ms.find()) {
                String name = ms.group(1);
                if (name != null && !name.isBlank()) {
                    result.add(name.trim());
                }
            }

            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] PokemonList: loaded %s roles from %s",
                    result.size(), RESOURCE_PATH
            );

        } catch (Throwable t) {
            HytaleLogger.getLogger().at(Level.INFO).log(
                    "[TaleMon_PokeBall] PokemonList: load failed: %s",
                    String.valueOf(t)
            );
        }

        cached = result;
        return cached;
    }
}
